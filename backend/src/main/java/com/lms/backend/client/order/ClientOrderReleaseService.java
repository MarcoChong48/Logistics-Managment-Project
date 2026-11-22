package com.lms.backend.client.order;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.admin.placeholder.AdminOrderCancellationPlaceholder;
import com.lms.backend.admin.placeholder.AdminRefundPlaceholder;
import com.lms.backend.shared.database.CheckoutPaymentRepository;
import com.lms.backend.shared.database.CustomerOrder;
import com.lms.backend.shared.database.CustomerOrderReleaseRepository;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.value.SharedOrderStatus;

@Service
public class ClientOrderReleaseService {

    private static final String CLIENT_CANCELLED = "CLIENT_CANCELLED";
    private static final String PAYMENT_DEADLINE_EXPIRED = "PAYMENT_DEADLINE_EXPIRED";

    private final SharedCurrentUserReader currentUser;
    private final CustomerOrderRepository orders;
    private final CustomerOrderReleaseRepository releases;
    private final CheckoutPaymentRepository payments;
    private final AdminOrderCancellationPlaceholder cancellation;
    private final AdminRefundPlaceholder refunds;

    public ClientOrderReleaseService(SharedCurrentUserReader currentUser, CustomerOrderRepository orders,
            CustomerOrderReleaseRepository releases, CheckoutPaymentRepository payments,
            AdminOrderCancellationPlaceholder cancellation, AdminRefundPlaceholder refunds) {
        this.currentUser = currentUser;
        this.orders = orders;
        this.releases = releases;
        this.payments = payments;
        this.cancellation = cancellation;
        this.refunds = refunds;
    }

    @Transactional
    public ClientCancelOrderResponse cancelOwnOrder(String orderReference) {
        long clientId = currentUser.sharedReadCurrentUser().userID();
        CustomerOrder order = orders.findByOrderReferenceAndClientUserID(orderReference, clientId)
                .orElseThrow(() -> new SharedRuleException(HttpStatus.NOT_FOUND, "CLIENT_ORDER_NOT_FOUND",
                        "Order was not found."));
        if (!SharedOrderStatus.CANCELLED.name().equals(order.orderStatus)) {
            if (!canCancelBeforePicking(order.orderID, order.orderStatus)) {
                throw new SharedRuleException(HttpStatus.CONFLICT, "CLIENT_ORDER_CANCELLATION_NOT_ALLOWED",
                        "Only orders awaiting payment or ready for picking can be cancelled before picking starts.");
            }
            cancellation.cancelClientOrder(order.orderID, clientId, CLIENT_CANCELLED + ": " + order.orderReference);
            refunds.recordClientCancellationRefunds(order.orderID);
        } else {
            payments.markUnverifiedAcceptanceForReconciliation(order.orderID);
        }
        return new ClientCancelOrderResponse(order.orderReference, SharedOrderStatus.CANCELLED);
    }

    boolean canCancelBeforePicking(long orderId, String orderStatus) {
        return (SharedOrderStatus.AWAITING_PAYMENT.name().equals(orderStatus)
                || SharedOrderStatus.READY_FOR_PICKING.name().equals(orderStatus))
                && !releases.hasFulfillmentStarted(orderId);
    }

    @Transactional(timeout = 15)
    public void expireIfDue(long orderId) {
        CustomerOrder order = orders.findUnlockedOrderForExpiry(orderId).orElse(null);
        if (order == null || !order.isAwaitingPayment() || order.canAcceptPaymentAt(releases.databaseTime())
                || releases.hasFulfillmentStarted(orderId) || releases.hasUnlinkedHeldReservations(orderId)) {
            return;
        }
        payments.markUnverifiedAcceptanceForReconciliation(orderId);
        if (!payments.hasAcceptedOrUnresolvedPayment(orderId)) {
            cancellation.cancelClientOrder(orderId, null, PAYMENT_DEADLINE_EXPIRED + ": " + order.orderReference);
        }
    }
}
