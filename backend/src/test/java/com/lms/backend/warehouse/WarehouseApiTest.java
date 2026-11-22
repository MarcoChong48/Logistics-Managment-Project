package com.lms.backend.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.jayway.jsonpath.JsonPath;
import com.lms.backend.shared.log.SharedSecurityLogWriter;

import jakarta.persistence.EntityManager;

@SpringBootTest(properties = {"lms.client.orders.expiry.enabled=false", "lms.client.stripe.enabled=false",
        "lms.shared.bootstrap-admin-username=", "lms.shared.bootstrap-admin-temporary-password="})
@AutoConfigureMockMvc
@Transactional
class WarehouseApiTest {

    private static final String PATH = "/api/warehouse";
    private static final String PASSWORD = "WarehouseFixture123!";

    @Autowired private MockMvc mvc;
    @Autowired private Environment environment;
    @Autowired JdbcClient db;
    @Autowired private PasswordEncoder passwords;
    @Autowired private EntityManager entities;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean private SharedSecurityLogWriter securityLogs;

    @Test
    void sharedSessionAndCsrfProtectWarehouseRoutes() throws Exception {
        String summary = PATH + "/summary";
        String readAll = PATH + "/notifications/read-all";
        mvc.perform(get(summary).servletPath(summary)).andExpect(status().isUnauthorized());
        var client = signIn(user("CLIENT"));
        read(summary, client).andExpect(status().isForbidden());
        var worker = signIn(user("WAREHOUSE_WORKER"));
        read(summary, worker).andExpect(status().isOk())
                .andExpect(jsonPath("$.myTaskCount").value(0))
                .andExpect(jsonPath("$.unreadAdminNotificationCount").value(0));
        mvc.perform(post(readAll).servletPath(readAll).session(worker.session())).andExpect(status().isForbidden());
        write(readAll, worker, null).andExpect(status().isOk()).andExpect(jsonPath("$.markedCount").value(0));
        db.sql("UPDATE app_user SET session_version = session_version + 1 WHERE user_id = :id")
                .param("id", worker.userId()).update();
        entities.clear();
        read(summary, worker).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-payment", "zero", "no-timestamp", "competing", "reconciliation", "pending",
        "refunded", "short-hold", "unlinked", "used-hold"})
    void firstScanRejectsUnresolvedPaymentOrIncorrectHolds(String condition) throws Exception {
        var order = order(2);
        var worker = signIn(user("WAREHOUSE_WORKER"));
        switch (condition) {
            case "no-payment" -> {
                db.sql("DELETE FROM stock_reservation WHERE payment_id = ?").param(order.paymentId()).update();
                db.sql("DELETE FROM payment WHERE payment_id = ?").param(order.paymentId()).update();
            }
            case "zero" -> db.sql("UPDATE payment SET amount = 0 WHERE payment_id = ?").param(order.paymentId()).update();
            case "no-timestamp" -> db.sql("UPDATE payment SET accepted_at = NULL WHERE payment_id = ?")
                    .param(order.paymentId()).update();
            case "competing" -> db.sql("""
                    INSERT INTO payment (order_id, amount, payment_status, accepted_at)
                    VALUES (?, 5, 'ACCEPTED', statement_timestamp())
                    """).param(order.id()).update();
            case "reconciliation", "pending" -> db.sql("""
                    INSERT INTO payment (order_id, amount, payment_status) VALUES (?, 5, ?)
                    """).param(order.id()).param(condition.equals("pending") ? "PENDING" : "RECONCILIATION_REQUIRED")
                    .update();
            case "refunded" -> db.sql("INSERT INTO refund (payment_id, amount) VALUES (?, 5)")
                    .param(order.paymentId()).update();
            case "short-hold" -> db.sql("UPDATE stock_reservation SET quantity = 1 WHERE payment_id = ?")
                    .param(order.paymentId()).update();
            case "unlinked" -> db.sql("""
                    INSERT INTO stock_reservation (order_item_id, quantity) VALUES (?, 1)
                    """).param(order.itemId()).update();
            case "used-hold" -> db.sql("""
                    UPDATE stock_reservation SET released_at = statement_timestamp() WHERE payment_id = ?
                    """).param(order.paymentId()).update();
            default -> throw new AssertionError(condition);
        }

        scan(order, worker, order.sku(), UUID.randomUUID()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_PAYMENT_OR_HOLDS_UNRESOLVED"));
        assertEquals("READY_FOR_PICKING", orderStatus(order));
        assertEquals(0, picked(order));
        assertEquals(0L, db.sql("SELECT count(*) FROM picking_task WHERE order_id = ?")
                .param(order.id()).query(Long.class).single());
    }

    @Test
    void exactSkuAndRequestReplayPreserveIntentionalIdenticalScans() throws Exception {
        var order = order(2);
        var worker = signIn(user("WAREHOUSE_WORKER"));
        var requestId = UUID.randomUUID();
        scan(order, worker, " " + order.sku(), UUID.randomUUID()).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("REJECTED"));
        assertEquals("READY_FOR_PICKING", orderStatus(order));
        scan(order, worker, order.sku(), requestId).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ACCEPTED"));
        scan(order, worker, order.sku(), requestId).andExpect(status().isOk());
        scan(order, worker, "different", requestId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_REQUEST_ID_REUSED"));
        assertEquals(1, picked(order));
        scan(order, worker, order.sku(), UUID.randomUUID()).andExpect(status().isOk());
        scan(order, worker, order.sku(), UUID.randomUUID()).andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("REJECTED"));
        assertEquals(2, picked(order));
        assertEquals(2L, scanCount(order, "ACCEPTED"));
        assertEquals(2L, scanCount(order, "REJECTED"));
        assertEquals(1L, db.sql("""
                SELECT count(*) FROM picking_assignment_history a JOIN picking_task t USING (picking_task_id)
                WHERE t.order_id = ? AND a.ended_at IS NULL
                """).param(order.id()).query(Long.class).single());
    }

    @Test
    void reversalRequiresBoundedReasonAndDoesNotRepeatAfterAnotherScan() throws Exception {
        var order = order(2);
        var worker = signIn(user("WAREHOUSE_WORKER"));
        scan(order, worker, order.sku(), UUID.randomUUID()).andExpect(status().isOk());
        scan(order, worker, order.sku(), UUID.randomUUID()).andExpect(status().isOk());
        String path = taskPath(order) + "/items/" + order.itemId() + "/picked-quantity";
        UUID id = UUID.randomUUID();
        write(path, worker, reversal(id, "   ")).andExpect(status().isBadRequest());
        write(path, worker, reversal(id, "a".repeat(301))).andExpect(status().isBadRequest());
        assertEquals(2, picked(order));

        String body = reversal(id, "Damaged packaging");
        write(path, worker, body).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("REVERSED"));
        assertEquals(1, picked(order));
        scan(order, worker, order.sku(), UUID.randomUUID()).andExpect(status().isOk());
        write(path, worker, body).andExpect(status().isOk());
        assertEquals(2, picked(order));
        assertEquals(1L, scanCount(order, "REVERSED"));
        assertEquals(3L, scanCount(order, "ACCEPTED"));
        assertTrue(db.sql("""
                SELECT reversal.reason = 'Damaged packaging' AND original.result = 'ACCEPTED'
                FROM picking_scan_history reversal JOIN picking_scan_history original
                ON original.scan_id = reversal.original_scan_id
                JOIN picking_task t ON t.picking_task_id = reversal.picking_task_id
                WHERE t.order_id = ?
                """).param(order.id()).query(Boolean.class).single());
    }

    @Test
    void packingDoesNotConsumeStockAndIsIdempotent() throws Exception {
        var order = order(2);
        var worker = signIn(user("WAREHOUSE_WORKER"));
        scan(order, worker, order.sku(), UUID.randomUUID()).andExpect(status().isOk());
        String pack = taskPath(order) + "/confirm-packing";
        write(pack, worker, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_PICKING_INCOMPLETE"));
        scan(order, worker, order.sku(), UUID.randomUUID()).andExpect(status().isOk());
        long shortage = db.sql("""
                INSERT INTO shortage_case (order_id, product_id, reported_by_worker_id, missing_quantity)
                VALUES (?, ?, ?, 1) RETURNING shortage_case_id
                """).param(order.id()).param(order.productId()).param(worker.userId()).query(Long.class).single();
        write(pack, worker, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_SHORTAGE_OPEN"));
        db.sql("UPDATE shortage_case SET case_status = 'RESOLVED' WHERE shortage_case_id = ?").param(shortage).update();
        write(pack, worker, null).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("PACKED"));
        write(pack, worker, null).andExpect(status().isOk()).andExpect(jsonPath("$.result").value("PACKED"));

        assertEquals("READY_FOR_PICKUP", orderStatus(order));
        assertEquals(order.reference() + "-PACKAGE", db.sql("""
                SELECT package_qr_code FROM delivery_package WHERE order_id = ? AND custody_status = 'WAREHOUSE'
                """).param(order.id()).query(String.class).single());
        assertTrue(db.sql("""
                SELECT physical_stock = 10 AND reserved_stock = 2 FROM inventory WHERE product_id = ?
                """).param(order.productId()).query(Boolean.class).single());
        assertTrue(db.sql("""
                SELECT status = 'HELD' AND consumed_at IS NULL FROM stock_reservation WHERE payment_id = ?
                """).param(order.paymentId()).query(Boolean.class).single());
        assertEquals(0L, db.sql("""
                SELECT count(*) FROM stock_movement WHERE product_id = ?
                """).param(order.productId()).query(Long.class).single());
        assertEquals(0L, db.sql("""
                SELECT count(*) FROM delivery_package p JOIN pickup_list_item i USING (package_id) WHERE p.order_id = ?
                """).param(order.id()).query(Long.class).single());
    }

    @Test
    void packingRevalidatesPaymentEvidenceAfterPicking() throws Exception {
        var order = order(1);
        var worker = signIn(user("WAREHOUSE_WORKER"));
        scan(order, worker, order.sku(), UUID.randomUUID()).andExpect(status().isOk());
        db.sql("UPDATE payment SET accepted_at = NULL WHERE payment_id = ?").param(order.paymentId()).update();
        write(taskPath(order) + "/confirm-packing", worker, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WAREHOUSE_PAYMENT_OR_HOLDS_UNRESOLVED"));
        assertEquals("PICKING", orderStatus(order));
        assertEquals(0L, db.sql("SELECT count(*) FROM delivery_package WHERE order_id = ?")
                .param(order.id()).query(Long.class).single());
    }

    @Test
    void cancellationAndFirstAcceptedScanCannotBothWin() throws Exception {
        var order = order(1);
        var client = signIn(order.client());
        var worker = signIn(user("WAREHOUSE_WORKER"));
        TestTransaction.flagForCommit();
        TestTransaction.end();

        var start = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var picking = threads.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return scan(order, worker, order.sku(), UUID.randomUUID()).andReturn().getResponse().getStatus();
            });
            var cancellation = threads.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return write("/api/client/orders/" + order.reference() + "/cancel", client, null)
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();

            int scanStatus = picking.get(20, TimeUnit.SECONDS);
            int cancelStatus = cancellation.get(20, TimeUnit.SECONDS);
            assertEquals(List.of(200, 409), java.util.stream.Stream.of(scanStatus, cancelStatus).sorted().toList());
            if (scanStatus == 200) {
                assertEquals("PICKING", orderStatus(order));
                assertEquals(1, picked(order));
            } else {
                assertEquals("CANCELLED", orderStatus(order));
                assertEquals(0, picked(order));
            }
        } finally {
            new TransactionTemplate(transactions).executeWithoutResult(status -> deleteRaceFixture(order, worker.userId()));
            TestTransaction.start();
        }
    }

    void deleteRaceFixture(FixtureOrder order, long workerId) {
        db.sql("DELETE FROM audit_log WHERE actor_user_id IN (?, ?)").param(order.client().id()).param(workerId).update();
        db.sql("DELETE FROM warehouse_picking_command WHERE order_id = ?").param(order.id()).update();
        db.sql("""
                DELETE FROM picking_scan_history WHERE picking_task_id IN
                (SELECT picking_task_id FROM picking_task WHERE order_id = ?)
                """).param(order.id()).update();
        db.sql("""
                DELETE FROM picking_assignment_history WHERE picking_task_id IN
                (SELECT picking_task_id FROM picking_task WHERE order_id = ?)
                """).param(order.id()).update();
        db.sql("DELETE FROM picking_task WHERE order_id = ?").param(order.id()).update();
        db.sql("DELETE FROM stock_movement WHERE product_id = ?").param(order.productId()).update();
        db.sql("DELETE FROM stock_reservation WHERE order_item_id = ?").param(order.itemId()).update();
        db.sql("DELETE FROM order_item WHERE order_id = ?").param(order.id()).update();
        db.sql("DELETE FROM refund WHERE payment_id = ?").param(order.paymentId()).update();
        db.sql("DELETE FROM payment WHERE order_id = ?").param(order.id()).update();
        db.sql("DELETE FROM delivery_package WHERE order_id = ?").param(order.id()).update();
        db.sql("DELETE FROM order_delivery_detail WHERE order_id = ?").param(order.id()).update();
        db.sql("DELETE FROM customer_order WHERE order_id = ?").param(order.id()).update();
        db.sql("DELETE FROM inventory WHERE product_id = ?").param(order.productId()).update();
        db.sql("DELETE FROM product WHERE product_id = ?").param(order.productId()).update();
        db.sql("DELETE FROM app_user WHERE user_id IN (?, ?)").param(order.client().id()).param(workerId).update();
    }

    ResultActions scan(FixtureOrder order, Session worker, String sku, UUID requestId) throws Exception {
        return write(taskPath(order) + "/scans", worker,
                """
                {"scannedIdentifier":"%s","requestId":"%s"}
                """.formatted(sku, requestId));
    }

    private static String reversal(UUID id, String reason) {
        return """
                {"pickedQuantity":1,"requestId":"%s","reason":"%s"}
                """.formatted(id, reason);
    }

    static String taskPath(FixtureOrder order) {
        return PATH + "/orders/" + order.reference() + "/picking-task";
    }

    @Test
    void pickupListRouteFollowsDeliveryProfile() throws Exception {
        var worker = signIn(user("WAREHOUSE_WORKER"));
        var response = read("/api/warehouse/orders/unknown/pickup-list", worker);
        if (environment.matchesProfiles("delivery-full")) {
            response.andExpect(status().isOk()).andExpect(jsonPath("$.pickupListId").doesNotExist());
        } else {
            response.andExpect(status().isNotFound());
        }
    }

    private long scanCount(FixtureOrder order, String result) {
        return db.sql("""
                SELECT count(*) FROM picking_scan_history s JOIN picking_task t USING (picking_task_id)
                WHERE t.order_id = ? AND s.result = ?
                """).param(order.id()).param(result).query(Long.class).single();
    }

    private String orderStatus(FixtureOrder order) {
        return db.sql("SELECT order_status FROM customer_order WHERE order_id = ?").param(order.id())
                .query(String.class).single();
    }

    private int picked(FixtureOrder order) {
        return db.sql("SELECT picked_quantity FROM order_item WHERE order_item_id = ?").param(order.itemId())
                .query(Integer.class).single();
    }

    FixtureOrder order(int quantity) {
        FixtureUser client = user("CLIENT");
        String reference = "Warehouse-" + UUID.randomUUID();
        String sku = "SKU-" + UUID.randomUUID();
        long orderId = db.sql("""
                INSERT INTO customer_order (order_reference, client_user_id, payment_deadline, total_amount, order_status)
                VALUES (?, ?, NOW() + INTERVAL '1 hour', 5 * ?, 'READY_FOR_PICKING') RETURNING order_id
                """).param(reference).param(client.id()).param(quantity).query(Long.class).single();
        long productId = db.sql("""
                INSERT INTO product (sku, name, current_price) VALUES (?, 'Warehouse fixture', 5) RETURNING product_id
                """).param(sku).query(Long.class).single();
        db.sql("INSERT INTO inventory (product_id, physical_stock, reserved_stock) VALUES (?, 10, ?)")
                .param(productId).param(quantity).update();
        long itemId = db.sql("""
                INSERT INTO order_item (order_id, product_id, quantity, unit_price) VALUES (?, ?, ?, 5)
                RETURNING order_item_id
                """).param(orderId).param(productId).param(quantity).query(Long.class).single();
        long paymentId = db.sql("""
                INSERT INTO payment (order_id, amount, payment_status, accepted_at)
                VALUES (?, 5 * ?, 'ACCEPTED', statement_timestamp()) RETURNING payment_id
                """).param(orderId).param(quantity).query(Long.class).single();
        db.sql("INSERT INTO stock_reservation (order_item_id, payment_id, quantity) VALUES (?, ?, ?)")
                .param(itemId).param(paymentId).param(quantity).update();
        return new FixtureOrder(orderId, reference, productId, itemId, paymentId, sku, client);
    }

    record FixtureOrder(long id, String reference, long productId, long itemId, long paymentId, String sku,
            FixtureUser client) {}

    ResultActions read(String path, Session session) throws Exception {
        return mvc.perform(get(path).servletPath(path).session(session.session()));
    }

    ResultActions write(String path, Session session, String body) throws Exception {
        var request = post(path).servletPath(path).session(session.session())
                .header(session.csrfHeader(), session.csrfToken());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(request);
    }

    Session signIn(FixtureUser user) throws Exception {
        Session guest = sessionInfo(null, user.id());
        String login = "/api/shared/login";
        var result = mvc.perform(post(login).servletPath(login).session(guest.session())
                .header(guest.csrfHeader(), guest.csrfToken()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sharedLoginUserUsername":"%s","sharedLoginUserPassword":"%s"}
                        """.formatted(user.username(), PASSWORD))).andExpect(status().isOk()).andReturn();
        return sessionInfo(assertInstanceOf(MockHttpSession.class, result.getRequest().getSession(false)), user.id());
    }

    private Session sessionInfo(MockHttpSession session, long userId) throws Exception {
        String path = "/api/shared/session";
        var request = get(path).servletPath(path);
        if (session != null) {
            request.session(session);
        }
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var token = JsonPath.parse(result.getResponse().getContentAsString());
        return new Session(assertInstanceOf(MockHttpSession.class, result.getRequest().getSession(false)),
                token.read("$.sharedViewSessionSecurityTokenHeaderName"), token.read("$.sharedViewSessionSecurityToken"),
                userId);
    }

    FixtureUser user(String role) {
        String username = "warehouse-" + UUID.randomUUID();
        long id = db.sql("INSERT INTO app_user (username, password_hash, role) VALUES (:name, :hash, :role) RETURNING user_id")
                .param("name", username).param("hash", passwords.encode(PASSWORD)).param("role", role)
                .query(Long.class).single();
        return new FixtureUser(id, username);
    }

    record FixtureUser(long id, String username) {}
    record Session(MockHttpSession session, String csrfHeader, String csrfToken, long userId) {}
}
