package com.lms.backend;

import static com.lms.backend.admin.common.AdminCommonRule.adminCheckReasonText;
import static com.lms.backend.admin.common.AdminCommonRule.adminCountPage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.lms.backend.shared.error.SharedRuleException;

class AdminCommonRuleCheckTest {

    // ===== SECTION 1: REASONS =====

    /**
     * What it does: Supplies reason inputs and their expected trimmed values.
     * Input: None.
     * Output: Seven rule examples, with null expected text for invalid inputs.
     */
    static Stream<Arguments> adminCheckReasonTextCases() {
        return Stream.of(
                Arguments.of("", null),
                Arguments.of("abcd", null),
                Arguments.of("     ", null),
                Arguments.of("abcde", "abcde"),
                Arguments.of("x".repeat(300), "x".repeat(300)),
                Arguments.of("x".repeat(301), null),
                Arguments.of("   valid reason   ", "valid reason"));
    }

    /**
     * What it does: Checks which reasons are accepted and how surrounding spaces are handled.
     * Input: A reason and the expected trimmed text, if accepted.
     * Output: Pass when the rule gives the expected text or the prescribed error.
     */
    @ParameterizedTest
    @MethodSource("adminCheckReasonTextCases")
    void adminCheckReasonTextResult(String adminCheckReasonTextValue, String adminCheckReasonTextExpected) {
        if (adminCheckReasonTextExpected == null) {
            SharedRuleException adminCheckReasonTextFailure = assertThrows(SharedRuleException.class,
                    () -> adminCheckReasonText(adminCheckReasonTextValue));
            assertEquals("ADMIN_REASON_REQUIRED", adminCheckReasonTextFailure.getCode());
        } else {
            assertEquals(adminCheckReasonTextExpected, adminCheckReasonText(adminCheckReasonTextValue));
        }
    }

    // ===== SECTION 2: PAGE COUNT =====

    /**
     * What it does: Checks an empty list, a full page and the first row on page two.
     * Input: A row count and the expected number of pages.
     * Output: Pass when page count matches.
     */
    @ParameterizedTest
    @CsvSource({"0,1", "20,1", "21,2"})
    void adminCountPageResult(long adminCountPageTotalRowCount, int adminCountPageExpected) {
        assertEquals(adminCountPageExpected, adminCountPage(adminCountPageTotalRowCount));
    }
}
