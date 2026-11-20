package com.lms.backend.client.order;

import com.lms.backend.shared.value.SharedOrderStatus;

public record ClientCancelOrderResponse(String orderReference, SharedOrderStatus orderStatus) {
}
