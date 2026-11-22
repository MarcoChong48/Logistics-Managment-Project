package com.lms.backend.client.account;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.lms.backend.shared.log.SharedSecurityLogWriter;

import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"lms.client.orders.expiry.enabled=false", "lms.client.stripe.enabled=false",
        "lms.shared.bootstrap-admin-username=", "lms.shared.bootstrap-admin-temporary-password="})
@AutoConfigureMockMvc
@Transactional
class ClientAccountApiTest {

    private static final String ACCOUNT = "/api/client/account";
    private static final String DEFAULTS = ACCOUNT + "/delivery-defaults";
    private static final String ORDERS = "/api/client/orders";
    private static final String PASSWORD = "AccountFixture123!";
    private static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-11-01T10:00:00Z");
    private static final ClientDeliveryDefaults FIRST = new ClientDeliveryDefaults(
            "陳小明", "+852 2345 6789", "九龍彌敦道12號");
    private static final ClientDeliveryDefaults SECOND = new ClientDeliveryDefaults(
            "Wong Mei Ling", "2234-5678", "Flat 3, 17 Queen's Road Central, Hong Kong");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient database;
    @Autowired private PasswordEncoder passwords;
    @Autowired private ObjectMapper json;
    @MockitoBean private SharedSecurityLogWriter securityLogs;

    @Test
    void accountAndEmptyDefaultsArePrivateAndReadsNeverCreateOrChangeTheProfile() throws Exception {
        var owner = user("CLIENT");
        var session = signIn(owner);

        read(ACCOUNT, session).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.*", hasSize(4)))
                .andExpect(jsonPath("$.userId").value(Long.toString(owner.id())))
                .andExpect(jsonPath("$.username").value(owner.username()))
                .andExpect(jsonPath("$.createdAt").value("2026-11-01T10:00:00Z"))
                .andExpect(jsonPath("$.deliveryDefaults.*", hasSize(3)))
                .andExpect(jsonPath("$.deliveryDefaults.deliveryAddress").value(nullValue()));
        read(DEFAULTS, session).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.*", hasSize(3))).andExpect(jsonPath("$.recipientName").value(nullValue()))
                .andExpect(jsonPath("$.contactPhone").value(nullValue()))
                .andExpect(jsonPath("$.deliveryAddress").value(nullValue()));
        assertEquals(0, profileCount(owner));

        replace(session, FIRST).andExpect(status().isOk());
        read(DEFAULTS, session).andExpect(status().isOk()).andExpect(jsonPath("$.recipientName").value(FIRST.recipientName()));
        read(ACCOUNT, session).andExpect(status().isOk()).andExpect(jsonPath("$.deliveryDefaults.contactPhone")
                .value(FIRST.contactPhone()));
        assertEquals(FIRST, storedDefaults(owner));
    }

