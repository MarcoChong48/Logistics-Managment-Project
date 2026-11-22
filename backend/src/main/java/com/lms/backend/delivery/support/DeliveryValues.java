package com.lms.backend.delivery.support;

import com.lms.backend.shared.error.SharedRuleException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;

public final class DeliveryValues {
    private DeliveryValues() {}

    public static Map<String,Object> fields(Object... pairs) {
        Map<String,Object> result = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            result.put((String) pairs[index], pairs[index + 1]);
        }
        return result;
    }

    public static long id(String input) {
        try {
            long id = Long.parseLong(input);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
            // Reject an invalid or missing record ID.
        }
        throw bad("Invalid record ID.");
    }

    public static String required(Map<String,Object> values, String key, int length) {
        String text = optional(values, key, length);
        if (text.isBlank()) throw bad("Enter " + key + ".");
        return text;
    }

    public static String optional(Map<String,Object> values, String key, int length) {
        Object value = values.get(key);
        if (value == null) return "";
        if (!(value instanceof String text) || text.length() > length) {
            throw bad("Invalid " + key + ".");
        }
        return text.strip();
    }

    public static SharedRuleException bad(String text) {
        return new SharedRuleException(HttpStatus.BAD_REQUEST, "DELIVERY_INVALID_INPUT", text);
    }

    public static SharedRuleException conflict(String text) {
        return new SharedRuleException(HttpStatus.CONFLICT, "DELIVERY_CONFLICT", text);
    }

    public static SharedRuleException missing(String text) {
        return new SharedRuleException(HttpStatus.NOT_FOUND, "DELIVERY_NOT_FOUND", text);
    }

    public static SharedRuleException denied() {
        return new SharedRuleException(HttpStatus.FORBIDDEN, "DELIVERY_ACCESS_DENIED", "You cannot access this delivery record.");
    }
}
