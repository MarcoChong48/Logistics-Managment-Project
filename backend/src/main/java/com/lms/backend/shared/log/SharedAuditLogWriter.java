package com.lms.backend.shared.log;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.shared.database.AuditLog;
import com.lms.backend.shared.database.AuditLogRepository;

@Service
public class SharedAuditLogWriter {

    // ===== SECTION 1: OTHER CLASSES THIS CLASS USES =====

    private final AuditLogRepository sharedWriteAuditLogRepository;

    /**
     * What it does: Connects the audit writer to its repository.
     * Input: The audit log repository.
     * Output: A writer for audit rows.
     */
    public SharedAuditLogWriter(AuditLogRepository sharedWriteAuditLogRepository) {
        this.sharedWriteAuditLogRepository = sharedWriteAuditLogRepository;
    }

    // ===== SECTION 2: sharedWriteAuditLog =====

    /**
     * What it does: Records a successful action in the same transaction as the saved change.
     * Input: The optional actor, action, target, and maps containing only approved changed fields.
     * Output: One audit row. A private field name fails the write before it reaches the database.
     */
    @Transactional
    public void sharedWriteAuditLog(Long actorUserID, String action, String targetType, String targetID,
            Map<String, ?> sharedWriteAuditLogOldValueMap, Map<String, ?> sharedWriteAuditLogNewValueMap) {
        // Check both maps before inserting an audit row.
        sharedWriteAuditLogCheckFieldNames(sharedWriteAuditLogOldValueMap);
        sharedWriteAuditLogCheckFieldNames(sharedWriteAuditLogNewValueMap);

        // Use JSON objects for supplied values and keep empty inputs as SQL null.
        AuditLog sharedWriteAuditLogRow = new AuditLog();
        sharedWriteAuditLogRow.actorUserID = actorUserID;
        sharedWriteAuditLogRow.action = action;
        sharedWriteAuditLogRow.targetType = targetType;
        sharedWriteAuditLogRow.targetID = targetID;
        sharedWriteAuditLogRow.oldValue = sharedWriteAuditLogOldValueMap == null
                ? null : new LinkedHashMap<>(sharedWriteAuditLogOldValueMap);
        sharedWriteAuditLogRow.newValue = sharedWriteAuditLogNewValueMap == null
                ? null : new LinkedHashMap<>(sharedWriteAuditLogNewValueMap);
        sharedWriteAuditLogRow.timestamp = Instant.now();
        sharedWriteAuditLogRow.result = "SUCCESS";
        sharedWriteAuditLogRepository.save(sharedWriteAuditLogRow);
    }

    /**
     * What it does: Checks every field name in a change, including any nested JSON objects.
     * Input: A change value to inspect.
     * Output: None. A private field stops the write, with no field value in the exception.
     */
    private void sharedWriteAuditLogCheckFieldNames(Object sharedWriteAuditLogValue) {
        if (sharedWriteAuditLogValue instanceof Map<?, ?> sharedWriteAuditLogMap) {
            for (Map.Entry<?, ?> sharedWriteAuditLogEntry : sharedWriteAuditLogMap.entrySet()) {
                if (!(sharedWriteAuditLogEntry.getKey() instanceof String sharedWriteAuditLogKey)
                        || SharedPrivateKeyRule.sharedCheckPrivateKeyName(sharedWriteAuditLogKey)) {
                    throw new IllegalArgumentException("An audit value has an invalid or private field name.");
                }
                sharedWriteAuditLogCheckFieldNames(sharedWriteAuditLogEntry.getValue());
            }
        } else if (sharedWriteAuditLogValue instanceof Iterable<?> sharedWriteAuditLogList) {
            for (Object sharedWriteAuditLogItem : sharedWriteAuditLogList) {
                sharedWriteAuditLogCheckFieldNames(sharedWriteAuditLogItem);
            }
        }
    }
}
