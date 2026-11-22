package com.lms.backend.delivery.support;

import static com.lms.backend.delivery.support.DeliveryValues.bad;
import static com.lms.backend.delivery.support.DeliveryValues.conflict;
import static com.lms.backend.delivery.support.DeliveryValues.required;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
@Profile("delivery-full")
public class DeliveryCommands {
    private static final long ASSIGNMENT_LOCK = 62032601L;
    private final JdbcTemplate db;
    private final JsonMapper json;

    public DeliveryCommands(JdbcTemplate db, JsonMapper json) {
        this.db = db;
        this.json = json;
    }

    // Use a transaction lock so a second request sees the first result.
    public void lock() {
        db.queryForList("SELECT pg_advisory_xact_lock(?)", ASSIGNMENT_LOCK);
    }

    @Transactional
    public Map<String,Object> run(long actor, String action, String target, Map<String,Object> body,
                                  Supplier<Map<String,Object>> work) {
        UUID requestId;
        try {
            requestId = UUID.fromString(required(body, "requestId", 36));
        } catch (IllegalArgumentException ex) {
            throw bad("Invalid request ID.");
        }
        var payload = new TreeMap<>(body);
        payload.remove("requestId");
        String hash = hash(json.writeValueAsBytes(payload));
        lock();

        List<Map<String,Object>> previous = db.queryForList("""
            SELECT payload_hash, result_json::text FROM delivery_operation_result
            WHERE actor_user_id=? AND action=? AND target=? AND request_id=?
            """, actor, action, target, requestId);
        if (!previous.isEmpty()) {
            Map<String,Object> result = previous.getFirst();
            if (!hash.equals(result.get("payload_hash"))) throw conflict("This request ID was used for another change.");
            return json.readValue((String) result.get("result_json"), new TypeReference<Map<String,Object>>() {});
        }

        Map<String,Object> result = work.get();
        db.update("""
            INSERT INTO delivery_operation_result(actor_user_id,action,target,request_id,payload_hash,result_json)
            VALUES (?,?,?,?,?,?::jsonb)
            """, actor, action, target, requestId, hash, json.writeValueAsString(result));
        return result;
    }

    private static String hash(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
