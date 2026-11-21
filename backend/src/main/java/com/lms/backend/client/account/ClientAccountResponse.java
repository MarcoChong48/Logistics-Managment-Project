package com.lms.backend.client.account;

import java.time.Instant;

record ClientAccountResponse(String userId, String username, Instant createdAt, ClientDeliveryDefaults deliveryDefaults) {
}
