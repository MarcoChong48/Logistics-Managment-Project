// TEMPORARY TEST CODE - remove before integration
// Stage 4 repeats Stage 1 against the real sign-in routes and disposable database.
package com.lms.backend;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import com.lms.backend.shared.login.SharedBootstrapAdminRunner;

import tools.jackson.databind.JsonNode;

@TestPropertySource(properties = "lms.shared.session-idle-timeout-minutes=3")
@TestMethodOrder(MethodOrderer.MethodName.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SharedLoginFlowCheckTest extends SharedFlowCheck {

    private static final String BAD_PASSWORD = "FlowBadInput42";
    private static final String UNKNOWN_USERNAME = "flow_unknown_login";
    private static final String NOT_LOGGED_IN = "SHARED_NOT_LOGGED_IN";
    private static final String LOGIN_REFUSED = "SHARED_LOGIN_FAILED";
    private static final String LOCKED = "SHARED_ACCOUNT_LOCKED";
    private static final String LOGOUT = "/api/shared/logout";

    @Autowired SharedBootstrapAdminRunner bootstrapRunner;
    private Session firstAdmin;
    private String workerUsername;
    private String workerPassword = "FlowWarehouse42Own";

    // What it does: Opens sign-in, enters the fresh bootstrap account and checks the forced gate.
    // Input: The fresh Stage 0 database. Output: A temporary session for the next case.
    @Test @DisplayName("S1-T01")
    void checkS1T01() {
        firstAdmin = browser();
        assertTrue(firstAdmin.get("/").expect(200).body().contains(LOGIN_PAGE));
        firstAdmin.get(LOGIN_PAGE).expect(200);
        assertTrue(firstAdmin.login(bootstrapUsername, bootstrapPassword).expect(200).json()
                .path("mustChangePassword").asBoolean());
        assertTrue(firstAdmin.get(ADMIN_DASHBOARD).expect(302).header("Location").contains(CHANGE_PASSWORD));
        reportPartial("root-page navigation and the visible login page");
    }

    // What it does: Sends each listed invalid password and checks the first reported rule.
    // Input: The forced bootstrap session. Output: Every password refusal and message.
    @Test @DisplayName("S1-T02")
    void checkS1T02() {
        Map<String, String> sharedCheckPasswordFailures = Map.of(
                "Ab1", "Use at least 8 characters.",
                "abcdefg1", "Use at least one capital letter from A to Z.",
                "ABCDEFG1", "Use at least one small letter from a to z.",
                "Abcdefgh", "Use at least one number from 0 to 9.",
                "Aa1" + "界".repeat(24), "Use fewer characters. The password can use at most 72 bytes.",
                bootstrapPassword, "The new password must be different from the current one.");
        sharedCheckPasswordFailures.forEach((sharedCheckNewPassword, sharedCheckExpectedMessage) -> {
            JsonNode sharedCheckPasswordError = firstAdmin.change(bootstrapPassword, sharedCheckNewPassword,
                    sharedCheckNewPassword).expect(400).code("SHARED_PASSWORD_RULE_FAILED").json();
            assertEquals(sharedCheckExpectedMessage, sharedCheckPasswordError.path("message").asText());
        });
        firstAdmin.change(bootstrapPassword, FLOW_ADMIN_PASSWORD, "FlowMismatch42").expect(400)
                .code("SHARED_CONFIRM_PASSWORD_DIFFERENT");
        reportPartial("visible form errors that the browser can display before sending a request");
    }

    // What it does: Sets the permanent bootstrap password and checks sign-in with both values.
    // Input: The temporary Admin session. Output: A new Admin session with a dashboard start page.
    @Test @DisplayName("S1-T03")
    void checkS1T03() {
        assertEquals("Password changed. Please log in again.",
                firstAdmin.change(bootstrapPassword, FLOW_ADMIN_PASSWORD, FLOW_ADMIN_PASSWORD)
                        .expect(200).json().path("message").asText());
        firstAdmin.get(SESSION).expect(200);
        firstAdmin.login(bootstrapUsername, bootstrapPassword).expect(401).code(LOGIN_REFUSED);
        firstAdmin = admin();
        assertEquals(bootstrapUsername, firstAdmin.get(SESSION).expect(200).json().path("username").asText());
        reportPartial("browser redirect after the change and username placement in the top bar");
    }

    // What it does: Confirms the three inputs share one public sign-in refusal.
    // Input: Unknown user, wrong password and an overlength password. Output: The same message each time.
    @Test @DisplayName("S1-T04")
    void checkS1T04() {
        firstAdmin = admin();
        firstAdmin.post(LOGOUT, null).expect(200);
        for (Reply sharedCheckLoginFailure : List.of(
                firstAdmin.login(UNKNOWN_USERNAME, BAD_PASSWORD),
                firstAdmin.login(bootstrapUsername, BAD_PASSWORD),
                firstAdmin.login(bootstrapUsername, FLOW_ADMIN_PASSWORD + "a".repeat(80)))) {
            assertEquals("Wrong username or password.",
                    sharedCheckLoginFailure.expect(401).code(LOGIN_REFUSED).json().path("message").asText());
        }
        reportPartial("visual persistence of the open sign-in form");
    }

    // What it does: Creates a Warehouse Worker and checks the role's start page and denied routes.
    // Input: The bootstrap Admin session. Output: A permanent Warehouse Worker for the next cases.
    @Test @DisplayName("S1-T05")
    void checkS1T05() {
        firstAdmin = admin();
        String[] sharedCheckWorker = testUser(firstAdmin, "WAREHOUSE_WORKER");
        workerUsername = sharedCheckWorker[0];
        Session sharedCheckWorkerSession = browser();
        sharedCheckWorkerSession.login(workerUsername, sharedCheckWorker[1]).expect(200);
        sharedCheckWorkerSession.change(sharedCheckWorker[1], workerPassword, workerPassword).expect(200);
        JsonNode sharedCheckWorkerLogin = sharedCheckWorkerSession.login(workerUsername, workerPassword)
                .expect(200).json();
        assertEquals(workerUsername, sharedCheckWorkerLogin.path("username").asText());
        assertEquals("WAREHOUSE_WORKER", sharedCheckWorkerLogin.path("role").asText());
        assertEquals(WELCOME_PAGE, sharedCheckWorkerLogin.path("sharedLoginUserNextPageWebAddress").asText());
        assertTrue(sharedCheckWorkerSession.get(ADMIN_DASHBOARD).expect(302).header("Location")
                .contains(WELCOME_PAGE));
        sharedCheckWorkerSession.get("/api/admin/temporary-test/mode").expect(403).code("SHARED_ACCESS_DENIED");
        reportPartial("welcome page layout and notice rendering");
    }

    // What it does: Locks the worker at five failed tries and checks the stored lock.
    // Input: The worker's username and correct password. Output: HTTP and SQL assertions about the lock.
    @Test @DisplayName("S1-T06")
    void checkS1T06() {
        Session sharedCheckWorker = browser();
        for (int sharedCheckAttempt = 1; sharedCheckAttempt < 5; sharedCheckAttempt++) {
            sharedCheckWorker.login(workerUsername, BAD_PASSWORD).expect(401).code(LOGIN_REFUSED);
        }
        assertTrue(sharedCheckWorker.login(workerUsername, BAD_PASSWORD).expect(401).code(LOCKED)
                .json().path("message").asText().contains("15 minutes"));
        sharedCheckWorker.login(workerUsername, workerPassword).expect(401).code(LOCKED);
        Map<String, Object> sharedCheckWorkerRow = workerState();
        assertEquals(5, ((Number) sharedCheckWorkerRow.get("failed_login_count")).intValue());
        assertNotNull(sharedCheckWorkerRow.get("lock_until"));
        assertTrue(database.sql("""
                SELECT lock_until BETWEEN CURRENT_TIMESTAMP + INTERVAL '14 minutes'
                                  AND CURRENT_TIMESTAMP + INTERVAL '15 minutes'
                FROM app_user WHERE username = :username
                """).param("username", workerUsername).query(Boolean.class).single());
        reportPartial("visual persistence of the sign-in form");
    }

    // What it does: Clears the test lock and signs in again. Input: The locked worker. Output: Cleared lock data.
    @Test @DisplayName("S1-T07")
    void checkS1T07() {
        firstAdmin.tool("end-lock", Map.of("adminTemporaryEndUserLockUsername", workerUsername)).expect(200);
        browser().login(workerUsername, workerPassword).expect(200);
        assertEquals(0, ((Number) workerState().get("failed_login_count")).intValue());
        assertNull(workerState().get("lock_until"));
    }

    // What it does: Verifies a successful login resets failures below five. Input: The worker. Output: Two logins.
    @Test @DisplayName("S1-T08")
    void checkS1T08() {
        Session sharedCheckWorker = browser();
        for (int sharedCheckAttempts : List.of(3, 4)) {
            for (int sharedCheckAttempt = 0; sharedCheckAttempt < sharedCheckAttempts; sharedCheckAttempt++) {
                sharedCheckWorker.login(workerUsername, BAD_PASSWORD).expect(401).code(LOGIN_REFUSED);
            }
            sharedCheckWorker.login(workerUsername, workerPassword).expect(200);
            sharedCheckWorker.post(LOGOUT, null).expect(200);
        }
    }

    // What it does: Expires a temporary password through the test tools.
    // Input: A new Client test user. Output: Refused sign-in and no authenticated session.
    @Test @DisplayName("S1-T09")
    void checkS1T09() {
        String[] sharedCheckExpiredUser = testUser(firstAdmin, "CLIENT");
        firstAdmin.tool("expire-temporary-password",
                Map.of("adminTemporaryExpireTemporaryPasswordUsername", sharedCheckExpiredUser[0])).expect(200);
        Session sharedCheckGuest = browser();
        assertEquals("This temporary password has expired. Please ask an Admin for a new one.",
                sharedCheckGuest.login(sharedCheckExpiredUser[0], sharedCheckExpiredUser[1]).expect(401)
                        .code("SHARED_TEMPORARY_PASSWORD_EXPIRED").json().path("message").asText());
        assertFalse(sharedCheckGuest.get(SESSION).expect(200).json().path("sharedViewSessionIsLoggedIn").asBoolean());
        reportPartial("visual persistence of the sign-in form");
    }

    // What it does: Checks the three-minute session and the endpoint behind Stay logged in.
    // Input: The test-only timeout override. Output: An authenticated extension response.
    @Test @DisplayName("S1-T10")
    void checkS1T10() {
        firstAdmin = admin();
        assertEquals(180, firstAdmin.get(SESSION).expect(200).json().path("sharedViewSessionIdleTimeoutSeconds").asInt());
        assertEquals("Session extended.", firstAdmin.post("/api/shared/session/extend", null)
                .expect(200).json().path("message").asText());
        firstAdmin.get(ADMIN_DASHBOARD).expect(200);
        reportPartial("real countdown, the warning dialog and changing Compose settings");
    }

    // What it does: Expires the session and checks subsequent navigation.
    // Input: The running embedded web server. Output: A login redirect and a rejected protected request.
    @Test @DisplayName("S1-T11")
    void checkS1T11() {
        firstAdmin.expire();
        assertTrue(firstAdmin.get(ADMIN_DASHBOARD).expect(302).header("Location").contains(LOGIN_PAGE));
        firstAdmin.get(USERS).expect(401).code(NOT_LOGGED_IN);
        reportPartial("waiting three minutes, using Back, rendering the notice and restoring Compose");
    }

    // What it does: Signs out and reopens the protected page. Input: A permanent Admin. Output: A login redirect.
    @Test @DisplayName("S1-T12")
    void checkS1T12() {
        firstAdmin = admin();
        assertEquals("You are logged out.", firstAdmin.post(LOGOUT, null).expect(200).json().path("message").asText());
        assertTrue(firstAdmin.get(ADMIN_DASHBOARD).expect(302).header("Location").contains(LOGIN_PAGE));
        reportPartial("browser Back and reload");
    }

    // What it does: Tries to sign out without a security token. Input: An Admin. Output: Refusal and active session.
    @Test @DisplayName("S1-T13")
    void checkS1T13() {
        firstAdmin = admin();
        firstAdmin.send("POST", LOGOUT, null, false).expect(403).code("SHARED_SECURITY_TOKEN_INVALID");
        assertTrue(firstAdmin.get(SESSION).expect(200).json().path("sharedViewSessionIsLoggedIn").asBoolean());
    }

    // What it does: Changes the worker's test status and checks session revocation.
    // Input: The active worker and Admin. Output: A revoked cookie, refused login and restored access.
    @Test @DisplayName("S1-T14")
    void checkS1T14() {
        Session sharedCheckWorker = browser();
        sharedCheckWorker.login(workerUsername, workerPassword).expect(200);
        setWorkerStatus("BANNED");
        sharedCheckWorker.get("/api/shared/session").expect(401).code("SHARED_SESSION_ENDED");
        assertTrue(sharedCheckWorker.login(workerUsername, workerPassword).expect(403)
                .code("SHARED_ACCOUNT_NOT_ACTIVE").json().path("message").asText().startsWith("This account cannot be used."));
        setWorkerStatus("ACTIVE");
        sharedCheckWorker.login(workerUsername, workerPassword).expect(200);
        reportPartial("browser reload and notice rendering");
    }

    // What it does: Changes the password in one of two signed-in clients.
    // Input: Two sessions on the same worker. Output: The other session is revoked.
    @Test @DisplayName("S1-T15")
    void checkS1T15() {
        Session sharedCheckWorkerFirst = browser();
        Session sharedCheckWorkerSecond = browser();
        sharedCheckWorkerFirst.login(workerUsername, workerPassword).expect(200);
        sharedCheckWorkerSecond.login(workerUsername, workerPassword).expect(200);
        String sharedCheckWorkerNewPassword = "FlowWarehouse43Own";
        sharedCheckWorkerFirst.change(workerPassword, sharedCheckWorkerNewPassword, sharedCheckWorkerNewPassword)
                .expect(200);
        workerPassword = sharedCheckWorkerNewPassword;
        sharedCheckWorkerSecond.get(SESSION).expect(401).code("SHARED_SESSION_ENDED");
        reportPartial("browser navigation after the revoked session");
    }

    // What it does: Inspects security events without exposing sensitive inputs.
    // Input: Events written by S1-T01–T15. Output: Required event types, client metadata and privacy assertions.
    @Test @DisplayName("S1-T16")
    void checkS1T16() {
        List<Map<String, Object>> sharedCheckRows = database.sql("""
                SELECT event_type, ip_address, left(device_info, 25) AS device, details
                FROM security_log ORDER BY security_log_id DESC LIMIT 40
                """).query().listOfRows();
        for (String sharedCheckEvent : List.of("LOGIN_SUCCESS", "LOGIN_FAILURE", "ACCOUNT_LOCKED", "LOGOUT",
                "PASSWORD_CHANGED", "TEMPORARY_PASSWORD_ISSUED", "SESSION_REVOKED", "ACCESS_DENIED")) {
            assertTrue(sharedCheckRows.stream().anyMatch(sharedCheckRow -> sharedCheckEvent.equals(
                    sharedCheckRow.get("event_type"))), sharedCheckEvent);
        }
        assertTrue(sharedCheckRows.stream().anyMatch(sharedCheckRow ->
                "LMS flow check".equals(sharedCheckRow.get("device"))
                        && sharedCheckRow.get("ip_address") != null));
        for (String sharedCheckSensitive : List.of(
                bootstrapPassword, FLOW_ADMIN_PASSWORD, workerPassword, UNKNOWN_USERNAME, BAD_PASSWORD)) {
            assertFalse(sharedCheckRows.toString().contains(sharedCheckSensitive), "Security log leaked a sign-in input.");
        }
    }

    // What it does: Checks that every stored credential has BCrypt's shape.
    // Input: The password table. Output: Checks of prefix and length only.
    @Test @DisplayName("S1-T17")
    void checkS1T17() {
        assertTrue(count("SELECT count(*) FROM app_user") > 0);
        assertEquals(0L, count("""
                SELECT count(*) FROM app_user
                WHERE left(password_hash, 4) NOT IN ('$2a$', '$2b$', '$2y$') OR length(password_hash) <> 60
                """));
    }

    // What it does: Runs the same bootstrap logic used at startup and checks permanent access.
    // Input: The active bootstrap Admin. Output: Stable Admin count and a ready session.
    @Test @DisplayName("S1-T18")
    void checkS1T18() {
        long sharedCheckAdminCount = count("SELECT count(*) FROM app_user WHERE role = 'ADMIN'");
        bootstrapRunner.sharedCreateBootstrapAdmin();
        assertEquals(sharedCheckAdminCount, count("SELECT count(*) FROM app_user WHERE role = 'ADMIN'"));
        assertFalse(admin().get(SESSION).expect(200).json().path("mustChangePassword").asBoolean());
        reportPartial("actual container restart and wait");
    }

    // What it does: Rechecks health as an unauthenticated user. Input: None. Output: A connected health response.
    @Test @DisplayName("S1-T20")
    void checkS1T20() {
        assertTrue(browser().get("/api/shared/health").expect(200).json()
                .path("sharedCheckSystemHealthDatabaseIsConnected").asBoolean());
    }

    // What it does: Reads the worker's stored lock status. Input: The fixture name. Output: Count and time.
    private Map<String, Object> workerState() {
        return database.sql("SELECT failed_login_count, lock_until FROM app_user WHERE username = :username")
                .param("username", workerUsername).query().singleRow();
    }

    // What it does: Sets the fixture's status using the temporary tool. Input: A status. Output: HTTP success.
    private void setWorkerStatus(String accountStatus) {
        firstAdmin.tool("user-status", Map.of("adminTemporarySetUserStatusUsername", workerUsername,
                "adminTemporarySetUserStatusAccountStatus", accountStatus)).expect(200);
    }
}