    @Test
    void putCreatesReplacesAndClearsFieldsExplicitly() throws Exception {
        var owner = user("CLIENT");
        var session = signIn(owner);

        replace(session, new ClientDeliveryDefaults("  陳小明 ", "  +852 2345 6789 ", " 九龍\r\n彌敦道12號 "))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.recipientName").value(FIRST.recipientName()))
                .andExpect(jsonPath("$.contactPhone").value(FIRST.contactPhone()))
                .andExpect(jsonPath("$.deliveryAddress").value("九龍\n彌敦道12號"));
        assertEquals(1, profileCount(owner));

        replace(session, SECOND).andExpect(status().isOk());
        assertEquals(SECOND, storedDefaults(owner));
        replace(session, new ClientDeliveryDefaults(null, SECOND.contactPhone(), SECOND.deliveryAddress()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recipientName").value(nullValue()));
        assertEquals(new ClientDeliveryDefaults(null, SECOND.contactPhone(), SECOND.deliveryAddress()), storedDefaults(owner));

        replace(session, ClientDeliveryDefaults.EMPTY).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryAddress").value(nullValue()));
        replace(session, ClientDeliveryDefaults.EMPTY).andExpect(status().isOk());
        read(ACCOUNT, session).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryDefaults.contactPhone").value(nullValue()));
        assertEquals(1, profileCount(owner));
        assertEquals(ClientDeliveryDefaults.EMPTY, storedDefaults(owner));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "{\"recipientName\":null}", """
            {"recipientName":"  ","contactPhone":null,"deliveryAddress":null}""", """
            {"recipientName":null,"contactPhone":23456789,"deliveryAddress":null}""", """
            {"recipientName":null,"contactPhone":"852-abc-6789","deliveryAddress":null}""", """
            {"recipientName":null,"contactPhone":"++852-2345-6789","deliveryAddress":null}""", """
            {"recipientName":null,"contactPhone":"123456","deliveryAddress":null}""", """
            {"recipientName":null,"contactPhone":"1234567890123456","deliveryAddress":null}""", """
            {"recipientName":"N\\u0000ame","contactPhone":null,"deliveryAddress":null}""", """
            {"recipientName":"First\\nLast","contactPhone":null,"deliveryAddress":null}""", """
            {"recipientName":null,"contactPhone":null,"deliveryAddress":"!@#"}""", """
            {"recipientName":null,"contactPhone":null,"deliveryAddress":{"building":"12"}}"""})
    void invalidDefaultsAreRejectedWithoutChangingData(String body) throws Exception {
        var owner = user("CLIENT");
        var session = signIn(owner);
        replace(session, FIRST).andExpect(status().isOk());
        replaceJson(session, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_DELIVERY_DEFAULTS_INVALID"));
        assertEquals(FIRST, storedDefaults(owner));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", """
            {"recipientName":null,"contactPhone":null,"deliveryAddress":null,"recipientName":"Replacement"}""", """
            {"recipientName":null,"contactPhone":null,"deliveryAddress":null} {}""", """
            {"recipientName":null,"contactPhone":null,"deliveryAddress":null"""})
    void malformedOrAmbiguousJsonCannotReplaceStoredDefaults(String body) throws Exception {
        var owner = user("CLIENT");
        var session = signIn(owner);
        replace(session, FIRST).andExpect(status().isOk());
        replaceJson(session, body).andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.code").value("SHARED_INPUT_INVALID"));
        assertEquals(FIRST, storedDefaults(owner));
    }

    @Test
    void lengthValidationMatchesSchemaAndCreateOrderLimitsIncludingUnicode() throws Exception {
        var owner = user("CLIENT");
        var session = signIn(owner);
        var exact = new ClientDeliveryDefaults("\uD842\uDFB7".repeat(120), null, "街".repeat(1000));
        replace(session, exact).andExpect(status().isOk());
        assertEquals(exact, storedDefaults(owner));
        for (var oversized : Set.of(new ClientDeliveryDefaults("x".repeat(121), null, null),
                new ClientDeliveryDefaults(null, "1".repeat(41), null),
                new ClientDeliveryDefaults(null, null, "街".repeat(1001)))) {
            replace(session, oversized).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CLIENT_DELIVERY_DEFAULTS_INVALID"));
        }
        assertEquals(exact, storedDefaults(owner));
    }

    @Test
    void ownershipComesFromSessionAndOtherRolesAreDenied() throws Exception {
        var first = user("CLIENT");
        var second = user("CLIENT");
        var firstSession = signIn(first);
        var secondSession = signIn(second);
        replace(firstSession, FIRST).andExpect(status().isOk());
        replace(secondSession, SECOND).andExpect(status().isOk());

        mvc.perform(get(ACCOUNT).servletPath(ACCOUNT).session(firstSession.session()).param("userId", "" + second.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(Long.toString(first.id())))
                .andExpect(jsonPath("$.deliveryDefaults.recipientName").value(FIRST.recipientName()));
        mvc.perform(get(DEFAULTS).servletPath(DEFAULTS).session(firstSession.session()).param("userId", "" + second.id()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.recipientName").value(FIRST.recipientName()));
        replaceJson(firstSession, """
                {"userId":"%s","recipientName":null,"contactPhone":null,"deliveryAddress":null}
                """.formatted(second.id())).andExpect(status().isBadRequest());
        mvc.perform(put(DEFAULTS).servletPath(DEFAULTS).session(firstSession.session())
                .header(firstSession.csrfHeader(), firstSession.csrfToken()).param("userId", "" + second.id())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(ClientDeliveryDefaults.EMPTY)))
                .andExpect(status().isOk());
        assertEquals(ClientDeliveryDefaults.EMPTY, storedDefaults(first));
        assertEquals(SECOND, storedDefaults(second));

        for (String role : Set.of("ADMIN", "WAREHOUSE_WORKER", "DELIVERY_WORKER")) {
            var other = user(role);
            var otherSession = signIn(other);
            read(ACCOUNT, otherSession).andExpect(status().isForbidden());
            read(DEFAULTS, otherSession).andExpect(status().isForbidden());
            replace(otherSession, FIRST).andExpect(status().isForbidden());
            assertEquals(0, profileCount(other));
        }
    }

    @Test
    void absentExpiredAndRevokedSessionsAreDenied() throws Exception {
        for (String path : Set.of(ACCOUNT, DEFAULTS)) {
            mvc.perform(get(path).servletPath(path)).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("SHARED_NOT_LOGGED_IN"));
        }
        mvc.perform(put(DEFAULTS).servletPath(DEFAULTS).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(FIRST))).andExpect(status().isUnauthorized());

        var owner = user("CLIENT");
        var expired = signIn(owner);
        expired.session().invalidate();
        read(DEFAULTS, expired).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SHARED_NOT_LOGGED_IN"));
        var expiredRead = signIn(owner);
        var expiredWrite = signIn(owner);
        var passwordSession = signIn(owner);
        String changePassword = "/api/shared/change-password";
        String newPassword = "UpdatedAccountFixture456!";
        mvc.perform(post(changePassword).servletPath(changePassword).session(passwordSession.session())
                .header(passwordSession.csrfHeader(), passwordSession.csrfToken()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("sharedChangePasswordCurrentPassword", PASSWORD,
                        "sharedChangePasswordNewPassword", newPassword,
                        "sharedChangePasswordConfirmPassword", newPassword))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Password changed. Please log in again."));

        read(ACCOUNT, expiredRead).andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.code").value("SHARED_SESSION_ENDED"));
        replace(expiredWrite, FIRST).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SHARED_SESSION_ENDED"));
        assertEquals(0, profileCount(owner));
    }

    @Test
    void putRequiresTheSharedCsrfHeader() throws Exception {
        var owner = user("CLIENT");
        var session = signIn(owner);
        String body = json.writeValueAsString(FIRST);
        mvc.perform(put(DEFAULTS).servletPath(DEFAULTS).session(session.session())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        mvc.perform(put(DEFAULTS).servletPath(DEFAULTS).session(session.session()).header(session.csrfHeader(), "invalid")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        assertEquals(0, profileCount(owner));
        replace(session, FIRST).andExpect(status().isOk());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFirstWritesKeepOneProfileWithOneCompleteSetOfValues() throws Exception {
        var owner = user("CLIENT");
        try {
            var firstSession = signIn(owner);
            var secondSession = signIn(owner);
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            try (var callers = Executors.newFixedThreadPool(2)) {
                var first = callers.submit(() -> replaceWhenReady(firstSession, FIRST, ready, start));
                var second = callers.submit(() -> replaceWhenReady(secondSession, SECOND, ready, start));
                boolean bothReady = ready.await(5, TimeUnit.SECONDS);
                start.countDown();
                assertTrue(bothReady);
                assertEquals(FIRST, first.get(15, TimeUnit.SECONDS));
                assertEquals(SECOND, second.get(15, TimeUnit.SECONDS));
            }
            assertEquals(1, profileCount(owner));
            assertTrue(Set.of(FIRST, SECOND).contains(storedDefaults(owner)));
        } finally {
            database.sql("DELETE FROM client_profile WHERE user_id = :id").param("id", owner.id()).update();
            database.sql("DELETE FROM app_user WHERE user_id = :id").param("id", owner.id()).update();
        }
    }

    @Test
    void explicitOrderDetailsOverrideDefaultsAndProfileChangesNeverRewriteTheOrder() throws Exception {
        var session = signIn(user("CLIENT"));
        replace(session, FIRST).andExpect(status().isOk());
        long product = database.sql("""
                INSERT INTO product (sku, name, current_price) VALUES (:sku, 'Account test product', 18.00)
                RETURNING product_id
                """).param("sku", "account-" + UUID.randomUUID()).query(Long.class).single();
        database.sql("INSERT INTO inventory (product_id, physical_stock) VALUES (:id, 5)").param("id", product).update();
        var items = List.of(Map.of("productId", product, "quantity", 1));
        String key = UUID.randomUUID().toString();
        String orderBody = json.writeValueAsString(Map.of("items", items, "recipientName", SECOND.recipientName(),
                "contactPhone", SECOND.contactPhone(), "deliveryAddress", SECOND.deliveryAddress()));
        String firstResponse = createOrder(session, key, orderBody).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.parse(firstResponse).read("$.orderReference");

        assertOrderAddress(session, reference, SECOND);
        replace(session, new ClientDeliveryDefaults("New Name", "2222 1111", "9 New Street, Hong Kong"))
                .andExpect(status().isOk());
        assertOrderAddress(session, reference, SECOND);
        replace(session, ClientDeliveryDefaults.EMPTY).andExpect(status().isOk());
        assertOrderAddress(session, reference, SECOND);
        String replayResponse = createOrder(session, key, orderBody).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertEquals(json.readTree(firstResponse), json.readTree(replayResponse));
        assertOrderAddress(session, reference, SECOND);
        replace(session, FIRST).andExpect(status().isOk());
        createOrder(session, UUID.randomUUID().toString(), json.writeValueAsString(Map.of("items", items)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CLIENT_ORDER_INVALID"));
    }

    private void assertOrderAddress(Session session, String reference, ClientDeliveryDefaults expected) throws Exception {
        read(ORDERS + "/" + reference, session).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryDetails.version").value(1))
                .andExpect(jsonPath("$.deliveryDetails.recipientName").value(expected.recipientName()))
                .andExpect(jsonPath("$.deliveryDetails.phone").value(expected.contactPhone()))
                .andExpect(jsonPath("$.deliveryDetails.address").value(expected.deliveryAddress()));
    }

    private ResultActions createOrder(Session session, String key, String body) throws Exception {
        return mvc.perform(post(ORDERS).servletPath(ORDERS).session(session.session())
                .header(session.csrfHeader(), session.csrfToken()).header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ClientDeliveryDefaults replaceWhenReady(Session session, ClientDeliveryDefaults values, CountDownLatch ready,
            CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return json.readValue(replace(session, values).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsString(StandardCharsets.UTF_8), ClientDeliveryDefaults.class);
    }

    private ResultActions read(String path, Session session) throws Exception {
        return mvc.perform(get(path).servletPath(path).session(session.session()));
    }

    private ResultActions replace(Session session, ClientDeliveryDefaults values) throws Exception {
        return replaceJson(session, json.writeValueAsString(values));
    }

    private ResultActions replaceJson(Session session, String body) throws Exception {
        return mvc.perform(put(DEFAULTS).servletPath(DEFAULTS).session(session.session())
                .header(session.csrfHeader(), session.csrfToken()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Session signIn(FixtureUser user) throws Exception {
        Session guest = sessionInfo(null);
        String login = "/api/shared/login";
        var result = mvc.perform(post(login).servletPath(login).session(guest.session())
                .header(guest.csrfHeader(), guest.csrfToken()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("sharedLoginUserUsername", user.username(),
                        "sharedLoginUserPassword", PASSWORD)))).andExpect(status().isOk()).andReturn();
        return sessionInfo(assertInstanceOf(MockHttpSession.class, result.getRequest().getSession(false)));
    }

    private Session sessionInfo(MockHttpSession session) throws Exception {
        String path = "/api/shared/session";
        var request = get(path).servletPath(path);
        if (session != null) {
            request.session(session);
        }
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var payload = JsonPath.parse(result.getResponse().getContentAsString());
        return new Session(assertInstanceOf(MockHttpSession.class, result.getRequest().getSession(false)),
                payload.read("$.sharedViewSessionSecurityTokenHeaderName"), payload.read("$.sharedViewSessionSecurityToken"));
    }

    private FixtureUser user(String role) {
        String username = "account-" + UUID.randomUUID();
        long id = database.sql("""
                INSERT INTO app_user (username, password_hash, role, created_at) VALUES (:name, :hash, :role, :created)
                RETURNING user_id
                """).param("name", username).param("hash", passwords.encode(PASSWORD)).param("role", role)
                .param("created", CREATED).query(Long.class).single();
        return new FixtureUser(id, username);
    }

    private int profileCount(FixtureUser owner) {
        return database.sql("SELECT COUNT(*) FROM client_profile WHERE user_id = :id")
                .param("id", owner.id()).query(Integer.class).single();
    }

    private ClientDeliveryDefaults storedDefaults(FixtureUser owner) {
        return database.sql("""
                SELECT default_recipient_name, default_phone, default_address
                FROM client_profile WHERE user_id = :id
                """).param("id", owner.id()).query((row, index) -> new ClientDeliveryDefaults(
                        row.getString("default_recipient_name"), row.getString("default_phone"),
                        row.getString("default_address"))).single();
    }

    private record FixtureUser(long id, String username) {
    }

    private record Session(MockHttpSession session, String csrfHeader, String csrfToken) {
    }
}
