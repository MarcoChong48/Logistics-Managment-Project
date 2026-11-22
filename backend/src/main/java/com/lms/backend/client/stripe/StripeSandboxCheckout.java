package com.lms.backend.client.stripe;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.lms.backend.client.order.ClientCheckoutResponse;
import com.lms.backend.client.order.ClientCheckoutService;
import com.lms.backend.client.stripe.StripeSandboxGateway.SignedSessionEvent;
import com.lms.backend.shared.database.CheckoutPaymentRepository;
import com.lms.backend.shared.database.CustomerOrderReleaseRepository;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;

@Service
@ConditionalOnProperty(name = "lms.client.stripe.enabled", havingValue = "true")
public class StripeSandboxCheckout {

    private static final Logger LOG = LoggerFactory.getLogger(StripeSandboxCheckout.class);

    private final ClientCheckoutService checkout;
    private final CheckoutPaymentRepository payments;
    private final CustomerOrderRepository orders;
    private final CustomerOrderReleaseRepository orderRows;
    private final StripeSandboxRepository stripeRows;
    private final StripeSandboxGateway stripe;
    private final TransactionTemplate transaction;

    StripeSandboxCheckout(ClientCheckoutService checkout, CheckoutPaymentRepository payments,
            CustomerOrderRepository orders, CustomerOrderReleaseRepository orderRows,
            StripeSandboxRepository stripeRows, StripeSandboxGateway stripe, PlatformTransactionManager transactions) {
        this.checkout = checkout;
        this.payments = payments;
        this.orders = orders;
        this.orderRows = orderRows;
        this.stripeRows = stripeRows;
        this.stripe = stripe;
        this.transaction = new TransactionTemplate(transactions);
    }

    @Transactional(propagation = Propagation.NEVER)
    public ClientCheckoutResponse start(String orderReference) {
        var local = checkout.startCheckout(orderReference, StripeSandboxGateway::minorUnits);
        long id = local.checkoutId();
        try {
            var existing = stripeRows.sessionId(id);
            Session session;
            if (existing.isPresent()) {
                session = stripe.retrieve(existing.get());
                stripe.verifyBinding(id, existing.get(), session);
            } else {
                // Commit the single creation claim before the call. An ambiguous call is never retried as a create.
                if (!Boolean.TRUE.equals(transaction.execute(status -> entryOpen(id) && stripeRows.claimCreation(id)))) {
                    return local;
                }
                session = stripe.create(id, local.amountHkd());
                stripe.verifyBinding(id, session.getId(), session);
                stripeRows.bindSession(id, session.getId());
            }
            String url = usableUrl(session, StripeSandboxGateway.minorUnits(local.amountHkd()));
            if (url != null && Boolean.TRUE.equals(transaction.execute(status -> entryOpen(id)))) {
                return new ClientCheckoutResponse(id, local.orderReference(), local.amountHkd(),
                        local.checkoutExpiresAt(), true, url);
            }
        } catch (StripeException | IllegalStateException failure) {
            LOG.warn("Stripe sandbox checkout {} requires reconciliation ({})", id, failure.getClass().getSimpleName());
        }
        return local;
    }

    void receive(SignedSessionEvent event) {
        if (stripeRows.enqueue(event)) {
            processEvent(event);
        }
    }

    @Scheduled(fixedDelayString = "${lms.client.stripe.reconciliation-interval:PT15S}",
            initialDelayString = "${lms.client.stripe.reconciliation-interval:PT15S}")
    void sweep() {
        for (var event : stripeRows.pendingEvents()) {
            processEvent(event);
        }
        for (var link : stripeRows.dueSessions()) {
            stripeRows.markSessionChecked(link.checkoutId());
            try {
                reconcile(link.checkoutId(), link.sessionId(), link.entryCanClose());
            } catch (StripeException | RuntimeException failure) {
                LOG.warn("Will retry Stripe sandbox checkout {} ({})", link.checkoutId(),
                        failure.getClass().getSimpleName());
            }
        }
    }

    private void processEvent(SignedSessionEvent event) {
        stripeRows.markEventChecked(event.eventId());
        try {
            reconcile(event.checkoutId(), event.sessionId(), false);
            stripeRows.markEventProcessed(event.eventId());
        } catch (StripeException | RuntimeException failure) {
            LOG.warn("Will retry Stripe sandbox event {} ({})", event.eventId(), failure.getClass().getSimpleName());
        }
    }

    private void reconcile(long id, String sessionId, boolean closeOpenSession) throws StripeException {
        var session = stripe.retrieve(sessionId);
        stripe.verifyBinding(id, sessionId, session);
        stripeRows.bindSession(id, sessionId);
        if (closeOpenSession && "open".equals(session.getStatus())) {
            try {
                stripe.expire(sessionId);
            } catch (StripeException failure) {
                if (!Objects.equals(400, failure.getStatusCode())) {
                    throw failure;
                }
                // Payment or provider expiry may have won the race. Read the current state below.
            }
            session = stripe.retrieve(sessionId);
            stripe.verifyBinding(id, sessionId, session);
        }
        var verified = stripe.outcome(session);
        checkout.reconcile(id, checkoutId -> verified);
    }

    private boolean entryOpen(long checkoutId) {
        long orderId = payments.findOrderId(checkoutId).orElseThrow();
        var order = orders.findOrderForPaymentChange(orderId).orElseThrow();
        var attempt = payments.findAttempt(orderId, checkoutId).orElseThrow();
        var now = orderRows.databaseTime();
        return order.canAcceptPaymentAt(now) && attempt.isPending() && !attempt.entryClosed()
                && attempt.entryDeadline() != null && attempt.entryDeadline().isAfter(now);
    }

    private String usableUrl(Session session, long amount) {
        if (!"open".equals(session.getStatus()) || !"unpaid".equals(session.getPaymentStatus())
                || !Objects.equals(session.getAmountTotal(), amount) || session.getUrl() == null
                || session.getExpiresAt() == null || session.getExpiresAt() <= Instant.now().getEpochSecond()) {
            return null;
        }
        try {
            URI url = URI.create(session.getUrl());
            return "https".equalsIgnoreCase(url.getScheme()) && "checkout.stripe.com".equalsIgnoreCase(url.getHost())
                    && url.getRawUserInfo() == null ? session.getUrl() : null;
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }
}
