// TEMPORARY TEST CODE - remove before integration
// Stage 4 reruns the HTTP role and shared team-document acceptance cases.
package com.lms.backend;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import tools.jackson.databind.JsonNode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
class SharedRoleProtectionFlowCheckTest extends SharedFlowCheck {

    // ===== SECTION 1: ADDRESSES AND FIXTURES =====
    private static final String ACCESS_DENIED_PAGE = WELCOME_PAGE + "?sharedShowWelcomeNotice=ACCESS_DENIED";
    private static final String CLIENT_PATH = "/client/anything.html";
    private static final String WAREHOUSE_PATH = "/warehouse/anything.html";
    private static final String DELIVERY_PATH = "/delivery/anything.html";
    private static final String DELIVERY_HOME = "/delivery/index.html";
    private static final String CLIENT_API = "/api/client/anything";
    private static final String WAREHOUSE_API = "/api/warehouse/anything";
    private static final String DELIVERY_API = "/api/delivery/anything";
    private static final String FLOW_ROLE_PASSWORD = "FlowRole42Own";
    private Session sharedCheckRoleBrowser;
    private SharedFlowTestAccount sharedCheckRoleWarehouseWorker;
    private SharedFlowTestAccount sharedCheckRoleDeliveryWorker;

    // ===== SECTION 2: FLOW CASES =====

    // What it does: Confirms all three role page prefixes and API prefixes require sign-in.
    // Input: A guest session. Output: Login redirects and shared unauthorized codes.
    @Test @DisplayName("S3-T22")
    void checkS3T22() {
        Session sharedCheckRoleGuest = browser();
        for (String sharedCheckRolePage : List.of(CLIENT_PATH, WAREHOUSE_PATH, DELIVERY_PATH)) {
            assertRedirect(sharedCheckRoleGuest.get(sharedCheckRolePage), LOGIN_PAGE);
        }
        for (String sharedCheckRoleWebAddress : List.of(CLIENT_API, WAREHOUSE_API, DELIVERY_API, "/api/client")) {
            sharedCheckRoleGuest.get(sharedCheckRoleWebAddress).expect(401).code("SHARED_NOT_LOGGED_IN");
        }
    }

    // What it does: Makes three tool accounts and checks the Client's role boundary.
    // Input: A synthetic Client and the example role paths.
    // Output: A wrong-role page redirect, wrong-role API denial and matching-role 404.
    @Test @DisplayName("S3-T23")
    void checkS3T23() {
        reportPartial("Rendered welcome notice and visibility of its Back link.");
        Session sharedCheckRoleAdmin = admin();
        SharedFlowTestAccount sharedCheckRoleClient = SharedFlowTestAccount.create(sharedCheckRoleAdmin, "CLIENT");
        sharedCheckRoleWarehouseWorker = SharedFlowTestAccount.create(sharedCheckRoleAdmin, "WAREHOUSE_WORKER");
        sharedCheckRoleDeliveryWorker = SharedFlowTestAccount.create(sharedCheckRoleAdmin, "DELIVERY_WORKER");
        sharedCheckRoleBrowser = browser();
        finishFirstLogin(sharedCheckRoleClient, "CLIENT");
        checkWelcomeRedirect(sharedCheckRoleBrowser, WAREHOUSE_PATH, WELCOME_PAGE);
        sharedCheckRoleBrowser.get(DELIVERY_API).expect(403).code("SHARED_ACCESS_DENIED");
        sharedCheckRoleBrowser.get(CLIENT_API).expect(404).code("SHARED_NOT_FOUND");
    }

    // What it does: Checks the working guide and notes included with this stage.
    // Input: Three read-only documentation files mounted by the check service.
    // Output: The guide link, all twelve sections and the approved note version.
    @Test @DisplayName("S3-T24")
    void checkS3T24() throws IOException {
        reportPartial("The historical Stage 3 .github-publish snapshot and private-path scan are separate handoff checks.");
        Path sharedCheckTeamGuideFolder = Path.of("/flow-docs");
        String sharedCheckTeamGuideReadme = Files.readString(sharedCheckTeamGuideFolder.resolve("README.md"));
        assertTrue(sharedCheckTeamGuideReadme.lines().limit(12).anyMatch(line -> line.contains("(TEAM_GUIDE.md)")));
        String sharedCheckTeamGuideText = Files.readString(sharedCheckTeamGuideFolder.resolve("TEAM_GUIDE.md"));
        List<String> sharedCheckTeamGuideHeadingList = sharedCheckTeamGuideText.lines()
                .filter(line -> line.matches("^## [0-9]+\\..+")).toList();
        assertEquals(12, sharedCheckTeamGuideHeadingList.size());
        for (int headingIndex = 0; headingIndex < sharedCheckTeamGuideHeadingList.size(); headingIndex++) {
            assertTrue(sharedCheckTeamGuideHeadingList.get(headingIndex).startsWith("## " + (headingIndex + 1) + ". "));
        }
        assertTrue(Files.readString(sharedCheckTeamGuideFolder.resolve("DESIGN_NOTES_GENERAL.md"))
                .contains("Version 2.7, 22 November 2026."));
    }

    // What it does: Switches to the Warehouse Worker before checking that role's page and both API path forms.
    // Input: The issued Warehouse Worker account. Output: Wrong-role refusals and matching-role 404s.
    @Test @DisplayName("S3-T25")
    void checkS3T25() {
        reportPartial("Rendered welcome notice and visibility of its Back link.");
        sharedCheckRoleBrowser.post("/api/shared/logout", null).expect(200);
        finishFirstLogin(sharedCheckRoleWarehouseWorker, "WAREHOUSE_WORKER");
        checkWelcomeRedirect(sharedCheckRoleBrowser, DELIVERY_PATH, WELCOME_PAGE);
        sharedCheckRoleBrowser.get(CLIENT_API).expect(403).code("SHARED_ACCESS_DENIED");
        sharedCheckRoleBrowser.get(WAREHOUSE_API).expect(404).code("SHARED_NOT_FOUND");
        sharedCheckRoleBrowser.get("/api/warehouse").expect(404).code("SHARED_NOT_FOUND");
    }

