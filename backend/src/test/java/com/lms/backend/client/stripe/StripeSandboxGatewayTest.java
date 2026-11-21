package com.lms.backend.client.stripe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.lms.backend.client.order.CheckoutProviderVerification.Outcome;
import com.lms.backend.shared.error.SharedRuleException;
import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.checkout.SessionCreateParams;

import tools.jackson.databind.json.JsonMapper;

class StripeSandboxGatewayTest {

    private final StripeClient stripe = mock(StripeClient.class, RETURNS_DEEP_STUBS);
    private final JsonMapper json = JsonMapper.builder().build();
    private final StripeSandboxGateway gateway = new StripeSandboxGateway(stripe, json, "whsec_fixture",
            "http://localhost:8081/shared/shared-welcome.html", "stripe-tests");

    @Test
    void createsOneTimeHkdSdkRequestWithExactAmountAndStableKey() throws Exception {
        long before = Instant.now().getEpochSecond();
        gateway.create(17, new BigDecimal("12.34"));
        var params = ArgumentCaptor.forClass(SessionCreateParams.class);
        var options = ArgumentCaptor.forClass(RequestOptions.class);
        verify(stripe.v1().checkout().sessions()).create(params.capture(), options.capture());

        assertEquals("lms-checkout-stripe-tests-17", options.getValue().getIdempotencyKey());
        var request = params.getValue();
        assertEquals(SessionCreateParams.Mode.PAYMENT, request.getMode());
        assertEquals(List.of(SessionCreateParams.AllowedPaymentMethodType.CARD), request.getAllowedPaymentMethodTypes());
        assertFalse(request.getAdaptivePricing().getEnabled());
        assertEquals("17", request.getClientReferenceId());
        assertEquals("17", request.getMetadata().get("lms_checkout_id"));
        assertEquals("stripe-tests", request.getMetadata().get("lms_checkout_scope"));
        assertTrue(request.getExpiresAt() >= before + 1800);
        var line = request.getLineItems().getFirst();
        assertEquals(1L, line.getQuantity());
        assertEquals("hkd", line.getPriceData().getCurrency());
        assertEquals(1234L, line.getPriceData().getUnitAmount());
    }

    @Test
    void usesExactMinorUnitsAndRejectsFractionalMinorUnits() {
        assertEquals(400, StripeSandboxGateway.minorUnits(new BigDecimal("4.00")));
        assertEquals(99_999_999, StripeSandboxGateway.minorUnits(new BigDecimal("999999.99")));
        assertThrows(SharedRuleException.class, () -> StripeSandboxGateway.minorUnits(new BigDecimal("4.001")));
    }

    @Test
    void keepsDeclinedOrUncertainIntentUnresolvedUntilTerminalClosure() {
        var session = new Session();
        session.setStatus("open");
        session.setPaymentStatus("unpaid");
        var intent = new PaymentIntent();
        intent.setId("pi_fixture");
        intent.setLivemode(false);
        intent.setCurrency("hkd");
        intent.setAmountReceived(0L);
        intent.setStatus("requires_payment_method");
        session.setPaymentIntentObject(intent);

        assertEquals(Outcome.UNRESOLVED, gateway.outcome(session).outcome());
        session.setStatus("expired");
        assertEquals(Outcome.UNRESOLVED, gateway.outcome(session).outcome());
        intent.setStatus("canceled");
        assertEquals(Outcome.ENDED_UNPAID, gateway.outcome(session).outcome());
    }

    @Test
    void rejectsWrongSandboxSessionBindingAndUntrustedIntent() {
        var session = new Session();
        session.setId("cs_test_fixture17");
        session.setLivemode(false);
        session.setMode("payment");
        session.setCurrency("hkd");
        session.setClientReferenceId("17");
        session.setMetadata(Map.of("lms_checkout_id", "17", "lms_checkout_scope", "stripe-tests"));
        gateway.verifyBinding(17, "cs_test_fixture17", session);
        assertThrows(IllegalStateException.class, () -> gateway.verifyBinding(17, "cs_test_another17", session));
        assertThrows(IllegalStateException.class, () -> gateway.verifyBinding(18, "cs_test_fixture17", session));
        session.setLivemode(true);
        assertThrows(IllegalStateException.class, () -> gateway.verifyBinding(17, "cs_test_fixture17", session));
        session.setLivemode(false);
        session.setCurrency("usd");
        assertThrows(IllegalStateException.class, () -> gateway.verifyBinding(17, "cs_test_fixture17", session));
        session.setCurrency("hkd");
        session.setMetadata(Map.of("lms_checkout_id", "17", "lms_checkout_scope", "other-stripe-tests"));
        assertThrows(IllegalStateException.class, () -> gateway.verifyBinding(17, "cs_test_fixture17", session));

        var intent = new PaymentIntent();
        intent.setId("pi_fixture17");
        intent.setLivemode(true);
        intent.setCurrency("hkd");
        session.setPaymentIntentObject(intent);
        assertThrows(IllegalStateException.class, () -> gateway.outcome(session));
        intent.setLivemode(false);
        intent.setCurrency("usd");
        assertThrows(IllegalStateException.class, () -> gateway.outcome(session));
        intent.setCurrency("hkd");
        intent.setId("not_a_payment_intent");
        session.setPaymentIntentObject(intent);
        assertThrows(IllegalStateException.class, () -> gateway.outcome(session));
    }

    @Test
    void rejectsLiveCredentialsBeforeOpeningAProviderClient() {
        assertThrows(IllegalStateException.class, () -> new StripeSandboxConfiguration().stripeSandboxGateway(
                json, null, "sk_live_fixture", "whsec_fixture", "http://localhost:8081/", "stripe-tests"));
    }
}
