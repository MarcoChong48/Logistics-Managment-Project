// TEMPORARY TEST CODE - remove before integration
// Stage 4 repeats the Stage 2 account workflow against the isolated flow-check database.
package com.lms.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import tools.jackson.databind.JsonNode;

@TestMethodOrder(MethodOrderer.MethodName.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminUserFlowCheckTest extends SharedFlowCheck {

    // ===== SECTION 1: FLOW STATE =====

    private static final String SECOND_ADMIN_PASSWORD = "Stage2Admin26";
    private Session adminUserBrowser;
    private Session adminUserPickerBrowser;
    private String adminUserPickerTemporaryPassword;
    private String adminUserDriverTemporaryPassword;
    private String adminUserDriverResetPassword;
    private String adminUserClientTemporaryPassword;
    private String adminUserSecondAdminTemporaryPassword;
    private String adminUserWorkerUsername;
    private String adminUserWorkerTemporaryPassword;
    private List<String> adminUserWorkerPickingReferences;

    // ===== SECTION 2: STAGE 2 CASES =====

    // What it does: Opens the user list.
    // Input: The Stage 1 accounts.
    // Output: Safe rows with the own-account flag.
    @Test
    @DisplayName("S2-T01")
    void checkS2T01() {
        reportPartial("visual table, (you) label and own-row Actions display");
        adminUserBrowser = admin();
        adminUserBrowser.get("/admin/admin-users.html").expect(200);
        Reply adminSearchUserReply = adminUserBrowser.get(USERS).expect(200);
        List<JsonNode> adminSearchUserRows = rows(adminSearchUserReply.json());
        assertTrue(adminSearchUserRows.stream().anyMatch(row -> row.path("username").asText().startsWith("test_")));
        JsonNode adminSearchUserOwnRow = adminSearchUserRows.stream()
                .filter(row -> row.path("username").asText().equalsIgnoreCase(bootstrapUsername)).findFirst()
                .orElseThrow();
        assertTrue(adminSearchUserOwnRow.path("adminSearchUserIsOwnAccount").asBoolean());
        assertFalse(adminSearchUserOwnRow.path("adminSearchUserCanChange").asBoolean(true));
        assertFalse(adminSearchUserReply.body().contains("\"passwordHash\""));
        assertFalse(adminSearchUserReply.body().contains("\"password\""));
    }

    // What it does: Searches by name, role and status.
    // Input: Sample Clients.
    // Output: Matching, repeatable pages.
    @Test
    @DisplayName("S2-T02")
    void checkS2T02() {
        reportPartial("browser controls, clear behavior and address preservation after reload");
        adminUserBrowser.tool("many-test-users", null).expect(200);
        Set<String> adminSearchUserExpectedUsernames = new HashSet<>();
        adminSearchUserExpectedUsernames.add("test_client_1");
        for (int adminSearchUserSuffix = 10; adminSearchUserSuffix <= 19; adminSearchUserSuffix++) {
            adminSearchUserExpectedUsernames.add("test_client_" + adminSearchUserSuffix);
        }
        JsonNode adminSearchUserKeywordResult = adminUserBrowser.get(USERS + "?adminSearchUserKeyword=CLIENT_1")
                .expect(200).json();
        assertEquals(adminSearchUserExpectedUsernames, new HashSet<>(usernames(adminSearchUserKeywordResult)));
        JsonNode adminSearchUserClientResult = adminUserBrowser.get(USERS + "?adminSearchUserRole=CLIENT")
                .expect(200).json();
        assertTrue(adminSearchUserClientResult.path("adminSearchUserTotalRowCount").asLong() >= 25);
        assertTrue(rows(adminSearchUserClientResult).stream()
                .allMatch(row -> row.path("role").asText().equals("CLIENT")));
        JsonNode adminSearchUserClearedResult = adminUserBrowser.get(USERS).expect(200).json();
        assertTrue(usernames(adminSearchUserClearedResult).contains(bootstrapUsername));

        String adminSearchUserActivePath = USERS + "?adminSearchUserAccountStatus=ACTIVE";
        JsonNode adminSearchUserActiveResult = adminUserBrowser.get(adminSearchUserActivePath).expect(200).json();
        assertFalse(rows(adminSearchUserActiveResult).isEmpty());
        assertTrue(rows(adminSearchUserActiveResult).stream()
                .allMatch(row -> row.path("accountStatus").asText().equals("ACTIVE")));
        assertEquals(usernames(adminSearchUserActiveResult),
                usernames(adminUserBrowser.get(adminSearchUserActivePath).expect(200).json()));
    }

    // What it does: Checks paging.
    // Input: The unfiltered list.
    // Output: A correct total and distinct adjacent pages.
    @Test
    @DisplayName("S2-T03")
    void checkS2T03() {
        reportPartial("visual pager and clicks on its controls");
        JsonNode adminSearchUserFirstPage = adminUserBrowser.get(USERS + "?adminSearchUserPageNumber=1")
                .expect(200).json();
        JsonNode adminSearchUserNextPage = adminUserBrowser.get(USERS + "?adminSearchUserPageNumber=2")
                .expect(200).json();
        long adminSearchUserDatabaseCount = database.sql("SELECT count(*) FROM app_user").query(Long.class).single();
        assertEquals(adminSearchUserDatabaseCount,
                adminSearchUserFirstPage.path("adminSearchUserTotalRowCount").asLong());
        assertEquals((adminSearchUserDatabaseCount + 19) / 20,
                adminSearchUserFirstPage.path("adminSearchUserTotalPageCount").asLong());
        assertEquals(1, adminSearchUserFirstPage.path("adminSearchUserPageNumber").asInt());
        assertEquals(20, rows(adminSearchUserFirstPage).size());
        assertEquals(2, adminSearchUserNextPage.path("adminSearchUserPageNumber").asInt());
        assertEquals(Math.min(20, adminSearchUserDatabaseCount - 20), rows(adminSearchUserNextPage).size());
        assertTrue(usernames(adminSearchUserFirstPage).stream()
                .noneMatch(usernames(adminSearchUserNextPage)::contains));
        assertEquals(usernames(adminSearchUserFirstPage),
                usernames(adminUserBrowser.get(USERS + "?adminSearchUserPageNumber=1").expect(200).json()));
    }

    // What it does: Creates the picker.
    // Input: Username and role.
    // Output: A one-time password and saved expiry.
    @Test
    @DisplayName("S2-T04")
    void checkS2T04() {
        reportPartial("temporary-password dialog, clipboard, paste and feedback");
        JsonNode adminCreateUserReply = createUser("mei_picker", "WAREHOUSE_WORKER");
        adminUserPickerTemporaryPassword = issuedPassword(adminCreateUserReply, "adminCreateUserTemporaryPassword");
        assertEquals("mei_picker", adminCreateUserReply.path("username").asText());
        assertEquals("WAREHOUSE_WORKER", adminCreateUserReply.path("role").asText());
        Instant temporaryPasswordExpiryTime = Instant.parse(
                adminCreateUserReply.path("adminCreateUserTemporaryPasswordExpiryTime").asText());
        assertTrue(temporaryPasswordExpiryTime.isAfter(Instant.now().plusSeconds(23 * 60 * 60)));
        assertTrue(temporaryPasswordExpiryTime.isBefore(Instant.now().plusSeconds(25 * 60 * 60)));
        assertTrue(database.sql("""
                SELECT must_change_password AND temp_password_expires_at
                    BETWEEN now() + interval '23 hours' AND now() + interval '25 hours'
                FROM app_user WHERE username = 'mei_picker'
                """).query(Boolean.class).single());
        Reply adminViewUserReply = adminUserBrowser.get(USERS + "/" + userID("mei_picker")).expect(200);
        assertEquals("mei_picker", adminViewUserReply.json().path("username").asText());
        assertFalse(adminViewUserReply.body().contains(adminUserPickerTemporaryPassword));
        assertFalse(adminViewUserReply.body().contains("\"passwordHash\""));
        assertFalse(adminUserBrowser.get(USERS + "?adminSearchUserKeyword=mei_picker").expect(200)
                .body().contains(adminUserPickerTemporaryPassword));
    }

    // What it does: Refuses invalid names.
    // Input: Four malformed names and a duplicate.
    // Output: No new accounts.
    @Test
    @DisplayName("S2-T05")
    void checkS2T05() {
        reportPartial("dialog interaction and visual error placement");
        long adminCreateUserBefore = database.sql("SELECT count(*) FROM app_user").query(Long.class).single();
        for (String username : List.of("ab", "bad name", "mei-picker", "a".repeat(31))) {
            adminUserBrowser.post(USERS, Map.of("adminCreateUserUsername", username,
                    "adminCreateUserRole", "CLIENT")).expect(400).code("ADMIN_USERNAME_INVALID");
        }
        adminUserBrowser.post(USERS, Map.of("adminCreateUserUsername", "MEI_PICKER",
                "adminCreateUserRole", "CLIENT")).expect(409).code("ADMIN_USERNAME_TAKEN");
        assertEquals(adminCreateUserBefore, database.sql("SELECT count(*) FROM app_user").query(Long.class).single());
    }

    // What it does: Creates and edits a profile.
    // Input: Driver fields.
    // Output: Updated fields and a unique employee ID.
    @Test
    @DisplayName("S2-T06")
    void checkS2T06() {
        reportPartial("profile dialog rendering and interaction");
        JsonNode adminCreateUserReply = adminUserBrowser.post(USERS, Map.of(
                "adminCreateUserUsername", "ken_driver", "adminCreateUserRole", "DELIVERY_WORKER",
                "adminCreateUserEmployeeNumber", "E-100", "adminCreateUserVehicleType", "Van",
                "adminCreateUserVehiclePlate", "AB 1234", "adminCreateUserEquipmentNote", "Trolley"))
                .expect(200).json();
        adminUserDriverTemporaryPassword = issuedPassword(adminCreateUserReply, "adminCreateUserTemporaryPassword");
        String adminViewUserPath = USERS + "/" + userID("ken_driver");
        JsonNode adminViewUserProfile = adminUserBrowser.get(adminViewUserPath).expect(200).json();
        assertEquals("E-100", adminViewUserProfile.path("employeeNumber").asText());
        assertEquals("Van", adminViewUserProfile.path("vehicleType").asText());
        assertEquals("AB 1234", adminViewUserProfile.path("vehiclePlate").asText());
        assertEquals("Trolley", adminViewUserProfile.path("equipmentNote").asText());
        adminUserBrowser.put(adminViewUserPath + "/delivery-worker-profile", Map.of(
                "adminEditDeliveryWorkerProfileEmployeeNumber", "E-100", "adminEditDeliveryWorkerProfileVehicleType",
                "Truck", "adminEditDeliveryWorkerProfileVehiclePlate", "AB 1234",
                "adminEditDeliveryWorkerProfileEquipmentNote", "Trolley")).expect(200);
        assertEquals("Truck", adminUserBrowser.get(adminViewUserPath).expect(200).json().path("vehicleType").asText());
        adminUserBrowser.post(USERS, Map.of(
                "adminCreateUserUsername", "lee_driver", "adminCreateUserRole", "DELIVERY_WORKER",
                "adminCreateUserEmployeeNumber", "E-100")).expect(409).code("ADMIN_EMPLOYEE_NUMBER_TAKEN");
        assertEquals(0L, database.sql("SELECT count(*) FROM app_user WHERE username = 'lee_driver'")
                .query(Long.class).single());
    }

    // What it does: Creates two accounts.
    // Input: Client and Admin roles.
    // Output: A temporary password for each.
    @Test
    @DisplayName("S2-T07")
    void checkS2T07() {
        reportPartial("creation and issued-password dialogs");
        JsonNode adminCreateUserClient = createUser("sarah_client", "CLIENT");
        JsonNode adminCreateUserAdmin = createUser("second_admin", "ADMIN");
        assertEquals("CLIENT", adminCreateUserClient.path("role").asText());
        assertEquals("ADMIN", adminCreateUserAdmin.path("role").asText());
        adminUserClientTemporaryPassword = issuedPassword(adminCreateUserClient, "adminCreateUserTemporaryPassword");
        adminUserSecondAdminTemporaryPassword =
                issuedPassword(adminCreateUserAdmin, "adminCreateUserTemporaryPassword");
    }

    // What it does: Completes first login.
    // Input: The picker's issued password.
    // Output: A Warehouse Worker session.
    @Test
    @DisplayName("S2-T08")
    void checkS2T08() {
        reportPartial("password and welcome pages as rendered in a browser");
        adminUserPickerBrowser = browser();
        checkFirstLogin(adminUserPickerBrowser, "mei_picker", adminUserPickerTemporaryPassword, FLOW_PICKER_PASSWORD,
                "WAREHOUSE_WORKER", WELCOME_PAGE);
    }

    // What it does: Changes the picker into a driver.
    // Input: The open worker session.
    // Output: A revoked old session.
    @Test
    @DisplayName("S2-T09")
    void checkS2T09() {
        reportPartial("browser notice after clicking a link or reloading the welcome page");
        changeRole("mei_picker", "DELIVERY_WORKER", "moved to delivery team").expect(200);
        adminUserPickerBrowser.get("/api/shared/session").expect(401).code("SHARED_SESSION_ENDED");
        JsonNode sharedLoginUserReply = adminUserPickerBrowser.login("mei_picker", FLOW_PICKER_PASSWORD)
                .expect(200).json();
        assertEquals("DELIVERY_WORKER", sharedLoginUserReply.path("role").asText());
        assertEquals(WELCOME_PAGE, sharedLoginUserReply.path("sharedLoginUserNextPageWebAddress").asText());
    }

    // What it does: Checks refused changes.
    // Input: Bad roles, absent reasons and inactive states.
    // Output: No role change.
    @Test
    @DisplayName("S2-T10")
    void checkS2T10() {
        reportPartial("Actions visibility and disabled confirmation without a reason");
        changeRole("sarah_client", "WAREHOUSE_WORKER", "testing client role").expect(409)
                .code("ADMIN_ROLE_CHANGE_NOT_ALLOWED");
        changeRole("mei_picker", "DELIVERY_WORKER", "testing same role")
                .expect(409).code("ADMIN_ROLE_CHANGE_NOT_ALLOWED");
        changeRole("mei_picker", "WAREHOUSE_WORKER", "").expect(400).code("ADMIN_REASON_REQUIRED");
        try {
            for (String accountStatus : List.of("BANNED", "INACTIVE", "LOCKED")) {
                setStatus("mei_picker", accountStatus);
                changeRole("mei_picker", "WAREHOUSE_WORKER", "testing account status").expect(409)
                        .code("ADMIN_USER_STATE_CONFLICT");
            }
        } finally {
            setStatus("mei_picker", "ACTIVE");
        }
        assertEquals("DELIVERY_WORKER", database.sql("SELECT role FROM app_user WHERE username = 'mei_picker'")
                .query(String.class).single());
    }

    // What it does: Previews a busy worker.
    // Input: Two sample picking tasks.
    // Output: A blocked role change.
    @Test
    @DisplayName("S2-T11")
    void checkS2T11() {
        reportPartial("impact sentence and references as rendered in the change dialog");
        String adminTemporaryCreateWorkerMessage = adminUserBrowser.tool("worker-with-active-work", null)
                .expect(200).json().path("message").asText();
        var adminTemporaryCreateWorkerMatch = Pattern.compile(
                "Warehouse Worker: (test_warehouse_\\d+)\\. Temporary password: (\\S+) \\| Picking orders: "
                + "(TEST-\\d+), (TEST-\\d+)").matcher(adminTemporaryCreateWorkerMessage);
        assertTrue(adminTemporaryCreateWorkerMatch.find(), "The worker tool must return its picking references.");
        adminUserWorkerUsername = adminTemporaryCreateWorkerMatch.group(1);
        adminUserWorkerTemporaryPassword = adminTemporaryCreateWorkerMatch.group(2);
        adminUserWorkerPickingReferences = List.of(adminTemporaryCreateWorkerMatch.group(3),
                adminTemporaryCreateWorkerMatch.group(4));
        checkWorkerImpact();
        changeRole(adminUserWorkerUsername, "DELIVERY_WORKER", "move active worker").expect(409)
                .code("ADMIN_USER_HAS_ACTIVE_WORK");
    }

    // What it does: Bans the busy worker.
    // Input: A reason and the fixture's password.
    // Output: Login is denied.
    @Test
    @DisplayName("S2-T12")
    void checkS2T12() {
        reportPartial("impact sentence and references as rendered in the ban dialog");
        checkWorkerImpact();
        ban(adminUserBrowser, adminUserWorkerUsername, "repeated wrong scans").expect(200);
        assertEquals("BANNED", status(adminUserWorkerUsername));
        assertAccountInactive(browser(), adminUserWorkerUsername, adminUserWorkerTemporaryPassword);
    }

    // What it does: Unbans the worker.
    // Input: A reason.
    // Output: Active status and a forced password change.
    @Test
    @DisplayName("S2-T13")
    void checkS2T13() {
        reportPartial("unban modal interaction and forced password page rendering");
        adminUserBrowser.post(USERS + "/" + userID(adminUserWorkerUsername) + "/unban",
                Map.of("adminUnbanUserReason", "ban was a mistake")).expect(200);
        assertEquals("ACTIVE", status(adminUserWorkerUsername));
        assertTrue(browser().login(adminUserWorkerUsername, adminUserWorkerTemporaryPassword).expect(200)
                .json().path("mustChangePassword").asBoolean());
    }

    // What it does: Deactivates and restores the Client.
    // Input: Two reasons.
    // Output: The same account survives.
    @Test
    @DisplayName("S2-T14")
    void checkS2T14() {
        reportPartial("Deactivated badge wording and confirmation dialogs");
        long userID = userID("sarah_client");
        adminUserBrowser.post(USERS + "/" + userID + "/deactivate",
                Map.of("adminDeactivateUserReason", "deactivate test account")).expect(200);
        assertEquals("INACTIVE", status("sarah_client"));
        assertAccountInactive(browser(), "sarah_client", adminUserClientTemporaryPassword);
        adminUserBrowser.post(USERS + "/" + userID + "/reactivate",
                Map.of("adminReactivateUserReason", "restore test account")).expect(200);
        assertEquals("ACTIVE", status("sarah_client"));
        assertEquals(userID, userID("sarah_client"));
    }

    // What it does: Unlocks a timed lock.
    // Input: Five failed driver logins.
    // Output: Zero failures and working login.
    @Test
    @DisplayName("S2-T15")
    void checkS2T15() {
        reportPartial("timed-lock badge, failed-tries note, Actions menu and Unlock window controls");
        lockDriver();
        JsonNode adminSearchUserLockedRow = searchDriver();
        assertTrue(adminSearchUserLockedRow.path("adminSearchUserIsLockedNow").asBoolean());
        assertEquals(5, adminSearchUserLockedRow.path("failedLoginCount").asInt());
        adminUserBrowser.post(USERS + "/" + userID("ken_driver") + "/unlock", null).expect(200);
        JsonNode adminSearchUserUnlockedRow = searchDriver();
        assertEquals("ACTIVE", adminSearchUserUnlockedRow.path("accountStatus").asText());
        assertFalse(adminSearchUserUnlockedRow.path("adminSearchUserIsLockedNow").asBoolean(true));
        assertEquals(0, adminSearchUserUnlockedRow.path("failedLoginCount").asInt());
        browser().login("ken_driver", adminUserDriverTemporaryPassword).expect(200);
    }

    // What it does: Checks reset validation.
    // Input: No confirmation, a short method and a valid method.
    // Output: One reset.
    @Test
    @DisplayName("S2-T16")
    void checkS2T16() {
        reportPartial("disabled button and one-time password window");
        String adminResetPasswordPath = USERS + "/" + userID("ken_driver") + "/reset-password";
        adminUserBrowser.post(adminResetPasswordPath, Map.of("adminResetPasswordIdentityIsConfirmed", false,
                "adminResetPasswordIdentityCheckMethod", "staff card shown in person")).expect(400)
                .code("ADMIN_IDENTITY_NOT_CONFIRMED");
        adminUserBrowser.post(adminResetPasswordPath, Map.of("adminResetPasswordIdentityIsConfirmed", true,
                "adminResetPasswordIdentityCheckMethod", "abc")).expect(400).code("ADMIN_REASON_REQUIRED");
        String adminResetPasswordIssued = resetDriver();
        assertFalse(adminResetPasswordIssued.equals(adminUserDriverTemporaryPassword));
    }

    // What it does: Checks that resetting replaces the old password.
    // Input: Old and new credentials.
    // Output: Forced change.
    @Test
    @DisplayName("S2-T17")
    void checkS2T17() {
        reportPartial("forced change page rendering");
        Session sharedLoginUserBrowser = browser();
        sharedLoginUserBrowser.login("ken_driver", adminUserDriverTemporaryPassword)
                .expect(401).code("SHARED_LOGIN_FAILED");
        assertTrue(sharedLoginUserBrowser.login("ken_driver", adminUserDriverResetPassword).expect(200)
                .json().path("mustChangePassword").asBoolean());
    }

    // What it does: Resets a locked driver.
    // Input: Five failed logins and identity proof.
    // Output: Immediate login.
    @Test
    @DisplayName("S2-T18")
    void checkS2T18() {
        reportPartial("reset and forced change pages as rendered in a browser");
        lockDriver();
        assertTrue(searchDriver().path("adminSearchUserIsLockedNow").asBoolean());
        assertTrue(browser().login("ken_driver", resetDriver()).expect(200)
                .json().path("mustChangePassword").asBoolean());
        assertFalse(searchDriver().path("adminSearchUserIsLockedNow").asBoolean(true));
    }

    // What it does: Checks reset blocking for banned accounts.
    // Input: A banned driver.
    // Output: A state conflict.
    @Test
    @DisplayName("S2-T19")
    void checkS2T19() {
        reportPartial("Reset password line absent in the banned account's Actions menu");
        ban(adminUserBrowser, "ken_driver", "stop driver access").expect(200);
        assertEquals("BANNED", searchDriver().path("accountStatus").asText());
        adminUserBrowser.post(USERS + "/" + userID("ken_driver") + "/reset-password",
                Map.of("adminResetPasswordIdentityIsConfirmed", true,
                        "adminResetPasswordIdentityCheckMethod", "staff card shown in person"))
                .expect(409).code("ADMIN_USER_STATE_CONFLICT");
    }

    // What it does: Refuses a self-ban.
    // Input: The first Admin's ID.
    // Output: The signed-in account stays active.
    @Test
    @DisplayName("S2-T20")
    void checkS2T20() {
        reportPartial("browser console interaction");
        long userID = database.sql("SELECT user_id FROM app_user WHERE role = 'ADMIN' ORDER BY user_id LIMIT 1")
                .query(Long.class).single();
        assertEquals(userID(bootstrapUsername), userID);
        ban(adminUserBrowser, bootstrapUsername, "testing my own account").expect(409)
                .code("ADMIN_OWN_ACCOUNT_NOT_ALLOWED");
        assertEquals("ACTIVE", status(bootstrapUsername));
        assertTrue(adminUserBrowser.get("/api/shared/session").expect(200).json()
                .path("sharedViewSessionIsLoggedIn").asBoolean());
    }

    // What it does: Bans the second Admin.
    // Input: An open Admin session.
    // Output: The second session is revoked.
    @Test
    @DisplayName("S2-T21")
    void checkS2T21() {
        reportPartial("actual navigation from the second Admin's browser");
        Session sharedLoginUserBrowser = browser();
        checkFirstLogin(sharedLoginUserBrowser, "second_admin", adminUserSecondAdminTemporaryPassword,
                SECOND_ADMIN_PASSWORD, "ADMIN", ADMIN_DASHBOARD);
        ban(adminUserBrowser, "second_admin", "remove test admin access").expect(200);
        assertEquals("BANNED", status("second_admin"));
        sharedLoginUserBrowser.get(USERS).expect(401).code("SHARED_SESSION_ENDED");
    }

    // What it does: Tests the Admin role boundary.
    // Input: A signed-in worker.
    // Output: A denied Users request.
    @Test
    @DisplayName("S2-T22")
    void checkS2T22() {
        reportPartial("browser console interaction");
        Session sharedLoginUserBrowser = browser();
        sharedLoginUserBrowser.login("mei_picker", FLOW_PICKER_PASSWORD).expect(200);
        sharedLoginUserBrowser.get(USERS).expect(403).code("SHARED_ACCESS_DENIED");
    }

    // What it does: Reads the account audit.
    // Input: The latest 30 rows.
    // Output: Changes without credential values.
    @Test
    @DisplayName("S2-T23")
    void checkS2T23() {
        List<Map<String, Object>> adminSearchAuditLogRows = database.sql("""
                SELECT action, target_type, target_id, old_value, new_value
                FROM audit_log ORDER BY audit_id DESC LIMIT 30
                """).query().listOfRows();
        Set<String> adminSearchAuditLogActions = new HashSet<>();
        for (Map<String, Object> adminSearchAuditLogRow : adminSearchAuditLogRows) {
            adminSearchAuditLogActions.add(adminSearchAuditLogRow.get("action").toString());
        }
        assertTrue(adminSearchAuditLogActions.containsAll(Set.of("USER_CREATED", "DELIVERY_WORKER_PROFILE_EDITED",
                "USER_ROLE_CHANGED", "USER_BANNED", "USER_UNBANNED", "USER_DEACTIVATED", "USER_REACTIVATED",
                "USER_UNLOCKED", "USER_PASSWORD_RESET")));
        String adminSearchAuditLogText = adminSearchAuditLogRows.toString();
        assertTrue(adminSearchAuditLogText.contains("moved to delivery team"));
        assertTrue(adminSearchAuditLogText.contains("staff card shown in person"));
        assertFalse(adminSearchAuditLogText.contains("\"passwordHash\""));
        assertFalse(adminSearchAuditLogText.contains("\"password\""));
        assertFalse(adminSearchAuditLogText.contains(adminUserPickerTemporaryPassword));
        assertFalse(adminSearchAuditLogText.contains(adminUserDriverResetPassword));
    }

    // What it does: Reads account security events.
    // Input: The latest selected events.
    // Output: Rejections and successes.
    @Test
    @DisplayName("S2-T24")
    void checkS2T24() {
        String adminSearchSecurityLogRows = database.sql("""
                SELECT event_type, result, details FROM security_log
                WHERE event_type IN
                    ('REJECTED_ADMIN_ACTION', 'ACCOUNT_BANNED', 'ROLE_CHANGED', 'ACCOUNT_UNLOCKED_BY_ADMIN')
                ORDER BY security_log_id DESC LIMIT 20
                """).query().listOfRows().toString();
        assertTrue(adminSearchSecurityLogRows.contains("adminBanUser: ADMIN_OWN_ACCOUNT_NOT_ALLOWED"));
        assertTrue(adminSearchSecurityLogRows.contains("adminChangeUserRole: ADMIN_USER_HAS_ACTIVE_WORK"));
        assertTrue(adminSearchSecurityLogRows.contains("ACCOUNT_BANNED"));
        assertTrue(adminSearchSecurityLogRows.contains("ROLE_CHANGED"));
        assertTrue(adminSearchSecurityLogRows.contains("ACCOUNT_UNLOCKED_BY_ADMIN"));
    }

    // What it does: Repeats the confirmed request concurrently.
    // Input: Two Admin sessions.
    // Output: One committed ban.
    @Test
    @DisplayName("S2-T25")
    void checkS2T25() {
        reportPartial("physical double click; sent simultaneous equivalent requests");
        long adminBanUserCountBefore = database.sql("SELECT count(*) FROM audit_log WHERE action = 'USER_BANNED'")
                .query(Long.class).single();
        Session adminBanUserSecondBrowser = admin();
        var adminBanUserFirstReply = CompletableFuture.supplyAsync(
                () -> ban(adminUserBrowser, "sarah_client", "test duplicate confirmation"));
        var adminBanUserSecondReply = CompletableFuture.supplyAsync(
                () -> ban(adminBanUserSecondBrowser, "sarah_client", "test duplicate confirmation"));
        Reply adminBanUserFirstResult = adminBanUserFirstReply.join();
        Reply adminBanUserSecondResult = adminBanUserSecondReply.join();
        if (adminBanUserFirstResult.response().statusCode() == 200) {
            adminBanUserSecondResult.expect(409).code("ADMIN_USER_STATE_CONFLICT");
        } else {
            adminBanUserFirstResult.expect(409).code("ADMIN_USER_STATE_CONFLICT");
            adminBanUserSecondResult.expect(200);
        }
        assertEquals(adminBanUserCountBefore + 1,
                database.sql("SELECT count(*) FROM audit_log WHERE action = 'USER_BANNED'").query(Long.class).single());
    }

    // What it does: Repairs old-style locks.
    // Input: A stored LOCKED status.
    // Output: Active with clear attempts and lock.
    @Test
    @DisplayName("S2-T27")
    void checkS2T27() {
        reportPartial("old lock badge colour and label and transition between Actions menus");
        setStatus("mei_picker", "LOCKED");
        JsonNode adminSearchUserResult = adminUserBrowser.get(
                USERS + "?adminSearchUserKeyword=mei_picker&adminSearchUserAccountStatus=LOCKED_NOW")
                .expect(200).json();
        JsonNode adminSearchUserRow = rows(adminSearchUserResult).getFirst();
        assertEquals("LOCKED", adminSearchUserRow.path("accountStatus").asText());
        assertTrue(adminSearchUserRow.path("adminSearchUserIsLockedNow").asBoolean());
        Session sharedLoginUserBrowser = browser();
        assertAccountInactive(sharedLoginUserBrowser, "mei_picker", FLOW_PICKER_PASSWORD);
        adminUserBrowser.post(USERS + "/" + userID("mei_picker") + "/unlock", null).expect(200);
        sharedLoginUserBrowser.login("mei_picker", FLOW_PICKER_PASSWORD).expect(200);
        assertTrue(database.sql("""
                SELECT account_status = 'ACTIVE' AND failed_login_count = 0 AND lock_until IS NULL
                FROM app_user WHERE username = 'mei_picker'
                """).query(Boolean.class).single());
    }

    // ===== SECTION 3: HELPER FUNCTIONS =====

    // What it does: Reads the search rows.
    // Input: A Users response.
    // Output: Its rows in display order.
    private List<JsonNode> rows(JsonNode adminSearchUserResponse) {
        List<JsonNode> adminSearchUserResult = new ArrayList<>();
        adminSearchUserResponse.path("adminSearchUserRowList").forEach(adminSearchUserResult::add);
        return adminSearchUserResult;
    }

    // What it does: Reads listed names.
    // Input: A Users response.
    // Output: The usernames in display order.
    private List<String> usernames(JsonNode adminSearchUserResponse) {
        return rows(adminSearchUserResponse).stream().map(row -> row.path("username").asText()).toList();
    }

    // What it does: Creates a standard fixture user.
    // Input: Username and role.
    // Output: Its creation response.
    private JsonNode createUser(String username, String role) {
        return adminUserBrowser.post(USERS, Map.of("adminCreateUserUsername", username,
                "adminCreateUserRole", role)).expect(200).json();
    }

    // What it does: Reads a fresh password without displaying it.
    // Input: An issuance response and its field.
    // Output: The temporary password used for later requests.
    private String issuedPassword(JsonNode adminReadIssuedPasswordReply, String adminReadIssuedPasswordField) {
        String adminReadIssuedPasswordValue = adminReadIssuedPasswordReply.path(adminReadIssuedPasswordField)
                .asText("");
        assertFalse(adminReadIssuedPasswordValue.isBlank(), "The response should include the issued password.");
        return adminReadIssuedPasswordValue;
    }

    // What it does: Completes a forced change.
    // Input: Credentials and expected role and address.
    // Output: A signed-in user.
    private void checkFirstLogin(Session sharedLoginUserBrowser, String username, String sharedLoginUserTemporary,
            String sharedLoginUserPermanent, String role, String sharedLoginUserStartPage) {
        JsonNode sharedLoginUserResult = sharedLoginUserBrowser.login(username, sharedLoginUserTemporary)
                .expect(200).json();
        assertTrue(sharedLoginUserResult.path("mustChangePassword").asBoolean());
        assertEquals("/shared/shared-change-password.html",
                sharedLoginUserResult.path("sharedLoginUserNextPageWebAddress").asText());
        sharedLoginUserBrowser.change(sharedLoginUserTemporary, sharedLoginUserPermanent, sharedLoginUserPermanent)
                .expect(200);
        JsonNode sharedLoginUserFinal = sharedLoginUserBrowser.login(username, sharedLoginUserPermanent)
                .expect(200).json();
        assertEquals(username, sharedLoginUserFinal.path("username").asText());
        assertEquals(role, sharedLoginUserFinal.path("role").asText());
        assertEquals(sharedLoginUserStartPage, sharedLoginUserFinal.path("sharedLoginUserNextPageWebAddress").asText());
        assertFalse(sharedLoginUserFinal.path("mustChangePassword").asBoolean(true));
    }

    // What it does: Posts a role change.
    // Input: Username, role and reason.
    // Output: The server's reply.
    private Reply changeRole(String username, String role, String adminChangeUserRoleReason) {
        return adminUserBrowser.post(USERS + "/" + userID(username) + "/role",
                Map.of("adminChangeUserRoleNewRole", role, "adminChangeUserRoleReason", adminChangeUserRoleReason));
    }

    // What it does: Prepares a stored status.
    // Input: Username and status.
    // Output: The account is updated in test mode.
    private void setStatus(String username, String accountStatus) {
        adminUserBrowser.tool("user-status", Map.of("adminTemporarySetUserStatusUsername", username,
                "adminTemporarySetUserStatusAccountStatus", accountStatus)).expect(200);
    }

    // What it does: Reads the worker's impact.
    // Input: The active-work fixture.
    // Output: Its two expected references.
    private void checkWorkerImpact() {
        JsonNode adminPreviewUserImpactReply = adminUserBrowser.get(
                USERS + "/" + userID(adminUserWorkerUsername) + "/impact").expect(200).json();
        assertEquals(2, adminPreviewUserImpactReply.path("adminPreviewUserImpactActivePickingTaskCount").asLong());
        assertTrue(adminPreviewUserImpactReply.path("adminPreviewUserImpactHasActiveWork").asBoolean());
        Set<String> adminPreviewUserImpactReferences = new HashSet<>();
        adminPreviewUserImpactReply.path("adminPreviewUserImpactActivePickingOrderReferenceList")
                .forEach(reference -> adminPreviewUserImpactReferences.add(reference.asText()));
        assertEquals(Set.copyOf(adminUserWorkerPickingReferences), adminPreviewUserImpactReferences);
    }

    // What it does: Posts a ban.
    // Input: A session, the target name and a reason.
    // Output: The server's reply.
    private Reply ban(Session adminBanUserBrowser, String username, String adminBanUserReason) {
        return adminBanUserBrowser.post(USERS + "/" + userID(username) + "/ban",
                Map.of("adminBanUserReason", adminBanUserReason));
    }

    // What it does: Reads a saved status.
    // Input: Username.
    // Output: The status in PostgreSQL.
    private String status(String username) {
        return database.sql("SELECT account_status FROM app_user WHERE username = :username")
                .param("username", username).query(String.class).single();
    }

    // What it does: Confirms login refusal.
    // Input: Valid credentials for an inactive account.
    // Output: The inactive error.
    private void assertAccountInactive(Session sharedLoginUserBrowser, String username,
            String sharedLoginUserPassword) {
        JsonNode sharedLoginUserError = sharedLoginUserBrowser.login(username, sharedLoginUserPassword).expect(403)
                .code("SHARED_ACCOUNT_NOT_ACTIVE").json();
        assertTrue(sharedLoginUserError.path("message").asText().contains("This account cannot be used."));
    }

    // What it does: Locks the driver by failed login.
    // Input: An active account.
    // Output: The fifth attempt locks it.
    private void lockDriver() {
        Session sharedLoginUserBrowser = browser();
        for (int sharedLoginUserAttempt = 1; sharedLoginUserAttempt <= 4; sharedLoginUserAttempt++) {
            sharedLoginUserBrowser.login("ken_driver", "WrongDriver26").expect(401).code("SHARED_LOGIN_FAILED");
        }
        sharedLoginUserBrowser.login("ken_driver", "WrongDriver26").expect(401).code("SHARED_ACCOUNT_LOCKED");
    }

    // What it does: Searches for the driver.
    // Input: None.
    // Output: The sole row with its current status.
    private JsonNode searchDriver() {
        return rows(adminUserBrowser.get(USERS + "?adminSearchUserKeyword=ken_driver").expect(200).json()).getFirst();
    }

    // What it does: Resets the driver using the accepted identity check.
    // Input: None.
    // Output: A new temporary password.
    private String resetDriver() {
        JsonNode adminResetPasswordReply = adminUserBrowser.post(USERS + "/" + userID("ken_driver") + "/reset-password",
                Map.of("adminResetPasswordIdentityIsConfirmed", true,
                        "adminResetPasswordIdentityCheckMethod", "staff card shown in person")).expect(200).json();
        adminUserDriverResetPassword = issuedPassword(adminResetPasswordReply, "adminResetPasswordTemporaryPassword");
        return adminUserDriverResetPassword;
    }
}
