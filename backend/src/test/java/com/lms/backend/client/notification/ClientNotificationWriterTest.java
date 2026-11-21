package com.lms.backend.client.notification;

import static com.lms.backend.shared.notification.ClientNotificationWriter.Result.ALREADY_RECORDED;
import static com.lms.backend.shared.notification.ClientNotificationWriter.Result.EVIDENCE_MISSING;
import static com.lms.backend.shared.notification.ClientNotificationWriter.Result.INSERTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.lms.backend.shared.notification.ClientNotificationWriter;
import com.lms.backend.shared.notification.ClientNotificationWriter.Result;
import com.lms.backend.shared.value.SharedDeliveryFailureReason;

@SpringBootTest(properties = {"lms.client.orders.expiry.enabled=false", "lms.client.stripe.enabled=false",
        "lms.shared.bootstrap-admin-username=", "lms.shared.bootstrap-admin-temporary-password="})
class ClientNotificationWriterTest {

    @Autowired private ClientNotificationWriter writer;
    @Autowired private JdbcClient db;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void requiresACallerTransaction() {
        assertThrows(IllegalTransactionStateException.class, () -> writer.paymentConfirmed(1, 1));
    }

    @Test
    void concurrentDuplicateEventsCommitOnlyOneNotification() throws Exception {
        var owner = user("CLIENT");
        long order = order(owner, "READY_FOR_PICKING");
        try {
            long payment = payment(order, "ACCEPTED");
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            try (var workers = Executors.newFixedThreadPool(2)) {
                var first = workers.submit(() -> publishWhenReady(ready, start, order, payment));
                var duplicate = workers.submit(() -> publishWhenReady(ready, start, order, payment));
                boolean bothReady = ready.await(5, TimeUnit.SECONDS);
                start.countDown();
                assertTrue(bothReady);
                assertEquals(List.of(INSERTED, ALREADY_RECORDED),
                        List.of(first.get(15, TimeUnit.SECONDS), duplicate.get(15, TimeUnit.SECONDS))
                        .stream().sorted().toList());
            }
            assertEquals(1L, count(order, "PAYMENT_CONFIRMED"));
            assertEquals(owner, db.sql("SELECT recipient_user_id FROM notification WHERE order_id=:id")
                    .param("id", order).query(Long.class).single());
        } finally {
            cleanPaymentOrder(order, owner);
        }
    }

    @Test
    void rollbackRemovesNotificationTogetherWithBusinessStateAndAllowsRetry() {
        long owner = user("CLIENT");
        long order = order(owner, "AWAITING_PAYMENT");
        try {
            long payment = payment(order, "PENDING");
            TransactionTemplate tx = new TransactionTemplate(transactions);
            tx.executeWithoutResult(status -> {
                assertEquals(EVIDENCE_MISSING, writer.paymentConfirmed(order, payment));
                acceptPayment(order, payment);
                assertEquals(INSERTED, writer.paymentConfirmed(order, payment));
                assertEquals(1L, count(order, "PAYMENT_CONFIRMED"));
                status.setRollbackOnly();
            });
            assertEquals(0L, count(order, "PAYMENT_CONFIRMED"));
            assertEquals("AWAITING_PAYMENT", db.sql("SELECT order_status FROM customer_order WHERE order_id=:id")
                    .param("id", order).query(String.class).single());
            assertEquals("PENDING", db.sql("SELECT payment_status FROM payment WHERE payment_id=:id")
                    .param("id", payment).query(String.class).single());

            tx.executeWithoutResult(status -> {
                acceptPayment(order, payment);
                assertEquals(INSERTED, writer.paymentConfirmed(order, payment));
            });
            tx.executeWithoutResult(status -> assertEquals(ALREADY_RECORDED, writer.paymentConfirmed(order, payment)));
            assertEquals(1L, count(order, "PAYMENT_CONFIRMED"));
        } finally {
            cleanPaymentOrder(order, owner);
        }
    }

