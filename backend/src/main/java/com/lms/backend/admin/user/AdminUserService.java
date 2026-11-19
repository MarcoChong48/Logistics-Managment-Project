package com.lms.backend.admin.user;

import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_TABLE_PAGE_SIZE;
import static com.lms.backend.admin.common.AdminCommonRule.adminCheckReasonText;
import static com.lms.backend.admin.common.AdminCommonRule.adminCountPage;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_DELIVERY_WORKER_PROFILE_EDITED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_USER_BANNED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_USER_CREATED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_USER_DEACTIVATED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_USER_PASSWORD_RESET;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_USER_REACTIVATED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_USER_ROLE_CHANGED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_USER_UNBANNED;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_USER_UNLOCKED;
import static com.lms.backend.shared.log.SharedSecurityEvent.SHARED_SECURITY_EVENT_ACCOUNT_BANNED;
import static com.lms.backend.shared.log.SharedSecurityEvent.SHARED_SECURITY_EVENT_ACCOUNT_DEACTIVATED;
import static com.lms.backend.shared.log.SharedSecurityEvent.SHARED_SECURITY_EVENT_ACCOUNT_REACTIVATED;
import static com.lms.backend.shared.log.SharedSecurityEvent.SHARED_SECURITY_EVENT_ACCOUNT_UNBANNED;
import static com.lms.backend.shared.log.SharedSecurityEvent.SHARED_SECURITY_EVENT_ACCOUNT_UNLOCKED_BY_ADMIN;
import static com.lms.backend.shared.log.SharedSecurityEvent.SHARED_SECURITY_EVENT_LOGIN_SUCCESS;
import static com.lms.backend.shared.log.SharedSecurityEvent.SHARED_SECURITY_EVENT_ROLE_CHANGED;
import static com.lms.backend.shared.log.SharedSecurityEvent.SHARED_SECURITY_EVENT_TEMPORARY_PASSWORD_ISSUED;
import static com.lms.backend.shared.login.SharedLoginRule.SHARED_TEMPORARY_PASSWORD_VALID_HOURS;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_STATUS_WAREHOUSE;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKUP_LIST_STATUS_CLOSED;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.lms.backend.shared.database.AppUser;
import com.lms.backend.shared.database.AppUserRepository;
import com.lms.backend.shared.database.CustomerOrder;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.database.DeliveryPackage;
import com.lms.backend.shared.database.DeliveryPackageRepository;
import com.lms.backend.shared.database.DeliveryWorkerProfile;
import com.lms.backend.shared.database.DeliveryWorkerProfileRepository;
import com.lms.backend.shared.database.PickingAssignmentHistory;
import com.lms.backend.shared.database.PickingAssignmentHistoryRepository;
import com.lms.backend.shared.database.PickingTask;
import com.lms.backend.shared.database.PickingTaskRepository;
import com.lms.backend.shared.database.PickupListRepository;
import com.lms.backend.shared.database.SecurityLogRepository;
import com.lms.backend.shared.error.SharedMessageResponse;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedAuditLogWriter;
import com.lms.backend.shared.log.SharedSecurityLogWriter;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.login.SharedPasswordRule;
import com.lms.backend.shared.value.SharedAccountStatus;
import com.lms.backend.shared.value.SharedOrderStatus;
import com.lms.backend.shared.value.SharedUserRole;

@Service
public class AdminUserService {

    // ===== SECTION 1: CONSTANTS =====

    private static final Logger ADMIN_USER_LOGGER = LoggerFactory.getLogger(AdminUserService.class);

    // ===== SECTION 2: OTHER CLASSES THIS CLASS USES =====

    private final AppUserRepository adminUserRepository;
    private final DeliveryWorkerProfileRepository adminEditDeliveryWorkerProfileRepository;
    private final PickingAssignmentHistoryRepository adminPreviewUserImpactPickingAssignmentRepository;
    private final PickingTaskRepository adminPreviewUserImpactPickingTaskRepository;
    private final CustomerOrderRepository adminPreviewUserImpactOrderRepository;
    private final PickupListRepository adminPreviewUserImpactPickupListRepository;
    private final DeliveryPackageRepository adminPreviewUserImpactPackageRepository;
    private final SecurityLogRepository adminSearchUserSecurityLogRepository;
    private final SharedCurrentUserReader adminUserCurrentUserReader;
    private final SharedAuditLogWriter adminUserAuditLogWriter;
    private final SharedSecurityLogWriter adminUserSecurityLogWriter;
    private final PasswordEncoder adminUserPasswordEncoder;

    /**
     * What it does: Connects account management to the shared database, session and logging services.
     * Input: The account and work repositories, the session reader, log writers and password encoder.
     * Output: A service ready to enforce the account rules.
     */
    public AdminUserService(
            AppUserRepository adminUserRepository,
            DeliveryWorkerProfileRepository adminEditDeliveryWorkerProfileRepository,
            PickingAssignmentHistoryRepository adminPreviewUserImpactPickingAssignmentRepository,
            PickingTaskRepository adminPreviewUserImpactPickingTaskRepository,
            CustomerOrderRepository adminPreviewUserImpactOrderRepository,
            PickupListRepository adminPreviewUserImpactPickupListRepository,
            DeliveryPackageRepository adminPreviewUserImpactPackageRepository,
            SecurityLogRepository adminSearchUserSecurityLogRepository,
            SharedCurrentUserReader adminUserCurrentUserReader,
            SharedAuditLogWriter adminUserAuditLogWriter,
            SharedSecurityLogWriter adminUserSecurityLogWriter,
            PasswordEncoder adminUserPasswordEncoder) {
        this.adminUserRepository = adminUserRepository;
        this.adminEditDeliveryWorkerProfileRepository = adminEditDeliveryWorkerProfileRepository;
        this.adminPreviewUserImpactPickingAssignmentRepository = adminPreviewUserImpactPickingAssignmentRepository;
        this.adminPreviewUserImpactPickingTaskRepository = adminPreviewUserImpactPickingTaskRepository;
        this.adminPreviewUserImpactOrderRepository = adminPreviewUserImpactOrderRepository;
        this.adminPreviewUserImpactPickupListRepository = adminPreviewUserImpactPickupListRepository;
        this.adminPreviewUserImpactPackageRepository = adminPreviewUserImpactPackageRepository;
        this.adminSearchUserSecurityLogRepository = adminSearchUserSecurityLogRepository;
        this.adminUserCurrentUserReader = adminUserCurrentUserReader;
        this.adminUserAuditLogWriter = adminUserAuditLogWriter;
        this.adminUserSecurityLogWriter = adminUserSecurityLogWriter;
        this.adminUserPasswordEncoder = adminUserPasswordEncoder;
    }

    // ===== SECTION 3: adminSearchUser =====

