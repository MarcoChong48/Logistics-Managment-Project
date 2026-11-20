// TEMPORARY TEST CODE - remove before integration
// Stage 4 checks the super admin guard and recovery on the isolated flow database.
package com.lms.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;

import com.lms.backend.shared.database.AppUserRepository;
import com.lms.backend.shared.log.SharedSecurityLogWriter;
import com.lms.backend.shared.login.SharedBootstrapAdminRunner;

import tools.jackson.databind.JsonNode;

@TestMethodOrder(MethodOrderer.MethodName.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminSuperAdminFlowCheckTest extends SharedFlowCheck {

    // ===== SECTION 1: FLOW STATE AND RECOVERY COLLABORATORS =====

    private static final String ADMIN_PASSWORD = "FlowBackup42Own";
    private static final String RESET_PASSWORD = "FlowBootstrap42Temp";
    private static final String RECOVERED_PASSWORD = "FlowBootstrap42Own";
    private static final String RESET_REASON = "rescue drill";
    private static final Map<String, Object> RESET_IDENTITY = Map.of(
            "adminResetPasswordIdentityIsConfirmed", true,
            "adminResetPasswordIdentityCheckMethod", RESET_REASON);

    private Session adminSuperAdminBrowser;
    private Session adminRegularAdminBrowser;
    private String adminRegularAdminUsername;
    private List<String> adminBannedClientUsernameList;

    @Autowired private AppUserRepository sharedCreateBootstrapAdminRepository;
    @Autowired private PasswordEncoder sharedCreateBootstrapAdminPasswordEncoder;
    @Autowired private SharedSecurityLogWriter sharedCreateBootstrapAdminSecurityWriter;
    @Autowired private PlatformTransactionManager sharedCreateBootstrapAdminTransactionManager;
    @Autowired private SharedBootstrapAdminRunner sharedCreateBootstrapAdminDefaultRunner;

    // ===== SECTION 2: STAGE 4 CASES =====

    // What it does: Checks the bootstrap Admin's permissions.
    // Input: Stage 2 users.
    // Output: The flags and CSV column.
    @Test
    @DisplayName("S4-T18")
    void checkS4T18() {
        reportPartial("visual comparison, own-row labels, Super admin flag, note, role choices and Actions menu");
        adminSuperAdminBrowser = admin();
        adminSuperAdminBrowser.get("/admin/admin-users.html").expect(200);
        JsonNode adminSearchUserResponse = adminSuperAdminBrowser.get(USERS + "?adminSearchUserRole=ADMIN")
                .expect(200).json();
        assertTrue(adminSearchUserResponse.path("adminSearchUserCanManageAdminAccount").asBoolean());
        List<JsonNode> adminSearchUserAdminRows = userRows(adminSearchUserResponse);
        assertEquals(List.of(bootstrapUsername), adminSearchUserAdminRows.stream()
                .filter(row -> row.path("adminSearchUserIsSuperAdmin").asBoolean())
                .map(row -> row.path("username").asText()).toList());
        JsonNode adminSearchUserOwnRow = userRow(adminSearchUserAdminRows, bootstrapUsername);
        assertTrue(adminSearchUserOwnRow.path("adminSearchUserIsOwnAccount").asBoolean());
        assertFalse(adminSearchUserOwnRow.path("adminSearchUserCanChange").asBoolean(true));
        assertTrue(userRow(adminSearchUserAdminRows, "second_admin").path("adminSearchUserCanChange").asBoolean());

        // The CSV exposes the identity flag without the viewer's per-account write permission.
        String adminDownloadUserCSV = adminSuperAdminBrowser.get(USERS + "/csv?adminSearchUserRole=ADMIN")
                .expect(200).body();
        List<String> adminDownloadUserCSVLines = adminDownloadUserCSV.lines().toList();
        assertTrue(adminDownloadUserCSVLines.getFirst().contains("\"adminSearchUserIsSuperAdmin\""));
        assertFalse(adminDownloadUserCSVLines.getFirst().contains("\"adminSearchUserCanChange\""));
        assertTrue(adminDownloadUserCSVLines.stream().anyMatch(line -> line.contains("\"" + bootstrapUsername + "\"")
                && line.endsWith(",\"true\"")));
    }

    // What it does: Signs in a regular Admin.
    // Input: A test Admin.
    // Output: Read-only Admin rows and editable worker rows.
    @Test
    @DisplayName("S4-T19")
    void checkS4T19() {
        reportPartial("note, flag, Actions menus and create/change role choices as rendered in a browser");
        String[] adminTemporaryCreateTestUserCredentials = testUser(adminSuperAdminBrowser, "ADMIN");
        adminRegularAdminUsername = adminTemporaryCreateTestUserCredentials[0];
        assertTrue(adminRegularAdminUsername.startsWith("test_admin_"));
        String adminRegularAdminTemporaryPassword = adminTemporaryCreateTestUserCredentials[1];
        adminRegularAdminBrowser = browser();
        assertTrue(adminRegularAdminBrowser.login(adminRegularAdminUsername, adminRegularAdminTemporaryPassword)
                .expect(200).json().path("mustChangePassword").asBoolean());
        adminRegularAdminBrowser.change(adminRegularAdminTemporaryPassword, ADMIN_PASSWORD, ADMIN_PASSWORD).expect(200);
        adminRegularAdminBrowser.login(adminRegularAdminUsername, ADMIN_PASSWORD).expect(200);

        JsonNode adminSearchUserResponse = adminRegularAdminBrowser.get(USERS + "?adminSearchUserRole=ADMIN")
                .expect(200).json();
        assertFalse(adminSearchUserResponse.path("adminSearchUserCanManageAdminAccount").asBoolean(true));
        List<JsonNode> adminSearchUserAdminRows = userRows(adminSearchUserResponse);
        assertTrue(userRow(adminSearchUserAdminRows, bootstrapUsername)
                .path("adminSearchUserIsSuperAdmin").asBoolean());
        for (JsonNode adminSearchUserAdminRow : adminSearchUserAdminRows) {
            assertFalse(adminSearchUserAdminRow.path("adminSearchUserCanChange").asBoolean(true));
        }
        JsonNode adminSearchUserWorkerResponse = adminRegularAdminBrowser
                .get(USERS + "?adminSearchUserKeyword=mei_picker")
                .expect(200).json();
        assertTrue(userRow(userRows(adminSearchUserWorkerResponse), "mei_picker")
                .path("adminSearchUserCanChange").asBoolean());
        JsonNode adminViewUserResponse = adminRegularAdminBrowser.get(USERS + "/" + userID(bootstrapUsername))
                .expect(200).json();
        assertTrue(adminViewUserResponse.path("adminViewUserIsSuperAdmin").asBoolean());
        assertFalse(adminViewUserResponse.path("adminViewUserCanChange").asBoolean(true));
    }

    // What it does: Checks protected account writes.
    // Input: A regular Admin.
    // Output: Five refusals with no saved changes.
    @Test
    @DisplayName("S4-T20")
    void checkS4T20() {
        reportPartial("browser console commands; sent the same API requests directly");
        List<Map<String, Object>> adminCheckSuperAdminGuardBefore = adminAndPickerAccounts();
        String adminCheckSuperAdminGuardProtectedPath = USERS + "/" + userID(bootstrapUsername);
        adminRegularAdminBrowser.post(adminCheckSuperAdminGuardProtectedPath + "/ban",
                Map.of("adminBanUserReason", "super admin test")).expect(409).code("ADMIN_SUPER_ADMIN_PROTECTED");
        adminRegularAdminBrowser.post(adminCheckSuperAdminGuardProtectedPath + "/reset-password",
                Map.of("adminResetPasswordIdentityIsConfirmed", true,
                        "adminResetPasswordIdentityCheckMethod", "super admin test")).expect(409)
                .code("ADMIN_SUPER_ADMIN_PROTECTED");
        adminRegularAdminBrowser.post(USERS + "/" + userID("second_admin") + "/unban",
                Map.of("adminUnbanUserReason", "super admin test")).expect(403).code("ADMIN_SUPER_ADMIN_REQUIRED");
        adminRegularAdminBrowser.post(USERS, Map.of(
                "adminCreateUserUsername", "extra_admin", "adminCreateUserRole", "ADMIN")).expect(403)
                .code("ADMIN_SUPER_ADMIN_REQUIRED");
        adminRegularAdminBrowser.post(USERS + "/" + userID("mei_picker") + "/role",
                Map.of("adminChangeUserRoleNewRole", "ADMIN", "adminChangeUserRoleReason", "super admin test"))
                .expect(403).code("ADMIN_SUPER_ADMIN_REQUIRED");
        assertEquals(adminCheckSuperAdminGuardBefore, adminAndPickerAccounts());
        assertEquals(0, count("SELECT count(*) FROM app_user WHERE username = 'extra_admin'"));
    }

    // What it does: Checks rejection logging.
    // Input: The previous five refusals.
    // Output: Latest events in order.
    @Test
    @DisplayName("S4-T21")
    void checkS4T21() {
        assertEquals(List.of(
                "adminChangeUserRole: ADMIN_SUPER_ADMIN_REQUIRED",
                "adminCreateUser: ADMIN_SUPER_ADMIN_REQUIRED",
                "adminUnbanUser: ADMIN_SUPER_ADMIN_REQUIRED",
                "adminResetPassword: ADMIN_SUPER_ADMIN_PROTECTED",
                "adminBanUser: ADMIN_SUPER_ADMIN_PROTECTED"), database.sql("""
                SELECT details FROM security_log WHERE event_type = 'REJECTED_ADMIN_ACTION'
                ORDER BY security_log_id DESC LIMIT 5
                """).query(String.class).list());
    }

    // What it does: Checks regular Admin powers.
    // Input: Clients and a worker.
    // Output: Two bans and one staff role change.
    @Test
    @DisplayName("S4-T22")
    void checkS4T22() {
        reportPartial("Users controls and updates in the browser");
        adminBannedClientUsernameList = List.of(testUser(adminSuperAdminBrowser, "CLIENT")[0],
                testUser(adminSuperAdminBrowser, "CLIENT")[0]);
        String adminChangeUserRoleWorkerUsername = testUser(adminSuperAdminBrowser, "WAREHOUSE_WORKER")[0];
        for (String username : adminBannedClientUsernameList) {
            ban(adminRegularAdminBrowser, username);
            assertEquals("BANNED", accountStatus(username));
        }
        changeRole(adminRegularAdminBrowser, adminChangeUserRoleWorkerUsername, "DELIVERY_WORKER");
        assertEquals("DELIVERY_WORKER", database.sql("SELECT role FROM app_user WHERE username = :username")
                .param("username", adminChangeUserRoleWorkerUsername).query(String.class).single());
    }

    // What it does: Ends a regular Admin's access and lifts two bans.
    // Input: The affected accounts.
    // Output: New statuses.
    @Test
    @DisplayName("S4-T23")
    void checkS4T23() {
        reportPartial("browser navigation and list controls");
        ban(adminSuperAdminBrowser, adminRegularAdminUsername);
        assertEquals("Your session has ended. Please log in again.",
                adminRegularAdminBrowser.get(USERS).expect(401).code("SHARED_SESSION_ENDED")
                        .json().path("message").asText());
        List<JsonNode> adminSearchUserBannedRows = userRows(adminSuperAdminBrowser.get(
                USERS + "?adminSearchUserAccountStatus=BANNED").expect(200).json());
        userRow(adminSearchUserBannedRows, adminRegularAdminUsername);
        for (String username : adminBannedClientUsernameList) {
            assertEquals("BANNED", userRow(adminSearchUserBannedRows, username).path("accountStatus").asText());
            unban(username);
            assertEquals("ACTIVE", accountStatus(username));
        }
    }

    // What it does: Manages Admin roles and credentials.
    // Input: A banned Admin and a new account.
    // Output: Saved accounts.
    @Test
    @DisplayName("S4-T24")
    void checkS4T24() {
        reportPartial("confirmation and one-time password windows");
        unban(adminRegularAdminUsername);
        JsonNode adminResetPasswordResponse = adminSuperAdminBrowser.post(
                USERS + "/" + userID(adminRegularAdminUsername) + "/reset-password", RESET_IDENTITY).expect(200).json();
        assertFalse(adminResetPasswordResponse.path("adminResetPasswordTemporaryPassword").asText().isBlank());
        changeRole(adminSuperAdminBrowser, adminRegularAdminUsername, "WAREHOUSE_WORKER");
        JsonNode adminViewUserChanged = adminSuperAdminBrowser.get(USERS + "/" + userID(adminRegularAdminUsername))
                .expect(200).json();
        assertEquals("WAREHOUSE_WORKER", adminViewUserChanged.path("role").asText());
        assertEquals("ACTIVE", adminViewUserChanged.path("accountStatus").asText());
        assertTrue(adminViewUserChanged.path("mustChangePassword").asBoolean());

        JsonNode adminCreateUserResponse = adminSuperAdminBrowser.post(USERS, Map.of(
                "adminCreateUserUsername", "third_admin", "adminCreateUserRole", "ADMIN")).expect(200).json();
        assertFalse(adminCreateUserResponse.path("adminCreateUserTemporaryPassword").asText().isBlank());
        JsonNode adminViewUserCreated = adminSuperAdminBrowser.get(USERS + "/" + userID("third_admin"))
                .expect(200).json();
        assertEquals("ADMIN", adminViewUserCreated.path("role").asText());
        assertTrue(adminViewUserCreated.path("mustChangePassword").asBoolean());
    }

    // What it does: Protects the bootstrap Admin from test tools.
    // Input: Status and expiry requests.
    // Output: Two refusals.
    @Test
    @DisplayName("S4-T25")
    void checkS4T25() {
        reportPartial("test-tool result boxes in the browser");
        adminSuperAdminBrowser.tool("user-status", Map.of(
                "adminTemporarySetUserStatusUsername", bootstrapUsername,
                "adminTemporarySetUserStatusAccountStatus", "BANNED")).expect(409).code("ADMIN_SUPER_ADMIN_PROTECTED");
        adminSuperAdminBrowser.tool("expire-temporary-password", Map.of(
                "adminTemporaryExpireTemporaryPasswordUsername", bootstrapUsername)).expect(409)
                .code("ADMIN_SUPER_ADMIN_PROTECTED");
        assertEquals("ACTIVE", accountStatus(bootstrapUsername));
        assertTrue(adminSuperAdminBrowser.get(SESSION).expect(200)
                .json().path("sharedViewSessionIsLoggedIn").asBoolean());
    }

    // What it does: Recovers the first Admin.
    // Input: A requested startup reset.
    // Output: Revoked sessions and new access.
    @Test
    @DisplayName("S4-T26")
    @ExtendWith(OutputCaptureExtension.class)
    void checkS4T26(CapturedOutput sharedCreateBootstrapAdminMessages) {
        reportPartial("Compose setting edits, container restarts and waits; invoked both startup branches directly");
        SharedBootstrapAdminRunner sharedCreateBootstrapAdminResetRunner = new SharedBootstrapAdminRunner(
                sharedCreateBootstrapAdminRepository, sharedCreateBootstrapAdminPasswordEncoder,
                sharedCreateBootstrapAdminSecurityWriter, sharedCreateBootstrapAdminTransactionManager,
                bootstrapUsername, RESET_PASSWORD, true);
        sharedCreateBootstrapAdminResetRunner.sharedCreateBootstrapAdmin();
        assertTrue(sharedCreateBootstrapAdminMessages.getAll().contains("The super admin was reset. "
                + "Set LMS_BOOTSTRAP_ADMIN_RESET_ON_START back to false."));
        assertFalse(sharedCreateBootstrapAdminMessages.getAll().contains(RESET_PASSWORD));

        // A live cookie from before the reset and the previous permanent password both stop working.
        adminSuperAdminBrowser.get(USERS).expect(401).code("SHARED_SESSION_ENDED");
        Session sharedLoginUserRecoveredBrowser = browser();
        assertEquals("Wrong username or password.", sharedLoginUserRecoveredBrowser
                .login(bootstrapUsername, FLOW_ADMIN_PASSWORD).expect(401).code("SHARED_LOGIN_FAILED")
                .json().path("message").asText());
        JsonNode sharedLoginUserTemporary = sharedLoginUserRecoveredBrowser.login(bootstrapUsername, RESET_PASSWORD)
                .expect(200).json();
        assertTrue(sharedLoginUserTemporary.path("mustChangePassword").asBoolean());
        assertEquals(CHANGE_PASSWORD, sharedLoginUserTemporary.path("sharedLoginUserNextPageWebAddress").asText());
        sharedLoginUserRecoveredBrowser.change(RESET_PASSWORD, RECOVERED_PASSWORD, RECOVERED_PASSWORD).expect(200);

        // The ordinary startup rule preserves the chosen permanent password while the reset setting is off.
        sharedCreateBootstrapAdminDefaultRunner.sharedCreateBootstrapAdmin();
        assertFalse(sharedLoginUserRecoveredBrowser.login(bootstrapUsername, RECOVERED_PASSWORD).expect(200)
                .json().path("mustChangePassword").asBoolean(true));
        assertEquals("super admin reset on start", database.sql("""
                SELECT details FROM security_log WHERE event_type = 'TEMPORARY_PASSWORD_ISSUED'
                ORDER BY security_log_id DESC LIMIT 1
                """).query(String.class).single());

        // Later flow classes can still use the same permanent bootstrap test credential.
        sharedLoginUserRecoveredBrowser.change(RECOVERED_PASSWORD, FLOW_ADMIN_PASSWORD, FLOW_ADMIN_PASSWORD)
                .expect(200);
    }

    // ===== SECTION 3: HELPER FUNCTIONS =====

    // What it does: Collects search rows.
    // Input: A Users reply.
    // Output: An ordered list of visible accounts.
    private List<JsonNode> userRows(JsonNode adminSearchUserResponse) {
        List<JsonNode> adminSearchUserResult = new ArrayList<>();
        adminSearchUserResponse.path("adminSearchUserRowList").forEach(adminSearchUserResult::add);
        return adminSearchUserResult;
    }

    // What it does: Finds a row by name.
    // Input: The search rows and username.
    // Output: The matching row.
    private JsonNode userRow(List<JsonNode> adminSearchUserRowList, String username) {
        return adminSearchUserRowList.stream().filter(row -> row.path("username").asText().equalsIgnoreCase(username))
                .findFirst().orElseThrow(() -> new AssertionError("Missing Users row for " + username));
    }

    // What it does: Lists protected rows.
    // Input: The flow database.
    // Output: The planned before/after snapshot.
    private List<Map<String, Object>> adminAndPickerAccounts() {
        return database.sql("""
                SELECT user_id, username, role, account_status
                FROM app_user WHERE role = 'ADMIN' OR username = 'mei_picker' ORDER BY user_id
                """).query().listOfRows();
    }

    // What it does: Bans a fixture account.
    // Input: Admin browser and username.
    // Output: Successful write.
    private void ban(Session adminBanUserBrowser, String username) {
        adminBanUserBrowser.post(USERS + "/" + userID(username) + "/ban", Map.of("adminBanUserReason", RESET_REASON))
                .expect(200);
    }

    // What it does: Unbans a fixture account as the super admin.
    // Input: Username.
    // Output: Successful write.
    private void unban(String username) {
        adminSuperAdminBrowser.post(USERS + "/" + userID(username) + "/unban",
                Map.of("adminUnbanUserReason", RESET_REASON)).expect(200);
    }

    // What it does: Changes a fixture role.
    // Input: Admin browser, username and role.
    // Output: Successful write.
    private void changeRole(Session adminChangeUserRoleBrowser, String username, String role) {
        adminChangeUserRoleBrowser.post(USERS + "/" + userID(username) + "/role",
                Map.of("adminChangeUserRoleNewRole", role, "adminChangeUserRoleReason", RESET_REASON)).expect(200);
    }

    // What it does: Reads the current database status.
    // Input: Username.
    // Output: The stored account status.
    private String accountStatus(String username) {
        return database.sql("SELECT account_status FROM app_user WHERE username = :username")
                .param("username", username).query(String.class).single();
    }
}
