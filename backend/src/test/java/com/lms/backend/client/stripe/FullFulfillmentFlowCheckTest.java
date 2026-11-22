package com.lms.backend.client.stripe;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.StreamSupport;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.convention.TestBean;

import com.stripe.StripeClient;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.address=127.0.0.1", "lms.client.stripe.enabled=true",
        "lms.client.stripe.reconciliation-interval=PT1H", "lms.client.orders.expiry.enabled=false",
        "lms.delivery.demo-enabled=false", "lms.shared.bootstrap-admin-username=",
        "LMS_STRIPE_TEST_API_KEY=sk_test_step6_fixture", "LMS_STRIPE_WEBHOOK_SECRET=whsec_step6_fixture",
        "LMS_STRIPE_RETURN_URL=http://127.0.0.1/shared/shared-welcome.html",
        "LMS_STRIPE_CHECKOUT_SCOPE=step6-fulfillment"
})
@ActiveProfiles("delivery-full")
class FullFulfillmentFlowCheckTest {
    private static final String SCOPE = "step6-fulfillment";
    private static final String SECRET = "whsec_step6_fixture";
    private static final String PASSWORD = "Step6Fixture2026!";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir static Path proofs;
    @LocalServerPort private int port;
    @Autowired private JdbcClient db;
    @Autowired private PasswordEncoder passwords;
    @TestBean(methodName = "isolatedGateway", enforceOverride = true)
    private StripeSandboxGateway gateway;
    private final Map<String, Session> providerEvidence = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void proofStorage(DynamicPropertyRegistry registry) {
        registry.add("lms.delivery.proof-directory", () -> proofs.toString());
    }

    static StripeSandboxGateway isolatedGateway() {
        // The real webhook parser and evidence validator run without a network-capable SDK.
        return spy(new StripeSandboxGateway(mock(StripeClient.class), JSON, SECRET,
                "http://127.0.0.1/shared/shared-welcome.html", SCOPE));
    }

    @BeforeEach
    void requireDisposableDatabaseAndStubTransport() throws Exception {
        String database = db.sql("SELECT current_database()").query(String.class).single();
        assertTrue(database.matches("lms_step6_[0-9a-f]{8}"), "Run Step 6 against a fresh disposable database.");
        doAnswer(call -> {
            long paymentId = call.getArgument(0);
            assertEquals(new BigDecimal("10.00"), call.getArgument(1));
            var session = session(paymentId, "open", "unpaid");
            providerEvidence.put(session.getId(), session);
            return session;
        }).when(gateway).create(anyLong(), any(BigDecimal.class));
        doAnswer(call -> {
            String id = call.getArgument(0);
            var evidence = providerEvidence.get(id);
            assertTrue(evidence != null, "No synthetic evidence for " + id);
            return evidence;
        }).when(gateway).retrieve(anyString());
        doNothing().when(gateway).expire(anyString());
    }

