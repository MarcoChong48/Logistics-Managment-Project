package com.lms.backend.shared.database;

import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ClientOrderIdempotencyRepository {

    private final JdbcClient database;

    public ClientOrderIdempotencyRepository(JdbcClient database) {
        this.database = database;
    }

    // The database releases this lock with the caller's transaction.
    public void lockKey(long clientId, String key) {
        String scope = "lms:client:create-order:" + clientId + ":" + key;
        database.sql("SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(:scope, 0))")
                .param("scope", scope)
                .query(Integer.class).single();
    }

    public Optional<CompletedRequest> findCompleted(long clientId, String key) {
        return database.sql("""
                SELECT request_fingerprint, response_body::text AS response_body
                FROM client_order_idempotency
                WHERE client_user_id = :clientId AND idempotency_key = :key
                """)
                .param("clientId", clientId)
                .param("key", key)
                .query((row, index) -> new CompletedRequest(row.getBytes("request_fingerprint"),
                        row.getString("response_body"))).optional();
    }

    public void insertCompleted(long clientId, String key, byte[] fingerprint, long orderId, String responseBody) {
        database.sql("""
                INSERT INTO client_order_idempotency
                    (client_user_id, idempotency_key, request_fingerprint, order_id, response_body)
                VALUES (:clientId, :key, :fingerprint, :orderId, CAST(:responseBody AS JSONB))
                """)
                .param("clientId", clientId)
                .param("key", key)
                .param("fingerprint", fingerprint)
                .param("orderId", orderId)
                .param("responseBody", responseBody)
                .update();
    }

    public record CompletedRequest(byte[] fingerprint, String responseBody) {
    }
}
