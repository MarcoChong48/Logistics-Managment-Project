package com.lms.backend.admin.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// F5.2: public order data for the Admin screens.
public record AdminViewOrderResponse(
        Long orderID,
        String orderReference,
        Long clientUserID,
        String adminViewOrderClientUsername,
        String orderStatus,
        Instant createdTime,
        Instant paymentDeadline,
        BigDecimal totalAmount,
        int adminViewOrderDeliveryDetailVersionCount,
        List<AdminViewOrderItemRow> adminViewOrderItemList,
        List<AdminViewOrderPaymentRow> adminViewOrderPaymentList,
        List<AdminViewOrderRefundRow> adminViewOrderRefundList,
        AdminViewOrderPickingTask adminViewOrderPickingTask,
        List<AdminViewOrderPickingAssignmentRow> adminViewOrderPickingAssignmentList,
        AdminViewOrderPackage adminViewOrderPackage,
        List<AdminViewOrderDeliveryAttemptRow> adminViewOrderDeliveryAttemptList,
        List<AdminViewOrderClientInstructionRow> adminViewOrderClientInstructionList,
        List<AdminViewOrderShortageCaseRow> adminViewOrderShortageCaseList,
        List<AdminViewOrderTimelineRow> adminViewOrderTimelineList) {
}
