package com.lms.backend.admin.order;

import java.time.Instant;

// F5.3: public order data for the Admin screens.
public record AdminViewOrderTimelineRow(
        Instant adminViewOrderTimelineTime,
        String adminViewOrderTimelineKind,
        String adminViewOrderTimelineText,
        String adminViewOrderTimelineActorUsername) {
}
