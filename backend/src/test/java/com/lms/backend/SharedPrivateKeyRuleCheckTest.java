package com.lms.backend;

import static com.lms.backend.shared.log.SharedPrivateKeyRule.sharedCheckPrivateKeyName;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SharedPrivateKeyRuleCheckTest {

    // ===== SECTION 1: PRIVATE KEY NAMES =====

    /**
     * What it does: Checks private keys, safe keys and the two network-address exceptions.
     * Input: A log key and the expected privacy result.
     * Output: Pass when the rule gives that result.
     */
    @ParameterizedTest
    @CsvSource({
        "phone,true", "defaultPhone,true", "address,true", "addressText,true", "recipientName,true",
        "password,true", "passwordHash,true", "token,true", "clientSecret,true",
        "username,false", "role,false", "reason,false", "accountStatus,false", "identityCheckMethod,false",
        "orderReference,false", "deliveryDetailID,false", "ipAddress,false", "sharedSendRequestWebAddress,false"
    })
    void sharedCheckPrivateKeyNameResult(String sharedCheckPrivateKeyNameValue,
            boolean sharedCheckPrivateKeyNameExpected) {
        assertEquals(sharedCheckPrivateKeyNameExpected, sharedCheckPrivateKeyName(sharedCheckPrivateKeyNameValue));
    }
}
