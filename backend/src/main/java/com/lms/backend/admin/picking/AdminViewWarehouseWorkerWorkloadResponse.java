package com.lms.backend.admin.picking;

import java.util.List;

// F6.9: Every active Warehouse Worker and their current workload.
public record AdminViewWarehouseWorkerWorkloadResponse(
        List<AdminViewWarehouseWorkerWorkloadRow> adminViewWarehouseWorkerWorkloadRowList) {
}
