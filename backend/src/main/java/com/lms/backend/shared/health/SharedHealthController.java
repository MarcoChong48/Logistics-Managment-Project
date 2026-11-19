package com.lms.backend.shared.health;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SharedHealthController {

    // ===== SECTION 1: SERVICE THIS CLASS CALLS =====

    private final SharedHealthService sharedCheckSystemHealthService;

    /**
     * What it does: Receives the service used for the health check.
     * Input: The shared health service.
     * Output: A controller ready to answer health requests.
     */
    public SharedHealthController(SharedHealthService sharedCheckSystemHealthService) {
        this.sharedCheckSystemHealthService = sharedCheckSystemHealthService;
    }

    // ===== SECTION 2: GET /api/shared/health =====

    /**
     * What it does: Returns current health without requiring login.
     * Input: None.
     * Output: The health record with HTTP 200 when connected or HTTP 503 when disconnected.
     */
    @GetMapping("/api/shared/health")
    public ResponseEntity<SharedCheckSystemHealthResponse> sharedCheckSystemHealth() {
        // Return the current result even when the database is down. A reload must check again.
        SharedCheckSystemHealthResponse sharedCheckSystemHealthResponse =
                sharedCheckSystemHealthService.sharedCheckSystemHealth();
        HttpStatus sharedCheckSystemHealthStatus = HttpStatus.OK;
        if (!sharedCheckSystemHealthResponse.sharedCheckSystemHealthDatabaseIsConnected()) {
            sharedCheckSystemHealthStatus = HttpStatus.SERVICE_UNAVAILABLE;
        }
        return ResponseEntity.status(sharedCheckSystemHealthStatus)
                .cacheControl(CacheControl.noStore())
                .body(sharedCheckSystemHealthResponse);
    }
}