    /**
     * What it does: Searches accounts and obtains their last successful login times.
     * Input: The optional keyword, role and status filters and the one-based page number.
     * Output: The matching safe account rows and page totals, or 400 for an unknown filter.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminSearchUserResponse adminSearchUser(
            String adminSearchUserKeyword,
            String adminSearchUserRole,
            String adminSearchUserAccountStatus,
            int adminSearchUserPageNumber) {
        // Time and filters are shared by the account query and the displayed lock flags.
        Instant adminSearchUserTime = Instant.now();
        Long adminSearchUserCurrentUserID = adminUserCurrentUserReader.sharedReadCurrentUser().userID();
        SharedUserRole role = null;
        if (adminSearchUserRole != null && !adminSearchUserRole.isEmpty()) {
            role = adminReadRole(adminSearchUserRole);
            if (role == null) {
                throw adminRefuseInput("Choose a valid role.");
            }
        }
        boolean adminSearchUserIsLockedOnly = "LOCKED_NOW".equals(adminSearchUserAccountStatus);
        SharedAccountStatus accountStatus = adminReadSearchAccountStatus(adminSearchUserAccountStatus);
        String adminSearchUserKeywordText = adminSearchUserKeyword == null ? "" : adminSearchUserKeyword;
        int adminSearchUserRequestedPageNumber =
                Math.clamp(adminSearchUserPageNumber, 1, Integer.MAX_VALUE / ADMIN_TABLE_PAGE_SIZE);

        // Clamp a stale page link to the last result page when the list has shrunk.
        Page<AppUser> adminSearchUserPage = adminUserRepository.adminSearchUser(
                adminSearchUserKeywordText, role, accountStatus, adminSearchUserIsLockedOnly,
                SharedAccountStatus.LOCKED, adminSearchUserTime,
                PageRequest.of(adminSearchUserRequestedPageNumber - 1, ADMIN_TABLE_PAGE_SIZE));
        long adminSearchUserTotalRowCount = adminSearchUserPage.getTotalElements();
        int adminSearchUserTotalPageCount = adminCountPage(adminSearchUserTotalRowCount);
        int adminSearchUserActualPageNumber =
                Math.min(adminSearchUserRequestedPageNumber, adminSearchUserTotalPageCount);
        if (adminSearchUserActualPageNumber != adminSearchUserRequestedPageNumber
                && adminSearchUserTotalRowCount > 0) {
            adminSearchUserPage = adminUserRepository.adminSearchUser(
                    adminSearchUserKeywordText, role, accountStatus, adminSearchUserIsLockedOnly,
                    SharedAccountStatus.LOCKED, adminSearchUserTime,
                    PageRequest.of(adminSearchUserActualPageNumber - 1, ADMIN_TABLE_PAGE_SIZE));
        }

        // Attach last-login times in one query and copy only fields approved for the list.
        Map<Long, Instant> adminSearchUserLastLoginTimeMap =
                adminFindLastLoginTimes(adminSearchUserPage.getContent());
        List<AdminSearchUserRow> adminSearchUserRowList = new ArrayList<>();
        for (AppUser adminSearchUserAccount : adminSearchUserPage) {
            adminSearchUserRowList.add(new AdminSearchUserRow(
                    adminSearchUserAccount.userID, adminSearchUserAccount.username, adminSearchUserAccount.role,
                    adminSearchUserAccount.accountStatus, adminSearchUserAccount.failedLoginCount,
                    adminSearchUserAccount.lockUntil, adminSearchUserAccount.mustChangePassword,
                    adminSearchUserAccount.createdTime,
                    AdminUserRule.adminCheckLockedNowRule(adminSearchUserAccount.accountStatus,
                            adminSearchUserAccount.lockUntil, adminSearchUserTime),
                    adminSearchUserLastLoginTimeMap.get(adminSearchUserAccount.userID),
                    adminSearchUserCurrentUserID.equals(adminSearchUserAccount.userID)));
        }
        return new AdminSearchUserResponse(adminSearchUserRowList, adminSearchUserActualPageNumber,
                adminSearchUserTotalPageCount, adminSearchUserTotalRowCount);
    }

    // ===== SECTION 4: adminViewUser =====

    /**
     * What it does: Opens the safe facts for one account and the profile of a Delivery Worker.
     * Input: The selected account ID.
     * Output: The detail record, or 404 ADMIN_USER_NOT_FOUND.
     */
    @Transactional(readOnly = true)
    public AdminViewUserResponse adminViewUser(Long userID) {
        // Read only the account and the profile applicable to its current role.
        AppUser adminViewUserAccount = adminUserRepository.findById(userID).orElseThrow(this::adminRefuseMissingUser);
        DeliveryWorkerProfile adminViewUserProfile = new DeliveryWorkerProfile();
        if (adminViewUserAccount.role == SharedUserRole.DELIVERY_WORKER) {
            adminViewUserProfile = adminEditDeliveryWorkerProfileRepository.findById(userID)
                    .orElse(adminViewUserProfile);
        }
        Long adminViewUserCurrentUserID = adminUserCurrentUserReader.sharedReadCurrentUser().userID();
        return new AdminViewUserResponse(userID, adminViewUserAccount.username, adminViewUserAccount.role,
                adminViewUserAccount.accountStatus, adminViewUserAccount.failedLoginCount,
                adminViewUserAccount.lockUntil, adminViewUserAccount.mustChangePassword,
                adminViewUserAccount.temporaryPasswordExpiryTime, adminViewUserAccount.createdTime,
                AdminUserRule.adminCheckLockedNowRule(
                        adminViewUserAccount.accountStatus, adminViewUserAccount.lockUntil, Instant.now()),
                userID.equals(adminViewUserCurrentUserID), adminViewUserProfile.employeeNumber,
                adminViewUserProfile.vehicleType, adminViewUserProfile.vehiclePlate,
                adminViewUserProfile.equipmentNote);
    }

    // ===== SECTION 5: adminCreateUser =====

