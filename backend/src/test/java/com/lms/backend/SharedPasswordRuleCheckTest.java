package com.lms.backend;

import static com.lms.backend.shared.login.SharedPasswordRule.sharedCheckPasswordRule;
import static com.lms.backend.shared.login.SharedPasswordRule.sharedCreateTemporaryPassword;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class SharedPasswordRuleCheckTest {

    // ===== SECTION 1: PASSWORD RULE CHECKS =====

    /**
     * What it does: Checks that a short password fails the length rule first.
     * Input: A password with three characters.
     * Output: Pass when the first failure is the lower length limit.
     */
    @Test
    void sharedCheckPasswordRuleRejectsTooShort() {
        assertEquals("Use at least 8 characters.", sharedCheckPasswordRule("Ab1").getFirst());
    }

    /**
     * What it does: Checks priority when both length limits are exceeded.
     * Input: A 73-character ASCII password.
     * Output: Pass when the character failure comes before the byte failure.
     */
    @Test
    void sharedCheckPasswordRuleRejectsTooLong() {
        assertEquals(List.of("Use at most 72 characters.",
                        "Use fewer characters. The password can use at most 72 bytes."),
                sharedCheckPasswordRule("Aa1" + "x".repeat(70)));
    }

    /**
     * What it does: Checks that a missing capital letter is refused.
     * Input: Lowercase letters and a digit.
     * Output: Pass when the only failure names the capital letter requirement.
     */
    @Test
    void sharedCheckPasswordRuleRequiresCapitalLetter() {
        assertEquals(List.of("Use at least one capital letter from A to Z."), sharedCheckPasswordRule("abcdefg1"));
    }

    /**
     * What it does: Checks that a missing small letter is refused.
     * Input: Capital letters and a digit.
     * Output: Pass when the only failure names the small letter requirement.
     */
    @Test
    void sharedCheckPasswordRuleRequiresSmallLetter() {
        assertEquals(List.of("Use at least one small letter from a to z."), sharedCheckPasswordRule("ABCDEFG1"));
    }

    /**
     * What it does: Checks that a missing ASCII digit is refused.
     * Input: Capital and small letters with no digit.
     * Output: Pass when the only failure names the digit requirement.
     */
    @Test
    void sharedCheckPasswordRuleRequiresDigit() {
        assertEquals(List.of("Use at least one number from 0 to 9."), sharedCheckPasswordRule("Abcdefgh"));
    }

    /**
     * What it does: Checks that the minimum length is accepted.
     * Input: A valid password with exactly eight characters.
     * Output: Pass when no rule fails.
     */
    @Test
    void sharedCheckPasswordRuleAcceptsMinimumLength() {
        assertTrue(sharedCheckPasswordRule("Abcdefg1").isEmpty());
    }

    /**
     * What it does: Checks that the maximum length is accepted.
     * Input: A valid ASCII password with exactly 72 characters.
     * Output: Pass when no rule fails.
     */
    @Test
    void sharedCheckPasswordRuleAcceptsMaximumLength() {
        assertTrue(sharedCheckPasswordRule("Aa1" + "x".repeat(69)).isEmpty());
    }

    /**
     * What it does: Checks that a normal mixed password passes.
     * Input: A password that meets all limits and character requirements.
     * Output: Pass when no rule fails.
     */
    @Test
    void sharedCheckPasswordRuleAcceptsNormalPassword() {
        assertTrue(sharedCheckPasswordRule("Normal!Pass123").isEmpty());
    }

    /**
     * What it does: Checks the accepted UTF-8 byte boundary.
     * Input: Three ASCII characters and twenty-three three-byte characters.
     * Output: Pass when no rule fails at exactly 72 bytes.
     */
    @Test
    void sharedCheckPasswordRuleAcceptsMaximumByteCount() {
        assertTrue(sharedCheckPasswordRule("Aa1" + "界".repeat(23)).isEmpty());
    }

    /**
     * What it does: Checks that a short password can still exceed the UTF-8 byte limit.
     * Input: Three ASCII characters and twenty-four three-byte characters.
     * Output: Pass when the only failure is the byte limit.
     */
    @Test
    void sharedCheckPasswordRuleRejectsExcessBytes() {
        assertEquals(List.of("Use fewer characters. The password can use at most 72 bytes."),
                sharedCheckPasswordRule("Aa1" + "界".repeat(24)));
    }

    // ===== SECTION 2: TEMPORARY PASSWORD CHECKS =====

    /**
     * What it does: Checks the format and rules of 200 generated temporary passwords.
     * Input: None.
     * Output: Pass when each password passes every check and excludes the look-alikes.
     */
    @Test
    void sharedCreateTemporaryPasswordUsesRequiredFormat() {
        // Draw a new password each time so the checks cover a range of generated values.
        for (int sharedCreateTemporaryPasswordIndex = 0; sharedCreateTemporaryPasswordIndex < 200;
             sharedCreateTemporaryPasswordIndex++) {
            String sharedCreateTemporaryPasswordValue = sharedCreateTemporaryPassword();
            assertTrue(sharedCreateTemporaryPasswordValue.matches("[A-Za-z0-9]{4}(-[A-Za-z0-9]{4}){2}"));
            assertFalse(sharedCreateTemporaryPasswordValue.matches(".*[0O1lI].*"));
            assertTrue(sharedCheckPasswordRule(sharedCreateTemporaryPasswordValue).isEmpty());
        }
    }
}