    @Test
    void verifiedCheckoutReachesDeliveredWithOnePickupAndPrivateProof() throws Exception {
        var owner = signIn("CLIENT");
        var warehouse = signIn("WAREHOUSE_WORKER");
        var admin = signIn("ADMIN");
        var otherClient = signIn("CLIENT");
        assertEquals(401, new Browser().get("/api/warehouse/summary").statusCode());
        assertEquals(403, owner.browser().get("/api/warehouse/summary").statusCode());
        assertEquals(403, warehouse.browser().get("/api/delivery/workspace").statusCode());

        var order = createOrder(owner);
        assertStatus(order, owner, admin, "AWAITING_PAYMENT");
        assertEquals(404, otherClient.browser().get(order.tracking()).statusCode());
        assertEquals(403, owner.browser().get(order.admin()).statusCode());
        assertStock(order, 10, 0);
        assertEquals(List.of(), movements(order));

        long payment = checkout(order, owner);
        assertStock(order, 10, 2);
        assertReservation(order, payment, "HELD", false, false);
        assertEquals(List.of("RESERVE:0:2"), movements(order));
        var hiddenOrder = warehouse.browser().json(
                warehouse.browser().post(order.scan(), command("scannedIdentifier", order.sku())), 404);
        assertEquals("WAREHOUSE_ORDER_NOT_FOUND", hiddenOrder.path("code").asText());
        assertEquals(0L, scalar("SELECT count(*) FROM picking_task WHERE order_id = ?", order.id(), Long.class));
        assertEquals("AWAITING_PAYMENT", orderStatus(order));
        providerEvidence.put(sessionId(payment), session(payment, "complete", "paid"));
        webhook(payment, "uncaptured");
        assertEquals("AWAITING_PAYMENT", orderStatus(order));
        assertEquals(0L, scalar("SELECT count(*) FROM refund WHERE payment_id = ?", payment, Long.class));
        assertEquals(0L, scalar("SELECT count(*) FROM payment WHERE payment_id = ? AND accepted_at IS NOT NULL",
                payment, Long.class));
        assertStock(order, 10, 2);

        capture(payment);
        assertStatus(order, owner, admin, "READY_FOR_PICKING");
        assertEquals(1L, scalar("""
                SELECT count(*) FROM payment WHERE payment_id = ? AND accepted_at IS NOT NULL
                  AND payment_status = 'ACCEPTED' AND amount = 10 AND provider_reference = 'pi_step6' || payment_id
                """, payment, Long.class));
        assertReservation(order, payment, "HELD", false, false);
        assertEquals(1L, notificationCount(order, owner, "PAYMENT_CONFIRMED"));
        assertTrue(owner.browser().json(owner.browser().get("/api/client/notifications"), 200)
                .path("notifications").toString().contains("PAYMENT_CONFIRMED"));
        assertTrue(warehouse.browser().json(warehouse.browser().get("/api/warehouse/orders/available"), 200)
                .path("orderRowList").toString().contains(order.reference()));

        expect(403, warehouse.browser().postWithoutCsrf(order.scan(), command("scannedIdentifier", order.sku())));
        expect(403, owner.browser().post(order.scan(), command("scannedIdentifier", order.sku())));
        var badScan = warehouse.browser().json(
                warehouse.browser().post(order.scan(), command("scannedIdentifier", "STEP6-WRONG")), 200);
        assertEquals("REJECTED", badScan.path("result").asText());
        assertEquals("READY_FOR_PICKING", orderStatus(order));
        scan(warehouse, order);
        assertStatus(order, owner, admin, "PICKING");
        expect(409, warehouse.browser().post(order.task() + "/confirm-packing", null));
        scan(warehouse, order);
        expect(200, warehouse.browser().post(order.task() + "/confirm-packing", null));
        assertStatus(order, owner, admin, "READY_FOR_PICKUP");
        assertEquals("WAREHOUSE:true", scalar("""
                SELECT custody_status || ':' || (current_handler_user_id IS NULL) FROM delivery_package WHERE order_id = ?
                """, order.id(), String.class));
        assertEquals("PACKED", scalar("SELECT task_status FROM picking_task WHERE order_id = ?", order.id(), String.class));
        assertEquals(0L, scalar("""
                SELECT count(*) FROM picking_assignment_history JOIN picking_task USING (picking_task_id)
                WHERE order_id = ? AND ended_at IS NULL
                """, order.id(), Long.class));
        assertEquals(0L, listItems(order));
        assertStock(order, 10, 2);
        assertReservation(order, payment, "HELD", false, false);
        assertEquals(List.of("RESERVE:0:2"), movements(order));

        var driver = signIn("DELIVERY_WORKER");
        String handoff = order.warehouse() + "/pickup-list";
        expect(403, owner.browser().post(handoff, command()));
        var listCommand = command();
        var list = warehouse.browser().json(warehouse.browser().post(handoff, listCommand), 200);
        assertEquals(list, warehouse.browser().json(warehouse.browser().post(handoff, listCommand), 200));
        expect(409, warehouse.browser().post(handoff, command()));
        assertEquals(1L, listItems(order));
        String listId = list.path("pickupListId").asText();
        String listQr = list.path("listQrCode").asText();
        String parcelQr = warehouse.browser().json(warehouse.browser().get(order.warehouse() + "/package"), 200)
                .path("packageQrCode").asText();
        assertEquals(order.reference() + "-PACKAGE", parcelQr);
        assertNotEquals(parcelQr, listQr);
        assertNotEquals(order.reference(), listQr);
        expect(200, warehouse.browser().get(order.warehouse() + "/package/qr-code"));
        expect(200, warehouse.browser().get(handoff + "/" + listId + "/qr-code"));
        assertEquals(driver.id(), scalar("SELECT assigned_to_delivery_user_id FROM pickup_list WHERE pickup_list_id = ?",
                Long.parseLong(listId), Long.class));
        var otherDriver = signIn("DELIVERY_WORKER");
        var capability = driver.browser().json(driver.browser().get("/api/delivery/workspace"), 200).path("capabilities");
        assertTrue(capability.path("pickup").asBoolean() && capability.path("attempts").asBoolean()
                && capability.path("proofs").asBoolean());
        String pickup = "/api/delivery/pickup-lists";
        expect(403, otherDriver.browser().post(pickup + "/claim", command("qrCode", listQr)));
        expect(200, driver.browser().post(pickup + "/claim", command("qrCode", listQr)));
        assertStock(order, 10, 2);
        assertEquals(List.of("RESERVE:0:2"), movements(order));

        String collect = pickup + "/" + listId + "/collect";
        var collection = command("packageQrCode", parcelQr);
        var duplicate = race(() -> driver.browser().post(collect, collection),
                () -> driver.browser().post(collect, collection));
        assertEquals(driver.browser().json(duplicate.get(0), 200), driver.browser().json(duplicate.get(1), 200));
        expect(409, driver.browser().post(collect, command("packageQrCode", parcelQr)));
        assertStatus(order, owner, admin, "SHIPPED");
        assertStock(order, 8, 0);
        assertReservation(order, payment, "CONSUMED", false, true);
        assertEquals(List.of("RESERVE:0:2", "PICKUP:-2:-2"), movements(order));
        assertEquals(1L, notificationCount(order, owner, "SHIPPED"));
        assertEquals(List.of("PICKED_UP"), custody(order));
        assertEquals(driver.id(), scalar("SELECT current_handler_user_id FROM delivery_package WHERE order_id = ?",
                order.id(), Long.class));

        long packageId = scalar("SELECT package_id FROM delivery_package WHERE order_id = ?", order.id(), Long.class);
        String shipment = "/api/delivery/packages/" + packageId;
        expect(403, otherDriver.browser().post(shipment + "/start", command()));
        var started = driver.browser().json(driver.browser().post(shipment + "/start", command()), 200);
        assertStatus(order, owner, admin, "OUT_FOR_DELIVERY");
        expect(400, driver.browser().post(shipment + "/outcome", command("outcome", "DELIVERED", "proofIds", List.of())));
        assertEquals("OUT_FOR_DELIVERY", orderStatus(order));
        byte[] photo = photo();
        var proof = driver.browser().json(driver.browser().upload(shipment + "/proofs", photo), 200);
        String proofId = proof.path("id").asText();
        String proofPath = "/api/delivery/proofs/" + proofId;
        var openProof = driver.browser().get(proofPath);
        expect(200, openProof);
        assertArrayEquals(photo, openProof.body());
        assertEquals("image/png", openProof.headers().firstValue("Content-Type").orElseThrow());
        String cache = openProof.headers().firstValue("Cache-Control").orElseThrow();
        assertTrue(cache.contains("no-store") && cache.contains("private"));
        expect(403, otherDriver.browser().get(proofPath));
        expect(403, otherClient.browser().get(proofPath));
        expect(403, admin.browser().get(proofPath));

        var complete = command("outcome", "DELIVERED", "proofIds", List.of(proofId));
        var delivered = driver.browser().json(driver.browser().post(shipment + "/outcome", complete), 200);
        assertEquals(delivered, driver.browser().json(driver.browser().post(shipment + "/outcome", complete), 200));
        expect(403, driver.browser().get(proofPath));
        expect(409, driver.browser().post(collect, command("packageQrCode", parcelQr)));
        assertStatus(order, owner, admin, "DELIVERED");
        assertStock(order, 8, 0);
        assertEquals(List.of("RESERVE:0:2", "PICKUP:-2:-2"), movements(order));
        assertEquals(List.of("PICKED_UP", "DELIVERED"), custody(order));
        assertEquals("RECIPIENT:true", scalar("""
                SELECT custody_status || ':' || (current_handler_user_id IS NULL) FROM delivery_package WHERE order_id = ?
                """, order.id(), String.class));
        assertEquals(1L, notificationCount(order, owner, "DELIVERED"));
        assertEquals(1L, notificationCount(order, driver, "DELIVERED"));
        assertEquals(1L, scalar("""
                SELECT count(*) FROM delivery_attempt a JOIN delivery_proof p USING (delivery_attempt_id)
                JOIN order_delivery_detail d USING (delivery_detail_id)
                WHERE a.delivery_attempt_id = ? AND a.outcome = 'DELIVERED' AND a.end_time IS NOT NULL
                  AND a.pickup_custody_event_id IS NOT NULL AND p.delivery_used_for_outcome AND d.version_no = 1
                """, started.path("attemptId").asLong(), Long.class));

        var clientTracking = owner.browser().json(owner.browser().get(order.tracking()), 200);
        assertEquals(List.of("ORDER_PLACED", "PICKING_STARTED", "PACKING_CONFIRMED", "PACKAGE_CREATED",
                "PACKAGE_PICKED_UP", "DELIVERY_ATTEMPT_STARTED", "DELIVERY_COMPLETED"),
                StreamSupport.stream(clientTracking.path("progression").spliterator(), false)
                        .map(event -> event.path("type").asText()).toList());
        assertEquals("DELIVERED", clientTracking.path("deliveryAttempts").get(0).path("outcome").asText());
        assertFalse(clientTracking.toString().contains(proofPath));
        var adminOrder = admin.browser().json(admin.browser().get(order.admin()), 200);
        var attempts = adminOrder.path("adminViewOrderDeliveryAttemptList");
        assertEquals(1, attempts.size());
        assertEquals("DELIVERED", attempts.get(0).path("outcome").asText());
        assertEquals(1, attempts.get(0).path("adminViewOrderDeliveryAttemptPhotoProofCount").asInt());
        assertTrue(adminOrder.path("adminViewOrderRefundList").isEmpty());
    }

