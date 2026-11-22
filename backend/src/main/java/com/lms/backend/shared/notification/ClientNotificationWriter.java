package com.lms.backend.shared.notification;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.shared.value.SharedOrderStatus;

@Service
@Transactional(propagation = Propagation.MANDATORY)
public class ClientNotificationWriter {

    private final JdbcClient database;

    public ClientNotificationWriter(JdbcClient database) {
        this.database = database;
    }

    public Result paymentConfirmed(long orderId, long paymentId) {
        return publish(ClientNotificationType.PAYMENT_CONFIRMED, SharedOrderStatus.READY_FOR_PICKING, orderId,
                orderId, paymentId, """
                        SELECT o.client_user_id, NULL AS failure_reason
                        FROM customer_order o JOIN payment p USING (order_id)
                        WHERE o.order_id = :orderId AND o.order_status = :status AND p.payment_id = :evidenceId
                            AND p.payment_status = 'ACCEPTED' AND p.accepted_at IS NOT NULL
                        """);
    }

    public Result shipped(long orderId, long pickupListId) {
        return publish(ClientNotificationType.SHIPPED, SharedOrderStatus.SHIPPED, orderId, pickupListId, pickupListId,
                """
                        SELECT o.client_user_id, NULL AS failure_reason FROM customer_order o
                        JOIN delivery_package p USING (order_id)
                        JOIN pickup_list_item i USING (package_id)
                        WHERE o.order_id = :orderId AND o.order_status = :status AND i.pickup_list_id = :evidenceId
                            AND i.collection_outcome = 'COLLECTED' AND i.collected_at IS NOT NULL
                            AND EXISTS (SELECT 1 FROM package_custody_event e WHERE e.package_id = p.package_id
                                AND e.pickup_list_id = i.pickup_list_id AND e.event_type = 'PICKED_UP')
                        """);
    }

    public Result deliveryFailed(long orderId, long attemptId) {
        return deliveryOutcome(ClientNotificationType.DELIVERY_FAILED, SharedOrderStatus.DELIVERY_FAILED,
                orderId, attemptId, attemptId, "FAILED");
    }

    public Result delivered(long orderId, long attemptId) {
        return deliveryOutcome(ClientNotificationType.DELIVERED, SharedOrderStatus.DELIVERED,
                orderId, orderId, attemptId, "DELIVERED");
    }

    private Result deliveryOutcome(ClientNotificationType type, SharedOrderStatus status, long orderId,
            long sourceEventId, long attemptId, String outcome) {
        return publish(type, orderId, sourceEventId, database.sql("""
                SELECT o.client_user_id, a.failure_reason FROM customer_order o
                JOIN delivery_package p USING (order_id)
                JOIN delivery_attempt a USING (package_id)
                JOIN order_delivery_detail d ON d.delivery_detail_id = a.delivery_detail_id AND d.order_id = o.order_id
                WHERE o.order_id = :orderId AND o.order_status = :status AND a.delivery_attempt_id = :attemptId
                    AND a.outcome = :outcome AND a.end_time IS NOT NULL
                """).param("orderId", orderId).param("status", status.name()).param("attemptId", attemptId)
                .param("outcome", outcome));
    }

    private Result publish(ClientNotificationType type, SharedOrderStatus status, long orderId, long sourceEventId,
            long evidenceId, String evidenceSql) {
        return publish(type, orderId, sourceEventId, database.sql(evidenceSql)
                .param("orderId", orderId).param("status", status.name()).param("evidenceId", evidenceId));
    }

    private Result publish(ClientNotificationType type, long orderId, long sourceEventId, JdbcClient.StatementSpec evidence) {
        // Callers hold the parent order lock and have flushed the transition and its evidence in this transaction.
        var recipient = evidence.query((row, index) -> new Recipient(row.getLong("client_user_id"),
                row.getString("failure_reason"))).optional();
        if (recipient.isEmpty()) {
            boolean recorded = database.sql("""
                    SELECT EXISTS (SELECT 1 FROM notification
                        WHERE order_id = :orderId AND type = :type AND source_event_id = :sourceId)
                    """).param("orderId", orderId).param("type", type.name()).param("sourceId", sourceEventId)
                    .query(Boolean.class).single();
            return recorded ? Result.ALREADY_RECORDED : Result.EVIDENCE_MISSING;
        }
        var receiver = recipient.orElseThrow();
        int inserted = database.sql("""
                INSERT INTO notification (recipient_user_id, order_id, type, message, source_event_id)
                VALUES (:recipient, :orderId, :type, :message, :sourceId)
                ON CONFLICT (order_id, type, source_event_id) WHERE source_event_id IS NOT NULL DO NOTHING
                """).param("recipient", receiver.id()).param("orderId", orderId).param("type", type.name())
                .param("message", type.messageForEvent(receiver.failureCode())).param("sourceId", sourceEventId).update();
        return inserted == 1 ? Result.INSERTED : Result.ALREADY_RECORDED;
    }

    public enum Result {
        INSERTED, ALREADY_RECORDED, EVIDENCE_MISSING
    }

    private record Recipient(long id, String failureCode) {
    }
}
