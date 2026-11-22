package com.lms.backend.client.order;

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

import java.math.BigDecimal;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
class ClientOrderReadApiTest {

    private static final String ORDERS = "/api/client/orders";
    private static final String PASSWORD = "ReadOrderFixture123!";
    private static final OffsetDateTime PLACED = OffsetDateTime.parse("2026-10-15T10:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private JdbcClient db;
    @Autowired private PasswordEncoder passwords;
    @MockitoBean private SharedSecurityLogWriter securityLogs;

    @Test
    void noOrdersReturnsEmptyPage() throws Exception {
        read(ORDERS, signIn(user("CLIENT"))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.orders").isEmpty()).andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void listUsesStableNewestFirstCursorAndAggregatesItems() throws Exception {
        var owner = user("CLIENT");
        var old = order(owner, "DELIVERED", PLACED.minusDays(1));
        var tieA = order(owner, "AWAITING_PAYMENT", PLACED);
        var tieB = order(owner, "READY_FOR_PICKING", PLACED);
        order(user("CLIENT"), "AWAITING_PAYMENT", PLACED.plusMinutes(1));
        item(tieB, product("First"), 2, "8.00");
        item(tieB, product("Second"), 4, "5.90");
        var session = signIn(owner);

        mvc.perform(get(ORDERS).servletPath(ORDERS).session(session).param("limit", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.orders[*].orderReference", contains(tieB.reference())))
                .andExpect(jsonPath("$.orders[0].paymentDeadline").value(nullValue()))
                .andExpect(jsonPath("$.orders[0].totalAmountHkd").value(39.6))
                .andExpect(jsonPath("$.orders[0].itemSummary.productCount").value(2))
                .andExpect(jsonPath("$.orders[0].itemSummary.totalQuantity").value(6))
                .andExpect(jsonPath("$.orders[0].orderId").doesNotExist())
                .andExpect(jsonPath("$.nextCursor").value(tieB.reference()));

        order(owner, "AWAITING_PAYMENT", PLACED.plusMinutes(5));
        mvc.perform(get(ORDERS).servletPath(ORDERS).session(session).param("limit", "2").param("cursor", tieB.reference()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[*].orderReference", contains(tieA.reference(), old.reference())))
                .andExpect(jsonPath("$.orders[0].paymentDeadline").value("2026-10-16T10:00:00Z"))
                .andExpect(jsonPath("$.orders[1].itemSummary.totalQuantity").value(0))
                .andExpect(jsonPath("$.nextCursor").value(nullValue()));
    }

    @Test
    void allReadsRequireClientAndUnknownOrUnownedReferencesAreIndistinguishable() throws Exception {
        var order = order(user("CLIENT"), "AWAITING_PAYMENT", PLACED);
        var other = signIn(user("CLIENT"));
        var admin = signIn(user("ADMIN"));
        for (String path : new String[] {ORDERS, detailPath(order), detailPath(order) + "/tracking"}) {
            mvc.perform(get(path).servletPath(path)).andExpect(status().isUnauthorized());
            read(path, admin).andExpect(status().isForbidden());
        }
        for (String suffix : new String[] {"", "/tracking"}) {
            String denied = read(detailPath(order) + suffix, other).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("CLIENT_ORDER_NOT_FOUND")).andReturn().getResponse().getContentAsString();
            read(ORDERS + "/ORD-unknown" + suffix, other).andExpect(status().isNotFound())
                    .andExpect(content().string(denied));
            read(ORDERS + "/" + "X".repeat(61) + suffix, other).andExpect(status().isNotFound())
                    .andExpect(content().string(denied));
        }
        mvc.perform(get(ORDERS).servletPath(ORDERS).session(other).param("cursor", order.reference()))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CLIENT_ORDER_NOT_FOUND"));
        for (String limit : new String[] {"0", "51", "abc"}) {
            mvc.perform(get(ORDERS).servletPath(ORDERS).session(other).param("limit", limit))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void detailsUseSavedPricesAndLatestOrderSpecificAddress() throws Exception {
        var owner = user("CLIENT");
        var order = order(owner, "AWAITING_PAYMENT", PLACED);
        long renamed = product("Old name");
        item(order, renamed, 2, "12.30");
        item(order, product("Second"), 3, "5.00");
        String newSku = UUID.randomUUID().toString();
        db.sql("UPDATE product SET current_price=999, name='Renamed now', sku=:sku, listing_status='INACTIVE' WHERE product_id=:id")
                .param("sku", newSku).param("id", renamed).update();
        address(order, 1, "Old receiver");
        address(order, 2, "Current receiver");
        address(order(owner, "AWAITING_PAYMENT", PLACED), 5, "Other order address");

        read(detailPath(order), signIn(owner)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.createdAt").value("2026-10-15T10:00:00Z"))
                .andExpect(jsonPath("$.totalAmountHkd").value(39.6))
                .andExpect(jsonPath("$.items[0].name").value("Renamed now"))
                .andExpect(jsonPath("$.items[0].sku").value(newSku))
                .andExpect(jsonPath("$.items[0].labelSource").value("CURRENT_CATALOGUE"))
                .andExpect(jsonPath("$.items[0].unitPriceHkd").value(12.3))
                .andExpect(jsonPath("$.items[1].unitPriceHkd").value(5.0))
                .andExpect(jsonPath("$.items[1].quantity").value(3))
                .andExpect(jsonPath("$.deliveryDetails.recipientName").value("Current receiver"))
                .andExpect(jsonPath("$.deliveryDetails.version").value(2))
                .andExpect(jsonPath("$.deliveryDetails.phone").value("+852 5555 0100"))
                .andExpect(jsonPath("$.deliveryDetails.address").value("Fixture address 2"))
                .andExpect(jsonPath("$.paymentAttempts").isEmpty()).andExpect(jsonPath("$.canCancel").value(true));
    }

    @Test
    void paymentAndRefundProgressReportOnlyRecordedPublicStates() throws Exception {
        var owner = user("CLIENT");
        var order = order(owner, "READY_FOR_PICKING", PLACED);
        long pending = payment(order, "PENDING", null, "PRIVATE-PI-PENDING");
        long accepted = payment(order, "ACCEPTED", PLACED.plusMinutes(3), "PRIVATE-PI-ACCEPTED");
        long refundRequired = payment(order, "REFUND_REQUIRED", PLACED.plusMinutes(3), "PRIVATE-PI-REFUND-REQUIRED");
        refund(accepted, "REQUESTED", PLACED.plusMinutes(5));
        refund(accepted, "COMPLETED", PLACED.plusMinutes(6));
        refund(accepted, "COMPLETED", null);
        refund(refundRequired, "REQUESTED", null);
        var session = signIn(owner);

        for (String url : new String[] {detailPath(order), detailPath(order) + "/tracking"}) {
            read(url, session).andExpect(status().isOk())
                    .andExpect(jsonPath("$.orderStatus").value("READY_FOR_PICKING"))
                    .andExpect(jsonPath("$.paymentAttempts[*].status", contains("PENDING", "ACCEPTED", "REFUND_REQUIRED")))
                    .andExpect(jsonPath("$.paymentAttempts[0].acceptedAt").value(nullValue()))
                    .andExpect(jsonPath("$.paymentAttempts[0].refunds").isEmpty())
                    .andExpect(jsonPath("$.paymentAttempts[1].acceptedAt").value("2026-10-15T10:03:00Z"))
                    .andExpect(jsonPath("$.paymentAttempts[1].refunds[*].status", contains("REQUESTED", "COMPLETED", "UNKNOWN")))
                    .andExpect(jsonPath("$.paymentAttempts[1].refunds[0].completedAt").value(nullValue()))
                    .andExpect(jsonPath("$.paymentAttempts[1].refunds[1].completedAt").value("2026-10-15T10:06:00Z"))
                    .andExpect(jsonPath("$.paymentAttempts[2].refunds[0].status").value("REQUESTED"))
                    .andExpect(content().string(not(containsString("PRIVATE-PI"))))
                    .andExpect(content().string(not(containsString("PRIVATE-REFUND"))));
        }
        assertEquals("PENDING", db.sql("SELECT payment_status FROM payment WHERE payment_id=:id")
                .param("id", pending).query(String.class).single());
        assertEquals(1L, db.sql("SELECT count(*) FROM refund WHERE payment_id=:id AND refund_status='REQUESTED'")
                .param("id", accepted).query(Long.class).single());
    }

    @Test
    void ownOrderCannotInheritAnotherClientsNestedRecords() throws Exception {
        var owner = user("CLIENT");
        var owned = order(owner, "AWAITING_PAYMENT", PLACED);
        var unrelated = order(user("CLIENT"), "DELIVERED", PLACED);
        long sharedProduct = product("Shared ordered product");
        item(owned, sharedProduct, 1, "8.20");
        item(unrelated, sharedProduct, 2, "16.30");
        long closed = payment(unrelated, "ACCEPTED", PLACED.plusMinutes(3), "PRIVATE-PI-UNRELATED");
        refund(closed, "COMPLETED", PLACED.plusMinutes(6));
        long otherAddress = address(unrelated, 1, "Private unrelated address");
        long otherParcel = parcel(unrelated);
        long otherPickup = custody(otherParcel, "PICKED_UP", 3);
        attempt(otherParcel, otherAddress, otherPickup, user("DELIVERY_WORKER"), 4, 5, "DELIVERED", null);
        var session = signIn(owner);

        read(detailPath(owned), session).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryDetails").value(nullValue()))
                .andExpect(jsonPath("$.paymentAttempts").isEmpty())
                .andExpect(jsonPath("$.items[0].unitPriceHkd").value(8.2));
        read(detailPath(owned) + "/tracking", session).andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryAttempts").isEmpty())
                .andExpect(jsonPath("$.paymentAttempts").isEmpty())
                .andExpect(jsonPath("$.progression[*].type", contains("ORDER_PLACED")));
    }

    @Test
    void pendingTaskLeavesCancellationAvailableUntilStartPicking() throws Exception {
        var owner = user("CLIENT");
        var order = order(owner, "READY_FOR_PICKING", PLACED);
        db.sql("INSERT INTO picking_task (order_id) VALUES (:id)").param("id", order.id()).update();
        var session = signIn(owner);
        read(detailPath(order), session).andExpect(status().isOk()).andExpect(jsonPath("$.canCancel").value(true));
        db.sql("UPDATE picking_task SET start_time=:time WHERE order_id=:id")
                .param("id", order.id()).param("time", PLACED.plusHours(1)).update();
        read(detailPath(order), session).andExpect(status().isOk()).andExpect(jsonPath("$.canCancel").value(false));
        read(detailPath(order) + "/tracking", session).andExpect(status().isOk())
                .andExpect(jsonPath("$.progression[*].type", contains("ORDER_PLACED", "PICKING_STARTED")))
                .andExpect(jsonPath("$.progression[1].occurredAt").value("2026-10-15T11:00:00Z"));
    }

    @ParameterizedTest
    @CsvSource({"AWAITING_PAYMENT,Awaiting payment", "PICKING,Picking", "SHIPPED,Shipped", "DELIVERY_FAILED,Delivery failed",
            "READY_FOR_REDELIVERY,Ready for redelivery", "DELIVERED,Delivered", "CANCELLED,Cancelled"})
    void currentStatusWithoutHistoricalEvidenceDoesNotInventEvents(String orderStatus, String label) throws Exception {
        var owner = user("CLIENT");
        var order = order(owner, orderStatus, PLACED);
        read(detailPath(order) + "/tracking", signIn(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value(orderStatus)).andExpect(jsonPath("$.statusLabel").value(label))
                .andExpect(jsonPath("$.currentStatusSince").value(nullValue())).andExpect(jsonPath("$.historyComplete").value(false))
                .andExpect(jsonPath("$.progression[*].type", contains("ORDER_PLACED")))
                .andExpect(jsonPath("$.deliveryAttempts").isEmpty());
    }

    @Test
    void timelineUsesPersistedEvidenceAndRedeliveryCyclesWithoutLeakingPrivateFields() throws Exception {
        var owner = user("CLIENT");
        var order = order(owner, "DELIVERED", PLACED);
        var driver = user("DELIVERY_WORKER");
        long firstAddress = address(order, 1, "Fixture recipient 1");
        long secondAddress = address(order, 2, "Fixture recipient 2");
        db.sql("INSERT INTO picking_task (order_id, task_status, start_time, confirm_packing_time) VALUES (:id,'PACKED',:start,:end)")
                .param("id", order.id()).param("start", PLACED.plusHours(1)).param("end", PLACED.plusHours(2)).update();
        long parcel = parcel(order);
        long pickup1 = custody(parcel, "PICKED_UP", 3);
        custody(parcel, "RETURNED_TO_WAREHOUSE", 7);
        long pickup2 = custody(parcel, "PICKED_UP", 8);
        custody(parcel, "PRIVATE-CUSTODY", 9);
        attempt(parcel, secondAddress, pickup2, driver, 10, 11, "DELIVERED", null);
        attempt(parcel, firstAddress, pickup1, driver, 5, 6, "FAILED", "PRIVATE-FREE-TEXT");
        attempt(parcel, firstAddress, pickup1, driver, 4, 5, "FAILED", "RECIPIENT_UNAVAILABLE");
        var unrelated = order(user("CLIENT"), "SHIPPED", PLACED);
        attempt(parcel, address(unrelated, 1, "Private unrelated address"), pickup1, driver, 12, 13, "DELIVERED", null);

        read(detailPath(order) + "/tracking", signIn(owner)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.progression[*].type", contains("ORDER_PLACED", "PICKING_STARTED",
                        "PACKING_CONFIRMED", "PACKAGE_CREATED", "PACKAGE_PICKED_UP", "DELIVERY_ATTEMPT_STARTED", "DELIVERY_FAILED",
                        "DELIVERY_ATTEMPT_STARTED", "DELIVERY_FAILED", "PACKAGE_RETURNED_TO_WAREHOUSE", "PACKAGE_PICKED_UP",
                        "DELIVERY_ATTEMPT_STARTED", "DELIVERY_COMPLETED")))
                .andExpect(jsonPath("$.deliveryAttempts[*].attemptNumber", contains(1, 2, 3)))
                .andExpect(jsonPath("$.deliveryAttempts[*].cycleNumber", contains(1, 1, 2)))
                .andExpect(jsonPath("$.deliveryAttempts[*].deliveryAddressVersion", contains(1, 1, 2)))
                .andExpect(jsonPath("$.deliveryAttempts[0].failureReason").value("The recipient was unavailable."))
                .andExpect(jsonPath("$.deliveryAttempts[1].failureReason").value("The delivery could not be completed."))
                .andExpect(jsonPath("$.deliveryAttempts[2].startedAt").value("2026-10-15T20:00:00Z"))
                .andExpect(jsonPath("$.deliveryAttempts[2].endedAt").value("2026-10-15T21:00:00Z"))
                .andExpect(jsonPath("$.deliveryAttempts[2].outcome").value("DELIVERED"))
                .andExpect(content().string(not(containsString("PRIVATE-"))))
                .andExpect(content().string(not(containsString("Fixture recipient"))));
    }

    @Test
    void repeatedPickupOnTheSameListUsesOneCycleEvenWhenTheAttemptLinksToTheDuplicate() throws Exception {
        var owner = user("CLIENT");
        var order = order(owner, "OUT_FOR_DELIVERY", PLACED);
        long address = address(order, 1, "Fixture recipient");
        long parcel = parcel(order);
        long firstList = pickupList();
        pickupOnList(parcel, firstList, 3);
        long duplicate = pickupOnList(parcel, firstList, 4);
        custody(parcel, "RETURNED_TO_WAREHOUSE", 7);
        long next = pickupOnList(parcel, pickupList(), 8);
        var driver = user("DELIVERY_WORKER");
        attempt(parcel, address, duplicate, driver, 5, 6, "FAILED", null);
        attempt(parcel, address, next, driver, 9, 10, "DELIVERED", null);

        read(detailPath(order) + "/tracking", signIn(owner)).andExpect(status().isOk())
                .andExpect(jsonPath("$.progression[?(@.type == 'PACKAGE_PICKED_UP')].occurredAt",
                        contains("2026-10-15T13:00:00Z", "2026-10-15T18:00:00Z")))
                .andExpect(jsonPath("$.deliveryAttempts[*].cycleNumber", contains(1, 2)));
    }

    private ResultActions read(String url, MockHttpSession session) throws Exception {
        return mvc.perform(get(url).servletPath(url).session(session));
    }

    private String detailPath(FixtureOrder order) {
        return ORDERS + "/" + order.reference();
    }

    private MockHttpSession signIn(FixtureUser user) throws Exception {
        String sessionPath = "/api/shared/session";
        var guest = mvc.perform(get(sessionPath).servletPath(sessionPath)).andExpect(status().isOk()).andReturn();
        var token = JsonPath.parse(guest.getResponse().getContentAsString());
        String login = "/api/shared/login";
        var result = mvc.perform(post(login).servletPath(login)
                .session(assertInstanceOf(MockHttpSession.class, guest.getRequest().getSession(false)))
                .header(token.<String>read("$.sharedViewSessionSecurityTokenHeaderName"),
                        token.<String>read("$.sharedViewSessionSecurityToken"))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"sharedLoginUserUsername":"%s","sharedLoginUserPassword":"%s"}
                        """.formatted(user.username(), PASSWORD))).andExpect(status().isOk()).andReturn();
        return assertInstanceOf(MockHttpSession.class, result.getRequest().getSession(false));
    }

    private FixtureUser user(String role) {
        String username = "read-order-" + UUID.randomUUID();
        long id = db.sql("INSERT INTO app_user (username,password_hash,role) VALUES (:name,:hash,:role) RETURNING user_id")
                .param("name", username).param("hash", passwords.encode(PASSWORD)).param("role", role).query(Long.class).single();
        return new FixtureUser(id, username);
    }

    private FixtureOrder order(FixtureUser owner, String status, OffsetDateTime created) {
        String reference = "ORD-" + UUID.randomUUID();
        long id = db.sql("""
                INSERT INTO customer_order (order_reference,client_user_id,order_status,created_at,payment_deadline,total_amount)
                VALUES (:ref,:owner,:status,:created,:deadline,39.60) RETURNING order_id
                """).param("ref", reference).param("owner", owner.id()).param("status", status).param("created", created)
                .param("deadline", created.plusDays(1)).query(Long.class).single();
        return new FixtureOrder(id, reference);
    }

    private long product(String name) {
        return db.sql("INSERT INTO product (sku,name,current_price) VALUES (:sku,:name,50) RETURNING product_id")
                .param("sku", UUID.randomUUID().toString()).param("name", name).query(Long.class).single();
    }

    private void item(FixtureOrder order, long product, int quantity, String price) {
        db.sql("INSERT INTO order_item (order_id,product_id,quantity,unit_price) VALUES (:id,:product,:quantity,:price)")
                .param("id", order.id()).param("product", product).param("quantity", quantity)
                .param("price", new BigDecimal(price)).update();
    }

    private long address(FixtureOrder order, int version, String recipient) {
        return db.sql("""
                INSERT INTO order_delivery_detail (order_id,version_no,recipient_name,phone,address_text)
                VALUES (:id,:version,:recipient,'+852 5555 0100',:address) RETURNING delivery_detail_id
                """).param("id", order.id()).param("version", version).param("recipient", recipient)
                .param("address", "Fixture address " + version).query(Long.class).single();
    }

    private long payment(FixtureOrder order, String status, OffsetDateTime accepted, String providerRef) {
        return db.sql("""
                INSERT INTO payment (order_id,amount,payment_status,created_at,accepted_at,provider_reference)
                VALUES (:id,39.60,:status,:created,:accepted,:provider) RETURNING payment_id
                """).param("id", order.id()).param("status", status).param("created", PLACED.plusMinutes(1))
                .param("accepted", accepted, Types.TIMESTAMP_WITH_TIMEZONE).param("provider", providerRef).query(Long.class).single();
    }

    private void refund(long payment, String status, OffsetDateTime completed) {
        db.sql("""
                INSERT INTO refund (payment_id,amount,refund_status,created_at,completed_at,provider_reference)
                VALUES (:payment,39.60,:status,:created,:completed,:provider)
                """).param("payment", payment).param("status", status).param("created", PLACED.plusMinutes(4))
                .param("completed", completed, Types.TIMESTAMP_WITH_TIMEZONE).param("provider", "PRIVATE-REFUND-"+UUID.randomUUID())
                .update();
    }

    private long parcel(FixtureOrder order) {
        return db.sql("""
                INSERT INTO delivery_package (order_id,package_qr_code,created_at) VALUES (:id,:qr,:time) RETURNING package_id
                """).param("id", order.id()).param("qr", "PRIVATE-QR-" + UUID.randomUUID())
                .param("time", PLACED.plusHours(2)).query(Long.class).single();
    }

    private long custody(long parcel, String type, int hour) {
        return db.sql("""
                INSERT INTO package_custody_event (package_id,event_type,event_time,details)
                VALUES (:id,:type,:time,'{"secret":"PRIVATE-CUSTODY-NOTE"}') RETURNING custody_event_id
                """).param("id", parcel).param("type", type).param("time", PLACED.plusHours(hour)).query(Long.class).single();
    }

    private long pickupList() {
        return db.sql("INSERT INTO pickup_list (list_qr_code,created_by_worker_id) VALUES (:qr,:user) RETURNING pickup_list_id")
                .param("qr", "PRIVATE-LIST-" + UUID.randomUUID()).param("user", user("WAREHOUSE_WORKER").id())
                .query(Long.class).single();
    }

    private long pickupOnList(long parcel, long list, int hour) {
        return db.sql("""
                INSERT INTO package_custody_event (package_id,event_type,pickup_list_id,event_time)
                VALUES (:parcel,'PICKED_UP',:list,:time) RETURNING custody_event_id
                """).param("parcel", parcel).param("list", list).param("time", PLACED.plusHours(hour))
                .query(Long.class).single();
    }

    private void attempt(long parcel, long address, long pickup, FixtureUser driver, int start, int end,
            String outcome, String reason) {
        db.sql("""
                INSERT INTO delivery_attempt (package_id,delivery_detail_id,delivery_worker_user_id,pickup_custody_event_id,
                    start_time,end_time,outcome,failure_reason,internal_note)
                VALUES (:parcel,:address,:driver,:pickup,:start,:end,:outcome,:reason,'PRIVATE-ATTEMPT-NOTE')
                """).param("parcel", parcel).param("address", address).param("driver", driver.id()).param("pickup", pickup)
                .param("start", PLACED.plusHours(start)).param("end", PLACED.plusHours(end)).param("outcome", outcome)
                .param("reason", reason, Types.VARCHAR).update();
    }

    private record FixtureUser(long id, String username) {
    }

    private record FixtureOrder(long id, String reference) {
    }
}
