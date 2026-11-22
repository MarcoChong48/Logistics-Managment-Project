// TEMPORARY TEST CODE - remove before integration
// Stage 4 runs Stage 0 through the HTTP server and its isolated PostgreSQL database.
package com.lms.backend;

import static org.junit.jupiter.api.Assertions.*;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.apache.catalina.Context;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.tomcat.TomcatWebServer;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@TestMethodOrder(MethodOrderer.MethodName.class)
class SharedSetupFlowCheckTest extends SharedFlowCheck {

    // What it does: Recreates the disposable database before Spring starts for the first flow class.
    // Input: The test property registry and the check service's PostgreSQL settings.
    // Output: A fresh Stage 0 schema or a setup failure.
    @DynamicPropertySource
    static void sharedPrepareFlowDatabase(DynamicPropertyRegistry sharedPrepareFlowDatabaseRegistry) throws SQLException {
        // Require the exact Compose endpoint before issuing any database-creation command.
        String sharedPrepareFlowDatabaseAddress = System.getenv("SPRING_DATASOURCE_URL");
        if (!"jdbc:postgresql://db:5432/lms_flow_check".equals(sharedPrepareFlowDatabaseAddress)) {
            throw new IllegalStateException("Start the flow checks with the documented Compose command.");
        }
        // Plan v1.19 splits Stage 0 around the first bootstrap login. Only the first group initializes the schema.
        if (!Boolean.getBoolean("lms.flow.initialize")) {
            return;
        }
        String sharedPrepareFlowDatabaseUser = System.getenv("SPRING_DATASOURCE_USERNAME");
        String sharedPrepareFlowDatabasePassword = System.getenv("SPRING_DATASOURCE_PASSWORD");
        try (var sharedPrepareFlowDatabaseConnection = DriverManager.getConnection(
                "jdbc:postgresql://db:5432/postgres", sharedPrepareFlowDatabaseUser, sharedPrepareFlowDatabasePassword);
                var sharedPrepareFlowDatabaseStatement = sharedPrepareFlowDatabaseConnection.createStatement()) {
            sharedPrepareFlowDatabaseStatement.execute("DROP DATABASE IF EXISTS lms_flow_check");
            sharedPrepareFlowDatabaseStatement.execute("CREATE DATABASE lms_flow_check");
        }
        // Apply the same initialization scripts as Compose in the same order.
        try (var sharedPrepareFlowDatabaseConnection = DriverManager.getConnection(
                sharedPrepareFlowDatabaseAddress, sharedPrepareFlowDatabaseUser, sharedPrepareFlowDatabasePassword)) {
            for (String sharedPrepareFlowDatabaseScript : new String[] {"01_create_tables.sql", "03_admin_changes.sql"}) {
                ScriptUtils.executeSqlScript(sharedPrepareFlowDatabaseConnection,
                        new FileSystemResource(Path.of("/flow-schema", sharedPrepareFlowDatabaseScript)));
            }
        }
        sharedPrepareFlowDatabaseRegistry.add("spring.datasource.url", () -> sharedPrepareFlowDatabaseAddress);
    }

    // What it does: Marks the Compose process check for manual verification.
    // Input: None. Output: The reason this case is skipped.
    @Test @DisplayName("S0-T01") @Disabled("Compose startup, published port and container status are external.")
    void checkS0T01() {}

    // What it does: Counts the installed tables and checks the two Admin additions.
    // Input: The newly initialized flow database. Output: Assertions about its public schema.
    @Test @DisplayName("S0-T02")
    void checkS0T02() {
        assertEquals(27L, count("SELECT count(*) FROM pg_tables WHERE schemaname = 'public'"));
        assertEquals(2L, count("""
                SELECT count(*) FROM pg_tables
                WHERE schemaname = 'public' AND tablename IN ('shortage_case','delivery_assignment_history')
                """));
    }

