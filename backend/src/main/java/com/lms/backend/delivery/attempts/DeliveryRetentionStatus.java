package com.lms.backend.delivery.attempts;

import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("delivery-full")
public class DeliveryRetentionStatus {
    private volatile Check lastCheck;

    void finish(boolean successful) {
        lastCheck = new Check(Instant.now(), successful);
    }

    public Check lastCheck() {
        return lastCheck;
    }

    public record Check(Instant finishedAt, boolean successful) {}
}
