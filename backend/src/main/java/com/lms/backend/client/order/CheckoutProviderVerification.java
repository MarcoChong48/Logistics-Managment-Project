package com.lms.backend.client.order;

import java.math.BigDecimal;

@FunctionalInterface
public interface CheckoutProviderVerification {

    // Look up trusted provider state bound to this LMS checkout ID in the selected sandbox account.
    VerifiedCheckout verify(long checkoutId);

    enum Outcome {
        UNRESOLVED, CARD_DECLINED, ENDED_UNPAID, CAPTURED
    }

    record VerifiedCheckout(Outcome outcome, String providerReference, BigDecimal capturedAmount, String currency) {
    }
}
