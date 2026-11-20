package com.lms.backend;

import static com.lms.backend.admin.user.AdminUserRule.adminCheckLastAdminRule;
import static com.lms.backend.admin.user.AdminUserRule.adminCheckLockedNowRule;
import static com.lms.backend.admin.user.AdminUserRule.adminCheckRoleChangeRule;
import static com.lms.backend.admin.user.AdminUserRule.adminCheckSuperAdminRule;
import static com.lms.backend.admin.user.AdminUserRule.adminCheckUsernameRule;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.lms.backend.shared.value.SharedAccountStatus;
import com.lms.backend.shared.value.SharedUserRole;

class AdminUserRuleCheckTest {

    // ===== SECTION 1: USER RULES =====

    /**
     * What it does: Checks username boundaries and the allowed characters.
     * Input: A username and the expected validity.
     * Output: Pass when the rule returns the expected validity.
     */
    @ParameterizedTest(name = "Username {0} has result {1}")
    @CsvSource({
        "abc,false", "abcd,true",
        "abcdefghijklmnopqrstuvwxyz1234,true", "abcdefghijklmnopqrstuvwxyz12345,false",
        "bad name,false", "bad-name,false", "tést,false", "test_name,true"
    })
    void adminCheckUsernameRuleResult(String username, boolean adminCheckUsernameRuleExpected) {
        assertEquals(adminCheckUsernameRuleExpected, adminCheckUsernameRule(username));
    }

    /**
     * What it does: Checks all sixteen pairs of roles.
     * Input: The current role, the requested role and whether the change is allowed.
     * Output: Pass when the rule admits only the six changes between different staff roles.
     */
    @ParameterizedTest(name = "{0} to {1} has result {2}")
    @CsvSource({
        "CLIENT,CLIENT,false", "CLIENT,WAREHOUSE_WORKER,false", "CLIENT,DELIVERY_WORKER,false", "CLIENT,ADMIN,false",
        "WAREHOUSE_WORKER,CLIENT,false", "WAREHOUSE_WORKER,WAREHOUSE_WORKER,false",
        "WAREHOUSE_WORKER,DELIVERY_WORKER,true", "WAREHOUSE_WORKER,ADMIN,true",
        "DELIVERY_WORKER,CLIENT,false", "DELIVERY_WORKER,WAREHOUSE_WORKER,true",
        "DELIVERY_WORKER,DELIVERY_WORKER,false", "DELIVERY_WORKER,ADMIN,true",
        "ADMIN,CLIENT,false", "ADMIN,WAREHOUSE_WORKER,true", "ADMIN,DELIVERY_WORKER,true", "ADMIN,ADMIN,false"
    })
    void adminCheckRoleChangeRuleResult(SharedUserRole adminCheckRoleChangeRuleOldRole,
            SharedUserRole adminCheckRoleChangeRuleNewRole, boolean adminCheckRoleChangeRuleExpected) {
        assertEquals(adminCheckRoleChangeRuleExpected, "allowed".equals(
                adminCheckRoleChangeRule(adminCheckRoleChangeRuleOldRole, adminCheckRoleChangeRuleNewRole)));
    }

    /**
     * What it does: Checks how many other active Admins must remain.
     * Input: The count after excluding the target and the expected result.
     * Output: Pass when zero is refused and one is enough.
     */
    @ParameterizedTest
    @CsvSource({"0,false", "1,true"})
    void adminCheckLastAdminRuleResult(long adminCheckLastAdminRuleOtherCount, boolean adminCheckLastAdminRuleExpected) {
        assertEquals(adminCheckLastAdminRuleExpected, adminCheckLastAdminRule(adminCheckLastAdminRuleOtherCount));
    }

    /**
     * What it does: Checks timed locks and the old stored lock status.
     * Input: The saved status, the optional lock end and the expected result.
     * Output: Pass when a future lock or the old status reports a locked account.
     */
    @ParameterizedTest
    @CsvSource({
        "ACTIVE,1970-01-01T00:00:01Z,true", "ACTIVE,,false", "ACTIVE,1969-12-31T23:59:59Z,false", "LOCKED,,true"
    })
    void adminCheckLockedNowRuleResult(SharedAccountStatus accountStatus, Instant lockUntil,
            boolean adminCheckLockedNowRuleExpected) {
        assertEquals(adminCheckLockedNowRuleExpected, adminCheckLockedNowRule(accountStatus, lockUntil, Instant.EPOCH));
    }

    /**
     * What it does: Checks that the role and configured name must both identify the super admin.
     * Input: An account username and role, the bootstrap username and the expected result.
     * Output: Pass when only the configured Admin matches, regardless of capitals.
     */
    @ParameterizedTest
    @CsvSource({
        "admin_main,ADMIN,admin_main,true",
        "ADMIN_MAIN,ADMIN,admin_main,true",
        "admin_main,WAREHOUSE_WORKER,admin_main,false",
        "second_admin,ADMIN,admin_main,false",
        "admin_main,ADMIN,'',false"
    })
    void adminCheckSuperAdminRuleResult(String username, SharedUserRole role,
            String adminCheckSuperAdminRuleBootstrapUsername, boolean adminCheckSuperAdminRuleExpected) {
        assertEquals(adminCheckSuperAdminRuleExpected,
                adminCheckSuperAdminRule(username, role, adminCheckSuperAdminRuleBootstrapUsername));
    }
}
