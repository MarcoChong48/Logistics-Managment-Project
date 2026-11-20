// TEMPORARY TEST CODE - remove before integration
// Stage 4 repeats the HTTP and document parts of the shared password acceptance cases.
package com.lms.backend;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import tools.jackson.databind.JsonNode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class SharedChangePasswordFlowCheckTest extends SharedFlowCheck {

    // ===== SECTION 1: FIXTURES =====
    private static final String CHANGE_API = "/api/shared/change-password";
    private static final String LOGOUT_API = "/api/shared/logout";
    private static final String CHANGE_TEST_PASSWORD = "Harbour7lamps!";
    private Session sharedChangePasswordWorkerSession;
    private SharedFlowTestAccount sharedChangePasswordWorkerAccount;

    // ===== SECTION 2: FLOW CASES =====

    // What it does: Confirms an issued temporary password opens the forced flow and checks the UTF-8 rule boundary.
    // Input: A Warehouse Worker account issued by the live test tool.
    // Output: Forced-change response, page message regions and password-rule results.
    @Test @DisplayName("S3-T18")
    void checkS3T18() {
        reportPartial("Compared HTML and server rules. Sample rendering, live rules, strength scale and colours require a browser.");
        sharedChangePasswordWorkerAccount = SharedFlowTestAccount.create(admin(), "WAREHOUSE_WORKER");
        sharedChangePasswordWorkerSession = browser();
        JsonNode sharedLoginUserResponse = sharedChangePasswordWorkerSession.login(
                sharedChangePasswordWorkerAccount.username(), sharedChangePasswordWorkerAccount.temporaryPassword())
                .expect(200).json();
        assertTrue(sharedLoginUserResponse.path("mustChangePassword").asBoolean());
        assertEquals(CHANGE_PASSWORD, sharedLoginUserResponse.path("sharedLoginUserNextPageWebAddress").asText());
        assertTrue(sharedChangePasswordWorkerSession.get("/warehouse/anything.html")
                .expect(302).header("Location").endsWith(CHANGE_PASSWORD));
        String sharedChangePasswordPage = sharedChangePasswordWorkerSession.get(CHANGE_PASSWORD).expect(200).body();
        assertTrue(sharedChangePasswordPage.contains("sharedChangePasswordNoticePanel"));
        assertTrue(sharedChangePasswordPage.contains("Avoid names and common words."));

        // Mismatched repeats exercise the byte limit without changing the temporary password fixture.
        String sharedChangePasswordCurrentPassword = sharedChangePasswordWorkerAccount.temporaryPassword();
        sharedChangePasswordWorkerSession.change(sharedChangePasswordCurrentPassword, "Aa1" + "界".repeat(23),
                CHANGE_TEST_PASSWORD).expect(400).code("SHARED_CONFIRM_PASSWORD_DIFFERENT");
        assertError(sharedChangePasswordWorkerSession.change(sharedChangePasswordCurrentPassword,
                "Aa1" + "界".repeat(24), CHANGE_TEST_PASSWORD), "SHARED_PASSWORD_RULE_FAILED",
                "Use fewer characters. The password can use at most 72 bytes.");
        assertError(sharedChangePasswordWorkerSession.change(sharedChangePasswordCurrentPassword,
                sharedChangePasswordCurrentPassword, sharedChangePasswordCurrentPassword), "SHARED_PASSWORD_RULE_FAILED",
                "The new password must be different from the current one.");
    }

    // What it does: Verifies the distinct server answers and signs out of the temporary-password page.
    // Input: The worker's forced-change session from the previous check.
    // Output: Three ordered validation errors and a logged-out session.
    @Test @DisplayName("S3-T19")
    void checkS3T19() {
        reportPartial("Browser empty-field handling, Show/Hide, focus, field highlighting and rendered logout notice.");
        String sharedChangePasswordCurrentPassword = sharedChangePasswordWorkerAccount.temporaryPassword();
        assertError(sharedChangePasswordWorkerSession.change("WrongTemporary2", CHANGE_TEST_PASSWORD,
                CHANGE_TEST_PASSWORD), "SHARED_CURRENT_PASSWORD_WRONG", "The current password is wrong.");
        assertError(sharedChangePasswordWorkerSession.change(sharedChangePasswordCurrentPassword,
                "abcdefgh1", "abcdefgh1"), "SHARED_PASSWORD_RULE_FAILED",
                "Use at least one capital letter from A to Z.");
        assertError(sharedChangePasswordWorkerSession.change(sharedChangePasswordCurrentPassword,
                CHANGE_TEST_PASSWORD, "Harbour7lamps"), "SHARED_CONFIRM_PASSWORD_DIFFERENT",
                "The two new passwords are not the same.");
        sharedChangePasswordWorkerSession.post(LOGOUT_API, null).expect(200);
        assertFalse(sharedChangePasswordWorkerSession.get(SESSION).expect(200).json()
                .path("sharedViewSessionIsLoggedIn").asBoolean());
        assertTrue(sharedChangePasswordWorkerSession.get("/shared/shared-login.js").expect(200).body()
                .contains("You are logged out."));
    }

    // What it does: Checks that idle expiry and a stale tab cannot change a permanent password.
    // Input: The bootstrap user's live session and a stale security token.
    // Output: Both refusals and proof the old password still works.
    @Test @DisplayName("S3-T20")
    void checkS3T20() {
        reportPartial("Browser sample comparison and Cancel, Compose timeout edit and wait, and DevTools Offline.");
        Session sharedChangePasswordAdmin = admin();
        String sharedChangePasswordPage = sharedChangePasswordAdmin.get(CHANGE_PASSWORD).expect(200).body();
        assertTrue(sharedChangePasswordPage.contains("sharedChangePasswordCancelLink"));
        JsonNode sharedChangePasswordExpiredToken = sharedChangePasswordAdmin.get(SESSION).expect(200).json();
        Map<String, String> sharedChangePasswordInput = Map.of(
                "sharedChangePasswordCurrentPassword", FLOW_ADMIN_PASSWORD,
                "sharedChangePasswordNewPassword", CHANGE_TEST_PASSWORD,
                "sharedChangePasswordConfirmPassword", CHANGE_TEST_PASSWORD);
        sharedChangePasswordAdmin.expire();
        Reply sharedChangePasswordExpiredReply = sharedChangePasswordAdmin.send("POST", CHANGE_API,
                sharedChangePasswordInput, sharedChangePasswordExpiredToken).expect(401);
        assertTrue(java.util.List.of("SHARED_SESSION_ENDED", "SHARED_NOT_LOGGED_IN")
                .contains(sharedChangePasswordExpiredReply.json().path("code").asText()));

        // A second tab changes the cookie-backed session, while the original form retains its first token.
        sharedChangePasswordAdmin.login(bootstrapUsername, FLOW_ADMIN_PASSWORD).expect(200);
        JsonNode sharedChangePasswordStaleToken = sharedChangePasswordAdmin.get(SESSION).expect(200).json();
        sharedChangePasswordAdmin.post(LOGOUT_API, null).expect(200);
        sharedChangePasswordAdmin.login(bootstrapUsername, FLOW_ADMIN_PASSWORD).expect(200);
        sharedChangePasswordAdmin.send("POST", CHANGE_API, sharedChangePasswordInput, sharedChangePasswordStaleToken)
                .expect(403).code("SHARED_SECURITY_TOKEN_INVALID");
        admin();
        String sharedChangePasswordScript = sharedChangePasswordAdmin.get("/shared/shared-change-password.js")
                .expect(200).body();
        assertTrue(sharedChangePasswordScript.contains(
                "This page is out of date. Reload it and try again. Your password was not changed."));
        assertTrue(sharedChangePasswordScript.contains("The server did not answer."));
        assertTrue(sharedChangePasswordScript.contains("It is not known whether your password was changed."));
    }

    // What it does: Verifies all three page-level live regions exist before requests and checks refused sign-in.
    // Input: The public shared pages and a signed-out session.
    // Output: Live-region markup, forced-mode notice and a shared validation response.
    @Test @DisplayName("S3-T21")
    void checkS3T21() {
        reportPartial("Narrator speech, browser notice rendering and the placement of messages after form use.");
        Session sharedLoginBrowser = browser();
        assertLiveRegion(sharedLoginBrowser.get(LOGIN_PAGE).expect(200).body(), "sharedLoginUserMessage");
        assertLiveRegion(sharedLoginBrowser.get(WELCOME_PAGE).expect(200).body(), "sharedShowWelcomeNoticeMessage");
        String sharedChangePasswordPage = sharedLoginBrowser.get(CHANGE_PASSWORD).expect(200).body();
        assertLiveRegion(sharedChangePasswordPage, "sharedChangePasswordMessage");
        assertTrue(sharedChangePasswordPage.contains("sharedChangePasswordNoticePanel"));
        Reply sharedLoginFailure = sharedLoginBrowser.login("flow_unknown_user", "WrongFlowPass42")
                .expect(401).code("SHARED_LOGIN_FAILED");
        assertEquals("Wrong username or password.", sharedLoginFailure.json().path("message").asText());
        sharedLoginBrowser.get(LOGIN_PAGE + "?sharedLoginUserNotice=PASSWORD_NOT_CHANGED").expect(200);
        assertTrue(sharedLoginBrowser.get("/shared/shared-login.js").expect(200).body().contains(
                "Your session ended, so your new password was not saved. Please log in again."));
    }

    // ===== SECTION 3: VERIFICATION HELPERS =====

    // What it does: Checks a failed password rule. Input: Reply and expected error. Output: Error assertions.
    private void assertError(Reply reply, String code, String message) {
        assertEquals(message, reply.expect(400).code(code).json().path("message").asText());
    }

    // What it does: Checks that a region is present and available to assistive technology before scripts run.
    // Input: Page markup and region ID. Output: Assertions about the matching opening div.
    private void assertLiveRegion(String page, String regionID) {
        var sharedShowInlineMessageRegion = Pattern.compile("<div\\b[^>]*\\bid=\"" + regionID + "\"[^>]*>")
                .matcher(page);
        assertTrue(sharedShowInlineMessageRegion.find(), regionID);
        String sharedShowInlineMessageRegionOpening = sharedShowInlineMessageRegion.group();
        assertTrue(sharedShowInlineMessageRegionOpening.contains("aria-live=\"polite\""), regionID);
        assertFalse(sharedShowInlineMessageRegionOpening.contains("hidden"), regionID);
    }
}

// TEMPORARY TEST CODE - remove before integration
// Stage 4 uses the one-time credentials returned by the live test tool for role and password checks.
record SharedFlowTestAccount(String username, String temporaryPassword) {
    private static final Pattern TOOL_CREDENTIALS = Pattern.compile("Created ([^.]+)\\. Temporary password: (\\S+)");

    // What it does: Requests a new synthetic account.
    // Input: The Admin session and role. Output: Credentials held locally by the flow.
    static SharedFlowTestAccount create(SharedFlowCheck.Session admin, String role) {
        String toolMessage = admin.tool("test-user", Map.of("adminTemporaryCreateTestUserRole", role))
                .expect(200).json().path("message").asText();
        var credentials = TOOL_CREDENTIALS.matcher(toolMessage);
        assertTrue(credentials.matches(), "The test-user tool did not return the issued sign-in details.");
        return new SharedFlowTestAccount(credentials.group(1), credentials.group(2));
    }
}
