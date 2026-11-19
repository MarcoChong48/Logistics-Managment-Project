package com.lms.backend.shared.log;

import java.util.Locale;

public final class SharedPrivateKeyRule {

    // ===== SECTION 1: CONSTRUCTOR =====

    /**
     * What it does: Keeps the pure rule from being created as an object.
     * Input: None.
     * Output: None.
     */
    private SharedPrivateKeyRule() {
    }

    // ===== SECTION 2: sharedCheckPrivateKeyName =====

    /**
     * What it does: Checks if a field name refers to private information.
     * Input: A name that could appear in an audit value.
     * Output: True when a value under this name must be kept private.
     */
    public static boolean sharedCheckPrivateKeyName(String sharedCheckPrivateKeyNameValue) {
        // The two address exceptions are names for network locations.
        if (sharedCheckPrivateKeyNameValue.equals("ipAddress") || sharedCheckPrivateKeyNameValue.endsWith("WebAddress")) {
            return false;
        }
        String sharedCheckPrivateKeyNameLowerCase = sharedCheckPrivateKeyNameValue.toLowerCase(Locale.ROOT);
        return sharedCheckPrivateKeyNameLowerCase.contains("phone")
                || sharedCheckPrivateKeyNameLowerCase.contains("address")
                || sharedCheckPrivateKeyNameLowerCase.contains("recipient")
                || sharedCheckPrivateKeyNameLowerCase.contains("password")
                || sharedCheckPrivateKeyNameLowerCase.contains("token")
                || sharedCheckPrivateKeyNameLowerCase.contains("secret");
    }
}
