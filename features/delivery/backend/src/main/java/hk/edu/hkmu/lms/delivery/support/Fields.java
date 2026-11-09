package hk.edu.hkmu.lms.delivery.support;

import java.util.LinkedHashMap;
import java.util.Map;

/** JSON fields shared by the Delivery endpoints. */
public final class Fields {
    private Fields() {}

    public static Map<String, Object> fields(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put(pairs[i].toString(), pairs[i + 1]);
        }
        return result;
    }

    public static String requiredText(Map<String, Object> body, String key, int max) {
        String value = optionalText(body, key, max);
        if (value.isBlank()) throw ApiException.bad("Enter " + key + ".");
        return value;
    }

    public static String optionalText(Map<String, Object> body, String key, int max) {
        Object value = body.get(key);
        if (value == null) return "";
        if (!(value instanceof String text)) throw ApiException.bad("Check " + key + ".");
        text = text.trim();
        if (text.length() > max) throw ApiException.bad(key + " is too long.");
        return text;
    }
}
