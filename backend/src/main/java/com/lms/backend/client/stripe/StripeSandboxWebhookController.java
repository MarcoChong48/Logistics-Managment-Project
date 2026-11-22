package com.lms.backend.client.stripe;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import com.lms.backend.client.stripe.StripeSandboxGateway.SignedSessionEvent;
import com.stripe.exception.StripeException;

@RestController
@ConditionalOnProperty(name = "lms.client.stripe.enabled", havingValue = "true")
class StripeSandboxWebhookController {

    private final StripeSandboxGateway stripe;
    private final StripeSandboxCheckout checkouts;

    StripeSandboxWebhookController(StripeSandboxGateway stripe, StripeSandboxCheckout checkouts) {
        this.stripe = stripe;
        this.checkouts = checkouts;
    }

    @PostMapping(StripeSandboxConfiguration.WEBHOOK)
    ResponseEntity<Void> webhook(@RequestHeader(name = "Stripe-Signature", required = false) String signature,
            @RequestBody byte[] body) {
        if (signature == null) {
            return ResponseEntity.badRequest().build();
        }
        SignedSessionEvent event;
        try {
            // The SDK expects a String. Reject invalid UTF-8 so re-encoding preserves the signed bytes exactly.
            String rawBody = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(body)).toString();
            event = stripe.parseEvent(rawBody, signature);
        } catch (CharacterCodingException | StripeException | RuntimeException invalid) {
            return ResponseEntity.badRequest().build();
        }
        if (event != null) {
            checkouts.receive(event);
        }
        return ResponseEntity.noContent().build();
    }
}
