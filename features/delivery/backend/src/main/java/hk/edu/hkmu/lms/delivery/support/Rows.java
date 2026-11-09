package hk.edu.hkmu.lms.delivery.support;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Database helpers used by the Delivery workflows. */
public final class Rows {
    private Rows() {}

    public static void lock(JdbcTemplate db) {
        db.queryForObject("SELECT id FROM mutation_lock WHERE id=1 FOR UPDATE", Integer.class);
    }

    public static Map<String, Object> requiredRow(JdbcTemplate db, String sql, Object... args) {
        Map<String, Object> result = optionalRow(db, sql, args);
        if (result == null) {
            throw new ApiException(404, "NOT_FOUND", "The requested record was not found. Refresh the workspace.");
        }
        return result;
    }

    public static Map<String, Object> optionalRow(JdbcTemplate db, String sql, Object... args) {
        List<Map<String, Object>> rows = db.queryForList(sql, args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public static int rowCount(JdbcTemplate db, String sql, Object... args) {
        return db.queryForObject(sql, Integer.class, args);
    }

    public static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    public static int number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).intValue();
    }

    public static boolean flag(Map<String, Object> row, String key) {
        return Boolean.TRUE.equals(row.get(key));
    }

    public static String newId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
