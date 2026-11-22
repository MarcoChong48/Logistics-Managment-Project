package com.lms.backend.shared.database;

import java.util.OptionalLong;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Checks the shared fulfilment allocation while the caller holds the order row lock. */
@Repository
public class FulfillmentEligibility {

    private final JdbcClient db;
    private final CustomerOrderReleaseRepository orderRows;

    public FulfillmentEligibility(JdbcClient db, CustomerOrderReleaseRepository orderRows) {
        this.db = db;
        this.orderRows = orderRows;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OptionalLong heldPayment(long orderId) {
        var attempts = db.sql("""
                SELECT payment_id, payment_status, accepted_at IS NOT NULL, amount > 0
                FROM payment WHERE order_id = :orderId ORDER BY payment_id FOR UPDATE
                """).param("orderId", orderId)
                .query((rows, index) -> new Attempt(rows.getLong(1), rows.getString(2),
                        rows.getBoolean(3), rows.getBoolean(4))).list();

        Long paymentId = null;
        for (Attempt attempt : attempts) {
            if ("ENDED_UNPAID".equals(attempt.status()) && !attempt.timestamped()) {
                continue;
            }
            if (paymentId != null || !"ACCEPTED".equals(attempt.status()) || !attempt.timestamped()
                    || !attempt.positiveAmount()) {
                return OptionalLong.empty();
            }
            paymentId = attempt.id();
        }
        if (paymentId == null || !orderRows.checkoutHoldsAllItems(orderId, paymentId)) {
            return OptionalLong.empty();
        }

        boolean conflictingEvidence = db.sql("""
                SELECT EXISTS (SELECT 1 FROM refund WHERE payment_id = :paymentId)
                    OR EXISTS (
                        SELECT 1 FROM stock_reservation r JOIN order_item i USING (order_item_id)
                        WHERE i.order_id = :orderId AND r.status = 'HELD'
                          AND (r.payment_id IS DISTINCT FROM :paymentId
                               OR r.released_at IS NOT NULL OR r.consumed_at IS NOT NULL)
                    )
                """).param("paymentId", paymentId).param("orderId", orderId).query(Boolean.class).single();
        return conflictingEvidence ? OptionalLong.empty() : OptionalLong.of(paymentId);
    }

    private record Attempt(long id, String status, boolean timestamped, boolean positiveAmount) {}
}
