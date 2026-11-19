// TEMPORARY TEST CODE - remove before integration
// The mode check and local test tools require the Admin test profile.
package com.lms.backend.admin.temporarytest;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.lms.backend.shared.error.SharedMessageResponse;

@RestController
@Profile("admintest")
public class AdminTemporaryTestToolController {

    // ===== SECTION 1: SERVICE THIS CLASS CALLS =====

    private final AdminTemporaryTestToolService adminTemporaryViewTestModeService;

    /**
     * What it does: Receives the test-mode service.
     * Input: The service loaded by the admintest profile.
     * Output: A controller ready to answer the test-mode check.
     */
    public AdminTemporaryTestToolController(AdminTemporaryTestToolService adminTemporaryViewTestModeService) {
        this.adminTemporaryViewTestModeService = adminTemporaryViewTestModeService;
    }

    // ===== SECTION 2: GET /api/admin/temporary-test/mode =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 0 lets the Test tools page confirm that test mode is on.
    /**
     * What it does: Returns the test-mode status from the service.
     * Input: None.
     * Output: The test-mode record with HTTP 200. Without the profile, Spring has no route and returns 404.
     */
    @GetMapping("/api/admin/temporary-test/mode")
    public AdminTemporaryViewTestModeResponse adminTemporaryViewTestMode() {
        // The service supplies the typed response.
        return adminTemporaryViewTestModeService.adminTemporaryViewTestMode();
    }

    // ===== SECTION 3: POST /api/admin/temporary-test/test-user =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 1 exposes a test account with an issued password.
    /**
     * What it does: Passes the requested test setup to the service.
     * Input: The tool's request record.
     * Output: A shared success message or input error.
     */
    @PostMapping("/api/admin/temporary-test/test-user")
    public SharedMessageResponse adminTemporaryCreateTestUser(
            @RequestBody AdminTemporaryCreateTestUserRequest adminTemporaryCreateTestUserRequest) {
        return adminTemporaryViewTestModeService.adminTemporaryCreateTestUser(adminTemporaryCreateTestUserRequest);
    }

    // ===== SECTION 4: POST /api/admin/temporary-test/end-lock =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 1 exposes a test of automatic lock expiry.
    /**
     * What it does: Passes the requested test setup to the service.
     * Input: The tool's request record.
     * Output: A shared success message or input error.
     */
    @PostMapping("/api/admin/temporary-test/end-lock")
    public SharedMessageResponse adminTemporaryEndUserLock(
            @RequestBody AdminTemporaryEndUserLockRequest adminTemporaryEndUserLockRequest) {
        return adminTemporaryViewTestModeService.adminTemporaryEndUserLock(adminTemporaryEndUserLockRequest);
    }

    // ===== SECTION 5: POST /api/admin/temporary-test/expire-temporary-password =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 1 exposes a test of temporary password expiry.
    /**
     * What it does: Passes the requested test setup to the service.
     * Input: The tool's request record.
     * Output: A shared success message or input error.
     */
    @PostMapping("/api/admin/temporary-test/expire-temporary-password")
    public SharedMessageResponse adminTemporaryExpireTemporaryPassword(
            @RequestBody AdminTemporaryExpireTemporaryPasswordRequest adminTemporaryExpireTemporaryPasswordRequest) {
        return adminTemporaryViewTestModeService.adminTemporaryExpireTemporaryPassword(
                adminTemporaryExpireTemporaryPasswordRequest);
    }

    // ===== SECTION 6: POST /api/admin/temporary-test/user-status =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 1 exposes a test of account session revocation.
    /**
     * What it does: Passes the requested test setup to the service.
     * Input: The tool's request record.
     * Output: A shared success message or input error.
     */
    @PostMapping("/api/admin/temporary-test/user-status")
    public SharedMessageResponse adminTemporarySetUserStatus(
            @RequestBody AdminTemporarySetUserStatusRequest adminTemporarySetUserStatusRequest) {
        return adminTemporaryViewTestModeService.adminTemporarySetUserStatus(adminTemporarySetUserStatusRequest);
    }

    // ===== SECTION 7: POST /api/admin/temporary-test/many-test-users =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 2 needs enough accounts to test Users pagination and filters.
    /**
     * What it does: Creates the numbered Client test accounts through the service.
     * Input: None.
     * Output: The shared message with the account count.
     */
    @PostMapping("/api/admin/temporary-test/many-test-users")
    public SharedMessageResponse adminTemporaryCreateManyTestUser() {
        return adminTemporaryViewTestModeService.adminTemporaryCreateManyTestUser();
    }

    // ===== SECTION 8: POST /api/admin/temporary-test/worker-with-active-work =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 2 needs workers with open picking and delivery work.
    /**
     * What it does: Creates the workers and active work through the service.
     * Input: None.
     * Output: The shared message with temporary credentials and order references.
     */
    @PostMapping("/api/admin/temporary-test/worker-with-active-work")
    public SharedMessageResponse adminTemporaryCreateWorkerWithActiveWork() {
        return adminTemporaryViewTestModeService.adminTemporaryCreateWorkerWithActiveWork();
    }
}
