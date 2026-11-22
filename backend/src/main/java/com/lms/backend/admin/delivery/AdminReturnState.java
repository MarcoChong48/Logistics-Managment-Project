package com.lms.backend.admin.delivery;

import java.time.Instant;

/** The confirmed return receipt and the failure it followed. */
public record AdminReturnState(
        boolean adminFindReturnStateIsReturned,
        Instant adminFindReturnStateReturnedTime,
        Long adminFindReturnStateReturnCustodyEventID,
        Instant adminFindReturnStateLatestFailureTime) {
}
