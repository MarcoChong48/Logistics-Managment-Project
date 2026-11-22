package com.lms.backend.shared.database;

import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_IN_PROGRESS;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_PACKED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_RESERVATION_STATUS_CONSUMED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_RESERVATION_STATUS_HELD;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_RESERVATION_STATUS_RELEASED;

import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.lms.backend.shared.value.SharedOrderStatus;

@Repository
public class CustomerOrderReleaseRepository {

    private final JdbcClient database;

    public CustomerOrderReleaseRepository(JdbcClient database) {
        this.database = database;
    }

    public List<Long> findExpiredOrderIdsAfter(long afterId, int batchSize) {
        return database.sql("""
                SELECT order_id FROM customer_order
                WHERE order_status = :awaiting AND payment_deadline <= statement_timestamp()
                    AND order_id > :afterId
                ORDER BY order_id
                LIMIT :batchSize
                """)
                .param("awaiting", SharedOrderStatus.AWAITING_PAYMENT.name())
                .param("afterId", afterId)
                .param("batchSize", batchSize)
                .query(Long.class).list();
    }

    public Instant databaseTime() {
        return database.sql("SELECT statement_timestamp()").query(OffsetDateTime.class).single().toInstant();
    }

    public boolean hasFulfillmentStarted(long orderId) {
        return database.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM picking_task WHERE order_id = :orderId
                        AND (start_time IS NOT NULL OR task_status IN (:progress, :packed))
                    UNION ALL
                    SELECT 1 FROM stock_reservation sr JOIN order_item item USING (order_item_id)
                        WHERE item.order_id = :orderId
                            AND (sr.status = :consumed OR sr.consumed_at IS NOT NULL)
                )
                """)
                .param("orderId", orderId)
                .param("progress", SHARED_PICKING_TASK_STATUS_IN_PROGRESS)
                .param("packed", SHARED_PICKING_TASK_STATUS_PACKED)
                .param("consumed", SHARED_RESERVATION_STATUS_CONSUMED)
                .query(Boolean.class).single();
    }

    public List<CheckoutItem> findCheckoutItems(long orderId) {
        return database.sql("""
                SELECT order_item_id, product_id, quantity FROM order_item
                WHERE order_id = :orderId ORDER BY product_id
                """)
                .param("orderId", orderId)
                .query((row, index) -> new CheckoutItem(row.getLong("order_item_id"),
                        row.getLong("product_id"), row.getInt("quantity"))).list();
    }

    public boolean hasUnlinkedHeldReservations(long orderId) {
        return database.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM stock_reservation sr JOIN order_item item USING (order_item_id)
                    WHERE item.order_id = :orderId AND sr.status = :held AND sr.payment_id IS NULL
                        AND sr.released_at IS NULL AND sr.consumed_at IS NULL
                )
                """)
                .param("orderId", orderId)
                .param("held", SHARED_RESERVATION_STATUS_HELD)
                .query(Boolean.class).single();
    }

    public List<Long> findHeldProductIds(long orderId, Long paymentId) {
        return database.sql("""
                SELECT DISTINCT item.product_id FROM stock_reservation sr JOIN order_item item USING (order_item_id)
                WHERE item.order_id = :orderId AND sr.status = :held
                    AND (:paymentId IS NULL OR sr.payment_id = :paymentId)
                    AND sr.released_at IS NULL AND sr.consumed_at IS NULL
                ORDER BY item.product_id
                """)
                .param("orderId", orderId)
                .param("paymentId", paymentId, Types.BIGINT)
                .param("held", SHARED_RESERVATION_STATUS_HELD)
                .query(Long.class).list();
    }

    public boolean checkoutHoldsAllItems(long orderId, long paymentId) {
        return database.sql("""
                SELECT EXISTS (SELECT 1 FROM order_item WHERE order_id = :orderId)
                    AND NOT EXISTS (
                        SELECT 1 FROM order_item item WHERE item.order_id = :orderId AND item.quantity <> (
                            SELECT COALESCE(SUM(sr.quantity), 0) FROM stock_reservation sr
                            WHERE sr.order_item_id = item.order_item_id AND sr.payment_id = :paymentId
                                AND sr.status = :held AND sr.released_at IS NULL AND sr.consumed_at IS NULL
                        )
                    )
                """)
                .param("orderId", orderId)
                .param("paymentId", paymentId)
                .param("held", SHARED_RESERVATION_STATUS_HELD)
                .query(Boolean.class).single();
    }

    public List<HeldReservation> lockHeldReservations(long orderId, Long paymentId) {
        return database.sql("""
                SELECT sr.reservation_id, item.product_id, sr.quantity
                FROM stock_reservation sr JOIN order_item item USING (order_item_id)
                WHERE item.order_id = :orderId AND sr.status = :held
                    AND (:paymentId IS NULL OR sr.payment_id = :paymentId)
                    AND sr.released_at IS NULL AND sr.consumed_at IS NULL
                ORDER BY item.product_id, sr.reservation_id
                FOR UPDATE OF sr
                """)
                .param("orderId", orderId)
                .param("paymentId", paymentId, Types.BIGINT)
                .param("held", SHARED_RESERVATION_STATUS_HELD)
                .query((row, index) -> new HeldReservation(row.getLong("reservation_id"),
                        row.getLong("product_id"), row.getInt("quantity"))).list();
    }

    public int markReleased(long reservationId) {
        return database.sql("""
                UPDATE stock_reservation
                SET status = :released, released_at = GREATEST(reserved_at, statement_timestamp())
                WHERE reservation_id = :reservationId AND status = :held
                    AND released_at IS NULL AND consumed_at IS NULL
                """)
                .param("released", SHARED_RESERVATION_STATUS_RELEASED)
                .param("reservationId", reservationId)
                .param("held", SHARED_RESERVATION_STATUS_HELD)
                .update();
    }

    public void insertReleaseMovement(long productId, Long actorUserId, int quantity, String reason) {
        database.sql("""
                INSERT INTO stock_movement
                    (product_id, actor_user_id, movement_type, quantity_delta, reserved_delta, reason)
                VALUES (:productId, :actorUserId, 'RELEASE', 0, :reservedDelta, :reason)
                """)
                .param("productId", productId)
                .param("actorUserId", actorUserId, Types.BIGINT)
                .param("reservedDelta", -quantity)
                .param("reason", reason)
                .update();
    }

    public record HeldReservation(long reservationId, long productId, int quantity) {
    }

    public record CheckoutItem(long orderItemId, long productId, int quantity) {
    }
}