    @Test
    void verifiedOrderCancellationAndFirstScanHaveExactlyOneWinner() throws Exception {
        var owner = signIn("CLIENT");
        var warehouse = signIn("WAREHOUSE_WORKER");
        var order = createOrder(owner);
        long payment = checkout(order, owner);
        capture(payment);
        assertEquals("READY_FOR_PICKING", orderStatus(order));

        var results = race(() -> owner.browser().post(order.client() + "/cancel", null),
                () -> warehouse.browser().post(order.scan(), command("scannedIdentifier", order.sku())));
        assertEquals(List.of(200, 409), results.stream().map(HttpResponse::statusCode).sorted().toList());
        int picked = scalar("SELECT picked_quantity FROM order_item WHERE order_id = ?", order.id(), Integer.class);
        if (results.getFirst().statusCode() == 200) {
            assertEquals("CANCELLED", orderStatus(order));
            assertEquals(0, picked);
            expect(200, owner.browser().post(order.client() + "/cancel", null));
            assertReservation(order, payment, "RELEASED", true, false);
            assertStock(order, 10, 0);
            assertEquals(List.of("RESERVE:0:2", "RESERVATION_RELEASED:0:-2"), movements(order));
            assertEquals("REQUESTED", scalar("SELECT refund_status FROM refund WHERE payment_id = ?", payment, String.class));
        } else {
            assertEquals("PICKING", orderStatus(order));
            assertEquals(1, picked);
            assertReservation(order, payment, "HELD", false, false);
            assertStock(order, 10, 2);
            assertEquals(List.of("RESERVE:0:2"), movements(order));
            assertEquals(0L, scalar("SELECT count(*) FROM refund WHERE payment_id = ?", payment, Long.class));
        }
        assertEquals(0L, scalar("SELECT count(*) FROM delivery_package WHERE order_id = ?", order.id(), Long.class));
        System.out.println("Step 6 cancellation/first scan winner: " + orderStatus(order));
    }
    private Actor signIn(String role) throws Exception {
        String username = "step6-" + UUID.randomUUID();
        long id = db.sql("INSERT INTO app_user (username, password_hash, role) VALUES (?, ?, ?) RETURNING user_id")
                .params(username, passwords.encode(PASSWORD), role).query(Long.class).single();
        var browser = new Browser();
        browser.refreshSession();
        var login = browser.json(browser.post("/api/shared/login", Map.of(
                "sharedLoginUserUsername", username, "sharedLoginUserPassword", PASSWORD)), 200);
        assertEquals(role, login.path("role").asText());
        assertFalse(login.path("mustChangePassword").asBoolean());
        var current = browser.refreshSession();
        assertTrue(current.path("sharedViewSessionIsLoggedIn").asBoolean());
        assertEquals(username, current.path("username").asText());
        return new Actor(id, browser);
    }

