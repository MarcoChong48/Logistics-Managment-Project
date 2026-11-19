package com.lms.backend.shared.login;

import static com.lms.backend.shared.login.SharedLoginRule.SHARED_PASSWORD_MAXIMUM_LENGTH;
import static com.lms.backend.shared.login.SharedLoginRule.SHARED_PASSWORD_MAXIMUM_UTF8_BYTE_COUNT;
import static com.lms.backend.shared.login.SharedLoginRule.SHARED_PASSWORD_MINIMUM_LENGTH;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

public final class SharedPasswordRule {

    // ===== SECTION 1: CONSTRUCTOR =====

    /**
     * What it does: Keeps the static rules from being created as an object.
     * Input: None.
     * Output: None.
     */
    private SharedPasswordRule() {
    }

    // ===== SECTION 2: sharedCheckPasswordRule =====

    /**
     * What it does: Checks the entered password against the shared rules in message order.
     * Input: The exact password text.
     * Output: The list of unmet rules, or an empty list when all rules pass.
     */
    public static List<String> sharedCheckPasswordRule(String sharedCheckPasswordRulePassword) {
        // An omitted password fails the same rules as an empty one.
        if (sharedCheckPasswordRulePassword == null) {
            sharedCheckPasswordRulePassword = "";
        }

        // Count characters first, then apply the BCrypt byte limit to the unchanged text.
        List<String> sharedCheckPasswordRuleFailureList = new ArrayList<>();
        int sharedCheckPasswordRuleCharacterCount =
                sharedCheckPasswordRulePassword.codePointCount(0, sharedCheckPasswordRulePassword.length());
        if (sharedCheckPasswordRuleCharacterCount < SHARED_PASSWORD_MINIMUM_LENGTH) {
            sharedCheckPasswordRuleFailureList.add("Use at least 8 characters.");
        }
        if (sharedCheckPasswordRuleCharacterCount > SHARED_PASSWORD_MAXIMUM_LENGTH) {
            sharedCheckPasswordRuleFailureList.add("Use at most 72 characters.");
        }
        if (sharedCheckPasswordRulePassword.getBytes(StandardCharsets.UTF_8).length
                > SHARED_PASSWORD_MAXIMUM_UTF8_BYTE_COUNT) {
            sharedCheckPasswordRuleFailureList.add("Use fewer characters. The password can use at most 72 bytes.");
        }

        // The required letter and number groups use the stated ASCII ranges.
        if (sharedCheckPasswordRulePassword.chars().noneMatch(
                sharedCheckPasswordRuleCharacter -> sharedCheckPasswordRuleCharacter >= 'A'
                        && sharedCheckPasswordRuleCharacter <= 'Z')) {
            sharedCheckPasswordRuleFailureList.add("Use at least one capital letter from A to Z.");
        }
        if (sharedCheckPasswordRulePassword.chars().noneMatch(
                sharedCheckPasswordRuleCharacter -> sharedCheckPasswordRuleCharacter >= 'a'
                        && sharedCheckPasswordRuleCharacter <= 'z')) {
            sharedCheckPasswordRuleFailureList.add("Use at least one small letter from a to z.");
        }
        if (sharedCheckPasswordRulePassword.chars().noneMatch(
                sharedCheckPasswordRuleCharacter -> sharedCheckPasswordRuleCharacter >= '0'
                        && sharedCheckPasswordRuleCharacter <= '9')) {
            sharedCheckPasswordRuleFailureList.add("Use at least one number from 0 to 9.");
        }
        return sharedCheckPasswordRuleFailureList;
    }

    // ===== SECTION 3: sharedCreateTemporaryPassword =====

    /**
     * What it does: Draws a temporary password from characters that are easy to distinguish.
     * Input: None.
     * Output: Three groups of four random characters separated by hyphens.
     */
    public static String sharedCreateTemporaryPassword() {
        SecureRandom sharedCreateTemporaryPasswordGenerator = new SecureRandom();
        String sharedCreateTemporaryPasswordAlphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
        String sharedCreateTemporaryPasswordValue;

        // Draw a fresh candidate if any required character group is missing.
        do {
            StringBuilder sharedCreateTemporaryPasswordText = new StringBuilder();
            for (int sharedCreateTemporaryPasswordIndex = 0; sharedCreateTemporaryPasswordIndex < 12;
                 sharedCreateTemporaryPasswordIndex++) {
                if (sharedCreateTemporaryPasswordIndex > 0 && sharedCreateTemporaryPasswordIndex % 4 == 0) {
                    sharedCreateTemporaryPasswordText.append('-');
                }
                int sharedCreateTemporaryPasswordCharacterIndex =
                        sharedCreateTemporaryPasswordGenerator.nextInt(sharedCreateTemporaryPasswordAlphabet.length());
                sharedCreateTemporaryPasswordText.append(
                        sharedCreateTemporaryPasswordAlphabet.charAt(sharedCreateTemporaryPasswordCharacterIndex));
            }
            sharedCreateTemporaryPasswordValue = sharedCreateTemporaryPasswordText.toString();
        } while (!sharedCheckPasswordRule(sharedCreateTemporaryPasswordValue).isEmpty());

        return sharedCreateTemporaryPasswordValue;
    }
}
