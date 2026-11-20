package com.lms.backend;

import static com.lms.backend.admin.common.AdminPrivateValueRule.adminHidePrivateValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AdminPrivateValueRuleCheckTest {

    // ===== SECTION 1: PRIVATE JSON FIELDS =====

    /**
     * What it does: Checks that a private JSON key always hides its value.
     * Input: An agreed private key name.
     * Output: The check passes when the private value is replaced.
     */
    @ParameterizedTest
    @ValueSource(strings = {"phone", "defaultPhone", "address", "addressText", "recipientName", "password",
            "passwordHash", "token", "sharedSecret"})
    void adminHidePrivateValueMasksJSONKey(String adminHidePrivateValueKey) {
        assertEquals("{\"" + adminHidePrivateValueKey + "\":\"[hidden]\"}",
                adminHidePrivateValue("{\"" + adminHidePrivateValueKey + "\":\"private marker\"}"));
    }

    /**
     * What it does: Checks private keys inside objects and lists while keeping safe fields.
     * Input: A nested JSON value with a mix of private and safe fields.
     * Output: The check passes when only the private items and phone-like numbers change.
     */
    @Test
    void adminHidePrivateValueMasksNestedData() {
        assertEquals("{\"items\":[{\"pHONE\":\"[hidden]\"},\"[hidden number]\"],\"ipAddress\":\"192.0.2.10\","
                        + "\"orderReference\":\"TEST-0001\",\"receiptWebAddress\":\"https://example.test/receipt\"}",
                adminHidePrivateValue("{\"items\":[{\"pHONE\":[\"private marker\"]},555000001234],"
                        + "\"ipAddress\":\"192.0.2.10\",\"orderReference\":\"TEST-0001\","
                        + "\"receiptWebAddress\":\"https://example.test/receipt\"}"));
    }

    /**
     * What it does: Checks that structured data beyond the decoder's depth limit stays private.
     * Input: A JSON value with a private marker deeper than the parser can examine.
     * Output: The check passes when the value is hidden instead of shown as free text.
     */
    @Test
    void adminHidePrivateValueMasksOverlyDeepJSON() {
        String adminHidePrivateValueDeepJSON = "[".repeat(1100) + "{\"phone\":\"private marker\"}"
                + "]".repeat(1100);
        assertEquals("[hidden]", adminHidePrivateValue(adminHidePrivateValueDeepJSON));
    }

    // ===== SECTION 2: PHONE-LIKE NUMBERS =====

    /**
     * What it does: Checks that eight or more digits can be detected across spaces or hyphens.
     * Input: A phone-like value using one of the tested number formats.
     * Output: The check passes when the number is replaced and its surrounding words stay.
     */
    @ParameterizedTest
    @ValueSource(strings = {"5550 0000", "5550 0000 1234", "+1234 5678 9012", "9123-4567", "5550 0000-1234"})
    void adminHidePrivateValueMasksPhoneLikeNumbers(String adminHidePrivateValueNumber) {
        assertEquals("call [hidden number] about order TEST-0001 on 2026-11-10",
                adminHidePrivateValue("call " + adminHidePrivateValueNumber + " about order TEST-0001 on 2026-11-10"));
    }

    /**
     * What it does: Checks that dates, timestamps, short numbers and order references stay visible.
     * Input: A safe text value.
     * Output: The check passes when the value is unchanged.
     */
    @ParameterizedTest
    @ValueSource(strings = {"TEST-0001", "2026-11-10", "2026-11-10T08:30:00Z", "1234567"})
    void adminHidePrivateValueKeepsSafeText(String adminHidePrivateValueSafeText) {
        assertEquals(adminHidePrivateValueSafeText, adminHidePrivateValue(adminHidePrivateValueSafeText));
    }
}
