package com.lms.backend.shared.database;

import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PAYMENT_STATUS_ACCEPTED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PAYMENT_STATUS_ENDED_UNPAID;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PAYMENT_STATUS_PENDING;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PAYMENT_STATUS_RECONCILIATION_REQUIRED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PAYMENT_STATUS_REFUND_REQUIRED;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CheckoutPaymentRepository {

    private final JdbcClient database;

    public CheckoutPaymentRepository(JdbcClient database) {
        this.database = database;
    }

    // All payment and refund writers take the parent order lock first.
    public List<Attempt> findPending(long orderId) {
        return database.sql("""
                SELECT * FROM payment WHERE order_id = :order AND payment_status = :pending AND accepted_at IS NULL
                """).param("order", orderId).param("pending", SHARED_PAYMENT_STATUS_PENDING)
                .query(CheckoutPaymentRepository::readAttempt).list();
    }

    public Optional<Long> findOrderId(long paymentId) {
        return database.sql("SELECT order_id FROM payment WHERE payment_id = :id")
                .param("id", paymentId).query(Long.class).optional();
    }

    public Optional<Attempt> findAttempt(long orderId, long paymentId) {
        return database.sql("SELECT * FROM payment WHERE order_id = :order AND payment_id = :id")
                .param("order", orderId).param("id", paymentId)
                .query(CheckoutPaymentRepository::readAttempt).optional();
    }

    // Apparent acceptance blocks new checkout and expiry while the provider or an operator reconciles it.
    public boolean hasAcceptedOrUnresolvedPayment(long orderId) {
        return database.sql("""
                SELECT EXISTS (SELECT 1 FROM payment WHERE order_id = :order
                    AND (accepted_at IS NOT NULL OR payment_status IN (:accepted, :reconciliation)))
                """).param("order", orderId).param("accepted", SHARED_PAYMENT_STATUS_ACCEPTED)
                .param("reconciliation", SHARED_PAYMENT_STATUS_RECONCILIATION_REQUIRED).query(Boolean.class).single();
    }

    public void markUnverifiedAcceptanceForReconciliation(long orderId) {
        database.sql("""
                UPDATE payment SET payment_status = :reconciliation
                WHERE order_id = :order AND payment_status = :accepted AND accepted_at IS NULL
                """).param("order", orderId).param("accepted", SHARED_PAYMENT_STATUS_ACCEPTED)
                .param("reconciliation", SHARED_PAYMENT_STATUS_RECONCILIATION_REQUIRED).update();
    }

    public Attempt createPending(long orderId, BigDecimal amountHkd, Instant startedAt, Instant entryDeadline) {
        return database.sql("""
                INSERT INTO payment (order_id, amount, payment_status, created_at, checkout_expires_at)
                VALUES (:order, :amount, :pending, :startedAt, :entryDeadline) RETURNING *
                """).param("order", orderId).param("amount", amountHkd).param("pending", SHARED_PAYMENT_STATUS_PENDING)
                .param("startedAt", startedAt.atOffset(ZoneOffset.UTC))
                .param("entryDeadline", entryDeadline.atOffset(ZoneOffset.UTC))
                .query(CheckoutPaymentRepository::readAttempt).single();
    }

    public void closePendingEntry(long orderId) {
        database.sql("""
                UPDATE payment SET ended_at = GREATEST(created_at, statement_timestamp())
                WHERE order_id = :order AND payment_status = :pending AND ended_at IS NULL AND accepted_at IS NULL
                """).param("order", orderId).param("pending", SHARED_PAYMENT_STATUS_PENDING).update();
    }

    public void markEndedUnpaid(long paymentId) {
        database.sql("""
                UPDATE payment SET payment_status = :unpaid,
                    ended_at = COALESCE(ended_at, GREATEST(created_at, statement_timestamp()))
                WHERE payment_id = :id
                """).param("id", paymentId).param("unpaid", SHARED_PAYMENT_STATUS_ENDED_UNPAID).update();
    }

    public void markAccepted(long paymentId, String providerReference, Instant acceptedAt) {
        database.sql("""
                UPDATE payment SET payment_status = :accepted, provider_reference = :reference,
                    accepted_at = :acceptedAt, ended_at = COALESCE(ended_at, :acceptedAt)
                WHERE payment_id = :id
                """).param("id", paymentId).param("accepted", SHARED_PAYMENT_STATUS_ACCEPTED)
                .param("reference", providerReference).param("acceptedAt", acceptedAt.atOffset(ZoneOffset.UTC))
                .update();
    }

    public void markRefundRequired(long paymentId, String providerReference) {
        database.sql("""
                UPDATE payment SET payment_status = :refundRequired, provider_reference = :reference,
                    ended_at = COALESCE(ended_at, GREATEST(created_at, statement_timestamp()))
                WHERE payment_id = :id
                """).param("id", paymentId).param("refundRequired", SHARED_PAYMENT_STATUS_REFUND_REQUIRED)
                .param("reference", providerReference).update();
    }

    private static Attempt readAttempt(ResultSet row, int index) throws SQLException {
        var entryDeadline = row.getObject("checkout_expires_at", OffsetDateTime.class);
        return new Attempt(row.getLong("payment_id"), row.getBigDecimal("amount"), row.getString("payment_status"),
                row.getString("provider_reference"), entryDeadline == null ? null : entryDeadline.toInstant(),
                row.getObject("ended_at", OffsetDateTime.class) != null,
                row.getObject("accepted_at", OffsetDateTime.class) != null);
    }

    public record Attempt(long paymentId, BigDecimal amountHkd, String status, String providerReference,
            Instant entryDeadline, boolean entryClosed, boolean hasAcceptanceTime) {

        public boolean needsAcceptanceReconciliation() {
            return !hasAcceptanceTime && (SHARED_PAYMENT_STATUS_ACCEPTED.equals(status)
                    || SHARED_PAYMENT_STATUS_RECONCILIATION_REQUIRED.equals(status));
        }

        public boolean isPending() {
            return !hasAcceptanceTime && SHARED_PAYMENT_STATUS_PENDING.equals(status);
        }
    }
}
