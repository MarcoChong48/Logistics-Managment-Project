package com.lms.backend.admin.delivery;

import java.util.List;

/** F7.8: The current workloads of active Delivery Workers. */
public record AdminViewDeliveryWorkerWorkloadResponse(
        List<AdminViewDeliveryWorkerWorkloadRow> adminViewDeliveryWorkerWorkloadRowList) {
}
