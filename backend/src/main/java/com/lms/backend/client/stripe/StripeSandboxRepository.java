package com.lms.backend.client.stripe;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import com.lms.backend.client.stripe.StripeSandboxGateway.SignedSessionEvent;

@Repository
class StripeSandboxRepository {

    private final JdbcClient database;

    StripeSandboxRepository(JdbcClient database) {
        this.database = database;
    }

    void requireSchema() {
        if (!database.sql("""
                SELECT to_regclass('public.client_stripe_checkout') IS NOT NULL
                    AND to_regclass('public.client_stripe_event') IS NOT NULL
                """).query(Boolean.class).single()) {
            throw new IllegalStateException("Stripe sandbox requires migration 05 on the isolated database.");
        }
    }

    boolean claimCreation(long checkoutId) {
        return database.sql("""
                INSERT INTO client_stripe_checkout (payment_id) VALUES (:id)
                ON CONFLICT (payment_id) DO NOTHING
                """).param("id", checkoutId).update() == 1;
    }

    Optional<String> sessionId(long checkoutId) {
        return database.sql("""
                SELECT session_id FROM client_stripe_checkout WHERE payment_id = :id AND session_id IS NOT NULL
                """).param("id", checkoutId).query(String.class).optional();
    }

    void bindSession(long checkoutId, String sessionId) {
        if (database.sql("""
                UPDATE client_stripe_checkout SET session_id = :session
                WHERE payment_id = :id AND (session_id IS NULL OR session_id = :session)
                """).param("id", checkoutId).param("session", sessionId).update() != 1) {
            throw new IllegalStateException("Stripe Session conflicts with the original checkout.");
        }
    }

    boolean enqueue(SignedSessionEvent event) {
        return database.sql("""
                INSERT INTO client_stripe_event (event_id, payment_id, session_id)
                SELECT :eventId, payment_id, :session FROM client_stripe_checkout WHERE payment_id = :id
                ON CONFLICT (event_id) DO NOTHING
                """).param("eventId", event.eventId()).param("id", event.checkoutId())
                .param("session", event.sessionId()).update() == 1;
    }

    List<SignedSessionEvent> pendingEvents() {
        return database.sql("""
                SELECT event_id, payment_id, session_id FROM client_stripe_event
                WHERE processed_at IS NULL
                ORDER BY last_checked_at NULLS FIRST, received_at, event_id LIMIT 20
                """).query((row, i) -> new SignedSessionEvent(row.getString("event_id"), row.getLong("payment_id"),
                        row.getString("session_id"))).list();
    }

    void markEventChecked(String eventId) {
        database.sql("UPDATE client_stripe_event SET last_checked_at = statement_timestamp() WHERE event_id = :id")
                .param("id", eventId).update();
    }

    void markEventProcessed(String eventId) {
        database.sql("UPDATE client_stripe_event SET processed_at = statement_timestamp() WHERE event_id = :id")
                .param("id", eventId).update();
    }

    List<LinkedSession> dueSessions() {
        return database.sql("""
                SELECT sc.payment_id, sc.session_id, p.payment_status = 'PENDING' AS entry_can_close
                FROM client_stripe_checkout sc JOIN payment p USING (payment_id)
                JOIN customer_order o USING (order_id)
                WHERE sc.session_id IS NOT NULL AND p.accepted_at IS NULL AND (
                    p.payment_status IN ('ACCEPTED', 'RECONCILIATION_REQUIRED')
                    OR (p.payment_status = 'PENDING' AND
                        (p.ended_at IS NOT NULL OR p.checkout_expires_at <= statement_timestamp()
                         OR o.payment_deadline <= statement_timestamp() OR o.order_status <> 'AWAITING_PAYMENT'))
                )
                ORDER BY sc.last_checked_at NULLS FIRST, sc.payment_id LIMIT 20
                """).query((row, i) -> new LinkedSession(row.getLong("payment_id"), row.getString("session_id"), row.getBoolean("entry_can_close"))).list();
    }

    void markSessionChecked(long checkoutId) {
        database.sql("UPDATE client_stripe_checkout SET last_checked_at = statement_timestamp() WHERE payment_id = :id")
                .param("id", checkoutId).update();
    }

    record LinkedSession(long checkoutId, String sessionId, boolean entryCanClose) {
    }
}
