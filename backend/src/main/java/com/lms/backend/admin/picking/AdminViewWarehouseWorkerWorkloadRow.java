package com.lms.backend.admin.picking;

// F6.9: The worker and the number of open assignments on picking orders.
public record AdminViewWarehouseWorkerWorkloadRow(
        Long userID,
        String username,
        long adminViewWarehouseWorkerWorkloadActiveTaskCount) {
}
