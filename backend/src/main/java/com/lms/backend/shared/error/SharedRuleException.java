package com.lms.backend.shared.error;

import org.springframework.http.HttpStatus;

public class SharedRuleException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    /**
     * What it does: Describes a refused action in a form the error handler can return.
     * Input: The HTTP status, error code and message safe to show on a page.
     * Output: A rule exception carrying the three values.
     */
    public SharedRuleException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    /**
     * What it does: Supplies the HTTP status of the refused action.
     * Input: None.
     * Output: The status set when the exception was created.
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * What it does: Supplies the error code of the refused action.
     * Input: None.
     * Output: The code set when the exception was created.
     */
    public String getCode() {
        return code;
    }
}
