package com.lms.backend.client.notification;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
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
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.lms.backend.shared.log.SharedSecurityLogWriter;

@SpringBootTest(properties = {"lms.client.orders.expiry.enabled=false", "lms.client.stripe.enabled=false",
        "lms.shared.bootstrap-admin-username=", "lms.shared.bootstrap-admin-temporary-password="})
@AutoConfigureMockMvc
@Transactional
class ClientNotificationApiTest {

    private static final String PATH = "/api/client/notifications";
    private static final String PASSWORD = "NotificationFixture123!";
    private static final OffsetDateTime CREATED = OffsetDateTime.parse("2026-11-01T10:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient db;
    @Autowired private PasswordEncoder passwords;
    @MockitoBean private SharedSecurityLogWriter securityLogs;

    @Test
    void emptyInboxAndUnreadCountArePrivateAndDoNotCache() throws Exception {
        var client = signIn(user("CLIENT"));
        read(PATH, client).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.notifications").isEmpty()).andExpect(jsonPath("$.nextCursor").value(nullValue()));
        read(PATH + "/unread-count", client).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void stableCursorAndUnreadCountFilterByOwnerWithoutMarkingAnyRows() throws Exception {
        var owner = user("CLIENT");
        var order = order(owner);
        long older = notification(owner, order, "DELIVERED", "Your order was delivered.", CREATED.minusDays(1), false);
        long firstTie = notification(owner, order, "SHIPPED", "Your order was picked up for delivery.", CREATED, true);
        long lastTie = notification(owner, order, "PAYMENT_CONFIRMED", "Payment was confirmed for your order.", CREATED, false);
        notification(user("CLIENT"), null, "GENERAL", "Private other notification", CREATED.plusMinutes(1), false);
        var session = signIn(owner);

        mvc.perform(get(PATH).servletPath(PATH).session(session.session()).param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notifications[*].notificationId", contains(Long.toString(lastTie))))
                .andExpect(jsonPath("$.notifications[0].type").value("PAYMENT_CONFIRMED"))
                .andExpect(jsonPath("$.notifications[0].message").value("Payment was confirmed for your order."))
                .andExpect(jsonPath("$.notifications[0].orderReference").value(order.reference()))
                .andExpect(jsonPath("$.notifications[0].createdAt").value("2026-11-01T10:00:00Z"))
                .andExpect(jsonPath("$.notifications[0].isRead").value(false))
                .andExpect(jsonPath("$.nextCursor").value(Long.toString(lastTie)));

        notification(owner, order, "GENERAL", "New notification", CREATED.plusHours(1), true);
        mvc.perform(get(PATH).servletPath(PATH).session(session.session()).param("limit", "2")
                .param("cursor", Long.toString(lastTie)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notifications[*].notificationId",
                        contains(Long.toString(firstTie), Long.toString(older))))
                .andExpect(jsonPath("$.notifications[0].isRead").value(true))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
        read(PATH + "/unread-count", session).andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(2));
        for (String size : new String[] {"0", "51", "invalid"}) {
            mvc.perform(get(PATH).servletPath(PATH).session(session.session()).param("limit", size))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void markOneAndAllAreIdempotentAndOnlyChangeOwnedNotifications() throws Exception {
        var owner = user("CLIENT");
        var someoneElse = user("CLIENT");
        long one = notification(owner, null, "GENERAL", "Fixture", CREATED, false);
        notification(owner, null, "GENERAL", "Fixture", CREATED, false);
        long unowned = notification(someoneElse, null, "GENERAL", "Fixture", CREATED, false);
        var session = signIn(owner);

        write(PATH + "/" + one + "/read", session).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.notificationId").value(Long.toString(one)))
                .andExpect(jsonPath("$.isRead").value(true));
        write(PATH + "/" + one + "/read", session).andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true));
        read(PATH + "/unread-count", session).andExpect(jsonPath("$.unreadCount").value(1));

        String denied = write(PATH + "/" + unowned + "/read", session).andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.code").value("CLIENT_NOTIFICATION_NOT_FOUND")).andReturn().getResponse().getContentAsString();
        write(PATH + "/9223372036854775807/read", session).andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString("no-store"))).andExpect(content().string(denied));
        mvc.perform(get(PATH).servletPath(PATH).session(session.session()).param("cursor", Long.toString(unowned)))
                .andExpect(status().isNotFound()).andExpect(content().string(denied));

        write(PATH + "/read-all", session).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(jsonPath("$.markedRead").value(1));
        write(PATH + "/read-all", session).andExpect(jsonPath("$.markedRead").value(0));
        read(PATH + "/unread-count", session).andExpect(jsonPath("$.unreadCount").value(0));
        read(PATH + "/unread-count", signIn(someoneElse)).andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void cursorAndMarkReadPreserveIdentifiersBeyondJavaScriptNumberPrecision() throws Exception {
        var owner = user("CLIENT");
        long earlier = notification(owner, null, "GENERAL", "Fixture", CREATED, false);
        String large = "9007199254740993";
        db.sql("""
                INSERT INTO notification (notification_id,recipient_user_id,type,message,created_at)
                VALUES (:id,:owner,'GENERAL','Fixture',:created)
                """).param("id", Long.parseLong(large)).param("owner", owner.id()).param("created", CREATED).update();
        var session = signIn(owner);

        mvc.perform(get(PATH).servletPath(PATH).session(session.session()).param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notifications[0].notificationId").value(large))
                .andExpect(jsonPath("$.nextCursor").value(large));
        mvc.perform(get(PATH).servletPath(PATH).session(session.session()).param("cursor", large))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notifications[0].notificationId").value(Long.toString(earlier)));
        write(PATH + "/" + large + "/read", session).andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(large)).andExpect(jsonPath("$.isRead").value(true));
    }

    @Test
    void routesRequireClientRoleAndWritesRequireCsrf() throws Exception {
        var owner = user("CLIENT");
        long ownedId = notification(owner, null, "GENERAL", "Fixture", CREATED, false);
        var client = signIn(owner);
        var guest = sessionInfo(null);
        var admin = signIn(user("ADMIN"));
        var driver = signIn(user("DELIVERY_WORKER"));
        for (String url : new String[] {PATH, PATH + "/unread-count"}) {
            mvc.perform(get(url).servletPath(url)).andExpect(status().isUnauthorized());
            read(url, admin).andExpect(status().isForbidden());
            read(url, driver).andExpect(status().isForbidden());
        }
        for (String url : new String[] {PATH + "/" + ownedId + "/read", PATH + "/read-all"}) {
            write(url, guest).andExpect(status().isUnauthorized());
            write(url, admin).andExpect(status().isForbidden());
            write(url, driver).andExpect(status().isForbidden());
            mvc.perform(post(url).servletPath(url).session(client.session())).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SHARED_SECURITY_TOKEN_INVALID"));
        }
        assertEquals(Boolean.FALSE, db.sql("SELECT is_read FROM notification WHERE notification_id=:id")
                .param("id", ownedId).query(Boolean.class).single());
    }

    @Test
    void legacyMessagesAndForeignOrderLinksNeverExposePrivateText() throws Exception {
        var owner = user("CLIENT");
        var unrelated = order(user("CLIENT"));
        notification(owner, unrelated, "PAYMENT_CONFIRMED", "PRIVATE-AUDIT-MESSAGE", CREATED, false);
        notification(owner, null, "PRIVATE-AUDIT-TYPE", "PRIVATE-AUDIT-MESSAGE", CREATED.minusHours(1), false);
        notification(owner, null, "DELIVERY_FAILED", "PRIVATE-DRIVER-NOTE", CREATED.plusHours(1), false);
        notification(owner, null, "DELIVERY_FAILED", "The recipient was unavailable.", CREATED.plusHours(2), false);

        read(PATH, signIn(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[0].message").value("The recipient was unavailable."))
                .andExpect(jsonPath("$.notifications[1].message").value("The delivery could not be completed."))
                .andExpect(jsonPath("$.notifications[2].type").value("GENERAL"))
                .andExpect(jsonPath("$.notifications[2].orderReference").value(nullValue()))
                .andExpect(jsonPath("$.notifications[3].type").value("GENERAL"))
                .andExpect(content().string(not(containsString(unrelated.reference()))))
                .andExpect(content().string(not(containsString("PRIVATE-"))));
    }

    private ResultActions read(String path, Session session) throws Exception {
        return mvc.perform(get(path).servletPath(path).session(session.session()));
    }

    private ResultActions write(String path, Session session) throws Exception {
        return mvc.perform(post(path).servletPath(path).session(session.session())
                .header(session.csrfHeader(), session.csrfToken()));
    }

    private Session signIn(FixtureUser user) throws Exception {
        Session guest = sessionInfo(null);
        String login = "/api/shared/login";
        var result = mvc.perform(post(login).servletPath(login).session(guest.session())
                .header(guest.csrfHeader(), guest.csrfToken()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sharedLoginUserUsername":"%s","sharedLoginUserPassword":"%s"}
                        """.formatted(user.username(), PASSWORD))).andExpect(status().isOk()).andReturn();
        return sessionInfo(assertInstanceOf(MockHttpSession.class, result.getRequest().getSession(false)));
    }

    private Session sessionInfo(MockHttpSession session) throws Exception {
        String path = "/api/shared/session";
        var request = get(path).servletPath(path);
        if (session != null) {
            request.session(session);
        }
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var token = JsonPath.parse(result.getResponse().getContentAsString());
        return new Session(assertInstanceOf(MockHttpSession.class, result.getRequest().getSession(false)),
                token.read("$.sharedViewSessionSecurityTokenHeaderName"), token.read("$.sharedViewSessionSecurityToken"));
    }

    private FixtureUser user(String role) {
        String username = "notification-" + UUID.randomUUID();
        long id = db.sql("INSERT INTO app_user (username,password_hash,role) VALUES (:name,:hash,:role) RETURNING user_id")
                .param("name", username).param("hash", passwords.encode(PASSWORD)).param("role", role).query(Long.class).single();
        return new FixtureUser(id, username);
    }

    private FixtureOrder order(FixtureUser owner) {
        String reference = "ORD-" + UUID.randomUUID();
        long id = db.sql("""
                INSERT INTO customer_order (order_reference,client_user_id,payment_deadline,total_amount)
                VALUES (:reference,:owner,NOW()+INTERVAL '24 hours',12.00) RETURNING order_id
                """).param("reference", reference).param("owner", owner.id()).query(Long.class).single();
        return new FixtureOrder(id, reference);
    }

    private long notification(FixtureUser owner, FixtureOrder order, String type, String message,
            OffsetDateTime createdAt, boolean isRead) {
        return db.sql("""
                INSERT INTO notification (recipient_user_id,order_id,type,message,created_at,is_read)
                VALUES (:owner,:orderId,:type,:message,:createdAt,:isRead) RETURNING notification_id
                """).param("owner", owner.id()).param("orderId", order == null ? null : order.id(), Types.BIGINT)
                .param("type", type).param("message", message).param("createdAt", createdAt).param("isRead", isRead)
                .query(Long.class).single();
    }

    private record Session(MockHttpSession session, String csrfHeader, String csrfToken) {
    }

    private record FixtureUser(long id, String username) {
    }

    private record FixtureOrder(long id, String reference) {
    }
}