    // What it does: Checks the migration's columns, defaults, allowed statuses and username index.
    // Input: PostgreSQL's schema catalog. Output: Assertions about the installed definitions.
    @Test @DisplayName("S0-T03")
    void checkS0T03() {
        assertEquals(1L, count("""
                SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'app_user'
                AND column_name = 'session_version' AND data_type = 'integer' AND is_nullable = 'NO'
                AND column_default = '0'
                """));
        assertEquals(3L, count("""
                SELECT count(*) FROM information_schema.columns WHERE table_schema = 'public'
                AND table_name = 'client_instruction'
                AND (column_name IN ('recorded_by_user_id', 'resolution_note')
                  OR (column_name = 'source' AND column_default LIKE '%TRACKING_PAGE%'))
                """));
        assertEquals(1L, count("""
                SELECT count(*) FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'app_user'
                AND indexdef LIKE '%UNIQUE%lower(%username%'
                """));
        assertEquals(1L, count("""
                SELECT count(*) FROM pg_constraint WHERE conrelid = 'app_user'::regclass
                AND pg_get_constraintdef(oid) LIKE '%account_status%'
                AND pg_get_constraintdef(oid) LIKE '%ACTIVE%'
                AND pg_get_constraintdef(oid) LIKE '%INACTIVE%'
                AND pg_get_constraintdef(oid) LIKE '%LOCKED%'
                AND pg_get_constraintdef(oid) LIKE '%BANNED%'
                """));
    }

    // What it does: Checks the public health response and its limited fields.
    // Input: A signed-out session. Output: Assertions about health and response privacy.
    @Test @DisplayName("S0-T04")
    void checkS0T04() {
        JsonNode sharedCheckHealth = browser().get("/api/shared/health").expect(200).json();
        assertEquals(4, sharedCheckHealth.size());
        assertTrue(sharedCheckHealth.path("sharedCheckSystemHealthDatabaseIsConnected").asBoolean());
        assertEquals(27, sharedCheckHealth.path("sharedCheckSystemHealthTableCount").asInt());
        assertTrue(sharedCheckHealth.path("sharedCheckSystemHealthTestModeIsEnabled").asBoolean());
        assertDoesNotThrow(() -> Instant.parse(sharedCheckHealth.path("sharedCheckSystemHealthServerTime").asText()));
    }

    // What it does: Checks the dashboard address and health on the current signed-in build.
    // Input: The first Admin after setting a permanent password. Output: The dashboard and F0.1 responses.
    @Test @DisplayName("S0-T05")
    void checkS0T05() {
        Session sharedCheckDashboard = admin();
        assertEquals(ADMIN_DASHBOARD, sharedCheckDashboard.get(SESSION).expect(200).json()
                .path("sharedViewSessionNextPageWebAddress").asText());
        sharedCheckDashboard.get(ADMIN_DASHBOARD).expect(200);
        checkS0T04();
        reportPartial("dashboard side menu and frame (S9-H02)");
    }

    // What it does: Records the visual and offline check for the final hand tests.
    // Input: None. Output: The reason the flow intentionally omits this case.
    @Test @DisplayName("S0-T06")
    @Disabled("Plan v1.19 defers the visual comparison and offline browser reload to S9-H02.")
    void checkS0T06() {}

    // What it does: Checks that the current Admin can reach F0.2 and the tools page.
    // Input: The first Admin after setting a permanent password. Output: A true mode flag and a successful page reply.
    @Test @DisplayName("S0-T07")
    void checkS0T07() {
        Session sharedCheckTools = admin();
        assertTrue(sharedCheckTools.get("/api/admin/temporary-test/mode").expect(200).json()
                .path("adminTemporaryViewTestModeIsEnabled").asBoolean());
        sharedCheckTools.get("/admin/admin-temporary-test-tools.html").expect(200);
        reportPartial("visual confirmation of the tool cards (S9-H03)");
    }

    // What it does: Defers stopping the shared PostgreSQL process.
    // Input: None. Output: Why the outage step is skipped from the isolated run.
    @Test @DisplayName("S0-T08") @Disabled("Stopping the PostgreSQL container would also interrupt the user database.")
    void checkS0T08() {}

    // What it does: Defers the Compose change and restart.
    // Input: None. Output: Why Compose persistence requires a separate environment check.
    @Test @DisplayName("S0-T09") @Disabled("The persistence check stops and starts Compose services.")
    void checkS0T09() {}

