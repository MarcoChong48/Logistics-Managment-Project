package com.lms.backend.shared.error;

/** A stable error code and a short message that the page can show. */
public record SharedErrorResponse(String code, String message) {
}
