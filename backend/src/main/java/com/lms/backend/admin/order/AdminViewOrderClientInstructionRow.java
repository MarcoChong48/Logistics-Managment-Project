package com.lms.backend.admin.order;

import java.time.Instant;

// F5.2: public order data for the Admin screens.
public record AdminViewOrderClientInstructionRow(
        Long instructionID,
        String instructionType,
        String status,
        String source,
        Instant submittedTime,
        Long recordedByUserID,
        String adminViewOrderClientInstructionRecordedByUsername,
        Long resolvedByAdminID,
        String adminViewOrderClientInstructionResolvedByUsername,
        Instant resolvedTime,
        String resolutionNote) {
}