    @Test
    @Transactional
    void shippingRequiresPhysicalCollectionAndDeduplicatesAcrossEquivalentCustodyRows() {
        long order = order(user("CLIENT"), "READY_FOR_PICKUP");
        long parcel = parcel(order);
        long list = pickupList();
        db.sql("INSERT INTO pickup_list_item (pickup_list_id,package_id) VALUES (:list,:parcel)")
                .param("list", list).param("parcel", parcel).update();
        assertEquals(EVIDENCE_MISSING, writer.shipped(order, list));
        setOrderStatus(order, "SHIPPED");
        assertEquals(EVIDENCE_MISSING, writer.shipped(order, list));
        collect(parcel, list);
        assertEquals(EVIDENCE_MISSING, writer.shipped(order, list));
        recordPickup(parcel, list);
        assertEquals(INSERTED, writer.shipped(order, list));
        recordPickup(parcel, list);
        assertEquals(ALREADY_RECORDED, writer.shipped(order, list));
        assertEquals(1L, count(order, "SHIPPED"));

        setOrderStatus(order, "OUT_FOR_DELIVERY");
        assertEquals(ALREADY_RECORDED, writer.shipped(order, list));
        assertEquals(EVIDENCE_MISSING, writer.shipped(order, pickupList()));

        long nextList = pickupList();
        db.sql("INSERT INTO pickup_list_item (pickup_list_id,package_id) VALUES (:list,:parcel)")
                .param("list", nextList).param("parcel", parcel).update();
        collect(parcel, nextList);
        recordPickup(parcel, nextList);
        setOrderStatus(order, "SHIPPED");
        assertEquals(INSERTED, writer.shipped(order, nextList));
        assertEquals(2L, count(order, "SHIPPED"));
    }

    @Test
    @Transactional
    void deliveryRequiresOwnedAttemptAndUsesOnlyPublicFailureReasons() {
        long order = order(user("CLIENT"), "OUT_FOR_DELIVERY");
        long parcel = parcel(order);
        long address = address(order);
        long driver = user("DELIVERY_WORKER");
        long failed = attempt(parcel, address, driver, "FAILED", "WRONG_ADDRESS");
        assertEquals(EVIDENCE_MISSING, writer.deliveryFailed(order, failed));
        setOrderStatus(order, "DELIVERY_FAILED");
        assertEquals(INSERTED, writer.deliveryFailed(order, failed));
        assertEquals(ALREADY_RECORDED, writer.deliveryFailed(order, failed));

        long foreign = order(user("CLIENT"), "DELIVERY_FAILED");
        long mislinked = attempt(parcel, address(foreign), driver, "FAILED", "RECIPIENT_UNAVAILABLE");
        assertEquals(EVIDENCE_MISSING, writer.deliveryFailed(order, mislinked));
        assertEquals(EVIDENCE_MISSING, writer.deliveryFailed(foreign, failed));
        long unknown = attempt(parcel, address, driver, "FAILED", "PRIVATE-FREE-TEXT");
        assertEquals(INSERTED, writer.deliveryFailed(order, unknown));
        long noContact = attempt(parcel, address, driver, "FAILED", "UNABLE_TO_CONTACT");
        assertEquals(INSERTED, writer.deliveryFailed(order, noContact));
        assertEquals(List.of("The address could not be located.", "The delivery could not be completed.",
                "The recipient could not be contacted."), db.sql("""
                SELECT message FROM notification WHERE order_id=:id AND type='DELIVERY_FAILED' ORDER BY notification_id
                """).param("id", order).query(String.class).list());

        long delivered = attempt(parcel, address, driver, "DELIVERED", "PRIVATE-IGNORED-FIELD");
        assertEquals(EVIDENCE_MISSING, writer.delivered(order, delivered));
        setOrderStatus(order, "DELIVERED");
        assertEquals(ALREADY_RECORDED, writer.deliveryFailed(order, failed));
        assertEquals(INSERTED, writer.delivered(order, delivered));
        assertEquals(ALREADY_RECORDED, writer.delivered(order, attempt(parcel, address, driver, "DELIVERED", "PRIVATE-IGNORED-FIELD")));
        assertEquals(1L, count(order, "DELIVERED"));
    }

    @ParameterizedTest
    @CsvSource({"CLIENT_NOT_HOME,The recipient was unavailable.", "WRONG_ADDRESS,The address could not be located.",
            "CLIENT_REFUSED,The delivery was declined at the address.", "UNABLE_TO_CONTACT,The recipient could not be contacted.",
            "OTHER,The delivery could not be completed."})
    void publishedDeliveryFailureCodesMapToApprovedMessages(String code, String message) {
        assertEquals(message, SharedDeliveryFailureReason.messageForCode(code));
        assertEquals(message, SharedDeliveryFailureReason.approvedMessage(message));
    }

