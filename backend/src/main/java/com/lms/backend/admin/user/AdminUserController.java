package com.lms.backend.admin.user;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lms.backend.shared.error.SharedMessageResponse;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    // ===== SECTION 1: SERVICE THIS CLASS CALLS =====

    private final AdminUserService adminUserService;

    /**
     * What it does: Receives the service that handles account management requests.
     * Input: The Admin account service.
     * Output: A controller that can forward the account routes.
     */
    public AdminUserController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    // ===== SECTION 2: GET /api/admin/users =====

    /**
     * What it does: Runs the Users search using its optional filters and page number.
     * Input: A username fragment, role, status and page number.
     * Output: The matching page of accounts.
     */
    @GetMapping
    public AdminSearchUserResponse adminSearchUser(
            @RequestParam(name = "adminSearchUserKeyword", required = false)
            String adminSearchUserKeyword,
            @RequestParam(name = "adminSearchUserRole", required = false)
            String adminSearchUserRole,
            @RequestParam(name = "adminSearchUserAccountStatus", required = false)
            String adminSearchUserAccountStatus,
            @RequestParam(name = "adminSearchUserPageNumber", defaultValue = "1")
            int adminSearchUserPageNumber) {
        return adminUserService.adminSearchUser(
                adminSearchUserKeyword, adminSearchUserRole, adminSearchUserAccountStatus, adminSearchUserPageNumber);
    }

    // ===== SECTION 3: GET /api/admin/users/{userID} =====

    /**
     * What it does: Reads the selected account's details.
     * Input: The account ID.
     * Output: The account facts, or a not-found error.
     */
    @GetMapping("/{userID}")
    public AdminViewUserResponse adminViewUser(@PathVariable("userID") Long userID) {
        return adminUserService.adminViewUser(userID);
    }

    // ===== SECTION 4: POST /api/admin/users =====

    /**
     * What it does: Creates the account from the submitted form.
     * Input: The account and optional profile fields.
     * Output: The new temporary password and its expiry, or a rule error.
     */
    @PostMapping
    public AdminCreateUserResponse adminCreateUser(@RequestBody AdminCreateUserRequest adminCreateUserRequest) {
        return adminUserService.adminCreateUser(adminCreateUserRequest);
    }

    // ===== SECTION 5: PUT /api/admin/users/{userID}/delivery-worker-profile =====

    /**
     * What it does: Saves the submitted Delivery Worker profile.
     * Input: The account ID and delivery profile fields.
     * Output: The save message, or a rule error.
     */
    @PutMapping("/{userID}/delivery-worker-profile")
    public SharedMessageResponse adminEditDeliveryWorkerProfile(
            @PathVariable("userID") Long userID,
            @RequestBody AdminEditDeliveryWorkerProfileRequest adminEditDeliveryWorkerProfileRequest) {
        return adminUserService.adminEditDeliveryWorkerProfile(userID, adminEditDeliveryWorkerProfileRequest);
    }

    // ===== SECTION 6: GET /api/admin/users/{userID}/impact =====

    /**
     * What it does: Reads the selected account's open work.
     * Input: The account ID.
     * Output: The impact counts and order references, or a not-found error.
     */
    @GetMapping("/{userID}/impact")
    public AdminPreviewUserImpactResponse adminPreviewUserImpact(@PathVariable("userID") Long userID) {
        return adminUserService.adminPreviewUserImpact(userID);
    }

    // ===== SECTION 7: POST /api/admin/users/{userID}/role =====

    /**
     * What it does: Requests the chosen change of staff role.
     * Input: The account ID, new role and reason.
     * Output: A confirmation, or a rule error.
     */
    @PostMapping("/{userID}/role")
    public SharedMessageResponse adminChangeUserRole(
            @PathVariable("userID") Long userID,
            @RequestBody AdminChangeUserRoleRequest adminChangeUserRoleRequest) {
        return adminUserService.adminChangeUserRole(userID, adminChangeUserRoleRequest);
    }

    // ===== SECTION 8: POST /api/admin/users/{userID}/ban =====

    /**
     * What it does: Requests a ban for the selected account.
     * Input: The account ID and reason.
     * Output: A confirmation, or a rule error.
     */
    @PostMapping("/{userID}/ban")
    public SharedMessageResponse adminBanUser(
            @PathVariable("userID") Long userID,
            @RequestBody AdminBanUserRequest adminBanUserRequest) {
        return adminUserService.adminBanUser(userID, adminBanUserRequest);
    }

    // ===== SECTION 9: POST /api/admin/users/{userID}/unban =====

    /**
     * What it does: Requests that a ban be lifted.
     * Input: The account ID and reason.
     * Output: A confirmation, or a rule error.
     */
    @PostMapping("/{userID}/unban")
    public SharedMessageResponse adminUnbanUser(
            @PathVariable("userID") Long userID,
            @RequestBody AdminUnbanUserRequest adminUnbanUserRequest) {
        return adminUserService.adminUnbanUser(userID, adminUnbanUserRequest);
    }

    // ===== SECTION 10: POST /api/admin/users/{userID}/deactivate =====

    /**
     * What it does: Requests deactivation of the selected account.
     * Input: The account ID and reason.
     * Output: A confirmation, or a rule error.
     */
    @PostMapping("/{userID}/deactivate")
    public SharedMessageResponse adminDeactivateUser(
            @PathVariable("userID") Long userID,
            @RequestBody AdminDeactivateUserRequest adminDeactivateUserRequest) {
        return adminUserService.adminDeactivateUser(userID, adminDeactivateUserRequest);
    }

    // ===== SECTION 11: POST /api/admin/users/{userID}/reactivate =====

    /**
     * What it does: Requests reactivation of the selected account.
     * Input: The account ID and reason.
     * Output: A confirmation, or a rule error.
     */
    @PostMapping("/{userID}/reactivate")
    public SharedMessageResponse adminReactivateUser(
            @PathVariable("userID") Long userID,
            @RequestBody AdminReactivateUserRequest adminReactivateUserRequest) {
        return adminUserService.adminReactivateUser(userID, adminReactivateUserRequest);
    }

    // ===== SECTION 12: POST /api/admin/users/{userID}/unlock =====

    /**
     * What it does: Requests removal of the selected account's login lock.
     * Input: The account ID.
     * Output: A confirmation, or a rule error.
     */
    @PostMapping("/{userID}/unlock")
    public SharedMessageResponse adminUnlockUser(@PathVariable("userID") Long userID) {
        return adminUserService.adminUnlockUser(userID);
    }

    // ===== SECTION 13: POST /api/admin/users/{userID}/reset-password =====

    /**
     * What it does: Requests a temporary password for the selected account.
     * Input: The account ID and the identity check record.
     * Output: The new temporary password and its expiry, or a rule error.
     */
    @PostMapping("/{userID}/reset-password")
    public AdminResetPasswordResponse adminResetPassword(
            @PathVariable("userID") Long userID,
            @RequestBody AdminResetPasswordRequest adminResetPasswordRequest) {
        return adminUserService.adminResetPassword(userID, adminResetPasswordRequest);
    }
}