    /**
     * What it does: Creates an active account and, for a Delivery Worker, its profile.
     * Input: The desired username, role and optional Delivery Worker fields.
     * Output: The newly issued temporary password and its expiry, or a 400 or 409 rule error.
     */
    @Transactional
    public AdminCreateUserResponse adminCreateUser(AdminCreateUserRequest adminCreateUserRequest) {
        // Check the exact username and the four allowed roles before issuing a password.
        String username = adminCreateUserRequest.adminCreateUserUsername();
        if (!AdminUserRule.adminCheckUsernameRule(username)) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_USERNAME_INVALID",
                    "Use 4 to 30 characters: letters A to Z, digits and underscore.");
        }
        if (adminUserRepository.findByUsernameIgnoreCase(username).isPresent()) {
            throw adminRefuseUsernameTaken();
        }
        SharedUserRole role = adminReadRole(adminCreateUserRequest.adminCreateUserRole());
        if (role == null) {
            throw adminRefuseInput("Choose a valid role.");
        }
        DeliveryWorkerProfile adminCreateUserProfile = null;
        if (role == SharedUserRole.DELIVERY_WORKER) {
            adminCreateUserProfile = adminBuildDeliveryWorkerProfile(null,
                    adminCreateUserRequest.adminCreateUserEmployeeNumber(),
                    adminCreateUserRequest.adminCreateUserVehicleType(),
                    adminCreateUserRequest.adminCreateUserVehiclePlate(),
                    adminCreateUserRequest.adminCreateUserEquipmentNote());
        }

        // Only the hash enters the account row. The unique index handles concurrent creates.
        String adminCreateUserTemporaryPassword = SharedPasswordRule.sharedCreateTemporaryPassword();
        Instant temporaryPasswordExpiryTime =
                Instant.now().plus(SHARED_TEMPORARY_PASSWORD_VALID_HOURS, ChronoUnit.HOURS);
        AppUser adminCreateUserAccount = new AppUser();
        adminCreateUserAccount.username = username;
        adminCreateUserAccount.role = role;
        adminCreateUserAccount.accountStatus = SharedAccountStatus.ACTIVE;
        adminCreateUserAccount.passwordHash = adminUserPasswordEncoder.encode(adminCreateUserTemporaryPassword);
        adminCreateUserAccount.mustChangePassword = true;
        adminCreateUserAccount.temporaryPasswordExpiryTime = temporaryPasswordExpiryTime;
        adminSaveNewUser(adminCreateUserAccount);

        // The profile and audit share the account transaction.
        Long userID = adminCreateUserAccount.userID;
        if (adminCreateUserProfile != null) {
            adminCreateUserProfile.userID = userID;
            adminSaveDeliveryWorkerProfile(adminCreateUserProfile);
        }
        adminWriteUserAudit(userID, SHARED_AUDIT_ACTION_USER_CREATED, Map.of(),
                Map.of("username", username, "role", role.name()));
        adminWriteUserSecurity(userID, SHARED_SECURITY_EVENT_TEMPORARY_PASSWORD_ISSUED);
        return new AdminCreateUserResponse(userID, username, role, adminCreateUserTemporaryPassword,
                temporaryPasswordExpiryTime);
    }

    // ===== SECTION 6: adminEditDeliveryWorkerProfile =====

    /**
     * What it does: Saves a Delivery Worker's profile, creating the row when needed.
     * Input: The selected account ID and the profile fields.
     * Output: The save message, or a 400, 404 or 409 rule error.
     */
    @Transactional
    public SharedMessageResponse adminEditDeliveryWorkerProfile(
            Long userID, AdminEditDeliveryWorkerProfileRequest adminEditDeliveryWorkerProfileRequest) {
        // The account lock makes profile editing and role changes follow the same saved role.
        AppUser adminEditDeliveryWorkerProfileAccount = adminReadUserForChange(userID);
        if (adminEditDeliveryWorkerProfileAccount.role != SharedUserRole.DELIVERY_WORKER) {
            throw adminRefuseUserState("Only a Delivery Worker can have a delivery profile.");
        }
        if (adminEditDeliveryWorkerProfileAccount.accountStatus == SharedAccountStatus.LOCKED) {
            throw adminRefuseUserState("Unlock the account first.");
        }
        DeliveryWorkerProfile adminEditDeliveryWorkerProfileNew = adminBuildDeliveryWorkerProfile(userID,
                adminEditDeliveryWorkerProfileRequest.adminEditDeliveryWorkerProfileEmployeeNumber(),
                adminEditDeliveryWorkerProfileRequest.adminEditDeliveryWorkerProfileVehicleType(),
                adminEditDeliveryWorkerProfileRequest.adminEditDeliveryWorkerProfileVehiclePlate(),
                adminEditDeliveryWorkerProfileRequest.adminEditDeliveryWorkerProfileEquipmentNote());
        DeliveryWorkerProfile adminEditDeliveryWorkerProfileOld =
                adminEditDeliveryWorkerProfileRepository.findById(userID).orElseGet(DeliveryWorkerProfile::new);

        // Keep the old and new audit values limited to fields that changed.
        Map<String, Object> adminEditDeliveryWorkerProfileOldValueMap = new LinkedHashMap<>();
        Map<String, Object> adminEditDeliveryWorkerProfileNewValueMap = new LinkedHashMap<>();
        adminCompareDeliveryWorkerProfile(adminEditDeliveryWorkerProfileOld, adminEditDeliveryWorkerProfileNew,
                adminEditDeliveryWorkerProfileOldValueMap, adminEditDeliveryWorkerProfileNewValueMap);
        if (adminEditDeliveryWorkerProfileOld.userID == null || !adminEditDeliveryWorkerProfileNewValueMap.isEmpty()) {
            adminSaveDeliveryWorkerProfile(adminEditDeliveryWorkerProfileNew);
            adminWriteUserAudit(userID, SHARED_AUDIT_ACTION_DELIVERY_WORKER_PROFILE_EDITED,
                    adminEditDeliveryWorkerProfileOldValueMap, adminEditDeliveryWorkerProfileNewValueMap);
        }
        return new SharedMessageResponse("The profile of " + adminEditDeliveryWorkerProfileAccount.username
                + " was saved.");
    }

    // ===== SECTION 7: adminPreviewUserImpact =====

    /**
     * What it does: Counts open work and open orders attached to one user.
     * Input: The selected account ID.
     * Output: The user impact record, or 404 ADMIN_USER_NOT_FOUND.
     * Placeholder: PI-08 and PI-05 use Warehouse and Delivery rows and their provisional state words.
     * The temporary tools populate those shared tables until the other roles write the live records.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminPreviewUserImpactResponse adminPreviewUserImpact(Long userID) {
        AppUser adminPreviewUserImpactAccount = adminUserRepository.findById(userID)
                .orElseThrow(this::adminRefuseMissingUser);
        return adminReadUserImpact(adminPreviewUserImpactAccount);
    }

    // ===== SECTION 8: adminChangeUserRole =====

    /**
     * What it does: Changes a staff role and revokes the account's existing sessions.
     * Input: The account ID, requested role and reason.
     * Output: A change message, or a reason, missing account or state error.
     */
    @Transactional
    public SharedMessageResponse adminChangeUserRole(
            Long userID, AdminChangeUserRoleRequest adminChangeUserRoleRequest) {
        String adminChangeUserRoleReason = adminCheckReasonText(adminChangeUserRoleRequest.adminChangeUserRoleReason());

        // Take any shared Admin locks before an individual account lock.
        SharedUserRole role = adminReadObservedRole(userID);
        List<AppUser> adminChangeUserRoleAdminAccountList = adminLockAdminAccounts(role);
        AppUser adminChangeUserRoleAccount = adminReadUserForChange(userID, role);
        adminCheckOwnAccount(userID, "You cannot change your own role.");
        if (adminChangeUserRoleAccount.accountStatus != SharedAccountStatus.ACTIVE) {
            throw adminRefuseUserState("Only an active account can change role.");
        }
        SharedUserRole adminChangeUserRoleNewRole =
                adminReadRole(adminChangeUserRoleRequest.adminChangeUserRoleNewRole());
        String adminChangeUserRoleRuleAnswer = AdminUserRule.adminCheckRoleChangeRule(role, adminChangeUserRoleNewRole);
        if (!"allowed".equals(adminChangeUserRoleRuleAnswer)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_ROLE_CHANGE_NOT_ALLOWED",
                    adminChangeUserRoleRuleAnswer);
        }

        // Check current work again even when the browser already displayed a preview.
        AdminPreviewUserImpactResponse adminChangeUserRoleImpact = adminReadUserImpact(adminChangeUserRoleAccount);
        if (adminChangeUserRoleImpact.adminPreviewUserImpactHasActiveWork()) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_USER_HAS_ACTIVE_WORK",
                    "This user still has active work. Active picking tasks: "
                            + adminChangeUserRoleImpact.adminPreviewUserImpactActivePickingTaskCount()
                            + ". Open pickup lists: "
                            + adminChangeUserRoleImpact.adminPreviewUserImpactOpenPickupListCount()
                            + ". Held packages: " + adminChangeUserRoleImpact.adminPreviewUserImpactHeldPackageCount()
                            + ".");
        }
        if (role == SharedUserRole.ADMIN) {
            adminCheckOtherActiveAdmin(userID, adminChangeUserRoleAdminAccountList);
        }

        // Check the expected state in the same SQL statement that saves the new role and version.
        int adminChangeUserRoleUpdatedCount = adminUserRepository.adminChangeUserRole(
                userID, role, adminChangeUserRoleNewRole, SharedAccountStatus.ACTIVE, Instant.now());
        if (adminChangeUserRoleUpdatedCount != 1) {
            throw adminRefuseUserState("The account changed. Please refresh the page.");
        }
        if (adminChangeUserRoleNewRole == SharedUserRole.DELIVERY_WORKER
                && !adminEditDeliveryWorkerProfileRepository.existsById(userID)) {
            DeliveryWorkerProfile adminChangeUserRoleProfile = new DeliveryWorkerProfile();
            adminChangeUserRoleProfile.userID = userID;
            adminSaveDeliveryWorkerProfile(adminChangeUserRoleProfile);
        }
        adminWriteUserAudit(userID, SHARED_AUDIT_ACTION_USER_ROLE_CHANGED, Map.of("role", role.name()),
                Map.of("role", adminChangeUserRoleNewRole.name(), "reason", adminChangeUserRoleReason));
        adminWriteUserSecurity(userID, SHARED_SECURITY_EVENT_ROLE_CHANGED);
        return new SharedMessageResponse("The role of " + adminChangeUserRoleAccount.username + " was changed from "
                + role + " to " + adminChangeUserRoleNewRole + ". The user must log in again.");
    }

    // ===== SECTION 9: adminBanUser =====

    /**
     * What it does: Bans an active account and revokes its sessions even if it still has work.
     * Input: The account ID and ban reason.
     * Output: A ban message, or a reason, missing account or state error.
     */
    @Transactional
    public SharedMessageResponse adminBanUser(Long userID, AdminBanUserRequest adminBanUserRequest) {
        String adminBanUserReason = adminCheckReasonText(adminBanUserRequest.adminBanUserReason());

        // A ban may remove an active Admin, so count the remaining Admins after taking their locks.
        SharedUserRole role = adminReadObservedRole(userID);
        List<AppUser> adminBanUserAdminAccountList = adminLockAdminAccounts(role);
        AppUser adminBanUserAccount = adminReadUserForChange(userID, role);
        adminCheckOwnAccount(userID, "You cannot ban your own account.");
        if (adminBanUserAccount.accountStatus != SharedAccountStatus.ACTIVE) {
            throw adminRefuseUserState("Only an active account can be banned.");
        }
        if (role == SharedUserRole.ADMIN) {
            adminCheckOtherActiveAdmin(userID, adminBanUserAdminAccountList);
        }

        // Record unfinished work without letting it stop an emergency ban.
        adminSuspendUser(adminBanUserAccount, SharedAccountStatus.BANNED, adminBanUserReason,
                SHARED_AUDIT_ACTION_USER_BANNED, SHARED_SECURITY_EVENT_ACCOUNT_BANNED);
        return new SharedMessageResponse("User " + adminBanUserAccount.username + " was banned.");
    }

    // ===== SECTION 10: adminUnbanUser =====

    /**
     * What it does: Lifts a ban and clears the user's login attempts and timed lock.
     * Input: The account ID and unban reason.
     * Output: An unban message, or a reason, missing account or state error.
     */
    @Transactional
    public SharedMessageResponse adminUnbanUser(Long userID, AdminUnbanUserRequest adminUnbanUserRequest) {
        String adminUnbanUserReason = adminCheckReasonText(adminUnbanUserRequest.adminUnbanUserReason());
        AppUser adminUnbanUserAccount = adminReadUserForChange(userID);
        if (adminUnbanUserAccount.accountStatus != SharedAccountStatus.BANNED) {
            throw adminRefuseUserState("Only a banned account can be unbanned.");
        }

        // Permit a fresh login and record why the ban was lifted.
        adminRestoreUser(adminUnbanUserAccount, adminUnbanUserReason, SHARED_AUDIT_ACTION_USER_UNBANNED,
                SHARED_SECURITY_EVENT_ACCOUNT_UNBANNED);
        return new SharedMessageResponse("User " + adminUnbanUserAccount.username + " was unbanned.");
    }

    // ===== SECTION 11: adminDeactivateUser =====

    /**
     * What it does: Deactivates an active or banned account and preserves its history.
     * Input: The account ID and deactivation reason.
     * Output: A deactivation message, or a reason, missing account or state error.
     */
    @Transactional
    public SharedMessageResponse adminDeactivateUser(
            Long userID, AdminDeactivateUserRequest adminDeactivateUserRequest) {
        String adminDeactivateUserReason = adminCheckReasonText(adminDeactivateUserRequest.adminDeactivateUserReason());

        // Only an active Admin is counted toward the last Admin requirement.
        SharedUserRole role = adminReadObservedRole(userID);
        List<AppUser> adminDeactivateUserAdminAccountList = adminLockAdminAccounts(role);
        AppUser adminDeactivateUserAccount = adminReadUserForChange(userID, role);
        adminCheckOwnAccount(userID, "You cannot deactivate your own account.");
        if (adminDeactivateUserAccount.accountStatus != SharedAccountStatus.ACTIVE
                && adminDeactivateUserAccount.accountStatus != SharedAccountStatus.BANNED) {
            throw adminRefuseUserState("Only an active or banned account can be deactivated.");
        }
        if (role == SharedUserRole.ADMIN && adminDeactivateUserAccount.accountStatus == SharedAccountStatus.ACTIVE) {
            adminCheckOtherActiveAdmin(userID, adminDeactivateUserAdminAccountList);
        }

        // Keep the account and its work while ending its access.
        adminSuspendUser(adminDeactivateUserAccount, SharedAccountStatus.INACTIVE, adminDeactivateUserReason,
                SHARED_AUDIT_ACTION_USER_DEACTIVATED, SHARED_SECURITY_EVENT_ACCOUNT_DEACTIVATED);
        return new SharedMessageResponse("User " + adminDeactivateUserAccount.username + " was deactivated.");
    }

    // ===== SECTION 12: adminReactivateUser =====

    /**
     * What it does: Reactivates an inactive account and clears its login attempts and timed lock.
     * Input: The account ID and reactivation reason.
     * Output: A reactivation message, or a reason, missing account or state error.
     */
    @Transactional
    public SharedMessageResponse adminReactivateUser(
            Long userID, AdminReactivateUserRequest adminReactivateUserRequest) {
        String adminReactivateUserReason = adminCheckReasonText(adminReactivateUserRequest.adminReactivateUserReason());
        AppUser adminReactivateUserAccount = adminReadUserForChange(userID);
        if (adminReactivateUserAccount.accountStatus != SharedAccountStatus.INACTIVE) {
            throw adminRefuseUserState("Only a deactivated account can be reactivated.");
        }

        // Change the account state without altering its saved role or credentials.
        adminRestoreUser(adminReactivateUserAccount, adminReactivateUserReason, SHARED_AUDIT_ACTION_USER_REACTIVATED,
                SHARED_SECURITY_EVENT_ACCOUNT_REACTIVATED);
        return new SharedMessageResponse("User " + adminReactivateUserAccount.username + " was reactivated.");
    }

    // ===== SECTION 13: adminUnlockUser =====

    /**
     * What it does: Clears a timed lock or repairs the old stored locked state.
     * Input: The selected account ID.
     * Output: An unlock message, 404 if no account exists or 409 if it is not locked.
     */
    @Transactional
    public SharedMessageResponse adminUnlockUser(Long userID) {
        AppUser adminUnlockUserAccount = adminReadUserForChange(userID);
        if (!AdminUserRule.adminCheckLockedNowRule(
                adminUnlockUserAccount.accountStatus, adminUnlockUserAccount.lockUntil, Instant.now())) {
            throw adminRefuseUserState("This account is not locked.");
        }

        // An old stored lock is repaired to Active. A timed lock only needs the attempt fields cleared.
        Map<String, Object> adminUnlockUserOldValueMap = Map.of();
        Map<String, Object> adminUnlockUserNewValueMap = Map.of();
        if (adminUnlockUserAccount.accountStatus == SharedAccountStatus.LOCKED) {
            adminUnlockUserOldValueMap = Map.of("accountStatus", SharedAccountStatus.LOCKED.name());
            adminUnlockUserNewValueMap = Map.of("accountStatus", SharedAccountStatus.ACTIVE.name());
            adminUnlockUserAccount.accountStatus = SharedAccountStatus.ACTIVE;
        }
        adminUnlockUserAccount.failedLoginCount = 0;
        adminUnlockUserAccount.lockUntil = null;
        adminWriteUserAudit(userID, SHARED_AUDIT_ACTION_USER_UNLOCKED,
                adminUnlockUserOldValueMap, adminUnlockUserNewValueMap);
        adminWriteUserSecurity(userID, SHARED_SECURITY_EVENT_ACCOUNT_UNLOCKED_BY_ADMIN);
        return new SharedMessageResponse("User " + adminUnlockUserAccount.username + " was unlocked.");
    }

    // ===== SECTION 14: adminResetPassword =====

    /**
     * What it does: Issues a fresh temporary password, clears a timed lock and revokes earlier sessions.
     * Input: The account ID, identity confirmation and the method used to confirm the person's identity.
     * Output: The issued password and expiry, or a 400, 404 or 409 rule error.
     */
    @Transactional
    public AdminResetPasswordResponse adminResetPassword(
            Long userID, AdminResetPasswordRequest adminResetPasswordRequest) {
        // Identity must be confirmed before issuing a different person's credentials.
        if (!adminResetPasswordRequest.adminResetPasswordIdentityIsConfirmed()) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_IDENTITY_NOT_CONFIRMED",
                    "Please confirm that you checked the person's identity.");
        }
        String adminResetPasswordIdentityCheckMethod =
                adminCheckReasonText(adminResetPasswordRequest.adminResetPasswordIdentityCheckMethod());
        AppUser adminResetPasswordAccount = adminReadUserForChange(userID);
        adminCheckOwnAccount(userID, "Use Change password for your own account.");
        if (adminResetPasswordAccount.accountStatus != SharedAccountStatus.ACTIVE) {
            throw adminRefuseUserState("Unban or reactivate the account first.");
        }

        // Only the new hash is stored. Invalidate sessions that used the previous credentials.
        String adminResetPasswordTemporaryPassword = SharedPasswordRule.sharedCreateTemporaryPassword();
        Instant temporaryPasswordExpiryTime =
                Instant.now().plus(SHARED_TEMPORARY_PASSWORD_VALID_HOURS, ChronoUnit.HOURS);
        adminResetPasswordAccount.passwordHash = adminUserPasswordEncoder.encode(adminResetPasswordTemporaryPassword);
        adminResetPasswordAccount.mustChangePassword = true;
        adminResetPasswordAccount.temporaryPasswordExpiryTime = temporaryPasswordExpiryTime;
        adminResetPasswordAccount.failedLoginCount = 0;
        adminResetPasswordAccount.lockUntil = null;
        adminResetPasswordAccount.sessionVersion++;
        adminWriteUserAudit(userID, SHARED_AUDIT_ACTION_USER_PASSWORD_RESET, Map.of(),
                Map.of("identityCheckMethod", adminResetPasswordIdentityCheckMethod));
        adminWriteUserSecurity(userID, SHARED_SECURITY_EVENT_TEMPORARY_PASSWORD_ISSUED);
        return new AdminResetPasswordResponse(userID, adminResetPasswordAccount.username,
                adminResetPasswordTemporaryPassword, temporaryPasswordExpiryTime);
    }

    // ===== SECTION 15: HELPER FUNCTIONS =====

    /**
     * What it does: Reads a case-sensitive role word without exposing deserialization errors.
     * Input: The submitted role word, which can be empty.
     * Output: A known role, or null when the role word is absent or unknown.
     */
    private SharedUserRole adminReadRole(String adminReadRoleText) {
        if (adminReadRoleText == null) {
            return null;
        }
        try {
            return SharedUserRole.valueOf(adminReadRoleText);
        } catch (IllegalArgumentException adminReadRoleError) {
            return null;
        }
    }

    /**
     * What it does: Converts a search filter into a stored status.
     * Input: The optional search status word.
     * Output: A stored status, null for all or locked-now, or 400 for an unsupported filter.
     */
    private SharedAccountStatus adminReadSearchAccountStatus(String adminReadSearchAccountStatusText) {
        if (adminReadSearchAccountStatusText == null) {
            return null;
        }
        return switch (adminReadSearchAccountStatusText) {
            case "", "LOCKED_NOW" -> null;
            case "ACTIVE" -> SharedAccountStatus.ACTIVE;
            case "INACTIVE" -> SharedAccountStatus.INACTIVE;
            case "BANNED" -> SharedAccountStatus.BANNED;
            default -> throw adminRefuseInput("Choose a valid account status.");
        };
    }

    /**
     * What it does: Reads the most recent successful login time for each account on the current page.
     * Input: The current page of accounts.
     * Output: A map from user IDs with successful logins to their latest login time.
     */
    private Map<Long, Instant> adminFindLastLoginTimes(List<AppUser> adminFindLastLoginTimesAccountList) {
        if (adminFindLastLoginTimesAccountList.isEmpty()) {
            return Map.of();
        }
        List<Long> adminFindLastLoginTimesUserIDList = adminFindLastLoginTimesAccountList.stream()
                .map(adminFindLastLoginTimesAccount -> adminFindLastLoginTimesAccount.userID)
                .toList();

        // The grouped query returns one ID and time pair per account with login history.
        Map<Long, Instant> adminFindLastLoginTimesResultMap = new HashMap<>();
        for (Object[] adminFindLastLoginTimesRow : adminSearchUserSecurityLogRepository.findLatestEventTimes(
                adminFindLastLoginTimesUserIDList, SHARED_SECURITY_EVENT_LOGIN_SUCCESS)) {
            adminFindLastLoginTimesResultMap.put(
                    (Long) adminFindLastLoginTimesRow[0], (Instant) adminFindLastLoginTimesRow[1]);
        }
        return adminFindLastLoginTimesResultMap;
    }

    /**
     * What it does: Validates the optional profile fields and checks the employee number is available.
     * Input: The optional existing account ID and the four profile fields.
     * Output: A delivery profile draft, or a 400 or 409 profile rule error.
     */
    private DeliveryWorkerProfile adminBuildDeliveryWorkerProfile(
            Long userID, String employeeNumber, String vehicleType, String vehiclePlate, String equipmentNote) {
        // Blanks represent empty optional fields and never reserve an employee number.
        DeliveryWorkerProfile adminBuildDeliveryWorkerProfileValue = new DeliveryWorkerProfile();
        adminBuildDeliveryWorkerProfileValue.userID = userID;
        adminBuildDeliveryWorkerProfileValue.employeeNumber =
                adminCheckProfileField(employeeNumber, 60, "Employee number");
        adminBuildDeliveryWorkerProfileValue.vehicleType = adminCheckProfileField(vehicleType, 60, "Vehicle type");
        adminBuildDeliveryWorkerProfileValue.vehiclePlate = adminCheckProfileField(vehiclePlate, 40, "Vehicle plate");
        adminBuildDeliveryWorkerProfileValue.equipmentNote =
                adminCheckProfileField(equipmentNote, 600, "Equipment note");

        if (adminBuildDeliveryWorkerProfileValue.employeeNumber != null
                && adminEditDeliveryWorkerProfileRepository.findByEmployeeNumber(
                        adminBuildDeliveryWorkerProfileValue.employeeNumber)
                        .filter(adminBuildDeliveryWorkerProfileExisting -> !adminBuildDeliveryWorkerProfileExisting
                                .userID.equals(userID))
                        .isPresent()) {
            throw adminRefuseEmployeeNumberTaken();
        }
        return adminBuildDeliveryWorkerProfileValue;
    }

    /**
     * What it does: Checks the length of an optional profile field against its limit.
     * Input: The field text, character limit and its display label.
     * Output: The original text, null for an empty field, or an input error.
     */
    private String adminCheckProfileField(
            String adminCheckProfileFieldText, int adminCheckProfileFieldLimit, String adminCheckProfileFieldLabel) {
        if (adminCheckProfileFieldText == null) {
            return null;
        }
        if (adminCheckProfileFieldText.codePointCount(0, adminCheckProfileFieldText.length())
                > adminCheckProfileFieldLimit) {
            throw adminRefuseInput(adminCheckProfileFieldLabel + " can have at most "
                    + adminCheckProfileFieldLimit + " characters.");
        }
        return adminCheckProfileFieldText.isBlank() ? null : adminCheckProfileFieldText;
    }

    /**
     * What it does: Records the fields that changed between two Delivery Worker profiles.
     * Input: The earlier and current profiles and two mutable audit maps.
     * Output: None. The provided maps contain just the changed values.
     */
    private void adminCompareDeliveryWorkerProfile(
            DeliveryWorkerProfile adminCompareDeliveryWorkerProfileOld,
            DeliveryWorkerProfile adminCompareDeliveryWorkerProfileNew,
            Map<String, Object> adminCompareDeliveryWorkerProfileOldValueMap,
            Map<String, Object> adminCompareDeliveryWorkerProfileNewValueMap) {
        adminAddAuditChange("employeeNumber", adminCompareDeliveryWorkerProfileOld.employeeNumber,
                adminCompareDeliveryWorkerProfileNew.employeeNumber,
                adminCompareDeliveryWorkerProfileOldValueMap, adminCompareDeliveryWorkerProfileNewValueMap);
        adminAddAuditChange("vehicleType", adminCompareDeliveryWorkerProfileOld.vehicleType,
                adminCompareDeliveryWorkerProfileNew.vehicleType,
                adminCompareDeliveryWorkerProfileOldValueMap, adminCompareDeliveryWorkerProfileNewValueMap);
        adminAddAuditChange("vehiclePlate", adminCompareDeliveryWorkerProfileOld.vehiclePlate,
                adminCompareDeliveryWorkerProfileNew.vehiclePlate,
                adminCompareDeliveryWorkerProfileOldValueMap, adminCompareDeliveryWorkerProfileNewValueMap);
        adminAddAuditChange("equipmentNote", adminCompareDeliveryWorkerProfileOld.equipmentNote,
                adminCompareDeliveryWorkerProfileNew.equipmentNote,
                adminCompareDeliveryWorkerProfileOldValueMap, adminCompareDeliveryWorkerProfileNewValueMap);
    }

    /**
     * What it does: Copies a changed value into the two audit maps, preserving empty values.
     * Input: The approved field name, both values and the mutable audit maps.
     * Output: None. Equal values are omitted.
     */
    private void adminAddAuditChange(
            String adminAddAuditChangeField, Object adminAddAuditChangeOldValue, Object adminAddAuditChangeNewValue,
            Map<String, Object> adminAddAuditChangeOldValueMap, Map<String, Object> adminAddAuditChangeNewValueMap) {
        if (!Objects.equals(adminAddAuditChangeOldValue, adminAddAuditChangeNewValue)) {
            adminAddAuditChangeOldValueMap.put(adminAddAuditChangeField, adminAddAuditChangeOldValue);
            adminAddAuditChangeNewValueMap.put(adminAddAuditChangeField, adminAddAuditChangeNewValue);
        }
    }

    /**
     * What it does: Inserts an account before it is referenced by the profile or audit.
     * Input: A validated account with a hashed password.
     * Output: None. A concurrent duplicate username becomes the username-taken error.
     */
    private void adminSaveNewUser(AppUser adminSaveNewUserAccount) {
        try {
            adminUserRepository.saveAndFlush(adminSaveNewUserAccount);
        } catch (DataIntegrityViolationException adminSaveNewUserError) {
            String adminSaveNewUserConstraint = adminFindConstraintName(adminSaveNewUserError);
            if ("uq_app_user_username_lower".equals(adminSaveNewUserConstraint)
                    || "app_user_username_key".equals(adminSaveNewUserConstraint)) {
                throw adminRefuseUsernameTaken();
            }
            throw adminSaveNewUserError;
        }
    }

    /**
     * What it does: Stores the delivery profile and resolves concurrent duplicate employee numbers.
     * Input: A validated profile.
     * Output: None, or 409 when another profile uses the employee number.
     */
    private void adminSaveDeliveryWorkerProfile(DeliveryWorkerProfile adminSaveDeliveryWorkerProfileValue) {
        try {
            adminEditDeliveryWorkerProfileRepository.saveAndFlush(adminSaveDeliveryWorkerProfileValue);
        } catch (DataIntegrityViolationException adminSaveDeliveryWorkerProfileError) {
            if ("delivery_worker_profile_employee_number_key".equals(
                    adminFindConstraintName(adminSaveDeliveryWorkerProfileError))) {
                throw adminRefuseEmployeeNumberTaken();
            }
            throw adminSaveDeliveryWorkerProfileError;
        }
    }

    /**
     * What it does: Finds the database constraint behind a persistence exception.
     * Input: A data integrity error from the repository.
     * Output: The constraint name, or null when the error gives no name.
     */
    private String adminFindConstraintName(Throwable adminFindConstraintNameError) {
        for (Throwable adminFindConstraintNameCause = adminFindConstraintNameError;
             adminFindConstraintNameCause != null;
             adminFindConstraintNameCause = adminFindConstraintNameCause.getCause()) {
            if (adminFindConstraintNameCause instanceof ConstraintViolationException adminFindConstraintNameViolation) {
                return adminFindConstraintNameViolation.getConstraintName();
            }
        }
        return null;
    }

    /**
     * What it does: Reads a fresh account with a row lock before changing it.
     * Input: The selected user ID.
     * Output: The locked account, or 404 if it does not exist.
     */
    private AppUser adminReadUserForChange(Long userID) {
        return adminUserRepository.findByUserIDForChange(userID).orElseThrow(this::adminRefuseMissingUser);
    }

    /**
     * What it does: Locks the target and checks its role against the earlier lookup.
     * Input: The account ID and role observed before taking any account lock.
     * Output: The locked account, or a not-found or account-changed error.
     */
    private AppUser adminReadUserForChange(Long userID, SharedUserRole adminReadUserForChangeObservedRole) {
        AppUser adminReadUserForChangeAccount = adminReadUserForChange(userID);
        if (adminReadUserForChangeAccount.role != adminReadUserForChangeObservedRole) {
            throw adminRefuseUserState("The account changed. Please refresh the page.");
        }
        return adminReadUserForChangeAccount;
    }

    /**
     * What it does: Reads the target's role before deciding which account locks to take.
     * Input: The selected user ID.
     * Output: The observed role or 404 if there is no account.
     */
    private SharedUserRole adminReadObservedRole(Long userID) {
        return adminUserRepository.findRoleByUserID(userID).orElseThrow(this::adminRefuseMissingUser);
    }

    /**
     * What it does: Acquires Admin account locks in one order when the target is an Admin.
     * Input: The target's role before any locks have been acquired.
     * Output: The locked Admin account list, or an empty list for another role.
     */
    private List<AppUser> adminLockAdminAccounts(SharedUserRole role) {
        if (role == SharedUserRole.ADMIN) {
            return adminUserRepository.findAdminAccountsForChange();
        }
        return List.of();
    }

    /**
     * What it does: Refuses a change that would leave no active Admin account.
     * Input: The target ID and the locked Admin account list.
     * Output: None, or 409 ADMIN_LAST_ADMIN_NOT_ALLOWED if no other active Admin remains.
     */
    private void adminCheckOtherActiveAdmin(Long userID, List<AppUser> adminCheckOtherActiveAdminAccountList) {
        long adminCheckOtherActiveAdminCount = adminCheckOtherActiveAdminAccountList.stream()
                .filter(adminCheckOtherActiveAdminAccount -> !userID.equals(adminCheckOtherActiveAdminAccount.userID)
                        && adminCheckOtherActiveAdminAccount.accountStatus == SharedAccountStatus.ACTIVE)
                .count();
        if (!AdminUserRule.adminCheckLastAdminRule(adminCheckOtherActiveAdminCount)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_LAST_ADMIN_NOT_ALLOWED",
                    "At least one other active Admin must remain.");
        }
    }

    /**
     * What it does: Checks if the signed-in Admin is trying to change their own account.
     * Input: The target account ID and the action's error message.
     * Output: None, or 409 ADMIN_OWN_ACCOUNT_NOT_ALLOWED when the IDs match.
     */
    private void adminCheckOwnAccount(Long userID, String adminCheckOwnAccountMessage) {
        if (userID.equals(adminUserCurrentUserReader.sharedReadCurrentUser().userID())) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_OWN_ACCOUNT_NOT_ALLOWED",
                    adminCheckOwnAccountMessage);
        }
    }

    /**
     * What it does: Reads the same work counts for the preview, role changes, bans and deactivations.
     * Input: The affected account.
     * Output: Open-work counts and up to ten references for each order list.
     * Placeholder: PI-08 and PI-05 use Warehouse and Delivery records and provisional words from shared tables.
     * The temporary tools create those records until the other parts write them.
     */
    private AdminPreviewUserImpactResponse adminReadUserImpact(AppUser adminReadUserImpactAccount) {
        // PLACEHOLDER INPUT PI-08 and PI-05
        // From: Warehouse and Delivery.
        // What: Picking assignments, pickup lists, packages and the provisional statuses.
        // How to use: Read the same tables when those modules are connected. Temporary tools supply today's rows.
        Long userID = adminReadUserImpactAccount.userID;
        List<CustomerOrder> adminReadUserImpactPickingOrderList = adminFindPickingOrders(userID);
        List<CustomerOrder> adminReadUserImpactHeldOrderList = adminFindHeldOrders(userID);
        long adminReadUserImpactOpenPickupListCount =
                adminPreviewUserImpactPickupListRepository.countByClaimedByDeliveryWorkerIDAndStatusNot(
                        userID, SHARED_PICKUP_LIST_STATUS_CLOSED);
        long adminReadUserImpactOpenClientOrderCount =
                adminPreviewUserImpactOrderRepository.countByClientUserIDAndOrderStatusNotIn(userID,
                        List.of(SharedOrderStatus.DELIVERED.name(), SharedOrderStatus.CANCELLED.name()));
        boolean adminReadUserImpactHasActiveWork = !adminReadUserImpactPickingOrderList.isEmpty()
                || adminReadUserImpactOpenPickupListCount > 0
                || !adminReadUserImpactHeldOrderList.isEmpty();
        return new AdminPreviewUserImpactResponse(
                userID, adminReadUserImpactAccount.username, adminReadUserImpactAccount.role,
                adminReadUserImpactPickingOrderList.size(),
                adminFindImpactOrderReferences(adminReadUserImpactPickingOrderList),
                adminReadUserImpactOpenPickupListCount, adminReadUserImpactHeldOrderList.size(),
                adminFindImpactOrderReferences(adminReadUserImpactHeldOrderList),
                adminReadUserImpactOpenClientOrderCount,
                adminReadUserImpactHasActiveWork);
    }

    /**
     * What it does: Finds the orders for still-open assignments in the Picking status.
     * Input: The Warehouse Worker ID.
     * Output: One order per qualifying assignment.
     */
    private List<CustomerOrder> adminFindPickingOrders(Long workerUserID) {
        List<PickingAssignmentHistory> adminFindPickingOrdersAssignmentList =
                adminPreviewUserImpactPickingAssignmentRepository.findByWorkerUserIDAndEndedTimeIsNull(workerUserID);
        if (adminFindPickingOrdersAssignmentList.isEmpty()) {
            return List.of();
        }
        List<Long> adminFindPickingOrdersTaskIDList = adminFindPickingOrdersAssignmentList.stream()
                .map(adminFindPickingOrdersAssignment -> adminFindPickingOrdersAssignment.pickingTaskID)
                .distinct()
                .toList();
        Map<Long, Long> adminFindPickingOrdersOrderIDMap = new HashMap<>();
        for (PickingTask adminFindPickingOrdersTask :
                adminPreviewUserImpactPickingTaskRepository.findAllById(adminFindPickingOrdersTaskIDList)) {
            adminFindPickingOrdersOrderIDMap.put(
                    adminFindPickingOrdersTask.pickingTaskID, adminFindPickingOrdersTask.orderID);
        }
        Map<Long, CustomerOrder> adminFindPickingOrdersOrderMap =
                adminReadImpactOrderMap(adminFindPickingOrdersOrderIDMap.values().stream().distinct().toList());

        // Keep one order in the result for every matching open assignment row.
        List<CustomerOrder> adminFindPickingOrdersResultList = new ArrayList<>();
        for (PickingAssignmentHistory adminFindPickingOrdersAssignment : adminFindPickingOrdersAssignmentList) {
            CustomerOrder adminFindPickingOrdersOrder = adminFindPickingOrdersOrderMap.get(
                    adminFindPickingOrdersOrderIDMap.get(adminFindPickingOrdersAssignment.pickingTaskID));
            if (adminFindPickingOrdersOrder != null
                    && SharedOrderStatus.PICKING.name().equals(adminFindPickingOrdersOrder.orderStatus)) {
                adminFindPickingOrdersResultList.add(adminFindPickingOrdersOrder);
            }
        }
        return adminFindPickingOrdersResultList;
    }

    /**
     * What it does: Finds the orders for held packages that are currently in delivery.
     * Input: The current handler's user ID.
     * Output: The orders whose packages and statuses count as held work.
     */
    private List<CustomerOrder> adminFindHeldOrders(Long currentHandlerUserID) {
        List<DeliveryPackage> adminFindHeldOrdersPackageList = adminPreviewUserImpactPackageRepository
                .findByCurrentHandlerUserIDAndCustodyStatusNot(currentHandlerUserID, SHARED_CUSTODY_STATUS_WAREHOUSE);
        if (adminFindHeldOrdersPackageList.isEmpty()) {
            return List.of();
        }
        List<Long> adminFindHeldOrdersOrderIDList = adminFindHeldOrdersPackageList.stream()
                .map(adminFindHeldOrdersPackage -> adminFindHeldOrdersPackage.orderID)
                .toList();
        List<String> adminFindHeldOrdersStatusList = List.of(SharedOrderStatus.SHIPPED.name(),
                SharedOrderStatus.OUT_FOR_DELIVERY.name(), SharedOrderStatus.DELIVERY_FAILED.name());
        return adminPreviewUserImpactOrderRepository.findAllById(adminFindHeldOrdersOrderIDList).stream()
                .filter(adminFindHeldOrdersOrder ->
                        adminFindHeldOrdersStatusList.contains(adminFindHeldOrdersOrder.orderStatus))
                .toList();
    }

    /**
     * What it does: Reads several orders at once so work rows can be matched to them.
     * Input: The order IDs found on the work rows.
     * Output: An order map keyed by ID.
     */
    private Map<Long, CustomerOrder> adminReadImpactOrderMap(List<Long> adminReadImpactOrderMapOrderIDList) {
        Map<Long, CustomerOrder> adminReadImpactOrderMapResult = new HashMap<>();
        for (CustomerOrder adminReadImpactOrderMapOrder :
                adminPreviewUserImpactOrderRepository.findAllById(adminReadImpactOrderMapOrderIDList)) {
            adminReadImpactOrderMapResult.put(adminReadImpactOrderMapOrder.orderID, adminReadImpactOrderMapOrder);
        }
        return adminReadImpactOrderMapResult;
    }

    /**
     * What it does: Selects the order references shown in an impact preview.
     * Input: The orders counted for that part of the preview.
     * Output: Up to ten distinct references in reference order.
     */
    private List<String> adminFindImpactOrderReferences(List<CustomerOrder> adminFindImpactOrderReferencesOrderList) {
        return adminFindImpactOrderReferencesOrderList.stream()
                .map(adminFindImpactOrderReferencesOrder -> adminFindImpactOrderReferencesOrder.orderReference)
                .distinct()
                .sorted()
                .limit(10)
                .toList();
    }

    /**
     * What it does: Bans or deactivates an account after the caller finishes the state checks.
     * Input: The locked account, next status, reason, audit action and security event.
     * Output: None. The account version and audit are saved together.
     */
    private void adminSuspendUser(AppUser adminSuspendUserAccount, SharedAccountStatus adminSuspendUserNextStatus,
            String adminSuspendUserReason, String adminSuspendUserAuditAction, String adminSuspendUserSecurityEvent) {
        // Record work as it stands when access is suspended.
        AdminPreviewUserImpactResponse adminSuspendUserImpact = adminReadUserImpact(adminSuspendUserAccount);
        SharedAccountStatus accountStatus = adminSuspendUserAccount.accountStatus;
        adminSuspendUserAccount.accountStatus = adminSuspendUserNextStatus;
        adminSuspendUserAccount.sessionVersion++;
        adminWriteUserAudit(adminSuspendUserAccount.userID, adminSuspendUserAuditAction,
                Map.of("accountStatus", accountStatus.name()),
                Map.of("accountStatus", adminSuspendUserNextStatus.name(), "reason", adminSuspendUserReason,
                        "activePickingTaskCount", adminSuspendUserImpact.adminPreviewUserImpactActivePickingTaskCount(),
                        "openPickupListCount", adminSuspendUserImpact.adminPreviewUserImpactOpenPickupListCount(),
                        "heldPackageCount", adminSuspendUserImpact.adminPreviewUserImpactHeldPackageCount()));
        adminWriteUserSecurity(adminSuspendUserAccount.userID, adminSuspendUserSecurityEvent);
    }

    /**
     * What it does: Restores login access after the caller checks the earlier account state.
     * Input: The locked account, reason, audit action and security event.
     * Output: None. The cleared lock, Active state and audit are saved together.
     */
    private void adminRestoreUser(AppUser adminRestoreUserAccount, String adminRestoreUserReason,
            String adminRestoreUserAuditAction, String adminRestoreUserSecurityEvent) {
        SharedAccountStatus accountStatus = adminRestoreUserAccount.accountStatus;
        adminRestoreUserAccount.accountStatus = SharedAccountStatus.ACTIVE;
        adminRestoreUserAccount.failedLoginCount = 0;
        adminRestoreUserAccount.lockUntil = null;
        adminWriteUserAudit(adminRestoreUserAccount.userID, adminRestoreUserAuditAction,
                Map.of("accountStatus", accountStatus.name()),
                Map.of("accountStatus", SharedAccountStatus.ACTIVE.name(), "reason", adminRestoreUserReason));
        adminWriteUserSecurity(adminRestoreUserAccount.userID, adminRestoreUserSecurityEvent);
    }

    /**
     * What it does: Records a successful account action in the same transaction.
     * Input: The target user ID, audit action and approved values before and after the change.
     * Output: None. The audit writer refuses private field names.
     */
    private void adminWriteUserAudit(Long userID, String action,
            Map<String, ?> adminWriteUserAuditOldValueMap, Map<String, ?> adminWriteUserAuditNewValueMap) {
        Long actorUserID = adminUserCurrentUserReader.sharedReadCurrentUser().userID();
        adminUserAuditLogWriter.sharedWriteAuditLog(
                actorUserID, action, "USER", userID.toString(), adminWriteUserAuditOldValueMap,
                adminWriteUserAuditNewValueMap);
    }

    /**
     * What it does: Schedules the successful security event after the account transaction commits.
     * Input: The affected account ID and the security event word.
     * Output: None. The writer commits the event in its own transaction.
     */
    private void adminWriteUserSecurity(Long userID, String eventType) {
        // A separate transaction can only reference a new account after its insert has been committed.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            /**
             * What it does: Appends the event once the account change has been saved.
             * Input: None.
             * Output: None. Failures in the independent writer are reported to the server log.
             */
            @Override
            public void afterCommit() {
                try {
                    adminUserSecurityLogWriter.sharedWriteSecurityLog(userID, eventType, "SUCCESS", null);
                } catch (RuntimeException adminWriteUserSecurityError) {
                    // The action has committed. Keep its reply available, including any one-time password.
                    ADMIN_USER_LOGGER.error("Could not record security event {} for user ID {}.", eventType, userID);
                }
            }
        });
    }

    /**
     * What it does: Builds the error for a missing account.
     * Input: None.
     * Output: A 404 rule exception.
     */
    private SharedRuleException adminRefuseMissingUser() {
        return new SharedRuleException(HttpStatus.NOT_FOUND, "ADMIN_USER_NOT_FOUND", "This user was not found.");
    }

    /**
     * What it does: Builds the error for a refused account state change.
     * Input: The safe message explaining the state that blocks the change.
     * Output: A 409 rule exception.
     */
    private SharedRuleException adminRefuseUserState(String adminRefuseUserStateMessage) {
        return new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_USER_STATE_CONFLICT", adminRefuseUserStateMessage);
    }

    /**
     * What it does: Builds a validation error for an unsupported role, status or profile field.
     * Input: A safe message that names the rule.
     * Output: A 400 rule exception.
     */
    private SharedRuleException adminRefuseInput(String adminRefuseInputMessage) {
        return new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_INPUT_INVALID", adminRefuseInputMessage);
    }

    /**
     * What it does: Builds the duplicate-username rule error.
     * Input: None.
     * Output: A 409 rule exception.
     */
    private SharedRuleException adminRefuseUsernameTaken() {
        return new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_USERNAME_TAKEN", "This username is already taken.");
    }

    /**
     * What it does: Builds the duplicate employee-number rule error.
     * Input: None.
     * Output: A 409 rule exception.
     */
    private SharedRuleException adminRefuseEmployeeNumberTaken() {
        return new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_EMPLOYEE_NUMBER_TAKEN",
                "This employee number is already in use.");
    }
}