    private Order createOrder(Actor owner) throws Exception {
        String sku = "SKU-STEP6-" + UUID.randomUUID();
        long product = db.sql("INSERT INTO product (sku, name, current_price) VALUES (?, ?, 5) RETURNING product_id")
                .params(sku, "Step 6 fixture product").query(Long.class).single();
        db.sql("INSERT INTO inventory (product_id, physical_stock, reserved_stock) VALUES (?, 10, 0)")
                .param(product).update();
        var answer = owner.browser().json(owner.browser().send("POST", "/api/client/orders", JSON.writeValueAsBytes(Map.of(
                "items", List.of(Map.of("productId", product, "quantity", 2)), "recipientName", "Step 6 fixture",
                "contactPhone", "91234567", "deliveryAddress", "Fixture address, no real recipient")),
                "application/json", true, Map.of("Idempotency-Key", UUID.randomUUID().toString())), 201);
        assertEquals("AWAITING_PAYMENT", answer.path("orderStatus").asText());
        String reference = answer.path("orderReference").asText();
        long id = db.sql("SELECT order_id FROM customer_order WHERE order_reference = ?")
                .param(reference).query(Long.class).single();
        return new Order(id, reference, product, sku);
    }

    private long checkout(Order order, Actor owner) throws Exception {
        var checkout = owner.browser().json(owner.browser().post(order.client() + "/checkout", null), 200);
        assertTrue(checkout.path("providerCheckoutAvailable").asBoolean());
        assertEquals(0, new BigDecimal("10.00").compareTo(checkout.path("amountHkd").decimalValue()));
        assertEquals(order.reference(), checkout.path("orderReference").asText());
        return checkout.path("checkoutId").asLong();
    }

