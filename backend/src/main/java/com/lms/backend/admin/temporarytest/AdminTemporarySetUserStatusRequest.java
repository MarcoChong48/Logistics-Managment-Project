// TEMPORARY TEST CODE - remove before integration
// Stage 1 supplies the inputs for a test of account session revocation.
package com.lms.backend.admin.temporarytest;

import com.lms.backend.shared.value.SharedAccountStatus;

public record AdminTemporarySetUserStatusRequest(String adminTemporarySetUserStatusUsername,
        SharedAccountStatus adminTemporarySetUserStatusAccountStatus) {
}