    // What it does: Switches to Delivery Worker before checking Client and Warehouse role denials.
    // Input: The issued Delivery Worker account. Output: Wrong-role refusals and a matching-role 404.
    @Test @DisplayName("S3-T26")
    void checkS3T26() {
        reportPartial("Rendered welcome notice and visibility of its Back link.");
        sharedCheckRoleBrowser.post("/api/shared/logout", null).expect(200);
        finishFirstLogin(sharedCheckRoleDeliveryWorker, "DELIVERY_WORKER");
        sharedCheckRoleBrowser.get(DELIVERY_HOME).expect(200);
        checkWelcomeRedirect(sharedCheckRoleBrowser, CLIENT_PATH, DELIVERY_HOME);
        sharedCheckRoleBrowser.get(WAREHOUSE_API).expect(403).code("SHARED_ACCESS_DENIED");
        sharedCheckRoleBrowser.get(DELIVERY_API).expect(404).code("SHARED_NOT_FOUND");
    }

    // What it does: Checks the Admin cannot enter another role's routes and tests forced-change precedence.
    // Input: The bootstrap account, a new Client and security logs from earlier role cases.
    // Output: Denied page and API, password-change redirect and eight path-only log rows.
    @Test @DisplayName("S3-T27")
    void checkS3T27() {
        reportPartial("Rendered welcome message and clicking the Back to your start page control.");
        Session sharedCheckRoleAdmin = admin();
        checkWelcomeRedirect(sharedCheckRoleAdmin, CLIENT_PATH, ADMIN_DASHBOARD);
        sharedCheckRoleAdmin.get(ADMIN_DASHBOARD).expect(200);
        sharedCheckRoleAdmin.get("/api/delivery").expect(403).code("SHARED_ACCESS_DENIED");
        SharedFlowTestAccount sharedCheckRoleForcedClient = SharedFlowTestAccount.create(sharedCheckRoleAdmin, "CLIENT");
        sharedCheckRoleBrowser.post("/api/shared/logout", null).expect(200);
        sharedCheckRoleBrowser.login(sharedCheckRoleForcedClient.username(), sharedCheckRoleForcedClient.temporaryPassword())
                .expect(200);
        assertRedirect(sharedCheckRoleBrowser.get(CLIENT_PATH), CHANGE_PASSWORD);
        assertEquals(List.of("/api/delivery", CLIENT_PATH, WAREHOUSE_API, CLIENT_PATH, CLIENT_API, DELIVERY_PATH,
                DELIVERY_API, WAREHOUSE_PATH), database.sql("""
                SELECT details FROM security_log WHERE event_type = 'ACCESS_DENIED'
                ORDER BY security_log_id DESC LIMIT 8
                """).query(String.class).list());
    }

    // ===== SECTION 3: VERIFICATION HELPERS =====

    // What it does: Completes a new account's temporary-password flow and signs in again.
    // Input: The issued account and expected role. Output: The browser holds the permanent session.
    private void finishFirstLogin(SharedFlowTestAccount account, String role) {
        JsonNode sharedLoginUserTemporary = sharedCheckRoleBrowser.login(account.username(), account.temporaryPassword())
                .expect(200).json();
        assertTrue(sharedLoginUserTemporary.path("mustChangePassword").asBoolean());
        assertEquals(CHANGE_PASSWORD, sharedLoginUserTemporary.path("sharedLoginUserNextPageWebAddress").asText());
        sharedCheckRoleBrowser.change(account.temporaryPassword(), FLOW_ROLE_PASSWORD, FLOW_ROLE_PASSWORD).expect(200);
        JsonNode sharedLoginUserPermanent = sharedCheckRoleBrowser.login(account.username(), FLOW_ROLE_PASSWORD)
                .expect(200).json();
        assertEquals(role, sharedLoginUserPermanent.path("role").asText());
        assertFalse(sharedLoginUserPermanent.path("mustChangePassword").asBoolean());
        String expectedHome = "DELIVERY_WORKER".equals(role) ? DELIVERY_HOME : WELCOME_PAGE;
        assertEquals(expectedHome, sharedLoginUserPermanent.path("sharedLoginUserNextPageWebAddress").asText());
    }

    // What it does: Checks the denied-page destination and the signed-in person's home destination.
    // Input: The session, wrong-role page and home page. Output: A welcome redirect and session facts.
    private void checkWelcomeRedirect(Session session, String page, String homePage) {
        assertRedirect(session.get(page), ACCESS_DENIED_PAGE);
        session.get(ACCESS_DENIED_PAGE).expect(200);
        assertEquals(homePage, session.get(SESSION).expect(200).json()
                .path("sharedViewSessionNextPageWebAddress").asText());
        assertTrue(session.get("/shared/shared-welcome.js").expect(200).body()
                .contains("You are not allowed to open that page."));
    }

    // What it does: Accepts either absolute or relative redirects on the test server.
    // Input: The redirect reply and expected local path. Output: A destination check including the query.
    private void assertRedirect(Reply reply, String expectedPath) {
        java.net.URI sharedRoleExpectedServer = java.net.URI.create("http://127.0.0.1:" + port);
        String sharedRoleRedirect = reply.expect(302).header("Location");
        assertEquals(sharedRoleExpectedServer.resolve(expectedPath), sharedRoleExpectedServer.resolve(sharedRoleRedirect));
    }
}