    private void capture(long payment) throws Exception {
        var session = session(payment, "complete", "paid");
        var intent = new PaymentIntent();
        intent.setId("pi_step6" + payment);
        intent.setLivemode(false);
        intent.setCurrency("hkd");
        intent.setStatus("succeeded");
        intent.setAmountReceived(1000L);
        session.setPaymentIntentObject(intent);
        providerEvidence.put(sessionId(payment), session);
        webhook(payment, "captured");
        webhook(payment, "captured");
        assertEquals(1L, scalar("""
                SELECT count(*) FROM client_stripe_event WHERE payment_id = ? AND event_id LIKE 'evt_step6captured%'
                  AND processed_at IS NOT NULL
                """, payment, Long.class));
    }

    private void webhook(long payment, String name) throws Exception {
        String content = """
                {"object":"event","id":"evt_step6%s%d","type":"checkout.session.completed","livemode":false,
                 "data":{"object":{"id":"%s","object":"checkout.session",
                  "metadata":{"lms_checkout_id":"%d","lms_checkout_scope":"%s"}}}}
                """.formatted(name, payment, sessionId(payment), payment, SCOPE);
        long timestamp = Instant.now().getEpochSecond();
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(UTF_8), "HmacSHA256"));
        String hash = HexFormat.of().formatHex(mac.doFinal((timestamp + "." + content).getBytes(UTF_8)));
        expect(204, new Browser().send("POST", StripeSandboxConfiguration.WEBHOOK, content.getBytes(UTF_8),
                "application/json", false, Map.of("Stripe-Signature", "t=" + timestamp + ",v1=" + hash)));
    }

    private static String sessionId(long payment) {
        return "cs_test_step6" + payment;
    }

    private static Session session(long payment, String status, String paymentStatus) {
        var session = new Session();
        session.setId(sessionId(payment));
        session.setLivemode(false);
        session.setMode("payment");
        session.setCurrency("hkd");
        session.setClientReferenceId(Long.toString(payment));
        session.setMetadata(Map.of("lms_checkout_id", Long.toString(payment), "lms_checkout_scope", SCOPE));
        session.setStatus(status);
        session.setPaymentStatus(paymentStatus);
        session.setAmountTotal(1000L);
        session.setExpiresAt(Instant.now().plusSeconds(1800).getEpochSecond());
        session.setUrl("https://checkout.stripe.com/c/pay/" + session.getId());
        return session;
    }

    private void scan(Actor warehouse, Order order) throws Exception {
        assertEquals("ACCEPTED", warehouse.browser().json(
                warehouse.browser().post(order.scan(), command("scannedIdentifier", order.sku())), 200)
                .path("result").asText());
    }

    private void assertStatus(Order order, Actor owner, Actor admin, String expected) throws Exception {
        assertEquals(expected, orderStatus(order));
        assertEquals(expected, owner.browser().json(owner.browser().get(order.tracking()), 200).path("orderStatus").asText());
        assertEquals(expected, admin.browser().json(admin.browser().get(order.admin()), 200).path("orderStatus").asText());
    }

    private String orderStatus(Order order) {
        return scalar("SELECT order_status FROM customer_order WHERE order_id = ?", order.id(), String.class);
    }

    private void assertStock(Order order, int physical, int reserved) {
        assertEquals(physical + ":" + reserved, scalar("""
                SELECT physical_stock || ':' || reserved_stock FROM inventory WHERE product_id = ?
                """, order.product(), String.class));
    }

    private void assertReservation(Order order, long payment, String status, boolean released, boolean consumed) {
        assertEquals(List.of(status + ":" + payment + ":2:" + released + ":" + consumed), db.sql("""
                SELECT r.status || ':' || r.payment_id || ':' || r.quantity || ':' ||
                       (r.released_at IS NOT NULL) || ':' || (r.consumed_at IS NOT NULL)
                FROM stock_reservation r JOIN order_item i USING (order_item_id)
                WHERE i.order_id = ?
                """).param(order.id()).query(String.class).list());
    }

    private List<String> movements(Order order) {
        return db.sql("""
                SELECT movement_type || ':' || quantity_delta || ':' || reserved_delta
                FROM stock_movement WHERE product_id = ? ORDER BY movement_id
                """).param(order.product()).query(String.class).list();
    }

    private List<String> custody(Order order) {
        return db.sql("""
                SELECT event_type FROM package_custody_event JOIN delivery_package USING (package_id)
                WHERE order_id = ? ORDER BY custody_event_id
                """).param(order.id()).query(String.class).list();
    }

    private long listItems(Order order) {
        return scalar("""
                SELECT count(*) FROM pickup_list_item JOIN delivery_package USING (package_id) WHERE order_id = ?
                """, order.id(), Long.class);
    }

    private long notificationCount(Order order, Actor recipient, String type) {
        return db.sql("SELECT count(*) FROM notification WHERE order_id = ? AND recipient_user_id = ? AND type = ?")
                .params(order.id(), recipient.id(), type).query(Long.class).single();
    }

    private <T> T scalar(String sql, long id, Class<T> type) {
        return db.sql(sql).param(id).query(type).single();
    }

    private static Map<String, Object> command() {
        return Map.of("requestId", UUID.randomUUID().toString());
    }

    private static Map<String, Object> command(String field, Object value) {
        return Map.of("requestId", UUID.randomUUID().toString(), field, value);
    }

    private static Map<String, Object> command(String field, Object value, String secondField, Object secondValue) {
        return Map.of("requestId", UUID.randomUUID().toString(), field, value, secondField, secondValue);
    }

    private static List<HttpResponse<byte[]>> race(Callable<HttpResponse<byte[]>> first,
            Callable<HttpResponse<byte[]>> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var callers = Executors.newFixedThreadPool(2)) {
            var one = callers.submit(() -> synchronizedRequest(first, ready, start));
            var two = callers.submit(() -> synchronizedRequest(second, ready, start));
            try {
                assertTrue(ready.await(5, TimeUnit.SECONDS));
            } finally {
                start.countDown();
            }
            return List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS));
        }
    }

    private static HttpResponse<byte[]> synchronizedRequest(Callable<HttpResponse<byte[]>> send,
            CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return send.call();
    }

    private static byte[] photo() throws Exception {
        var photo = new BufferedImage(12, 12, BufferedImage.TYPE_INT_RGB);
        photo.setRGB(3, 4, Color.BLUE.getRGB());
        var bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(photo, "png", bytes));
        return bytes.toByteArray();
    }

    private static void expect(int status, HttpResponse<byte[]> response) {
        assertEquals(status, response.statusCode(), () -> new String(response.body(), UTF_8));
    }

    private record Actor(long id, Browser browser) {}
    private record Order(long id, String reference, long product, String sku) {
        String client() { return "/api/client/orders/" + reference; }
        String tracking() { return client() + "/tracking"; }
        String warehouse() { return "/api/warehouse/orders/" + reference; }
        String task() { return warehouse() + "/picking-task"; }
        String scan() { return task() + "/scans"; }
        String admin() { return "/api/admin/orders/" + id; }
    }

    private final class Browser {
        private final HttpClient http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
        private String tokenName;
        private String token;

        JsonNode refreshSession() throws Exception {
            var session = json(get("/api/shared/session"), 200);
            tokenName = session.path("sharedViewSessionSecurityTokenHeaderName").asText();
            token = session.path("sharedViewSessionSecurityToken").asText();
            return session;
        }

        HttpResponse<byte[]> get(String path) throws Exception {
            return send("GET", path, null, null, false, Map.of());
        }

        HttpResponse<byte[]> post(String path, Object body) throws Exception {
            return send("POST", path, body == null ? null : JSON.writeValueAsBytes(body),
                    "application/json", true, Map.of());
        }

        HttpResponse<byte[]> postWithoutCsrf(String path, Object body) throws Exception {
            return send("POST", path, JSON.writeValueAsBytes(body), "application/json", false, Map.of());
        }

        HttpResponse<byte[]> upload(String path, byte[] photo) throws Exception {
            String boundary = "step6-" + UUID.randomUUID();
            var body = new ByteArrayOutputStream();
            for (var field : Map.of("type", "PHOTO", "requestId", UUID.randomUUID().toString()).entrySet()) {
                body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field.getKey()
                        + "\"\r\n\r\n" + field.getValue() + "\r\n").getBytes(UTF_8));
            }
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"proof.png\""
                    + "\r\nContent-Type: image/png\r\n\r\n").getBytes(UTF_8));
            body.write(photo);
            body.write(("\r\n--" + boundary + "--\r\n").getBytes(UTF_8));
            return send("POST", path, body.toByteArray(), "multipart/form-data; boundary=" + boundary, true, Map.of());
        }

        HttpResponse<byte[]> send(String method, String path, byte[] body, String type, boolean csrf,
                Map<String, String> headers) throws Exception {
            var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                    .timeout(Duration.ofSeconds(15)).method(method, body == null
                            ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofByteArray(body));
            if (type != null) request.header("Content-Type", type);
            if (csrf) request.header(tokenName, token);
            headers.forEach(request::header);
            return http.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
        }

        JsonNode json(HttpResponse<byte[]> response, int status) {
            expect(status, response);
            return JSON.readTree(response.body());
        }
    }
}
