package com.lms.backend.warehouse;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.shared.error.SharedRuleException;

import tools.jackson.databind.json.JsonMapper;

@Repository
class WarehousePickingCommands {

    private final JdbcClient db;
    private final JsonMapper json;

    WarehousePickingCommands(JdbcClient db, JsonMapper json) {
        this.db = db;
        this.json = json;
    }

    // The caller holds the order row lock before looking up or recording a command.
    @Transactional(propagation = Propagation.MANDATORY)
    WarehouseRecordScanResponse run(long orderId, long actor, UUID requestId, List<?> payload,
            Supplier<WarehouseRecordScanResponse> work) {
        if (requestId == null) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "WAREHOUSE_REQUEST_ID_REQUIRED",
                    "Provide a request ID before modifying picking.");
        }
        String hash = hash(json.writeValueAsBytes(payload));
        var saved = db.sql("""
                SELECT payload_hash, result_json::text FROM warehouse_picking_command
                WHERE order_id = :orderId AND actor_user_id = :actor AND request_id = :requestId
                """).param("orderId", orderId).param("actor", actor).param("requestId", requestId)
                .query((row, index) -> new Saved(row.getString(1), row.getString(2))).optional();
        if (saved.isPresent()) {
            if (!hash.equals(saved.get().hash())) {
                throw new SharedRuleException(HttpStatus.CONFLICT, "WAREHOUSE_REQUEST_ID_REUSED",
                        "This request ID was already used for a different picking change.");
            }
            return json.readValue(saved.get().result(), WarehouseRecordScanResponse.class);
        }

        var result = work.get();
        db.sql("""
                INSERT INTO warehouse_picking_command (order_id, actor_user_id, request_id, payload_hash, result_json)
                VALUES (:orderId, :actor, :requestId, :hash, CAST(:result AS JSONB))
                """).param("orderId", orderId).param("actor", actor).param("requestId", requestId)
                .param("hash", hash).param("result", json.writeValueAsString(result)).update();
        return result;
    }

    private static String hash(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Saved(String hash, String result) {}
}