    // What it does: Checks the mode route under both profiles using two HTTP server instances.
    // Input: A temporary login on each instance. Output: Assertions about mode availability and session flag.
    @Test @DisplayName("S0-T10")
    void checkS0T10() {
        // Boot the alternate profile without changing any running Compose service.
        try (var sharedCheckWithoutProfile = new org.springframework.boot.builder.SpringApplicationBuilder(
                BackendApplication.class).run("--server.port=0", "--spring.profiles.active=",
                        "--spring.main.banner-mode=off")) {
            int sharedCheckPort = ((ServletWebServerApplicationContext) sharedCheckWithoutProfile)
                    .getWebServer().getPort();
            Session sharedCheckTestModeOff = browser(sharedCheckPort);
            assertFalse(sharedCheckTestModeOff.get("/api/shared/session").expect(200).json()
                    .path("sharedViewSessionTestModeIsEnabled").asBoolean());
            sharedCheckTestModeOff.login(bootstrapUsername, FLOW_ADMIN_PASSWORD).expect(200);
            sharedCheckTestModeOff.get("/api/admin/temporary-test/mode").expect(404).code("SHARED_NOT_FOUND");
            assertTrue(admin().get("/api/admin/temporary-test/mode").expect(200).json()
                    .path("adminTemporaryViewTestModeIsEnabled").asBoolean());
        }
        reportPartial("editing Compose and checking the hidden frame elements in the browser");
    }

    // What it does: Checks the error body for a nonexistent Admin endpoint.
    // Input: An authenticated bootstrap session. Output: A short 404 JSON error.
    @Test @DisplayName("S0-T11")
    void checkS0T11() {
        JsonNode sharedCheckMissingRoute = admin().get("/api/admin/nothing").expect(404)
                .code("SHARED_NOT_FOUND").json();
        assertEquals(2, sharedCheckMissingRoute.size());
        assertEquals("This web address was not found.", sharedCheckMissingRoute.path("message").asText());
    }
}

// TEMPORARY TEST CODE - remove before integration
// Stage 4 shares a real HTTP client, database assertions and result printing across the flow classes.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("admintest")
@ExtendWith(SharedFlowCheck.FlowResult.class)
abstract class SharedFlowCheck {

    static final String USERS = "/api/admin/users";
    static final String ADMIN_DASHBOARD = "/admin/admin-dashboard.html";
    static final String CHANGE_PASSWORD = "/shared/shared-change-password.html";
    static final String LOGIN_PAGE = "/shared/shared-login.html";
    static final String WELCOME_PAGE = "/shared/shared-welcome.html";
    static final String WAREHOUSE_HOME = "/warehouse/warehouse-available-orders.html";
    static final String SESSION = "/api/shared/session";
    static final String FLOW_ADMIN_PASSWORD = "FlowAdmin42Own";
    static final String FLOW_PICKER_PASSWORD = "FlowPicker42Own";
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ThreadLocal<String> FLOW_PARTIAL = new ThreadLocal<>();

    @LocalServerPort int port;
    @Autowired JdbcClient database;
    @Autowired ServletWebServerApplicationContext server;
    @Value("${lms.shared.bootstrap-admin-username}") String bootstrapUsername;
    @Value("${lms.shared.bootstrap-admin-temporary-password}") String bootstrapPassword;

    // What it does: Prevents a flow class from using a developer or user database.
    // Input: Inherited test configuration. Output: A validated PostgreSQL address for Spring.
    @DynamicPropertySource
    static void sharedRequireFlowDatabase(DynamicPropertyRegistry sharedRequireFlowDatabaseRegistry) {
        if (!"jdbc:postgresql://db:5432/lms_flow_check".equals(System.getenv("SPRING_DATASOURCE_URL"))) {
            throw new IllegalStateException("Start the flow checks with the documented Compose command.");
        }
    }

    // What it does: Starts a guest browser session on the test server.
    // Input: Optionally, the port of an additional test server. Output: An independent cookie jar.
    Session browser() { return browser(port); }

    Session browser(int sharedOpenBrowserPort) { return new Session(sharedOpenBrowserPort); }

    // What it does: Signs in with the permanent bootstrap password established by Stage 1.
    // Input: The current flow database. Output: An authenticated HTTP client.
    Session admin() {
        Session sharedOpenAdminSession = browser();
        sharedOpenAdminSession.login(bootstrapUsername, FLOW_ADMIN_PASSWORD).expect(200);
        assertEquals(ADMIN_DASHBOARD, sharedOpenAdminSession.get(SESSION).expect(200).json()
                .path("sharedViewSessionNextPageWebAddress").asText());
        return sharedOpenAdminSession;
    }

    // What it does: Finds the user's primary key. Input: Username. Output: Numeric ID.
    long userID(String username) {
        return database.sql("SELECT user_id FROM app_user WHERE LOWER(username) = LOWER(:username)")
                .param("username", username).query(Long.class).single();
    }

