package com.lms.backend.client.stripe;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.lms.backend.client.order.CheckoutProviderVerification.Outcome;
import com.lms.backend.client.order.CheckoutProviderVerification.VerifiedCheckout;
import com.lms.backend.shared.error.SharedRuleException;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import com.stripe.param.checkout.SessionExpireParams;
import com.stripe.param.checkout.SessionRetrieveParams;

import tools.jackson.databind.ObjectMapper;

class StripeSandboxGateway {

    private static final String PAYMENT_ID = "lms_checkout_id";
    private static final String CHECKOUT_SCOPE = "lms_checkout_scope";
    private static final Set<String> SESSION_EVENTS = Set.of("checkout.session.completed", "checkout.session.expired");
    private static final long MIN_HKD_MINOR = 400;
    private static final long MAX_HKD_MINOR = 99_999_999;

    private final StripeClient client;
    private final ObjectMapper json;
    private final String signingSecret;
    private final String returnUrl;
    private final String scope;

    StripeSandboxGateway(StripeClient client, ObjectMapper json, String signingSecret, String returnUrl, String scope) {
        this.client = client;
        this.json = json;
        this.signingSecret = signingSecret;
        this.returnUrl = returnUrl;
        this.scope = scope;
    }

    static long minorUnits(BigDecimal hkd) {
        try {
            long minor = hkd.movePointRight(2).longValueExact();
            if (minor >= MIN_HKD_MINOR && minor <= MAX_HKD_MINOR) {
                return minor;
            }
        } catch (ArithmeticException invalidPrecision) {
            // Amounts that cannot be represented exactly are ineligible.
        }
        throw new SharedRuleException(HttpStatus.CONFLICT, "CLIENT_STRIPE_AMOUNT_UNSUPPORTED",
                "Stripe sandbox requires an exact order total between HK$4.00 and HK$999,999.99.");
    }

    Session create(long checkoutId, BigDecimal amount) throws StripeException {
        String reference = Long.toString(checkoutId);
        var price = SessionCreateParams.LineItem.PriceData.builder().setCurrency("hkd")
                .setUnitAmount(minorUnits(amount)).setProductData(
                        SessionCreateParams.LineItem.PriceData.ProductData.builder().setName("LMS order").build());
        var params = SessionCreateParams.builder().setMode(SessionCreateParams.Mode.PAYMENT)
                .addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.CARD)
                .setAdaptivePricing(SessionCreateParams.AdaptivePricing.builder().setEnabled(false).build())
                .setClientReferenceId(reference).putMetadata(PAYMENT_ID, reference)
                .putMetadata(CHECKOUT_SCOPE, scope)
                .setSuccessUrl(returnUrl).setCancelUrl(returnUrl)
                // Stripe's native backstop needs margin above the 30-minute provider minimum.
                .setExpiresAt(Instant.now().plus(Duration.ofMinutes(31)).getEpochSecond())
                .addLineItem(SessionCreateParams.LineItem.builder().setPriceData(price.build()).setQuantity(1L).build())
                .build();
        var options = RequestOptions.builder().setIdempotencyKey("lms-checkout-" + scope + "-" + checkoutId).build();
        return client.v1().checkout().sessions().create(params, options);
    }

    Session retrieve(String sessionId) throws StripeException {
        return client.v1().checkout().sessions().retrieve(sessionId,
                SessionRetrieveParams.builder().addExpand("payment_intent").build());
    }

    void expire(String sessionId) throws StripeException {
        client.v1().checkout().sessions().expire(sessionId, SessionExpireParams.builder().build());
    }

    void verifyBinding(long checkoutId, String sessionId, Session session) {
        String reference = Long.toString(checkoutId);
        if (!validSessionId(sessionId) || !sessionId.equals(session.getId()) || !Boolean.FALSE.equals(session.getLivemode())
                || !"payment".equals(session.getMode()) || !"hkd".equals(session.getCurrency())
                || !reference.equals(session.getClientReferenceId()) || session.getMetadata() == null
                || !reference.equals(session.getMetadata().get(PAYMENT_ID))
                || !scope.equals(session.getMetadata().get(CHECKOUT_SCOPE))) {
            throw new IllegalStateException("Unexpected Stripe sandbox Session identity.");
        }
    }

    VerifiedCheckout outcome(Session session) {
        var intent = session.getPaymentIntentObject();
        if (intent != null && (!Boolean.FALSE.equals(intent.getLivemode()) || !"hkd".equals(intent.getCurrency())
                || intent.getId() == null || !intent.getId().matches("pi_[A-Za-z0-9_]{1,157}")
                || !Objects.equals(session.getPaymentIntent(), intent.getId()))) {
            throw new IllegalStateException("Unexpected Stripe sandbox PaymentIntent identity.");
        }
        if (intent != null && "succeeded".equals(intent.getStatus())) {
            if ("paid".equals(session.getPaymentStatus()) && intent.getAmountReceived() != null
                    && intent.getAmountReceived() > 0) {
                return new VerifiedCheckout(Outcome.CAPTURED, intent.getId(),
                        BigDecimal.valueOf(intent.getAmountReceived(), 2), "HKD");
            }
            return new VerifiedCheckout(Outcome.UNRESOLVED, null, null, null);
        }
        boolean terminalUnpaid = "expired".equals(session.getStatus()) && "unpaid".equals(session.getPaymentStatus())
                && (session.getPaymentIntent() == null || (intent != null && "canceled".equals(intent.getStatus())
                        && Long.valueOf(0).equals(intent.getAmountReceived())));
        return new VerifiedCheckout(terminalUnpaid ? Outcome.ENDED_UNPAID : Outcome.UNRESOLVED, null, null, null);
    }

    SignedSessionEvent parseEvent(String rawBody, String signature) throws StripeException {
        var event = Webhook.constructEvent(rawBody, signature, signingSecret);
        if (!Boolean.FALSE.equals(event.getLivemode()) || event.getAccount() != null || event.getType() == null
                || !SESSION_EVENTS.contains(event.getType())) {
            return null;
        }
        var session = json.readTree(event.getDataObjectDeserializer().getRawJson());
        var metadata = session.path("metadata");
        if (!metadata.has(PAYMENT_ID) || !scope.equals(metadata.path(CHECKOUT_SCOPE).asText())) {
            return null;
        }
        String eventId = event.getId();
        String sessionId = session.path("id").asText();
        long checkoutId = Long.parseLong(metadata.path(PAYMENT_ID).asText());
        if (eventId == null || !eventId.matches("evt_[A-Za-z0-9_]{1,155}") || !validSessionId(sessionId)
                || checkoutId <= 0) {
            throw new IllegalArgumentException("Unexpected Stripe sandbox event identity.");
        }
        return new SignedSessionEvent(eventId, checkoutId, sessionId);
    }

    private boolean validSessionId(String sessionId) {
        return sessionId != null && sessionId.matches("cs_test_[A-Za-z0-9_]{1,152}");
    }

    record SignedSessionEvent(String eventId, long checkoutId, String sessionId) {
    }
}
