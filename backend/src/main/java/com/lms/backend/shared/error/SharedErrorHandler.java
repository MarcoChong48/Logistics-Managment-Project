package com.lms.backend.shared.error;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.lms.backend.admin.common.AdminCommonRule;

@RestControllerAdvice
public class SharedErrorHandler extends ResponseEntityExceptionHandler {

    private final AdminCommonRule adminWriteRejectedActionRule;
    private final ObjectProvider<RequestMappingHandlerMapping> adminWriteRejectedActionMappings;

    /**
     * What it does: Connects the error handler to the Admin rejection log and the controller mappings.
     * Input: The common Admin rules and the provider of controller mappings.
     * Output: A handler that can log refusals even when a security filter refused the request.
     */
    public SharedErrorHandler(AdminCommonRule adminWriteRejectedActionRule,
            @Qualifier("requestMappingHandlerMapping")
            ObjectProvider<RequestMappingHandlerMapping> adminWriteRejectedActionMappings) {
        this.adminWriteRejectedActionRule = adminWriteRejectedActionRule;
        this.adminWriteRejectedActionMappings = adminWriteRejectedActionMappings;
    }

    // ===== SECTION 1: FRAMEWORK ERRORS =====

    /**
     * What it does: Changes a web framework error into the shared JSON error shape.
     * Input: The exception, any existing body, framework headers, HTTP status and original request.
     * Output: The shared JSON error, keeping the framework's status and headers.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception sharedHandleErrorException,
            Object sharedHandleErrorOriginalBody, HttpHeaders sharedHandleErrorHeaders,
            HttpStatusCode sharedHandleErrorStatus, WebRequest sharedHandleErrorRequest) {
        // Multipart validation may reject a large catalogue file before the import service runs.
        if (sharedHandleErrorException instanceof MaxUploadSizeExceededException
                && sharedHandleErrorRequest instanceof ServletWebRequest adminImportProductCSVRequest
                && "/api/admin/products/import".equals(adminImportProductCSVRequest.getRequest().getServletPath())) {
            return super.handleExceptionInternal(sharedHandleErrorException,
                    new SharedErrorResponse("ADMIN_IMPORT_FILE_INVALID",
                            "The file must be UTF-8, at most 1 MB, with the required header and at most 500 product lines."),
                    sharedHandleErrorHeaders, HttpStatus.BAD_REQUEST, sharedHandleErrorRequest);
        }
        // Use fixed public messages. Keep headers such as Allow on a 405 response.
        SharedErrorResponse sharedHandleErrorResponse;
        switch (sharedHandleErrorStatus.value()) {
            case 404 -> sharedHandleErrorResponse = new SharedErrorResponse("SHARED_NOT_FOUND",
                    "This web address was not found.");
            case 405 -> sharedHandleErrorResponse = new SharedErrorResponse("SHARED_METHOD_NOT_ALLOWED",
                    "This request method is not allowed at this web address.");
            default -> {
                if (sharedHandleErrorStatus.is4xxClientError()) {
                    sharedHandleErrorResponse = new SharedErrorResponse("SHARED_INPUT_INVALID",
                            "The request could not be read.");
                } else {
                    return handleException(sharedHandleErrorException);
                }
            }
        }
        return super.handleExceptionInternal(sharedHandleErrorException, sharedHandleErrorResponse,
                sharedHandleErrorHeaders, sharedHandleErrorStatus, sharedHandleErrorRequest);
    }

    // ===== SECTION 2: RULE ERRORS =====

    /**
     * What it does: Sends the reason that a service refused an action.
     * Input: A rule exception with a public message and the current HTTP request.
     * Output: The rule's HTTP status and its shared JSON error.
     */
    @ExceptionHandler(SharedRuleException.class)
    public ResponseEntity<SharedErrorResponse> handleException(SharedRuleException sharedHandleErrorException,
            HttpServletRequest sharedHandleErrorRequest) {
        int sharedHandleErrorStatus = sharedHandleErrorException.getStatus().value();
        if ((sharedHandleErrorStatus == 403 || sharedHandleErrorStatus == 409)
                && sharedHandleErrorRequest.getServletPath().startsWith("/api/admin/")
                && switch (sharedHandleErrorRequest.getMethod()) {
                    case "POST", "PUT", "PATCH", "DELETE" -> true;
                    default -> false;
                }) {
            // A log storage error must not replace the original refusal.
            try {
                adminWriteRejectedActionRule.adminWriteRejectedAction(
                        adminWriteRejectedActionFindFunctionName(sharedHandleErrorRequest),
                        sharedHandleErrorException.getCode());
            } catch (RuntimeException sharedHandleErrorLogFailure) {
                logger.error("Could not write the rejected Admin action.", sharedHandleErrorLogFailure);
            }
        }
        return ResponseEntity.status(sharedHandleErrorException.getStatus())
                .body(new SharedErrorResponse(sharedHandleErrorException.getCode(),
                        sharedHandleErrorException.getMessage()));
    }

    /**
     * What it does: Finds the code name of an Admin write even when a filter refused it before MVC.
     * Input: The rejected request.
     * Output: The mapped controller function name, or a fixed description for an unmapped action.
     */
    private String adminWriteRejectedActionFindFunctionName(HttpServletRequest adminWriteRejectedActionRequest) {
        Object adminWriteRejectedActionHandler = adminWriteRejectedActionRequest.getAttribute(
                HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (adminWriteRejectedActionHandler instanceof HandlerMethod adminWriteRejectedActionMethod) {
            return adminWriteRejectedActionMethod.getMethod().getName();
        }
        // Do not put a raw URL or client data into the security log.
        try {
            if (!ServletRequestPathUtils.hasParsedRequestPath(adminWriteRejectedActionRequest)) {
                ServletRequestPathUtils.parseAndCache(adminWriteRejectedActionRequest);
            }
            RequestMappingHandlerMapping adminWriteRejectedActionMapping = adminWriteRejectedActionMappings.getObject();
            var adminWriteRejectedActionChain = adminWriteRejectedActionMapping.getHandler(adminWriteRejectedActionRequest);
            if (adminWriteRejectedActionChain != null
                    && adminWriteRejectedActionChain.getHandler() instanceof HandlerMethod adminWriteRejectedActionMethod) {
                return adminWriteRejectedActionMethod.getMethod().getName();
            }
        } catch (Exception adminWriteRejectedActionMappingFailure) {
            // Even when the path or method cannot be mapped, keep the original refusal.
        }
        return "Unknown Admin action";
    }

    // ===== SECTION 3: UNEXPECTED ERRORS =====

    /**
     * What it does: Logs an unexpected failure with a reference that can be matched to the answer.
     * Input: An unexpected exception.
     * Output: A generic HTTP 500 error carrying the reference.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleException(Exception sharedHandleErrorException) {
        // Give the caller a reference while keeping exception details on the server.
        String sharedHandleErrorReference = UUID.randomUUID().toString();
        logger.error("Unexpected error. Reference: " + sharedHandleErrorReference, sharedHandleErrorException);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new SharedErrorResponse("SHARED_UNEXPECTED_ERROR",
                        "Something went wrong. Reference: " + sharedHandleErrorReference));
    }
}