    // What it does: Creates a fixture through the test-tool route.
    // Input: An Admin session and the chosen role. Output: The issued username and temporary password.
    String[] testUser(Session sharedCreateFlowUserSession, String sharedCreateFlowUserRole) {
        String sharedCreateFlowUserMessage = sharedCreateFlowUserSession.tool(
                "test-user", Map.of("adminTemporaryCreateTestUserRole", sharedCreateFlowUserRole))
                .expect(200).json().path("message").asText();
        var sharedCreateFlowUserMatch = java.util.regex.Pattern.compile(
                "^Created (test_\\w+)\\. Temporary password: (\\S+)$").matcher(sharedCreateFlowUserMessage);
        assertTrue(sharedCreateFlowUserMatch.matches(), "The tool must supply a username and the issued password.");
        return new String[] {sharedCreateFlowUserMatch.group(1), sharedCreateFlowUserMatch.group(2)};
    }

    // What it does: Runs a numeric verification query. Input: SQL defined by the flow case. Output: One count.
    long count(String sharedCountFlowRowsQuery) {
        return database.sql(sharedCountFlowRowsQuery).query(Long.class).single();
    }

    // What it does: Records any browser-only portion left out of this case.
    // Input: A concise description of the omitted steps. Output: A note on the case's result line.
    void reportPartial(String sharedReportFlowOmission) {
        FLOW_PARTIAL.set(sharedReportFlowOmission);
    }

