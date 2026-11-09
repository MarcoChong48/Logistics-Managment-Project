package hk.edu.hkmu.lms.delivery.web;

import hk.edu.hkmu.lms.delivery.support.ApiException;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@RestControllerAdvice
class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);
    @ExceptionHandler(ApiException.class) ResponseEntity<?> api(ApiException ex) {
        return ResponseEntity.status(ex.status()).body(Map.of("code",ex.code(),"message",ex.getMessage()));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MissingServletRequestParameterException.class,MissingServletRequestPartException.class})
    ResponseEntity<?> input(Exception ex) { return ResponseEntity.badRequest().body(Map.of("code","INVALID_INPUT","message","Check the submitted fields and try again.")); }
    @ExceptionHandler(MaxUploadSizeExceededException.class) ResponseEntity<?> upload(Exception ex) {
        return ResponseEntity.badRequest().body(Map.of("code","FILE_TOO_LARGE","message","Choose an image smaller than 5 MB."));
    }
    @ExceptionHandler(Exception.class) ResponseEntity<?> other(Exception ex) {
        // Log the exception type, not request bodies, passwords, contact data or proof bytes.
        log.error("Delivery operation failed: {}", ex.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(Map.of("code","SERVICE_ERROR","message","The operation could not be saved. Refresh and try again."));
    }
}
