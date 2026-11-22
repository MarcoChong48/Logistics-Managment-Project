package com.lms.backend.admin.delivery;

/** The advisory flag for a returned package whose client has yet to send a request. */
public enum AdminReviewWaitingFlag {
    NONE,
    REMINDER_DUE,
    FINAL_REVIEW
}
