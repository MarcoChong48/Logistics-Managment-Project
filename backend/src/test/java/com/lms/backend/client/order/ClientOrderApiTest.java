package com.lms.backend.client.order;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.lms.backend.shared.database.AppUser;
import com.lms.backend.shared.database.AppUserRepository;
import com.lms.backend.shared.database.CustomerOrderReleaseRepository;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.database.CustomerOrderWriteRepository;
import com.lms.backend.shared.database.Inventory;
import com.lms.backend.shared.database.InventoryRepository;
import com.lms.backend.shared.database.Product;
import com.lms.backend.shared.database.ProductRepository;
import com.lms.backend.shared.log.SharedSecurityLogWriter;
import com.lms.backend.shared.value.SharedAccountStatus;
import com.lms.backend.shared.value.SharedUserRole;

@SpringBootTest(properties = {
        "lms.shared.bootstrap-admin-username=",
        "lms.shared.bootstrap-admin-temporary-password=",
        "lms.client.orders.expiry.enabled=false"
})
@AutoConfigureMockMvc
@Transactional
class ClientOrderApiTest {

    private static final String ORDERS = "/api/client/orders";
    private static final String PASSWORD = "OrderFixture123!";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcClient database;

    @Autowired
    private ProductRepository products;

    @Autowired
    private InventoryRepository inventory;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CustomerOrderRepository orders;

    @Autowired
    private CustomerOrderReleaseRepository releaseRows;

    @Autowired
    private ClientOrderReleaseService releases;

    // Keep fixtures in the test transaction instead of committing separate audit rows.
    @MockitoBean
    private SharedSecurityLogWriter securityLogWriter;

    @MockitoSpyBean
    private CustomerOrderWriteRepository orderWrites;

    private final List<Long> fixtureUsers = new ArrayList<>();
    private final List<Long> fixtureProducts = new ArrayList<>();

    @Test
    void createsAndReservesASingleProductForTheSignedInClient() throws Exception {
        AppUser owner = createUser(SharedUserRole.CLIENT);
        AppUser otherClient = createUser(SharedUserRole.CLIENT);
        Product product = createProduct("19.90", "ACTIVE", 8, 2);
        String items = """
                {"productId":%d,"quantity":3,"unitPrice":0.01}
                """.formatted(product.productId);

        var response = submit(signIn(owner), request(items, ", \"clientUserId\":" + otherClient.userID))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$.orderStatus").value("AWAITING_PAYMENT"))
                .andExpect(jsonPath("$.totalAmount").value(59.70))
                .andReturn();
        var body = JsonPath.parse(response.getResponse().getContentAsString());
        String reference = body.read("$.orderReference");
        assertTrue(reference.startsWith("ORD-"));

        var saved = database.sql("""
                SELECT order_id, client_user_id, created_at, payment_deadline, total_amount, order_status
                FROM customer_order WHERE order_reference = :reference
                """).param("reference", reference).query((row, index) -> new SavedOrder(
                        row.getLong("order_id"), row.getLong("client_user_id"),
                        row.getObject("created_at", OffsetDateTime.class).toInstant(),
                        row.getObject("payment_deadline", OffsetDateTime.class).toInstant(),
                        row.getBigDecimal("total_amount"), row.getString("order_status"))).single();
        assertEquals(owner.userID, saved.clientUserId());
        assertEquals("AWAITING_PAYMENT", saved.orderStatus());
        assertEquals(new BigDecimal("59.70"), saved.total());
        assertEquals(Duration.ofHours(24), Duration.between(saved.createdAt(), saved.deadline()));
        assertEquals(saved.deadline(), Instant.parse(body.read("$.paymentDeadline")));

        assertEquals(1L, database.sql("""
                SELECT count(*) FROM order_delivery_detail WHERE order_id = :id AND version_no = 1
                  AND recipient_name = 'Test Recipient' AND phone = '+852 5555 0100'
                  AND address_text = 'Flat A, 12 Nathan Road, Kowloon, Hong Kong'
                """).param("id", saved.orderId()).query(Long.class).single());
        assertEquals(1L, database.sql("""
                SELECT count(*) FROM order_item oi JOIN stock_reservation sr USING (order_item_id)
                WHERE oi.order_id = :id AND oi.product_id = :productId AND oi.quantity = 3
                  AND oi.unit_price = 19.90 AND oi.picked_quantity = 0 AND sr.quantity = 3
                  AND sr.status = 'HELD' AND sr.payment_id IS NULL
                """).param("id", saved.orderId()).param("productId", product.productId).query(Long.class).single());
        assertEquals(1L, database.sql("""
                SELECT count(*) FROM stock_movement WHERE product_id = :productId AND actor_user_id = :actorId
                  AND quantity_delta = 0 AND reserved_delta = 3 AND movement_type = 'RESERVE' AND reason = :reference
                """).param("productId", product.productId).param("actorId", owner.userID)
                .param("reference", reference).query(Long.class).single());
        assertEquals(5, reservedStock(product));
        assertEquals(8, database.sql("SELECT physical_stock FROM inventory WHERE product_id = :id")
                .param("id", product.productId).query(Integer.class).single());
        assertEquals(0L, database.sql("SELECT count(*) FROM payment WHERE order_id = :id")
                .param("id", saved.orderId()).query(Long.class).single());
    }

