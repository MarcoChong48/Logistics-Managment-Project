package com.lms.backend.client.order;

import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_CUSTODY_EVENT_PICKED_UP;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.lms.backend.client.order.ClientOrderDetailsResponse.DeliveryDetails;

@Repository
class ClientOrderReadRepository {

    private final JdbcClient database;

    ClientOrderReadRepository(JdbcClient database) {
        this.database = database;
    }

    Optional<OrderRow> findOwned(long clientId, String reference) {
        return database.sql("""
                SELECT order_id, order_reference, created_at, order_status, payment_deadline, total_amount
                FROM customer_order WHERE client_user_id = :clientId AND order_reference = :reference
                """).param("clientId", clientId).param("reference", reference)
                .query((row, index) -> order(row)).optional();
    }

    List<SummaryRow> findPage(long clientId, OrderRow cursor, int pageSize) {
        String cursorFilter = cursor == null ? "" : "AND (created_at, order_id) < (:createdAt, :orderId)";
        var query = database.sql("""
                WITH page AS (
                    SELECT order_id, order_reference, created_at, order_status, payment_deadline, total_amount
                    FROM customer_order WHERE client_user_id = :clientId %s
                    ORDER BY created_at DESC, order_id DESC LIMIT :pageSize
                )
                SELECT page.*, COUNT(item.order_item_id) AS product_count, COALESCE(SUM(item.quantity), 0) AS item_quantity
                FROM page LEFT JOIN order_item item USING (order_id)
                GROUP BY page.order_id, page.order_reference, page.created_at, page.order_status,
                    page.payment_deadline, page.total_amount
                ORDER BY page.created_at DESC, page.order_id DESC
                """.formatted(cursorFilter)).param("clientId", clientId).param("pageSize", pageSize);
        if (cursor != null) {
            query.param("createdAt", cursor.createdAt()).param("orderId", cursor.id());
        }
        return query.query((row, index) -> new SummaryRow(order(row), row.getLong("product_count"),
                row.getLong("item_quantity"))).list();
    }

    List<ClientOrderDetailsResponse.Item> findItems(long orderId) {
        return database.sql("""
                SELECT p.sku, p.name, i.quantity, i.unit_price
                FROM order_item i LEFT JOIN product p USING (product_id) WHERE i.order_id = :orderId ORDER BY i.order_item_id
                """).param("orderId", orderId).query((row, index) -> {
                    String sku = row.getString("sku");
                    String labelSource = sku == null ? "UNAVAILABLE" : "CURRENT_CATALOGUE";
                    return new ClientOrderDetailsResponse.Item(sku, row.getString("name"), labelSource,
                            row.getInt("quantity"), row.getBigDecimal("unit_price"));
                }).list();
    }

    Optional<DeliveryDetails> findCurrentDeliveryDetails(long orderId) {
        return database.sql("""
                SELECT version_no, recipient_name, phone, address_text FROM order_delivery_detail WHERE order_id = :orderId
                ORDER BY version_no DESC LIMIT 1
                """).param("orderId", orderId).query((row, index) -> new DeliveryDetails(row.getInt("version_no"),
                row.getString("recipient_name"), row.getString("phone"), row.getString("address_text"))).optional();
    }

    List<PaymentRow> findPayments(long orderId) {
        return database.sql("""
                SELECT payment_id, payment_status, created_at, accepted_at FROM payment WHERE order_id = :orderId
                ORDER BY created_at, payment_id
                """).param("orderId", orderId).query((row, index) -> new PaymentRow(row.getLong("payment_id"),
                row.getString("payment_status"), time(row, "created_at"), time(row, "accepted_at"))).list();
    }

    List<RefundRow> findRefunds(long orderId) {
        return database.sql("""
                SELECT r.payment_id, r.amount, r.refund_status, r.created_at, r.completed_at
                FROM refund r JOIN payment p USING (payment_id) WHERE p.order_id = :orderId
                ORDER BY r.created_at, r.refund_id
                """).param("orderId", orderId).query((row, index) -> new RefundRow(row.getLong("payment_id"),
                row.getBigDecimal("amount"), row.getString("refund_status"), time(row, "created_at"),
                time(row, "completed_at"))).list();
    }

    FulfillmentRow findFulfillment(long orderId) {
        return database.sql("""
                SELECT t.start_time, t.confirm_packing_time, p.created_at AS package_created_at
                FROM customer_order o LEFT JOIN picking_task t USING (order_id) LEFT JOIN delivery_package p USING (order_id)
                WHERE o.order_id = :orderId
                """).param("orderId", orderId).query((row, index) -> new FulfillmentRow(time(row, "start_time"),
                time(row, "confirm_packing_time"), time(row, "package_created_at"))).single();
    }

    List<CustodyRow> findCustodyEvents(long orderId) {
        return database.sql("""
                SELECT e.custody_event_id, e.pickup_list_id, e.event_type, e.event_time FROM package_custody_event e
                JOIN delivery_package p USING (package_id) WHERE p.order_id = :orderId
                AND e.event_type IN (:pickedUp, 'RETURNED_TO_WAREHOUSE')
                ORDER BY e.event_time, e.custody_event_id
                """).param("orderId", orderId).param("pickedUp", SHARED_CUSTODY_EVENT_PICKED_UP)
                .query((row, index) -> new CustodyRow(row.getLong("custody_event_id"),
                        row.getObject("pickup_list_id", Long.class), row.getString("event_type"), time(row, "event_time"))).list();
    }

    List<AttemptRow> findDeliveryAttempts(long orderId) {
        return database.sql("""
                SELECT a.pickup_custody_event_id, d.version_no, a.start_time, a.end_time, a.outcome, a.failure_reason
                FROM delivery_attempt a JOIN delivery_package p ON p.package_id = a.package_id
                JOIN order_delivery_detail d ON d.delivery_detail_id = a.delivery_detail_id AND d.order_id = p.order_id
                WHERE p.order_id = :orderId ORDER BY a.start_time, a.delivery_attempt_id
                """).param("orderId", orderId).query((row, index) -> new AttemptRow(
                row.getObject("pickup_custody_event_id", Long.class), row.getInt("version_no"), time(row, "start_time"),
                time(row, "end_time"),
                row.getString("outcome"), row.getString("failure_reason"))).list();
    }

    private static OrderRow order(ResultSet row) throws SQLException {
        return new OrderRow(row.getLong("order_id"), row.getString("order_reference"),
                row.getObject("created_at", OffsetDateTime.class), row.getString("order_status"),
                time(row, "payment_deadline"), row.getBigDecimal("total_amount"));
    }

    private static Instant time(ResultSet row, String field) throws SQLException {
        var timestamp = row.getObject(field, OffsetDateTime.class);
        return timestamp == null ? null : timestamp.toInstant();
    }

    record OrderRow(long id, String reference, OffsetDateTime createdAt, String status, Instant deadline,
            BigDecimal amount) {
    }

    record SummaryRow(OrderRow order, long productCount, long quantity) {
    }

    record PaymentRow(long id, String status, Instant createdAt, Instant acceptedAt) {
    }

    record RefundRow(long paymentId, BigDecimal amount, String status, Instant createdAt, Instant completedAt) {
    }

    record FulfillmentRow(Instant pickingStartedAt, Instant packingConfirmedAt, Instant packageCreatedAt) {
    }

    record CustodyRow(long id, Long pickupListId, String type, Instant occurredAt) {
    }

    record AttemptRow(Long pickupEventId, int deliveryAddressVersion, Instant startedAt, Instant endedAt, String outcome,
            String failureReason) {
    }
}
