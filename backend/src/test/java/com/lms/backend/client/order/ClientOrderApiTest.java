package com.lms.backend.client.order;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.JsonPath;
import com.lms.backend.BackendApplication;
import com.lms.backend.client.order.CheckoutProviderVerification.Outcome;
import com.lms.backend.client.order.CheckoutProviderVerification.VerifiedCheckout;
import com.lms.backend.shared.database.AppUser;
import com.lms.backend.shared.database.AppUserRepository;
import com.lms.backend.shared.database.ClientOrderIdempotencyRepository;
import com.lms.backend.shared.database.CustomerOrderReleaseRepository;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.database.CustomerOrderWriteRepository;
import com.lms.backend.shared.database.Inventory;
import com.lms.backend.shared.database.InventoryRepository;
import com.lms.backend.shared.database.Product;
import com.lms.backend.shared.database.ProductRepository;
import com.lms.backend.shared.value.SharedListingStatus;
import com.lms.backend.shared.log.SharedSecurityLogWriter;
import com.lms.backend.shared.login.SharedCurrentUser;
import com.lms.backend.shared.notification.ClientNotificationWriter;
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

    @Autowired
    private ClientCheckoutService checkouts;

    @MockitoSpyBean
    private ClientNotificationWriter notificationWriter;

    // Keep fixtures in the test transaction instead of committing separate audit rows.
    @MockitoBean
    private SharedSecurityLogWriter securityLogWriter;

    @MockitoSpyBean
    private CustomerOrderWriteRepository orderWrites;

    @MockitoSpyBean
    private ClientOrderIdempotencyRepository idempotency;

    private final List<Long> fixtureUsers = new ArrayList<>();
    private final List<Long> fixtureProducts = new ArrayList<>();

    @Test
    void createsAStockNeutralOrderForTheSignedInClient() throws Exception {
        AppUser owner = createUser(SharedUserRole.CLIENT);
        AppUser otherClient = createUser(SharedUserRole.CLIENT);
        Product product = createProduct("19.90", "ACTIVE", 8, 2);
        String items = """
                {"productId":%d,"quantity":3,"unitPrice":0.01}
                """.formatted(product.productID);

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
                SELECT count(*) FROM order_item oi
                WHERE oi.order_id = :id AND oi.product_id = :productId AND oi.quantity = 3
                  AND oi.unit_price = 19.90 AND oi.picked_quantity = 0
                """).param("id", saved.orderId()).param("productId", product.productID).query(Long.class).single());
        assertEquals(0L, database.sql("""
                SELECT count(*) FROM stock_movement WHERE product_id = :productId AND actor_user_id = :actorId
                  AND quantity_delta = 0 AND reserved_delta = 3 AND movement_type = 'RESERVE' AND reason = :reference
                """).param("productId", product.productID).param("actorId", owner.userID)
                .param("reference", reference).query(Long.class).single());
        assertEquals(0L, countReservations(reference, "HELD"));
        assertEquals(2, reservedStock(product));
        assertEquals(8, database.sql("SELECT physical_stock FROM inventory WHERE product_id = :id")
                .param("id", product.productID).query(Integer.class).single());
        assertEquals(0L, database.sql("SELECT count(*) FROM payment WHERE order_id = :id")
                .param("id", saved.orderId()).query(Long.class).single());
    }

    @Test
    void createsOneItemPerProductUsingDatabasePricesWithoutReserving() throws Exception {
        AppUser owner = createUser(SharedUserRole.CLIENT);
        Product first = createProduct("10.10", "ACTIVE", 6, 0);
        Product second = createProduct("2.25", "ACTIVE", 6, 0);

        submit(signIn(owner), request(item(second, 3) + "," + item(first, 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.totalAmount").value(26.95));
        assertEquals(List.of(new BigDecimal("10.10"), new BigDecimal("2.25")), database.sql("""
                SELECT oi.unit_price FROM order_item oi
                JOIN customer_order co USING (order_id)
                WHERE co.client_user_id = :clientId
                ORDER BY oi.product_id
                """).param("clientId", owner.userID).query(BigDecimal.class).list());
        assertEquals(0, reservedStock(first));
        assertEquals(0, reservedStock(second));
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
    void rollsBackCheckoutAndStockWhenALaterDatabaseWriteFails() throws Exception {
        Product first = createProduct("4.00", "ACTIVE", 8, 1);
        Product second = createProduct("5.00", "ACTIVE", 5, 0);
        AppUser owner = createUser(SharedUserRole.CLIENT);
        var session = signIn(owner);
        String reference = createOrder(session, item(second, 2) + "," + item(first, 3));
        // The second movement violates the database constraint after the checkout and holds have been written.
        doAnswer(call -> {
            database.sql("""
                    INSERT INTO stock_movement (product_id, movement_type, quantity_delta, reserved_delta)
                    VALUES (:productId, 'RESERVE', 0, 0)
                    """).param("productId", second.productID).update();
            return null;
        }).when(orderWrites).insertReservationMovement(eq(second.productID.longValue()), eq(owner.userID.longValue()),
                eq(2), anyString());

        var result = checkout(session, reference)
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$.code").value("SHARED_UNEXPECTED_ERROR"))
                .andExpect(jsonPath("$.message")
                        .value(matchesPattern("Something went wrong\\. Reference: [0-9a-f-]{36}")))
                .andReturn();
        assertInstanceOf(DataIntegrityViolationException.class, result.getResolvedException());
        assertEquals(1L, countOrders(owner));
        assertEquals(0L, countCheckouts(reference));
        assertEquals(0L, countReservations(reference, "HELD"));
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
        String firstOrder = createOrder(firstSession, item(first, 3) + "," + item(second, 1));
        String secondOrder = createOrder(secondSession, item(second, 1) + "," + item(first, 3));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var left = workers.submit(() -> checkoutWhenReady(ready, start, firstSession, firstOrder));
            var right = workers.submit(() -> checkoutWhenReady(ready, start, secondSession, secondOrder));
            boolean bothReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(bothReady);
            assertEquals(List.of(200, 409),
                    Stream.of(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS)).sorted().toList());
        }
        assertEquals(2L, countOrders(firstClient) + countOrders(secondClient));
        assertEquals(1L, countCheckouts(firstOrder) + countCheckouts(secondOrder));
        assertEquals(2L, countForProducts("SELECT count(*) FROM stock_movement WHERE product_id IN (:ids)"));
        assertEquals(3, reservedStock(first));
        assertEquals(1, reservedStock(second));
    }

    @Test
    void ownerCanCancelTwiceWithOnlyOneRelease() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 1);
        AppUser owner = createUser(SharedUserRole.CLIENT);
        var session = signIn(owner);
        String reference = createReservedOrder(session, item(product, 3));

        cancel(session, reference).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderReference").value(reference))
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));
        cancel(session, reference).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));

        assertEquals(1, reservedStock(product));
        assertEquals("CANCELLED", orderStatus(reference));
        assertEquals(1L, countReservations(reference, "RELEASED"));
        assertTrue(entryClosedWhilePending(reference));
        checkout(session, reference).andExpect(status().isConflict());
        assertEquals(1L, database.sql("""
                SELECT count(*) FROM stock_movement WHERE product_id = :productId
                    AND movement_type = 'RELEASE' AND reserved_delta = -3 AND quantity_delta = 0
                    AND actor_user_id = :clientId AND reason = :reason
                """).param("productId", product.productID).param("clientId", owner.userID)
                .param("reason", "CLIENT_CANCELLED: " + reference).query(Long.class).single());
    }

    @Test
    void rejectsOtherClientsAnonymousUsersAndAdminsOnCancellation() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        String reference = createReservedOrder(signIn(createUser(SharedUserRole.CLIENT)), item(product, 3));
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
    @ValueSource(strings = {"picking", "consumed"})
    void protectsOrdersWhereFulfillmentHasStarted(String protectedBy) throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createReservedOrder(owner, item(product, 3));
        long id = orderId(reference);
        String sql = switch (protectedBy) {
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
        String reference = createReservedOrder(session, item(product, 3));
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
        assertTrue(entryClosedWhilePending(reference));
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
        String reference = createReservedOrder(session, item(second, 2) + "," + item(first, 3));
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
    void cancellationAndCheckoutCanShareTheSameProductLocks() throws Exception {
        Product first = createProduct("4.00", "ACTIVE", 6, 1);
        Product second = createProduct("5.00", "ACTIVE", 6, 1);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        AppUser otherClient = createUser(SharedUserRole.CLIENT);
        var otherSession = signIn(otherClient);
        String reference = createReservedOrder(owner, item(second, 2) + "," + item(first, 2));
        String otherOrder = createOrder(otherSession, item(first, 3) + "," + item(second, 3));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var newCheckout = workers.submit(() -> checkoutWhenReady(ready, start, otherSession, otherOrder));
            var cancellation = workers.submit(() -> {
                ready.countDown();
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return cancel(owner, reference).andReturn().getResponse().getStatus();
            });
            boolean bothReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertTrue(bothReady);
            assertEquals(200, newCheckout.get(20, TimeUnit.SECONDS));
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
        String reference = createReservedOrder(session, item(first, 3) + "," + item(second, 2));
        setReservedStock(second, 1);

        cancel(session, reference).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SHARED_UNEXPECTED_ERROR"));
        assertEquals("AWAITING_PAYMENT", orderStatus(reference));
        assertFalse(entryClosedWhilePending(reference));
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
        String retry = createReservedOrder(session, item(first, 3));
        String healthy = createReservedOrder(session, item(second, 2));
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

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void replaysOriginalCreatedResponseForNormalizedRequestWithoutReadingCurrentProducts() throws Exception {
        Product first = createProduct("4.00", "ACTIVE", 8, 0);
        Product second = createProduct("5.25", "ACTIVE", 8, 0);
        AppUser owner = createUser(SharedUserRole.CLIENT);
        var session = signIn(owner);
        String key = "normalized-retry";
        String originalBody = request(item(second, 2) + "," + item(first, 1));
        String response = postWithKey(session, key, originalBody).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String orderReference = JsonPath.read(response, "$.orderReference");
        assertEquals(1L, database.sql("""
                SELECT count(*) FROM client_order_idempotency
                WHERE client_user_id = :clientId AND idempotency_key = :key AND order_id = :orderId
                    AND octet_length(request_fingerprint) = 32
                """).param("clientId", owner.userID).param("key", key).param("orderId", orderId(orderReference))
                .query(Long.class).single());

        database.sql("UPDATE product SET listing_status = 'INACTIVE', current_price = 50 WHERE product_id IN (:ids)")
                .param("ids", fixtureProducts).update();
        database.sql("UPDATE inventory SET physical_stock = 0 WHERE product_id IN (:ids)")
                .param("ids", fixtureProducts).update();
        String normalizedRetry = request(item(first, 1) + "," + item(second, 2))
                .replace("Test Recipient", "  Test Recipient  ");
        String replay = postWithKey(session, key, normalizedRetry).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertEquals(response, replay);
        assertEquals(1L, countOrders(owner));
        assertEquals(0, reservedStock(first));
        assertEquals(0, reservedStock(second));
    }

    @ParameterizedTest
    @ValueSource(strings = {"items[0].quantity", "items[0].productId", "recipientName", "contactPhone",
            "deliveryAddress"})
    void rejectsKeyReuseWhenAnyNormalizedFieldChanges(String field) throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        AppUser owner = createUser(SharedUserRole.CLIENT);
        var session = signIn(owner);
        String body = request(item(product, 1));
        postWithKey(session, "original-order", body).andExpect(status().isCreated());
        Object different = switch (field) {
            case "items[0].quantity" -> 2;
            case "items[0].productId" -> Long.MAX_VALUE;
            default -> "Changed delivery detail";
        };
        String modified = JsonPath.parse(body).set("$." + field, different).jsonString();
        postWithKey(session, "original-order", modified).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_IDEMPOTENCY_KEY_CONFLICT"));
        assertEquals(1L, countOrders(owner));
        assertEquals(0, reservedStock(product));
    }

    @Test
    void scopesTheSameKeyToDifferentClients() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        AppUser first = createUser(SharedUserRole.CLIENT);
        AppUser second = createUser(SharedUserRole.CLIENT);
        String body = request(item(product, 2));
        String left = postWithKey(signIn(first), "s.replace-is-independent_~1", body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String right = postWithKey(signIn(second), "s.replace-is-independent_~1", body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertNotEquals(JsonPath.<String>read(left, "$.orderReference"), JsonPath.<String>read(right, "$.orderReference"));
        assertEquals(1L, countOrders(first));
        assertEquals(1L, countOrders(second));
        assertEquals(0, reservedStock(product));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "two keys", "a,b", "contains/slash", "é"})
    void rejectsMissingOrInvalidKeys(String key) throws Exception {
        assertInvalidKey(key);
    }

    @Test
    void rejectsMultipleIdempotencyKeyHeaders() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        AppUser owner = createUser(SharedUserRole.CLIENT);
        var session = signIn(owner);
        mvc.perform(post(ORDERS).servletPath(ORDERS).session(session.session())
                        .header(session.csrfHeader(), session.csrfToken())
                        .header("Idempotency-Key", "first-key", "second-key")
                        .contentType(MediaType.APPLICATION_JSON).content(request(item(product, 1))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_IDEMPOTENCY_KEY_INVALID"));
        assertEquals(0L, countOrders(owner));
        assertEquals(0, reservedStock(product));
    }

    @Test
    void rejectsKeysLongerThan128Characters() throws Exception {
        assertInvalidKey("a".repeat(129));
    }

    @Test
    void acceptsA128CharacterKey() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        postWithKey(signIn(createUser(SharedUserRole.CLIENT)), "a".repeat(128), request(item(product, 1)))
                .andExpect(status().isCreated());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void simultaneousCreateRequestsWaitForTheSameKeyWithoutReserving() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 3, 0);
        AppUser owner = createUser(SharedUserRole.CLIENT);
        var firstSession = signIn(owner);
        var retrySession = signIn(owner);
        String body = request(item(product, 3));
        String key = "concurrent-request";
        CountDownLatch rowInserted = new CountDownLatch(1);
        CountDownLatch canCommit = new CountDownLatch(1);
        doAnswer(call -> {
            call.callRealMethod();
            rowInserted.countDown();
            assertTrue(canCommit.await(15, TimeUnit.SECONDS));
            return null;
        }).when(idempotency).insertCompleted(eq(owner.userID.longValue()), eq(key), any(byte[].class),
                anyLong(), anyString());

        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> postWithKey(firstSession, key, body).andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString());
            try {
                assertTrue(rowInserted.await(10, TimeUnit.SECONDS));
                var retry = workers.submit(() -> postWithKey(retrySession, key, body).andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString());
                await().atMost(Duration.ofSeconds(10)).until(() -> database.sql("""
                        SELECT EXISTS (SELECT 1 FROM pg_locks WHERE locktype = 'advisory' AND NOT granted
                          AND database = (SELECT oid FROM pg_database WHERE datname = current_database()))
                        """).query(Boolean.class).single());
                canCommit.countDown();
                assertEquals(first.get(15, TimeUnit.SECONDS), retry.get(15, TimeUnit.SECONDS));
            } finally {
                canCommit.countDown();
            }
        }
        assertEquals(1L, countOrders(owner));
        assertEquals(0L, countForProducts("SELECT count(*) FROM stock_movement WHERE product_id IN (:ids)"));
        assertEquals(0, reservedStock(product));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedTransactionRollsBackTheInsertedKeyAndSameKeyCanSucceed() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        AppUser owner = createUser(SharedUserRole.CLIENT);
        String body = request(item(product, 2));
        String key = "retry-after-rollback";
        var session = signIn(owner);
        doAnswer(call -> {
            call.callRealMethod();
            throw new DataIntegrityViolationException("Injected failure after saving the replay record.");
        }).doCallRealMethod().when(idempotency).insertCompleted(eq(owner.userID.longValue()), eq(key),
                any(byte[].class), anyLong(), anyString());

        postWithKey(session, key, body).andExpect(status().isInternalServerError());
        assertEquals(0L, countOrders(owner));
        assertTrue(idempotency.findCompleted(owner.userID, key).isEmpty());
        assertEquals(0, reservedStock(product));
        postWithKey(session, key, body).andExpect(status().isCreated());
        assertEquals(1L, countOrders(owner));
        assertEquals(0, reservedStock(product));
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancelled", "expired"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void replaysTheOriginalReceiptAfterAnOrderCloses(String closure) throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        AppUser owner = createUser(SharedUserRole.CLIENT);
        var session = signIn(owner);
        String body = request(item(product, 2));
        String original = postWithKey(session, "closed-order", body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reference = JsonPath.read(original, "$.orderReference");
        if (closure.equals("cancelled")) {
            cancel(session, reference).andExpect(status().isOk());
        } else {
            long id = orderId(reference);
            makeOverdue(id);
            releases.expireIfDue(id);
        }
        assertEquals("CANCELLED", orderStatus(reference));
        String replay = postWithKey(session, "closed-order", body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderStatus").value("AWAITING_PAYMENT"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(original, replay);
        assertEquals(1L, countOrders(owner));
        assertEquals(0, reservedStock(product));
        assertEquals(0L, countReservations(reference, "RELEASED"));
        assertEquals(0L, countCheckouts(reference));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void replaysAcrossApplicationRestarts() {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        AppUser owner = createUser(SharedUserRole.CLIENT);
        var payload = new ClientCreateOrderRequest(
                List.of(new ClientCreateOrderRequest.Item(BigDecimal.valueOf(product.productID), BigDecimal.ONE)),
                "Test Recipient", "+852 5555 0100", "Flat A, 12 Nathan Road, Kowloon, Hong Kong");
        var context = SecurityContextHolder.createEmptyContext();
        var principal = new SharedCurrentUser(owner.userID, owner.username, owner.role);
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        SecurityContextHolder.setContext(context);
        try {
            ClientCreateOrderResponse first;
            try (var app = startCreateOrderApplication()) {
                first = app.getBean(ClientOrderService.class).createOrder("restart-retry", payload);
            }
            try (var restarted = startCreateOrderApplication()) {
                assertEquals(first, restarted.getBean(ClientOrderService.class).createOrder("restart-retry", payload));
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertEquals(1L, countOrders(owner));
        assertEquals(0, reservedStock(product));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void checkoutUsesTheSavedAmountAndCreatesOnePaymentLinkedHold() throws Exception {
        Product product = createProduct("19.90", "ACTIVE", 8, 1);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(owner, item(product, 3));
        database.sql("UPDATE product SET current_price = 40 WHERE product_id = :id")
                .param("id", product.productID).update();

        var first = checkout(owner, reference).andExpect(status().isOk())
                .andExpect(jsonPath("$.amountHkd").value(59.70))
                .andExpect(jsonPath("$.providerCheckoutAvailable").value(false)).andReturn();
        Number returnedId = JsonPath.read(first.getResponse().getContentAsString(), "$.checkoutId");
        long checkoutId = returnedId.longValue();
        assertEquals(checkoutId, checkoutId(owner, reference));
        assertEquals(1L, countCheckouts(reference));
        assertEquals(1L, database.sql("""
                SELECT count(*) FROM stock_reservation
                WHERE payment_id = :id AND status = 'HELD' AND quantity = 3
                """).param("id", checkoutId).query(Long.class).single());
        assertEquals(4, reservedStock(product));
        assertEquals("AWAITING_PAYMENT", orderStatus(reference));
        assertTrue(database.sql("""
                SELECT checkout_expires_at = created_at + INTERVAL '30 minutes'
                FROM payment WHERE payment_id = :id
                """).param("id", checkoutId).query(Boolean.class).single());
    }

    @Test
    void checkoutRequiresTheOwnerAndCsrf() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(owner, item(product, 3));
        String path = ORDERS + "/" + reference + "/checkout";

        checkout(signIn(createUser(SharedUserRole.CLIENT)), reference).andExpect(status().isNotFound());
        checkout(signIn(createUser(SharedUserRole.ADMIN)), reference).andExpect(status().isForbidden());
        mvc.perform(post(path).servletPath(path)).andExpect(status().isUnauthorized());
        mvc.perform(post(path).servletPath(path).session(owner.session()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SHARED_SECURITY_TOKEN_INVALID"));
        assertEquals(0L, countCheckouts(reference));
        assertEquals(0, reservedStock(product));
    }

    @Test
    void browserCannotSubmitAPaymentSuccessResult() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createReservedOrder(owner, item(product, 3));
        String path = ORDERS + "/" + reference + "/payment/success";

        mvc.perform(post(path).servletPath(path).session(owner.session())
                .header(owner.csrfHeader(), owner.csrfToken())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CAPTURED\",\"amountHkd\":12.00}"))
                .andExpect(status().isNotFound());
        assertEquals("AWAITING_PAYMENT", orderStatus(reference));
        assertEquals(1L, countReservations(reference, "HELD"));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentRequestsForTheSameOrderReuseOneCheckout() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 3, 0);
        AppUser client = createUser(SharedUserRole.CLIENT);
        var leftSession = signIn(client);
        var rightSession = signIn(client);
        String reference = createOrder(leftSession, item(product, 3));
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var left = workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return checkoutId(leftSession, reference);
            });
            var right = workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                return checkoutId(rightSession, reference);
            });
            start.countDown();
            assertEquals(left.get(15, TimeUnit.SECONDS), right.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1L, countCheckouts(reference));
        assertEquals(1L, countReservations(reference, "HELD"));
        assertEquals(3, reservedStock(product));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void declineAndLocalTimeoutPreserveHoldsUntilUnpaidClosure() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(owner, item(product, 3));
        long first = checkoutId(owner, reference);

        assertEquals(0L, notificationCount(reference));
        reconcile(first, Outcome.CARD_DECLINED);
        assertEquals(first, checkoutId(owner, reference));
        assertEquals(3, reservedStock(product));
        assertEquals(0L, notificationCount(reference));

        makeCheckoutWindowElapsed(first);
        releases.expireIfDue(orderId(reference));
        checkout(owner, reference).andExpect(status().isConflict());
        assertEquals(3, reservedStock(product));
        assertEquals("AWAITING_PAYMENT", orderStatus(reference));

        reconcile(first, Outcome.ENDED_UNPAID);
        reconcile(first, Outcome.ENDED_UNPAID);
        assertEquals(0, reservedStock(product));
        assertEquals(1L, countReservations(reference, "RELEASED"));
        long second = checkoutId(owner, reference);
        assertNotEquals(first, second);
        reconcile(first, Outcome.ENDED_UNPAID);
        assertEquals(3, reservedStock(product));
        assertEquals(2L, countCheckouts(reference));
        assertEquals(1L, countReservations(reference, "HELD"));
        assertEquals(1L, countReservations(reference, "RELEASED"));
        assertEquals(0L, notificationCount(reference));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lateCaptureForAnEndedAttemptLeavesTheNextCheckoutIntact() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(owner, item(product, 3));
        long first = checkoutId(owner, reference);
        reconcile(first, Outcome.ENDED_UNPAID);
        long second = checkoutId(owner, reference);

        capture(first, "sandbox-old-" + first, "12.00");
        assertRequestedRefund(first);
        assertEquals("AWAITING_PAYMENT", orderStatus(reference));
        assertEquals(second, checkoutId(owner, reference));
        assertEquals(3, reservedStock(product));
        assertEquals(1L, countReservations(reference, "HELD"));

        capture(second, "sandbox-current-" + second, "12.00");
        assertEquals("READY_FOR_PICKING", orderStatus(reference));
        assertEquals(3, reservedStock(product));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void checkoutIsCappedByTheOriginalOrderDeadline() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(owner, item(product, 3));
        long id = orderId(reference);
        database.sql("""
                UPDATE customer_order SET created_at = NOW() - INTERVAL '23 hours 50 minutes',
                    payment_deadline = NOW() + INTERVAL '10 minutes' WHERE order_id = :id
                """).param("id", id).update();
        Instant deadline = orders.findById(id).orElseThrow().paymentDeadline;
        var checkout = checkout(owner, reference).andExpect(status().isOk()).andReturn();
        String expiresAt = JsonPath.read(checkout.getResponse().getContentAsString(), "$.checkoutExpiresAt");
        assertEquals(deadline, Instant.parse(expiresAt));
        assertEquals(deadline, orders.findById(id).orElseThrow().paymentDeadline);
    }

    @ParameterizedTest
    @ValueSource(strings = {"cancelled", "expired", "before-expiry-sweep"})
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void lateCaptureCreatesOneFullRefundObligationWithoutRestoringStock(String closure) throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(owner, item(product, 3));
        long checkoutId = checkoutId(owner, reference);
        long orderId = orderId(reference);
        switch (closure) {
            case "cancelled" -> cancel(owner, reference).andExpect(status().isOk());
            case "expired" -> {
                makeOverdue(orderId);
                releases.expireIfDue(orderId);
            }
            case "before-expiry-sweep" -> makeOverdue(orderId);
            default -> throw new AssertionError(closure);
        }

        capture(checkoutId, "sandbox-late-" + checkoutId, "12.00");
        if (closure.equals("before-expiry-sweep")) {
            assertEquals("AWAITING_PAYMENT", orderStatus(reference));
            releases.expireIfDue(orderId);
        }
        capture(checkoutId, "sandbox-late-" + checkoutId, "12.00");
        reconcile(checkoutId, Outcome.ENDED_UNPAID);
        assertEquals("CANCELLED", orderStatus(reference));
        assertEquals(0, reservedStock(product));
        assertEquals(1L, countReservations(reference, "RELEASED"));
        assertRequestedRefund(checkoutId);
        assertEquals(0L, notificationCount(reference));
        assertTrue(database.sql("""
                SELECT payment_status = 'REFUND_REQUIRED' AND accepted_at IS NULL
                FROM payment WHERE payment_id = :id
                """).param("id", checkoutId).query(Boolean.class).single());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void captureMustMatchTheSavedOrderTotalEvenWhenItMatchesTheAttempt() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(owner, item(product, 3));
        long checkoutId = checkoutId(owner, reference);
        database.sql("UPDATE payment SET amount = 15.50 WHERE payment_id = :id")
                .param("id", checkoutId).update();

        capture(checkoutId, "sandbox-mismatch-" + checkoutId, "15.50");
        capture(checkoutId, "sandbox-mismatch-" + checkoutId, "15.50");
        assertEquals("AWAITING_PAYMENT", orderStatus(reference));
        assertRequestedRefund(checkoutId, "15.50");
        cancel(owner, reference).andExpect(status().isOk());
        assertEquals(0, reservedStock(product));
        assertRequestedRefund(checkoutId, "15.50");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void paidOwnerCanCancelBeforePickingEvenWhenATaskIsPending() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(owner, item(product, 3));
        long checkoutId = checkoutId(owner, reference);
        makeCheckoutWindowElapsed(checkoutId);
        capture(checkoutId, "sandbox-paid-" + checkoutId, "12.00");
        assertEquals("READY_FOR_PICKING", orderStatus(reference));
        assertEquals(1L, notificationCount(reference));
        assertEquals(3, reservedStock(product));
        assertEquals(0L, database.sql("SELECT count(*) FROM picking_task WHERE order_id = :id")
                .param("id", orderId(reference)).query(Long.class).single());
        database.sql("INSERT INTO picking_task (order_id) VALUES (:id)").param("id", orderId(reference)).update();

        cancel(owner, reference).andExpect(status().isOk());
        cancel(owner, reference).andExpect(status().isOk());
        capture(checkoutId, "sandbox-paid-" + checkoutId, "12.00");
        assertEquals("CANCELLED", orderStatus(reference));
        assertEquals(1L, notificationCount(reference));
        assertEquals(0, reservedStock(product));
        assertEquals(1L, countReservations(reference, "RELEASED"));
        assertRequestedRefund(checkoutId);
        assertTrue(database.sql("""
                SELECT payment_status = 'ACCEPTED' AND accepted_at IS NOT NULL
                FROM payment WHERE payment_id = :id
                """).param("id", checkoutId).query(Boolean.class).single());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentVerifiedCapturesCreateOnePrivatePaymentNotification() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        AppUser client = createUser(SharedUserRole.CLIENT);
        var owner = signIn(client);
        String reference = createOrder(owner, item(product, 3));
        long checkoutId = checkoutId(owner, reference);
        assertEquals(0L, notificationCount(reference));

        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var first = workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                capture(checkoutId, "sandbox-private-reference-" + checkoutId, "12.00");
                return null;
            });
            var duplicate = workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                capture(checkoutId, "sandbox-private-reference-" + checkoutId, "12.00");
                return null;
            });
            start.countDown();
            first.get(15, TimeUnit.SECONDS);
            duplicate.get(15, TimeUnit.SECONDS);
        }

        assertEquals("READY_FOR_PICKING", orderStatus(reference));
        assertEquals(1L, notificationCount(reference));
        assertEquals("Payment was confirmed for your order.", database.sql("""
                SELECT message FROM notification WHERE order_id = :orderId AND recipient_user_id = :clientId AND NOT is_read
                """).param("orderId", orderId(reference)).param("clientId", client.userID).query(String.class).single());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedNotificationDuringAcceptanceRollsBackAndVerifiedRetryCanSucceed() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(owner, item(product, 3));
        long checkoutId = checkoutId(owner, reference);
        String providerReference = "sandbox-notification-rollback-" + checkoutId;
        ClientNotificationWriter writerTarget = AopTestUtils.getUltimateTargetObject(notificationWriter);
        doAnswer(call -> {
            assertEquals(ClientNotificationWriter.Result.INSERTED, call.callRealMethod());
            return ClientNotificationWriter.Result.EVIDENCE_MISSING;
        }).doCallRealMethod().when(writerTarget).paymentConfirmed(orderId(reference), checkoutId);

        assertThrows(IllegalStateException.class, () -> capture(checkoutId, providerReference, "12.00"));
        assertEquals("AWAITING_PAYMENT", orderStatus(reference));
        assertEquals(0L, notificationCount(reference));
        assertTrue(database.sql("""
                SELECT payment_status = 'PENDING' AND accepted_at IS NULL
                FROM payment WHERE payment_id = :id
                """).param("id", checkoutId).query(Boolean.class).single());

        capture(checkoutId, providerReference, "12.00");
        assertEquals("READY_FOR_PICKING", orderStatus(reference));
        assertEquals(1L, notificationCount(reference));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentCaptureAndCancellationLeaveOneRefundAndRelease() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(owner, item(product, 3));
        long checkoutId = checkoutId(owner, reference);
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var payment = workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                capture(checkoutId, "sandbox-race-" + checkoutId, "12.00");
                return null;
            });
            var cancellation = workers.submit(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                cancel(owner, reference).andExpect(status().isOk());
                return null;
            });
            start.countDown();
            payment.get(15, TimeUnit.SECONDS);
            cancellation.get(15, TimeUnit.SECONDS);
        }
        assertEquals("CANCELLED", orderStatus(reference));
        assertEquals(0, reservedStock(product));
        assertEquals(1L, countReservations(reference, "RELEASED"));
        assertRequestedRefund(checkoutId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void expiryCanFinishWhileProviderVerificationIsInFlight() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(owner, item(product, 3));
        long checkoutId = checkoutId(owner, reference);
        long orderId = orderId(reference);
        var providerStarted = new CountDownLatch(1);
        var verified = new CompletableFuture<VerifiedCheckout>();
        try (var workers = Executors.newSingleThreadExecutor()) {
            var payment = workers.submit(() -> checkouts.reconcile(checkoutId, id -> {
                providerStarted.countDown();
                return verified.join();
            }));
            try {
                assertTrue(providerStarted.await(5, TimeUnit.SECONDS));
                makeOverdue(orderId);
                releases.expireIfDue(orderId);
                assertEquals("CANCELLED", orderStatus(reference));
                assertEquals(0, reservedStock(product));
            } finally {
                verified.complete(new VerifiedCheckout(Outcome.CAPTURED, "sandbox-expiry-" + checkoutId,
                        new BigDecimal("12.00"), "HKD"));
            }
            payment.get(15, TimeUnit.SECONDS);
        }
        assertEquals("CANCELLED", orderStatus(reference));
        assertEquals(0, reservedStock(product));
        assertEquals(1L, countReservations(reference, "RELEASED"));
        assertRequestedRefund(checkoutId);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void legacyUnlinkedHoldBlocksCheckoutForTheWholeOrderEvenWithEnoughStock() throws Exception {
        Product heldProduct = createProduct("4.00", "ACTIVE", 8, 0);
        Product otherProduct = createProduct("5.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(owner, item(heldProduct, 3) + "," + item(otherProduct, 2));
        long orderId = orderId(reference);
        long legacyHoldId = database.sql("""
                INSERT INTO stock_reservation (order_item_id, quantity)
                SELECT order_item_id, quantity FROM order_item WHERE order_id = :id AND product_id = :product
                RETURNING reservation_id
                """).param("id", orderId).param("product", heldProduct.productID).query(Long.class).single();
        setReservedStock(heldProduct, 3);

        for (int retry = 0; retry < 2; retry++) {
            checkout(owner, reference).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CLIENT_ORDER_CHECKOUT_NOT_AVAILABLE"))
                    .andExpect(jsonPath("$.message").value("Existing stock holds require reconciliation before checkout."));
        }

        assertEquals(0L, countCheckouts(reference));
        assertEquals(List.of(legacyHoldId), database.sql("""
                SELECT sr.reservation_id FROM stock_reservation sr JOIN order_item oi USING (order_item_id)
                WHERE oi.order_id = :id
                """).param("id", orderId).query(Long.class).list());
        assertTrue(database.sql("""
                SELECT status = 'HELD' AND payment_id IS NULL AND quantity = 3
                    AND released_at IS NULL AND consumed_at IS NULL
                FROM stock_reservation WHERE reservation_id = :id
                """).param("id", legacyHoldId).query(Boolean.class).single());
        assertEquals(3, reservedStock(heldProduct));
        assertEquals(0, reservedStock(otherProduct));
        assertEquals(0L, countForProducts("SELECT count(*) FROM stock_movement WHERE product_id IN (:ids)"));
        assertEquals("AWAITING_PAYMENT", orderStatus(reference));
    }

    @Test
    void legacyUnlinkedHoldIsLeftForAuditedReconciliation() throws Exception {
        Product product = createProduct("4.00", "ACTIVE", 8, 0);
        var owner = signIn(createUser(SharedUserRole.CLIENT));
        String reference = createOrder(owner, item(product, 3));
        long id = orderId(reference);
        database.sql("""
                INSERT INTO stock_reservation (order_item_id, quantity)
                SELECT order_item_id, quantity FROM order_item WHERE order_id = :id
                """).param("id", id).update();
        setReservedStock(product, 3);
        checkout(owner, reference).andExpect(status().isConflict());
        assertEquals(0L, countCheckouts(reference));
        var order = orders.findById(id).orElseThrow();
        order.createdTime = releaseRows.databaseTime().minus(Duration.ofHours(25));
        order.paymentDeadline = order.createdTime.plus(Duration.ofHours(24));
        orders.flush();
        releases.expireIfDue(id);
        assertEquals("AWAITING_PAYMENT", orderStatus(reference));
        assertEquals(1L, countReservations(reference, "HELD"));
        assertEquals(3, reservedStock(product));
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
            database.sql("DELETE FROM notification WHERE recipient_user_id IN (:ids)").param("ids", fixtureUsers).update();
            database.sql("DELETE FROM client_order_idempotency WHERE client_user_id IN (:ids)")
                    .param("ids", fixtureUsers).update();
            database.sql("""
                    DELETE FROM stock_reservation WHERE order_item_id IN (
                        SELECT oi.order_item_id FROM order_item oi JOIN customer_order co USING (order_id)
                        WHERE co.client_user_id IN (:ids))
                    """).param("ids", fixtureUsers).update();
            database.sql("""
                    DELETE FROM refund WHERE payment_id IN (
                        SELECT payment_id FROM payment JOIN customer_order USING (order_id)
                        WHERE client_user_id IN (:ids))
                    """).param("ids", fixtureUsers).update();
            database.sql("""
                    DELETE FROM payment WHERE order_id IN (
                        SELECT order_id FROM customer_order WHERE client_user_id IN (:ids))
                    """).param("ids", fixtureUsers).update();
            database.sql("""
                    DELETE FROM picking_task WHERE order_id IN (
                        SELECT order_id FROM customer_order WHERE client_user_id IN (:ids))
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
        product.SKU = UUID.randomUUID().toString();
        product.name = "Order test product";
        product.currentPrice = new BigDecimal(unitPrice);
        product.listingStatus = SharedListingStatus.valueOf(listingStatus);
        product.createdTime = Instant.now();
        product = products.saveAndFlush(product);
        fixtureProducts.add(product.productID);
        if (physical != null) {
            Inventory balance = new Inventory();
            balance.productID = product.productID;
            balance.updatedTime = Instant.now();
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

    private long notificationCount(String reference) {
        return database.sql("SELECT COUNT(*) FROM notification WHERE order_id = :id AND type = 'PAYMENT_CONFIRMED'")
                .param("id", orderId(reference)).query(Long.class).single();
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

    private ResultActions checkout(OrderSession session, String reference) throws Exception {
        String path = ORDERS + "/" + reference + "/checkout";
        return mvc.perform(post(path).servletPath(path).session(session.session())
                .header(session.csrfHeader(), session.csrfToken()));
    }

    private long checkoutId(OrderSession session, String reference) throws Exception {
        var result = checkout(session, reference).andExpect(status().isOk()).andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.checkoutId");
        return id.longValue();
    }

    private String createReservedOrder(OrderSession session, String items) throws Exception {
        String reference = createOrder(session, items);
        checkoutId(session, reference);
        return reference;
    }

    private long countCheckouts(String reference) {
        return database.sql("SELECT count(*) FROM payment WHERE order_id = :id")
                .param("id", orderId(reference)).query(Long.class).single();
    }

    private void makeCheckoutWindowElapsed(long checkoutId) {
        database.sql("""
                UPDATE payment SET created_at = NOW() - INTERVAL '40 minutes',
                    checkout_expires_at = NOW() - INTERVAL '10 minutes' WHERE payment_id = :id
                """).param("id", checkoutId).update();
    }

    private void reconcile(long checkoutId, Outcome outcome) {
        checkouts.reconcile(checkoutId, id -> new VerifiedCheckout(outcome, null, null, null));
    }

    private void capture(long checkoutId, String providerReference, String amount) {
        checkouts.reconcile(checkoutId, id ->
                new VerifiedCheckout(Outcome.CAPTURED, providerReference, new BigDecimal(amount), "HKD"));
    }

    private void assertRequestedRefund(long checkoutId) {
        assertRequestedRefund(checkoutId, "12.00");
    }

    private void assertRequestedRefund(long checkoutId, String amountHkd) {
        assertEquals(List.of("REQUESTED"), database.sql("""
                SELECT refund_status FROM refund WHERE payment_id = :id AND amount = :amount
                    AND provider_reference IS NULL AND completed_at IS NULL
                """).param("id", checkoutId).param("amount", new BigDecimal(amountHkd)).query(String.class).list());
        assertEquals(1L, database.sql("SELECT count(*) FROM refund WHERE payment_id = :id")
                .param("id", checkoutId).query(Long.class).single());
    }

    private boolean entryClosedWhilePending(String reference) {
        return database.sql("""
                SELECT ended_at IS NOT NULL AND payment_status = 'PENDING' AND accepted_at IS NULL
                FROM payment WHERE order_id = :id
                """).param("id", orderId(reference)).query(Boolean.class).single();
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
                .param("reserved", reserved).param("id", product.productID).update();
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
                .param("id", product.productID).query(Integer.class).single();
    }

    private int checkoutWhenReady(CountDownLatch ready, CountDownLatch start, OrderSession session, String reference)
            throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return checkout(session, reference).andReturn().getResponse().getStatus();
    }

    private ResultActions submit(OrderSession session, String body) throws Exception {
        return postWithKey(session, UUID.randomUUID().toString(), body);
    }

    private ResultActions postWithKey(OrderSession session, String key, String body) throws Exception {
        var request = post(ORDERS).servletPath(ORDERS).session(session.session())
                .header(session.csrfHeader(), session.csrfToken()).contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mvc.perform(request);
    }

    private void assertInvalidKey(String key) throws Exception {
        AppUser owner = createUser(SharedUserRole.CLIENT);
        postWithKey(signIn(owner), key, request("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CLIENT_ORDER_IDEMPOTENCY_KEY_INVALID"));
        assertEquals(0L, countOrders(owner));
    }

    private ConfigurableApplicationContext startCreateOrderApplication() {
        return new SpringApplicationBuilder(BackendApplication.class).run("--server.port=0", "--server.address=127.0.0.1",
                "--lms.shared.bootstrap-admin-username=", "--lms.shared.bootstrap-admin-temporary-password=",
                "--lms.client.orders.expiry.enabled=false", "--debug=false");
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
        return "{\"productId\":%d,\"quantity\":%d}".formatted(product.productID, quantity);
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
