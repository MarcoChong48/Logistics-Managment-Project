package com.lms.backend.client.order;

import java.math.BigDecimal;

public interface CheckoutRefundExecutor {

    // Reuse the LMS refund ID when retrying a full sandbox refund. A returned reference does not prove settlement.
    String requestFullRefund(long refundId, String providerPaymentReference, BigDecimal amountHkd);
}
