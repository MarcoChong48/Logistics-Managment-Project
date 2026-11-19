package com.lms.backend.admin.user;

import java.time.Instant;

import com.lms.backend.shared.value.SharedAccountStatus;
import com.lms.backend.shared.value.SharedUserRole;

public final class AdminUserRule {

    // ===== SECTION 1: CONSTRUCTOR =====

    /**
     * What it does: Keeps this set of pure rules from being instantiated.
     * Input: None.
     * Output: None.
     */
    private AdminUserRule() {
    }

    // ===== SECTION 2: adminCheckUsernameRule =====

    /**
     * What it does: Checks the username length and allowed characters.
     * Input: The exact entered username.
     * Output: True if the username is 4 to 30 ASCII letters, digits or underscores.
     */
    public static boolean adminCheckUsernameRule(String adminCheckUsernameRuleText) {
        return adminCheckUsernameRuleText != null && adminCheckUsernameRuleText.matches("[A-Za-z0-9_]{4,30}");
    }

    // ===== SECTION 3: adminCheckRoleChangeRule =====

    /**
     * What it does: Checks that a role change is between two different staff roles.
     * Input: The stored account role and the requested new role.
     * Output: The word "allowed", or the reason the change is refused.
     */
    public static String adminCheckRoleChangeRule(
            SharedUserRole adminCheckRoleChangeRuleOldRole, SharedUserRole adminCheckRoleChangeRuleNewRole) {
        // Client roles never take part in a staff role change.
        if (adminCheckRoleChangeRuleOldRole == SharedUserRole.CLIENT
                || adminCheckRoleChangeRuleNewRole == SharedUserRole.CLIENT) {
            return "A Client account cannot become staff, and staff cannot become a Client.";
        }
        if (adminCheckRoleChangeRuleOldRole == null || adminCheckRoleChangeRuleNewRole == null) {
            return "Choose Warehouse Worker, Delivery Worker or Admin.";
        }
        if (adminCheckRoleChangeRuleOldRole == adminCheckRoleChangeRuleNewRole) {
            return "The user already has this role.";
        }
        return "allowed";
    }

    // ===== SECTION 4: adminCheckLastAdminRule =====

    /**
     * What it does: Checks that an Admin change preserves an active Admin account.
     * Input: The number of other active Admins after locking the Admin accounts.
     * Output: True when another active Admin remains.
     */
    public static boolean adminCheckLastAdminRule(long adminCheckLastAdminRuleOtherAccountCount) {
        return adminCheckLastAdminRuleOtherAccountCount >= 1;
    }

    // ===== SECTION 5: adminCheckLockedNowRule =====

    /**
     * What it does: Recognizes a running temporary lock or the stored old locked status.
     * Input: The stored status, optional lock expiry and current time.
     * Output: True when either kind of lock is active.
     */
    public static boolean adminCheckLockedNowRule(
            SharedAccountStatus accountStatus, Instant lockUntil, Instant adminCheckLockedNowRuleTime) {
        return accountStatus == SharedAccountStatus.LOCKED
                || (lockUntil != null && lockUntil.isAfter(adminCheckLockedNowRuleTime));
    }
}