    // What it does: Reads a bundled page without bypassing the HTTP endpoint for behavior assertions.
    // Input: Resource path. Output: UTF-8 markup or script.
    static String readResource(String sharedReadFlowResourcePath) {
        try (var sharedReadFlowResourceStream = SharedFlowCheck.class.getResourceAsStream(sharedReadFlowResourcePath)) {
            assertNotNull(sharedReadFlowResourceStream, sharedReadFlowResourcePath);
            return new String(sharedReadFlowResourceStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (java.io.IOException sharedReadFlowResourceError) {
            throw new AssertionError(sharedReadFlowResourcePath, sharedReadFlowResourceError);
        }
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 drives the web app with a real cookie and the live security token.
    final class Session {
        private final URI webAddress;
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder()
                .cookieHandler(cookies)
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        Session(int serverPort) { webAddress = URI.create("http://127.0.0.1:" + serverPort); }

        // What it does: Reads or writes a route with ordinary session behavior.
        // Input: Relative web address and optional JSON request. Output: The response from the live server.
        Reply get(String path) { return send("GET", path, null, false); }
        Reply post(String path, Object body) { return send("POST", path, body, true); }
        Reply put(String path, Object body) { return send("PUT", path, body, true); }
        Reply tool(String path, Object body) { return post("/api/admin/temporary-test/" + path, body); }

        // What it does: Expires this cookie's session in the embedded server before the next HTTP request.
        // Input: The signed-in test session. Output: The server has ended it as it would after inactivity.
        void expire() {
            String sharedExpireFlowSessionID = cookies.getCookieStore().getCookies().stream()
                    .filter(sharedExpireFlowSessionCookie -> "JSESSIONID".equals(sharedExpireFlowSessionCookie.getName()))
                    .findFirst().orElseThrow().getValue();
            var sharedExpireFlowSessionServer = (TomcatWebServer) server.getWebServer();
            var sharedExpireFlowSessionContext = (Context) sharedExpireFlowSessionServer.getTomcat()
                    .getHost().findChild("");
            try {
                var sharedExpireFlowSession = sharedExpireFlowSessionContext.getManager()
                        .findSession(sharedExpireFlowSessionID);
                assertNotNull(sharedExpireFlowSession, "Expected an active session before simulating inactivity.");
                sharedExpireFlowSession.expire();
            } catch (java.io.IOException sharedExpireFlowSessionError) {
                throw new AssertionError("Could not expire the test session.", sharedExpireFlowSessionError);
            }
        }

        // What it does: Sends sign-in details. Input: Username and password. Output: Login response.
        Reply login(String username, String password) {
            return post("/api/shared/login", Map.of("sharedLoginUserUsername", username,
                    "sharedLoginUserPassword", password));
        }

        // What it does: Sends a password change. Input: Current, new and repeated passwords. Output: The rule result.
        Reply change(String currentPassword, String newPassword, String repeatPassword) {
            return post("/api/shared/change-password", Map.of("sharedChangePasswordCurrentPassword", currentPassword,
                    "sharedChangePasswordNewPassword", newPassword,
                    "sharedChangePasswordConfirmPassword", repeatPassword));
        }

        // What it does: Sends a JSON request, optionally retaining a specifically supplied token.
        // Input: Method, relative path, body and whether to get the current token. Output: HTTP reply.
        Reply send(String method, String path, Object body, boolean withToken) {
            return send(method, path, body, withToken ? get(SESSION).expect(200).json() : null);
        }

        Reply send(String method, String path, Object body, JsonNode token) {
            String sharedSendFlowRequestContent = body == null ? null : JSON.writeValueAsString(body);
            return exchange(method, path, sharedSendFlowRequestContent, "application/json", token);
        }

        // What it does: Uploads the provided text as a multipart CSV file.
        // Input: Route, field name and UTF-8 contents. Output: Import response.
        Reply upload(String path, String fieldName, String fileContent) {
            String sharedUploadBoundary = UUID.randomUUID().toString();
            String sharedUploadBody = "--" + sharedUploadBoundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"products.csv\"\r\n"
                    + "Content-Type: text/csv; charset=UTF-8\r\n\r\n" + fileContent + "\r\n"
                    + "--" + sharedUploadBoundary + "--\r\n";
            return exchange("POST", path, sharedUploadBody, "multipart/form-data; boundary=" + sharedUploadBoundary,
                    get(SESSION).expect(200).json());
        }

        // What it does: Sends a request without following redirects or logging request bodies.
        // Input: Method, address, optional body, media type and optional security token. Output: HTTP reply.
        private Reply exchange(String method, String path, String body, String contentType, JsonNode token) {
            HttpRequest.Builder sharedFlowRequest = HttpRequest.newBuilder(webAddress.resolve(path))
                    .timeout(Duration.ofSeconds(55))
                    .header("User-Agent", "LMS flow check")
                    .method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                            : HttpRequest.BodyPublishers.ofString(body));
            if (body != null) {
                sharedFlowRequest.header("Content-Type", contentType);
            }
            if (token != null) {
                sharedFlowRequest.header(token.path("sharedViewSessionSecurityTokenHeaderName").asText(),
                        token.path("sharedViewSessionSecurityToken").asText());
            }
            try {
                return new Reply(path, client.send(sharedFlowRequest.build(), HttpResponse.BodyHandlers.ofByteArray()));
            } catch (InterruptedException sharedFlowRequestInterrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(method + " " + path + " was interrupted.", sharedFlowRequestInterrupted);
            } catch (java.io.IOException sharedFlowRequestError) {
                throw new AssertionError(method + " " + path + " did not answer.", sharedFlowRequestError);
            }
        }
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 checks structured errors and downloaded files without echoing credentials.
    record Reply(String path, HttpResponse<byte[]> response) {
        // What it does: Asserts response status or an error code without dumping the body.
        // Input: Expected status or code. Output: The reply for the next assertion.
        Reply expect(int status) {
            assertEquals(status, response.statusCode(), path);
            return this;
        }

        Reply code(String expected) {
            assertEquals(expected, json().path("code").asText(), path);
            return this;
        }

        // What it does: Returns the live response in the representation the case needs.
        // Input: Optional header name. Output: JSON, UTF-8 text, bytes or a header.
        JsonNode json() { return JSON.readTree(bytes()); }
        String body() { return new String(bytes(), StandardCharsets.UTF_8); }
        byte[] bytes() { return response.body(); }
        String header(String name) { return response.headers().firstValue(name).orElse(""); }
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 prints the case ID and outcome while Maven produces independent countable reports.
    static final class FlowResult implements TestWatcher {
        // What it does: Reports JUnit's result. Input: Test context and any skip or failure. Output: One console line.
        @Override public void testSuccessful(ExtensionContext context) {
            String sharedCheckFlowOmission = FLOW_PARTIAL.get();
            FLOW_PARTIAL.remove();
            System.out.println(context.getDisplayName() + " Pass"
                    + (sharedCheckFlowOmission == null ? "" : " (omitted: " + sharedCheckFlowOmission + ")"));
        }
        @Override public void testFailed(ExtensionContext context, Throwable failure) {
            FLOW_PARTIAL.remove();
            System.out.println(context.getDisplayName() + " Fail " + failure.getClass().getSimpleName()
                    + " " + String.valueOf(failure.getMessage()).replaceAll("\\s+", " "));
        }
        @Override public void testDisabled(ExtensionContext context, java.util.Optional<String> reason) {
            System.out.println(context.getDisplayName() + " skipped " + reason.orElse("Disabled"));
        }
    }
}