    private Result publishWhenReady(CountDownLatch ready, CountDownLatch start, long order, long payment) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        // The state is already committed. Exercise the unique index without using the parent lock to serialize the inserts.
        return new TransactionTemplate(transactions).execute(status -> writer.paymentConfirmed(order, payment));
    }

    private long count(long order, String type) {
        return db.sql("SELECT COUNT(*) FROM notification WHERE order_id=:id AND type=:type")
                .param("id", order).param("type", type).query(Long.class).single();
    }

    private long user(String role) {
        return db.sql("INSERT INTO app_user (username,password_hash,role) VALUES (:name,'unused',:role) RETURNING user_id")
                .param("name", "notification-writer-" + UUID.randomUUID()).param("role", role).query(Long.class).single();
    }

    private long order(long owner, String status) {
        return db.sql("""
                INSERT INTO customer_order (order_reference,client_user_id,order_status,payment_deadline,total_amount)
                VALUES (:reference,:owner,:status,NOW()+INTERVAL '24 hours',12.00) RETURNING order_id
                """).param("reference", "ORD-" + UUID.randomUUID()).param("owner", owner).param("status", status)
                .query(Long.class).single();
    }

    private void setOrderStatus(long order, String status) {
        db.sql("UPDATE customer_order SET order_status=:status WHERE order_id=:id").param("id", order).param("status", status).update();
    }

    private long payment(long order, String status) {
        return db.sql("""
                INSERT INTO payment (order_id,amount,payment_status,accepted_at)
                VALUES (:id,12.00,:status,CASE WHEN :status='ACCEPTED' THEN NOW() ELSE NULL END) RETURNING payment_id
                """).param("id", order).param("status", status).query(Long.class).single();
    }

    private void acceptPayment(long order, long payment) {
        db.sql("UPDATE payment SET payment_status='ACCEPTED',accepted_at=NOW() WHERE payment_id=:id")
                .param("id", payment).update();
        setOrderStatus(order, "READY_FOR_PICKING");
    }

    private long parcel(long order) {
        return db.sql("INSERT INTO delivery_package (order_id,package_qr_code) VALUES (:id,:qr) RETURNING package_id")
                .param("id", order).param("qr", "PRIVATE-QR-" + UUID.randomUUID()).query(Long.class).single();
    }

    private long pickupList() {
        return db.sql("INSERT INTO pickup_list (list_qr_code,created_by_worker_id) VALUES (:code,:worker) RETURNING pickup_list_id")
                .param("code", "PRIVATE-LIST-" + UUID.randomUUID()).param("worker", user("WAREHOUSE_WORKER")).query(Long.class).single();
    }

    private void collect(long parcel, long list) {
        db.sql("""
                UPDATE pickup_list_item SET collection_outcome='COLLECTED',collected_at=NOW()
                WHERE package_id=:parcel AND pickup_list_id=:list
                """).param("parcel", parcel).param("list", list).update();
    }

    private void recordPickup(long parcel, long list) {
        db.sql("""
                INSERT INTO package_custody_event (package_id,pickup_list_id,event_type,details)
                VALUES (:parcel,:list,'PICKED_UP','{"internal":"PRIVATE-CUSTODY-NOTE"}')
                """).param("parcel", parcel).param("list", list).update();
    }

    private long address(long order) {
        return db.sql("""
                INSERT INTO order_delivery_detail (order_id,version_no,recipient_name,phone,address_text)
                VALUES (:id,1,'Fixture','55550100','PRIVATE-ADDRESS') RETURNING delivery_detail_id
                """).param("id", order).query(Long.class).single();
    }

    private long attempt(long parcel, long address, long driver, String outcome, String reason) {
        return db.sql("""
                INSERT INTO delivery_attempt (package_id,delivery_detail_id,delivery_worker_user_id,end_time,outcome,failure_reason,internal_note)
                VALUES (:parcel,:address,:driver,NOW(),:outcome,:reason,'PRIVATE-DRIVER-NOTE') RETURNING delivery_attempt_id
                """).param("parcel", parcel).param("address", address).param("driver", driver)
                .param("outcome", outcome).param("reason", reason).query(Long.class).single();
    }

    private void cleanPaymentOrder(long order, long owner) {
        db.sql("DELETE FROM notification WHERE order_id=:id").param("id", order).update();
        db.sql("DELETE FROM payment WHERE order_id=:id").param("id", order).update();
        db.sql("DELETE FROM customer_order WHERE order_id=:id").param("id", order).update();
        db.sql("DELETE FROM app_user WHERE user_id=:id").param("id", owner).update();
    }
}
