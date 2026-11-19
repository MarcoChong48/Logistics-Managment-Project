// TEMPORARY TEST CODE - remove before integration
// Stage 1 supplies the inputs for a test account with an issued password.
package com.lms.backend.admin.temporarytest;

import com.lms.backend.shared.value.SharedUserRole;

public record AdminTemporaryCreateTestUserRequest(SharedUserRole adminTemporaryCreateTestUserRole) {
}
