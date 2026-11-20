package com.lms.backend.admin.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.lms.backend.shared.log.SharedPrivateKeyRule;

import tools.jackson.core.JacksonException;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public final class AdminPrivateValueRule {

    // ===== SECTION 1: CONSTANTS =====

    private static final JsonMapper ADMIN_HIDE_PRIVATE_VALUE_JSON_MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Pattern ADMIN_HIDE_PRIVATE_VALUE_DATE_PATTERN =
            Pattern.compile("(?<![0-9])[0-9]{4}-[0-9]{2}-[0-9]{2}(?![0-9])");
    private static final Pattern ADMIN_HIDE_PRIVATE_VALUE_NUMBER_PATTERN =
            Pattern.compile("\\+?\\p{Nd}(?:[ -]?\\p{Nd}){7,}+");

    /**
     * What it does: Keeps the pure rule from being created as an object.
     * Input: None.
     * Output: None.
     */
    private AdminPrivateValueRule() {
    }

    // ===== SECTION 2: adminHidePrivateValue =====

    /**
     * What it does: Masks private JSON fields and phone-like numbers before logs are shown or exported.
     * Input: A log value as text, which may be empty.
     * Output: The masked value, or null for a missing value.
     */
    public static String adminHidePrivateValue(String adminHidePrivateValueText) {
        if (adminHidePrivateValueText == null) {
            return null;
        }

        // Decode keys and escaped characters before applying the privacy rules to JSON.
        String adminHidePrivateValueTrimmedText = adminHidePrivateValueText.stripLeading();
        if (adminHidePrivateValueTrimmedText.startsWith("{") || adminHidePrivateValueTrimmedText.startsWith("[")
                || adminHidePrivateValueTrimmedText.startsWith("\"")) {
            try {
                Object adminHidePrivateValueParsedValue =
                        ADMIN_HIDE_PRIVATE_VALUE_JSON_MAPPER.readValue(adminHidePrivateValueText, Object.class);
                return ADMIN_HIDE_PRIVATE_VALUE_JSON_MAPPER.writeValueAsString(
                        adminHidePrivateJSON(adminHidePrivateValueParsedValue));
            } catch (StreamConstraintsException adminHidePrivateValueLimitError) {
                // A structured value that exceeds parser limits may still hold private fields.
                return "[hidden]";
            } catch (JacksonException adminHidePrivateValueParseError) {
                // Free text can begin with JSON punctuation. The number rule still applies.
            }
        }
        return adminHidePhoneLikeNumber(adminHidePrivateValueText);
    }

    // ===== SECTION 3: HELPER FUNCTIONS =====

    /**
     * What it does: Walks a JSON value and masks private fields and phone-like numbers in values.
     * Input: A decoded value, including nested objects or lists.
     * Output: A JSON value safe to show.
     */
    private static Object adminHidePrivateJSON(Object adminHidePrivateJSONValue) {
        if (adminHidePrivateJSONValue instanceof Map<?, ?> adminHidePrivateJSONObject) {
            Map<String, Object> adminHidePrivateJSONResultMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> adminHidePrivateJSONEntry : adminHidePrivateJSONObject.entrySet()) {
                String adminHidePrivateJSONKey = (String) adminHidePrivateJSONEntry.getKey();
                adminHidePrivateJSONResultMap.put(adminHidePrivateJSONKey,
                        SharedPrivateKeyRule.sharedCheckPrivateKeyName(adminHidePrivateJSONKey)
                                ? "[hidden]" : adminHidePrivateJSON(adminHidePrivateJSONEntry.getValue()));
            }
            return adminHidePrivateJSONResultMap;
        }
        if (adminHidePrivateJSONValue instanceof List<?> adminHidePrivateJSONList) {
            List<Object> adminHidePrivateJSONResultList = new ArrayList<>(adminHidePrivateJSONList.size());
            for (Object adminHidePrivateJSONItem : adminHidePrivateJSONList) {
                adminHidePrivateJSONResultList.add(adminHidePrivateJSON(adminHidePrivateJSONItem));
            }
            return adminHidePrivateJSONResultList;
        }
        if (adminHidePrivateJSONValue instanceof String adminHidePrivateJSONString) {
            return adminHidePhoneLikeNumber(adminHidePrivateJSONString);
        }
        if (adminHidePrivateJSONValue instanceof Number adminHidePrivateJSONNumber) {
            String adminHidePrivateJSONNumberText = adminHidePrivateJSONNumber.toString();
            String adminHidePrivateJSONMaskedText = adminHidePhoneLikeNumber(adminHidePrivateJSONNumberText);
            if (!adminHidePrivateJSONMaskedText.equals(adminHidePrivateJSONNumberText)) {
                return adminHidePrivateJSONMaskedText;
            }
        }
        return adminHidePrivateJSONValue;
    }

    /**
     * What it does: Masks runs of at least eight digits without changing dates or shorter numbers.
     * Input: Text to inspect.
     * Output: Text with phone-like values replaced.
     */
    private static String adminHidePhoneLikeNumber(String adminHidePhoneLikeNumberText) {
        Matcher adminHidePhoneLikeNumberDateMatcher =
                ADMIN_HIDE_PRIVATE_VALUE_DATE_PATTERN.matcher(adminHidePhoneLikeNumberText);
        StringBuilder adminHidePhoneLikeNumberResult = new StringBuilder();
        int adminHidePhoneLikeNumberStartIndex = 0;
        while (adminHidePhoneLikeNumberDateMatcher.find()) {
            // Copy every date unchanged and search only the text between the dates.
            adminHidePhoneLikeNumberResult.append(ADMIN_HIDE_PRIVATE_VALUE_NUMBER_PATTERN.matcher(
                    adminHidePhoneLikeNumberText.substring(adminHidePhoneLikeNumberStartIndex,
                            adminHidePhoneLikeNumberDateMatcher.start())).replaceAll("[hidden number]"));
            adminHidePhoneLikeNumberResult.append(adminHidePhoneLikeNumberDateMatcher.group());
            adminHidePhoneLikeNumberStartIndex = adminHidePhoneLikeNumberDateMatcher.end();
        }
        return adminHidePhoneLikeNumberResult.append(ADMIN_HIDE_PRIVATE_VALUE_NUMBER_PATTERN.matcher(
                adminHidePhoneLikeNumberText.substring(adminHidePhoneLikeNumberStartIndex))
                .replaceAll("[hidden number]")).toString();
    }
}
