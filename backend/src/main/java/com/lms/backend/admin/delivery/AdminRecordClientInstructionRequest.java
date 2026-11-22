package com.lms.backend.admin.delivery;

/** The client request, identity check and pending request seen by the Admin. */
public record AdminRecordClientInstructionRequest(
        String adminRecordClientInstructionType,
        Boolean adminRecordClientInstructionIdentityIsConfirmed,
        String adminRecordClientInstructionIdentityCheckMethod,
        Long adminRecordClientInstructionExpectedPendingInstructionID) {
}
