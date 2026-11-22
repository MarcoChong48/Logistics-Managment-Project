package com.lms.backend.client.order;

import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PAYMENT_STATUS_REFUND_REQUIRED;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.lms.backend.admin.placeholder.AdminRefundPlaceholder;
import com.lms.backend.client.order.CheckoutProviderVerification.VerifiedCheckout;
import com.lms.backend.shared.database.CheckoutPaymentRepository;
import com.lms.backend.shared.database.CheckoutPaymentRepository.Attempt;
import com.lms.backend.shared.database.CustomerOrder;
import com.lms.backend.shared.database.CustomerOrderReleaseRepository;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.notification.ClientNotificationWriter;
import com.lms.backend.shared.value.SharedOrderStatus;

@Service
public class ClientCheckoutService {

    private static final Duration ENTRY_WINDOW = Duration.ofMinutes(30);

    private final SharedCurrentUserReader currentUser;
    private final CustomerOrderRepository orders;
    private final CustomerOrderReleaseRepository orderRows;
    private final CheckoutPaymentRepository payments;
    private final AdminRefundPlaceholder refunds;
    private final ClientOrderReservations reservations;
    private final ClientNotificationWriter notifications;
    private final TransactionTemplate settlementTransaction;

    public ClientCheckoutService(SharedCurrentUserReader currentUser, CustomerOrderRepository orders,
            CustomerOrderReleaseRepository orderRows, CheckoutPaymentRepository payments,
            ClientOrderReservations reservations, ClientNotificationWriter notifications,
            AdminRefundPlaceholder refunds, PlatformTransactionManager transactions) {
        this.currentUser = currentUser;
        this.orders = orders;
        this.orderRows = orderRows;
        this.payments = payments;
        this.refunds = refunds;
        this.reservations = reservations;
        this.notifications = notifications;
        this.settlementTransaction = new TransactionTemplate(transactions);
        this.settlementTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ClientCheckoutResponse startCheckout(String orderReference, Consumer<BigDecimal> validateProviderAmount) {
        long clientId = currentUser.sharedReadCurrentUser().userID();
        CustomerOrder order = orders.findByOrderReferenceAndClientUserID(orderReference, clientId)
                .orElseThrow(() -> new SharedRuleException(HttpStatus.NOT_FOUND, "CLIENT_ORDER_NOT_FOUND",
                        "Order was not found."));
        requireEligible(order, orderRows.databaseTime());
        if (orderRows.hasFulfillmentStarted(order.orderID) || payments.hasAcceptedOrUnresolvedPayment(order.orderID)) {
            throw checkoutConflict("The order is no longer eligible for checkout.");
        }
        validateProviderAmount.accept(order.totalAmount);

        var pending = payments.findPending(order.orderID);
        if (pending.size() > 1) {
            throw checkoutConflict("Existing checkout attempts require reconciliation.");
        }
        if (!pending.isEmpty()) {
            var attempt = pending.getFirst();
            Instant now = orderRows.databaseTime();
            requireEligible(order, now);
            if (attempt.entryClosed() || attempt.entryDeadline() == null || !attempt.entryDeadline().isAfter(now)
                    || !orderRows.checkoutHoldsAllItems(order.orderID, attempt.paymentId())) {
                throw checkoutConflict("The previous checkout must be resolved before starting another.");
            }
            return response(order, attempt);
        }

        if (!orderRows.findHeldProductIds(order.orderID, null).isEmpty()) {
            throw checkoutConflict("Existing stock holds require reconciliation before checkout.");
        }
        var items = orderRows.findCheckoutItems(order.orderID);
        if (items.isEmpty()) {
            throw checkoutConflict("This order has no items to reserve.");
        }
        var balances = reservations.lockAvailableStock(items);
        Instant startedAt = orderRows.databaseTime();
        requireEligible(order, startedAt);
        Instant entryDeadline = startedAt.plus(ENTRY_WINDOW);
        if (order.paymentDeadline.isBefore(entryDeadline)) {
            entryDeadline = order.paymentDeadline;
        }
        var attempt = payments.createPending(order.orderID, order.totalAmount, startedAt, entryDeadline);
        reservations.reserve(order, attempt.paymentId(), items, balances);
        return response(order, attempt);
    }

    // Internal entry point for a trusted adapter. Provider verification runs before taking database locks.
    @Transactional(propagation = Propagation.NEVER)
    public void reconcile(long checkoutId, CheckoutProviderVerification provider) {
        VerifiedCheckout verified = provider.verify(checkoutId);
        settlementTransaction.executeWithoutResult(transaction -> applyVerifiedResult(checkoutId, verified));
    }

    private void applyVerifiedResult(long checkoutId, VerifiedCheckout verified) {
        long orderId = payments.findOrderId(checkoutId).orElseThrow();
        CustomerOrder order = orders.findOrderForPaymentChange(orderId).orElseThrow();
        payments.markUnverifiedAcceptanceForReconciliation(orderId);
        var attempt = payments.findAttempt(orderId, checkoutId).orElseThrow();
        switch (verified.outcome()) {
            case UNRESOLVED, CARD_DECLINED -> {
                // Neither result proves that the checkout has ended unpaid.
            }
            case ENDED_UNPAID -> {
                if (attempt.isPending()) {
                    payments.markEndedUnpaid(checkoutId);
                    reservations.release(order, checkoutId, null, "CHECKOUT_ENDED_UNPAID");
                }
            }
            case CAPTURED -> applyCapture(order, attempt, verified);
        }
    }

    private void applyCapture(CustomerOrder order, Attempt attempt, VerifiedCheckout verified) {
        String reference = verified.providerReference();
        if (reference == null || reference.isBlank() || reference.length() > 160 || !"HKD".equals(verified.currency())
                || verified.capturedAmount() == null
                || (attempt.providerReference() != null && !attempt.providerReference().equals(reference))) {
            throw new IllegalArgumentException("Inconsistent sandbox payment evidence.");
        }
        BigDecimal captured = verified.capturedAmount().setScale(2, RoundingMode.UNNECESSARY);
        if (captured.signum() <= 0 || captured.precision() > 12) {
            throw new IllegalArgumentException("Invalid captured amount.");
        }
        if (attempt.hasAcceptanceTime()) {
            if (captured.compareTo(attempt.amountHkd()) != 0) {
                throw new IllegalArgumentException("Capture amount differs from the accepted payment.");
            }
            return;
        }
        if (attempt.needsAcceptanceReconciliation() && !SharedOrderStatus.CANCELLED.name().equals(order.orderStatus)) {
            // A verified capture alone cannot establish whether fulfilment already started.
            // Keep the review marker for the operator workflow in docs/integration/STEP_2_PAYMENT_RECONCILIATION.md.
            return;
        }

        boolean validAttempt = attempt.isPending() && captured.compareTo(attempt.amountHkd()) == 0
                && captured.compareTo(order.totalAmount) == 0
                && !payments.hasAcceptedOrUnresolvedPayment(order.orderID) && !orderRows.hasFulfillmentStarted(order.orderID)
                && orderRows.checkoutHoldsAllItems(order.orderID, attempt.paymentId());
        Instant acceptedAt = orderRows.databaseTime();
        // Entry cutoff governs issuing checkout access. The locked order deadline governs payment acceptance.
        if (validAttempt && order.canAcceptPaymentAt(acceptedAt)) {
            payments.markAccepted(attempt.paymentId(), reference, acceptedAt);
            order.orderStatus = SharedOrderStatus.READY_FOR_PICKING.name();
            orders.flush();
            if (notifications.paymentConfirmed(order.orderID, attempt.paymentId())
                    == ClientNotificationWriter.Result.EVIDENCE_MISSING) {
                throw new IllegalStateException("Accepted payment notification evidence is missing.");
            }
        } else {
            if (!SHARED_PAYMENT_STATUS_REFUND_REQUIRED.equals(attempt.status()) || attempt.providerReference() == null) {
                payments.markRefundRequired(attempt.paymentId(), reference);
            }
            refunds.recordVerifiedCaptureRefund(attempt.paymentId(), reference, captured);
        }
    }

    private void requireEligible(CustomerOrder order, Instant now) {
        if (!order.canAcceptPaymentAt(now)) {
            throw checkoutConflict("The order is no longer eligible for checkout.");
        }
    }

    private SharedRuleException checkoutConflict(String message) {
        return new SharedRuleException(HttpStatus.CONFLICT, "CLIENT_ORDER_CHECKOUT_NOT_AVAILABLE", message);
    }

    private ClientCheckoutResponse response(CustomerOrder order, Attempt attempt) {
        return new ClientCheckoutResponse(attempt.paymentId(), order.orderReference, attempt.amountHkd(),
                attempt.entryDeadline(), false, null);
    }
}