    @Test
    void createsOneItemAndReservationPerProductUsingDatabasePrices() throws Exception {
        AppUser owner = createUser(SharedUserRole.CLIENT);
        Product first = createProduct("10.10", "ACTIVE", 6, 0);
        Product second = createProduct("2.25", "ACTIVE", 6, 0);

        submit(signIn(owner), request(item(second, 3) + "," + item(first, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(26.95));
        assertEquals(List.of(new BigDecimal("10.10"), new BigDecimal("2.25")), database.sql("""
                SELECT oi.unit_price FROM order_item oi
                JOIN customer_order co USING (order_id)
                JOIN stock_reservation sr USING (order_item_id)
                WHERE co.client_user_id = :clientId AND sr.quantity = oi.quantity AND sr.status = 'HELD'
                ORDER BY oi.product_id
                """).param("clientId", owner.userID).query(BigDecimal.class).list());
        assertEquals(2, reservedStock(first));
        assertEquals(3, reservedStock(second));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.5", "2147483648"})
    void rejectsInvalidQuantities(String quantity) throws Exception {
        String items = "{\"productId\":1,\"quantity\":" + quantity + "}";
        submit(signIn(createUser(SharedUserRole.CLIENT)), request(items))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_INVALID"));
    }

    @Test
    void rejectsEmptyOrders() throws Exception {
        submit(signIn(createUser(SharedUserRole.CLIENT)), request(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_INVALID"));
    }

    @Test
    void rejectsMissingDeliveryDetails() throws Exception {
        submit(signIn(createUser(SharedUserRole.CLIENT)), """
                {"items":[{"productId":1,"quantity":1}],"recipientName":"Test Recipient","contactPhone":"55550100"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_INVALID"));
    }

    @ParameterizedTest
    @CsvSource({"recipientName,120", "contactPhone,40", "deliveryAddress,1000"})
    void rejectsOverlongDeliveryFields(String field, int maxLength) throws Exception {
        String body = JsonPath.parse(request("{\"productId\":1,\"quantity\":1}"))
                .set("$." + field, "a".repeat(maxLength + 1)).jsonString();
        submit(signIn(createUser(SharedUserRole.CLIENT)), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_INVALID"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"\\u0000", "\\ud800", "\\udfff"})
    void rejectsInvalidDeliveryTextBeforeWriting(String jsonEscape) throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 3, 0);
        AppUser owner = createUser(SharedUserRole.CLIENT);
        String body = request(item(product, 2)).replace("Test Recipient", "Test" + jsonEscape + "Recipient");

        submit(signIn(owner), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_INVALID"));
        assertEquals(0L, countOrders(owner));
        assertEquals(0, reservedStock(product));
    }

    @Test
    void rejectsDuplicateProducts() throws Exception {
        String line = "{\"productId\":1,\"quantity\":2}";
        submit(signIn(createUser(SharedUserRole.CLIENT)), request(line + "," + line))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_DUPLICATE_PRODUCT"));
    }

    @Test
    void rejectsMissingProducts() throws Exception {
        submit(signIn(createUser(SharedUserRole.CLIENT)), request("{\"productId\":9223372036854775807,\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_PRODUCT_NOT_FOUND"));
    }

    @Test
    void rejectsInactiveProducts() throws Exception {
        Product product = createProduct("4.00", "INACTIVE", 8, 0);
        submit(signIn(createUser(SharedUserRole.CLIENT)), request(item(product, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_PRODUCT_INACTIVE"));
        assertEquals(0, reservedStock(product));
    }

    @Test
    void rejectsInsufficientAvailableStockWithoutPartiallyReservingTheOrder() throws Exception {
        Product first = createProduct("4.00", "ACTIVE", 8, 1);
        Product second = createProduct("5.00", "ACTIVE", 4, 3);
        AppUser owner = createUser(SharedUserRole.CLIENT);
        submit(signIn(owner), request(item(first, 3) + "," + item(second, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_STOCK_UNAVAILABLE"));
        assertEquals(0L, countOrders(owner));
        assertEquals(1, reservedStock(first));
        assertEquals(3, reservedStock(second));
    }

    @Test
    void rejectsProductsWithoutAnInventoryRow() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", null, 0);
        submit(signIn(createUser(SharedUserRole.CLIENT)), request(item(product, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_STOCK_UNAVAILABLE"));
    }

    @Test
    void rejectsTotalsThatCannotBeStored() throws Exception {
        Product product = createProduct("9999999999.99", "ACTIVE", 2, 0);
        submit(signIn(createUser(SharedUserRole.CLIENT)), request(item(product, 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_TOTAL_TOO_LARGE"));
        assertEquals(0, reservedStock(product));
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mvc.perform(post(ORDERS).servletPath(ORDERS).contentType(MediaType.APPLICATION_JSON).content(request("")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SHARED_NOT_LOGGED_IN"));
    }

    @ParameterizedTest
    @EnumSource(names = {"ADMIN", "WAREHOUSE_WORKER", "DELIVERY_WORKER"})
    void rejectsSignedInUsersWithoutTheClientRole(SharedUserRole role) throws Exception {
        submit(signIn(createUser(role)), request(""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SHARED_ACCESS_DENIED"));
    }

    @Test
    void requiresCsrfOnTheOrderPost() throws Exception {
        var client = signIn(createUser(SharedUserRole.CLIENT));
        mvc.perform(post(ORDERS).servletPath(ORDERS).session(client.session())
                        .contentType(MediaType.APPLICATION_JSON).content(request("")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SHARED_SECURITY_TOKEN_INVALID"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackPersistedOrderAndStockWhenALaterDatabaseWriteFails() throws Exception {
        Product first = createProduct("4.00", "ACTIVE", 8, 1);
        Product second = createProduct("5.00", "ACTIVE", 5, 0);
        AppUser owner = createUser(SharedUserRole.CLIENT);
        // The second write violates the real nonzero movement constraint after the first item has been persisted.
        doAnswer(call -> {
            database.sql("""
                    INSERT INTO stock_movement (product_id, movement_type, quantity_delta, reserved_delta)
                    VALUES (:productId, 'RESERVE', 0, 0)
                    """).param("productId", second.productId).update();
            return null;
        }).when(orderWrites).insertReservationMovement(eq(second.productId.longValue()), eq(owner.userID.longValue()),
                eq(2), anyString());

        var result = submit(signIn(owner), request(item(second, 2) + "," + item(first, 3)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.code").value("SHARED_UNEXPECTED_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value(matchesPattern("Something went wrong\\. Reference: [0-9a-f-]{36}")))
                .andReturn();
        assertInstanceOf(DataIntegrityViolationException.class, result.getResolvedException());
        assertEquals(0L, countOrders(owner));
        assertEquals(0L, countForProducts("SELECT count(*) FROM order_item WHERE product_id IN (:ids)"));
        assertEquals(0L, countForProducts("SELECT count(*) FROM stock_movement WHERE product_id IN (:ids)"));
        assertEquals(1, reservedStock(first));
        assertEquals(0, reservedStock(second));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentClientsCannotBothReserveTheLastAvailableStock() throws Exception {
        Product first = createProduct("4.00", "ACTIVE", 3, 0);
        Product second = createProduct("5.00", "ACTIVE", 5, 0);
        AppUser firstClient = createUser(SharedUserRole.CLIENT);
        AppUser secondClient = createUser(SharedUserRole.CLIENT);
        var firstSession = signIn(firstClient);
        var secondSession = signIn(secondClient);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var left = workers.submit(() -> submitWhenReady(ready, start, firstSession,
                    request(item(first, 3) + "," + item(second, 1))));
            var right = workers.submit(() -> submitWhenReady(ready, start, secondSession,
                    request(item(second, 1) + "," + item(first, 3))));
            boolean bothReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(bothReady);
            assertEquals(List.of(201, 409),
                    Stream.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS)).sorted().toList());
        }
        assertEquals(1L, countOrders(firstClient) + countOrders(secondClient));
        assertEquals(2L, countForProducts("SELECT count(*) FROM order_item WHERE product_id IN (:ids)"));
        assertEquals(2L, countForProducts("SELECT count(*) FROM stock_movement WHERE product_id IN (:ids)"));
        assertEquals(3, reservedStock(first));
        assertEquals(1, reservedStock(second));
    }

    @Test
    void ownerCanCancelTwiceWithOnlyOneRelease() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 1);
        AppUser owner = createUser(SharedUserRole.CLIENT);
        var session = signIn(owner);
        String reference = createOrder(session, item(product, 3));

        cancel(session, reference).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderReference").value(reference))
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));
        cancel(session, reference).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));

        assertEquals(1, reservedStock(product));
        assertEquals("CANCELLED", orderStatus(reference));
        assertEquals(1L, countReservations(reference, "RELEASED"));
        assertEquals(1L, database.sql("""
                SELECT count(*) FROM stock_movement WHERE product_id = :productId
                    AND movement_type = 'RELEASE' AND reserved_delta = -3 AND quantity_delta = 0
                    AND actor_user_id = :clientId AND reason = :reason
                """).param("productId", product.productId).param("clientId", owner.userID)
                .param("reason", "CLIENT_CANCELLED: " + reference).query(Long.class).single());
    }

    @Test
    void rejectsOtherClientsAnonymousUsersAndAdminsOnCancellation() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        String reference = createOrder(signIn(createUser(SharedUserRole.CLIENT)), item(product, 3));
        String path = ORDERS + "/" + reference + "/cancel";

        cancel(signIn(createUser(SharedUserRole.CLIENT)), reference)
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CLIENT_ORDER_NOT_FOUND"));
        mvc.perform(post(path).servletPath(path)).andExpect(status().isUnauthorized());
        cancel(signIn(createUser(SharedUserRole.ADMIN)), reference)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SHARED_ACCESS_DENIED"));
        assertEquals("AWAITING_PAYMENT", orderStatus(reference));
        assertEquals(3, reservedStock(product));
    }

    @ParameterizedTest
    @ValueSource(strings = {"accepted", "acceptedAt", "picking", "consumed"})
    void protectsOrdersWithPaymentOrFulfillmentEvidence(String protectedBy) throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(owner, item(product, 3));
        long id = orderId(reference);
        String sql = switch (protectedBy) {
            case "accepted" -> "INSERT INTO payment (order_id, amount, payment_status) VALUES (:id, 12, 'ACCEPTED')";
            case "acceptedAt" -> "INSERT INTO payment (order_id, amount, accepted_at) VALUES (:id, 12, NOW())";
            case "picking" -> "INSERT INTO picking_task (order_id, task_status) VALUES (:id, 'IN_PROGRESS')";
            case "consumed" -> """
                    UPDATE stock_reservation sr SET status = 'CONSUMED', consumed_at = NOW()
                    FROM order_item item WHERE sr.order_item_id = item.order_item_id AND item.order_id = :id
                    """;
            default -> throw new AssertionError(protectedBy);
        };
        database.sql(sql).param("id", id).update();
        var order = orders.findById(id).orElseThrow();
        order.createdTime = releaseRows.databaseTime().minus(Duration.ofHours(25));
        order.paymentDeadline = order.createdTime.plus(Duration.ofHours(24));
        orders.flush();

        releases.expireIfDue(id);
        cancel(owner, reference).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_CANCELLATION_NOT_ALLOWED"));
        assertEquals(3, reservedStock(product));
        assertEquals("AWAITING_PAYMENT", orderStatus(reference));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void expiresDueOrdersAndBarsPaymentAtTheDeadlineBeforeTheSweep() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var session = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(session, item(product, 3));
        long id = orderId(reference);
        var future = orders.findById(id).orElseThrow();
        assertTrue(future.canAcceptPaymentAt(future.paymentDeadline.minusSeconds(1)));
        assertFalse(future.canAcceptPaymentAt(future.paymentDeadline));
        releases.expireIfDue(id);
        assertEquals("AWAITING_PAYMENT", orderStatus(reference));
        assertEquals(3, reservedStock(product));

        makeOverdue(id);
        assertFalse(orders.findById(id).orElseThrow().canAcceptPaymentAt(releaseRows.databaseTime()));
        releases.expireIfDue(id);
        releases.expireIfDue(id);
        assertEquals("CANCELLED", orderStatus(reference));
        assertFalse(orders.findById(id).orElseThrow().canAcceptPaymentAt(future.paymentDeadline.minusSeconds(1)));
        assertEquals(0, reservedStock(product));
        assertEquals(1L, countReservations(reference, "RELEASED"));
        assertEquals(1L, database.sql("""
                SELECT count(*) FROM stock_movement WHERE movement_type = 'RELEASE'
                    AND actor_user_id IS NULL AND reason = :reason
                """).param("reason", "PAYMENT_DEADLINE_EXPIRED: " + reference).query(Long.class).single());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCancellationAndExpiryReleaseAllProductsOnce() throws Exception {
        Product first = createProduct("4.00", "ACTIVE", 8, 1);
        Product second = createProduct("5.00", "ACTIVE", 8, 2);
        var session = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(session, item(second, 2) + "," + item(first, 3));
        long id = orderId(reference);
        makeOverdue(id);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var cancellation = workers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return cancel(session, reference).andReturn().getResponse().getStatus();
            });
            var expiry = workers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                releases.expireIfDue(id);
                return null;
            });
            boolean bothReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(bothReady);
            assertEquals(200, cancellation.get(20, TimeUnit.SECONDS));
            expiry.get(20, TimeUnit.SECONDS);
        }
        assertEquals("CANCELLED", orderStatus(reference));
        assertEquals(2L, countReservations(reference, "RELEASED"));
        assertEquals(2L, countForProducts("""
                SELECT count(*) FROM stock_movement WHERE product_id IN (:ids) AND movement_type = 'RELEASE'
                """));
        assertEquals(1, reservedStock(first));
        assertEquals(2, reservedStock(second));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void cancellationAndCreationCanShareTheSameProductLocks() throws Exception {
        Product first = createProduct("4.00", "ACTIVE", 6, 1);
        Product second = createProduct("5.00", "ACTIVE", 6, 1);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        AppUser otherClient = createUser(SharedUserRole.CLIENT);
        var otherSession = signIn(otherClient);
        String reference = createOrder(owner, item(second, 2) + "," + item(first, 2));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var creation = workers.submit(() -> submitWhenReady(ready, start, otherSession,
                    request(item(first, 3) + "," + item(second, 3))));
            var cancellation = workers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return cancel(owner, reference).andReturn().getResponse().getStatus();
            });
            boolean bothReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(bothReady);
            assertEquals(201, creation.get(20, TimeUnit.SECONDS));
            assertEquals(200, cancellation.get(20, TimeUnit.SECONDS));
        }
        assertEquals("CANCELLED", orderStatus(reference));
        assertEquals(2L, countReservations(reference, "RELEASED"));
        assertEquals(1L, countOrders(otherClient));
        assertEquals(4, reservedStock(first));
        assertEquals(4, reservedStock(second));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackEarlierReleasesWhenAnotherProductHasInconsistentStock() throws Exception {
        Product first = createProduct("4.00", "ACTIVE", 8, 0);
        Product second = createProduct("5.00", "ACTIVE", 8, 0);
        var session = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(session, item(first, 3) + "," + item(second, 2));
        setReservedStock(second, 1);

        cancel(session, reference).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SHARED_UNEXPECTED_ERROR"));
        assertEquals("AWAITING_PAYMENT", orderStatus(reference));
        assertEquals(2L, countReservations(reference, "HELD"));
        assertEquals(0L, countReservations(reference, "RELEASED"));
        assertEquals(0L, countForProducts("""
                SELECT count(*) FROM stock_movement WHERE product_id IN (:ids) AND movement_type = 'RELEASE'
                """));
        assertEquals(3, reservedStock(first));
        assertEquals(1, reservedStock(second));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void schedulerMakesProgressAfterFailureAndRetriesPersistedOrdersAfterRestart() throws Exception {
        Product first = createProduct("4.00", "ACTIVE", 8, 0);
        Product second = createProduct("5.00", "ACTIVE", 8, 0);
        var session = signIn(createUser(SharedUserRole.CLIENT));
        String retry = createOrder(session, item(first, 3));
        String healthy = createOrder(session, item(second, 2));
        makeOverdue(orderId(retry));
        makeOverdue(orderId(healthy));
        setReservedStock(first, 2);

        try (var scheduler = startExpiryScheduler()) {
            await().atMost(Duration.ofSeconds(10)).until(() -> orderStatus(healthy).equals("CANCELLED"));
        }
        assertEquals("AWAITING_PAYMENT", orderStatus(retry));
        assertEquals(1L, countReservations(retry, "HELD"));
        setReservedStock(first, 3);
        try (var restartedScheduler = startExpiryScheduler()) {
            await().atMost(Duration.ofSeconds(10)).until(() -> orderStatus(retry).equals("CANCELLED"));
        }
        assertEquals(1L, countReservations(retry, "RELEASED"));
        assertEquals(0, reservedStock(first));
        assertEquals(0, reservedStock(second));
    }

    @AfterEach
    void removeCommittedTestFixtures() {
        // Most tests roll back automatically. Concurrent requests and the rollback test use independent transactions.
        if (TestTransaction.isActive()) {
            return;
        }
        if (!fixtureProducts.isEmpty()) {
            database.sql("DELETE FROM stock_movement WHERE product_id IN (:ids)")
                    .param("ids", fixtureProducts).update();
        }
        if (!fixtureUsers.isEmpty()) {
            database.sql("""
                    DELETE FROM stock_reservation WHERE order_item_id IN (
                        SELECT oi.order_item_id FROM order_item oi JOIN customer_order co USING (order_id)
                        WHERE co.client_user_id IN (:ids))
                    """).param("ids", fixtureUsers).update();
            database.sql("""
                    DELETE FROM order_delivery_detail WHERE order_id IN (
                        SELECT order_id FROM customer_order WHERE client_user_id IN (:ids))
                    """).param("ids", fixtureUsers).update();
            database.sql("""
                    DELETE FROM order_item WHERE order_id IN (
                        SELECT order_id FROM customer_order WHERE client_user_id IN (:ids))
                    """).param("ids", fixtureUsers).update();
            database.sql("DELETE FROM customer_order WHERE client_user_id IN (:ids)").param("ids", fixtureUsers).update();
            database.sql("DELETE FROM app_user WHERE user_id IN (:ids)").param("ids", fixtureUsers).update();
        }
        if (!fixtureProducts.isEmpty()) {
            database.sql("DELETE FROM inventory WHERE product_id IN (:ids)").param("ids", fixtureProducts).update();
            database.sql("DELETE FROM product WHERE product_id IN (:ids)").param("ids", fixtureProducts).update();
        }
    }

    private AppUser createUser(SharedUserRole role) {
        AppUser user = new AppUser();
        user.username = "order-" + UUID.randomUUID();
        user.passwordHash = passwordEncoder.encode(PASSWORD);
        user.accountStatus = SharedAccountStatus.ACTIVE;
        user.role = role;
        user = users.saveAndFlush(user);
        fixtureUsers.add(user.userID);
        return user;
    }

    private Product createProduct(String unitPrice, String listingStatus, Integer physical, int reserved) {
        Product product = new Product();
        product.sku = UUID.randomUUID().toString();
        product.name = "Order test product";
        product.currentPrice = new BigDecimal(unitPrice);
        product.listingStatus = listingStatus;
        product = products.saveAndFlush(product);
        fixtureProducts.add(product.productId);
        if (physical != null) {
            Inventory balance = new Inventory();
            balance.productId = product.productId;
            balance.physicalStock = physical;
            balance.reservedStock = reserved;
            inventory.saveAndFlush(balance);
        }
        return product;
    }

    private long countOrders(AppUser owner) {
        return database.sql("SELECT count(*) FROM customer_order WHERE client_user_id = :id")
                .param("id", owner.userID).query(Long.class).single();
    }

    private long countForProducts(String sql) {
        return database.sql(sql).param("ids", fixtureProducts).query(Long.class).single();
    }

    private ResultActions cancel(OrderSession session, String reference) throws Exception {
        String path = ORDERS + "/" + reference + "/cancel";
        return mvc.perform(post(path).servletPath(path).session(session.session())
                .header(session.csrfHeader(), session.csrfToken()));
    }

    private String createOrder(OrderSession session, String items) throws Exception {
        var response = submit(session, request(items)).andExpect(status().isCreated()).andReturn();
        return JsonPath.read(response.getResponse().getContentAsString(), "$.orderReference");
    }

    private long orderId(String reference) {
        return database.sql("SELECT order_id FROM customer_order WHERE order_reference = :ref")
                .param("ref", reference).query(Long.class).single();
    }

    private String orderStatus(String reference) {
        return database.sql("SELECT order_status FROM customer_order WHERE order_reference = :ref")
                .param("ref", reference).query(String.class).single();
    }

    private void makeOverdue(long id) {
        database.sql("""
                UPDATE customer_order SET created_at = NOW() - INTERVAL '25 hours',
                    payment_deadline = NOW() - INTERVAL '1 hour' WHERE order_id = :id
                """).param("id", id).update();
    }

    private void setReservedStock(Product product, int reserved) {
        database.sql("UPDATE inventory SET reserved_stock = :reserved WHERE product_id = :id")
                .param("reserved", reserved).param("id", product.productId).update();
    }

    private long countReservations(String reference, String status) {
        return database.sql("""
                SELECT count(*) FROM stock_reservation sr JOIN order_item item USING (order_item_id)
                JOIN customer_order co USING (order_id)
                WHERE co.order_reference = :ref AND sr.status = :status
                    AND (sr.status <> 'RELEASED' OR sr.released_at >= sr.reserved_at)
                """).param("ref", reference).param("status", status).query(Long.class).single();
    }

    private AnnotationConfigApplicationContext startExpiryScheduler() {
        var context = new AnnotationConfigApplicationContext();
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("expiry-test",
                Map.of("lms.client.orders.expiry.enabled", "true", "lms.client.orders.expiry.interval", "PT1H")));
        context.registerBean(CustomerOrderReleaseRepository.class, () -> releaseRows);
        context.registerBean(ClientOrderReleaseService.class, () -> releases);
        context.register(ClientOrderExpiryJob.class);
        context.refresh();
        return context;
    }

    private int reservedStock(Product product) {
        return database.sql("SELECT reserved_stock FROM inventory WHERE product_id = :id")
                .param("id", product.productId).query(Integer.class).single();
    }

    private int submitWhenReady(CountDownLatch ready, CountDownLatch start, OrderSession session, String body)
            throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return submit(session, body).andReturn().getResponse().getStatus();
    }

    private ResultActions submit(OrderSession session, String body) throws Exception {
        return mvc.perform(post(ORDERS).servletPath(ORDERS).session(session.session())
                .header(session.csrfHeader(), session.csrfToken()).contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private OrderSession signIn(AppUser user) throws Exception {
        OrderSession guest = sessionInfo(null);
        String login = "/api/shared/login";
        var response = mvc.perform(post(login).servletPath(login).session(guest.session())
                        .header(guest.csrfHeader(), guest.csrfToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sharedLoginUserUsername":"%s","sharedLoginUserPassword":"%s"}
                                """.formatted(user.username, PASSWORD)))
                .andExpect(status().isOk()).andReturn();
        return sessionInfo(assertInstanceOf(MockHttpSession.class, response.getRequest().getSession(false)));
    }

    private OrderSession sessionInfo(MockHttpSession session) throws Exception {
        String path = "/api/shared/session";
        var request = get(path).servletPath(path);
        if (session != null) {
            request.session(session);
        }
        var result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        var data = JsonPath.parse(result.getResponse().getContentAsString());
        String header = data.read("$.sharedViewSessionSecurityTokenHeaderName");
        String token = data.read("$.sharedViewSessionSecurityToken");
        assertNotNull(token);
        return new OrderSession(assertInstanceOf(MockHttpSession.class, result.getRequest().getSession(false)),
                header, token);
    }

    private String item(Product product, int quantity) {
        return "{\"productId\":%d,\"quantity\":%d}".formatted(product.productId, quantity);
    }

    private String request(String items) {
        return request(items, "");
    }

    private String request(String items, String extraFields) {
        return """
                {"items":[%s],"recipientName":"Test Recipient","contactPhone":"+852 5555 0100",
                "deliveryAddress":"Flat A, 12 Nathan Road, Kowloon, Hong Kong"%s}
                """.formatted(items, extraFields);
    }

    private record OrderSession(MockHttpSession session, String csrfHeader, String csrfToken) {
    }

    private record SavedOrder(long orderId, long clientUserId, Instant createdAt, Instant deadline,
            BigDecimal total, String orderStatus) {
    }
}
