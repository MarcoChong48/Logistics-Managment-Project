package com.lms.backend.admin.picking;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lms.backend.admin.common.AdminDecidedRowResponse;

@RestController
@RequestMapping("/api/admin/picking-tasks")
public class AdminPickingController {

    // ===== SECTION 1: SERVICE THIS CLASS CALLS =====

    private final AdminPickingService adminPickingService;

    /**
     * What it does: Connects the picking routes to the Admin picking service.
     * Input: The picking service.
     * Output: A controller for the picking routes.
     */
    public AdminPickingController(AdminPickingService adminPickingService) {
        this.adminPickingService = adminPickingService;
    }

    // ===== SECTION 2: GET /api/admin/picking-tasks =====

    /**
     * What it does: Lists the tasks with an open picking assignment.
     * Input: The requested page number.
     * Output: The page of open picking assignments.
     */
    @GetMapping
    public AdminSearchPickingTaskResponse adminSearchPickingTask(
            @RequestParam(defaultValue = "1") int adminSearchPickingTaskPageNumber) {
        return adminPickingService.adminSearchPickingTask(adminSearchPickingTaskPageNumber);
    }

    // ===== SECTION 3: GET /api/admin/picking-tasks/worker-workload =====

    /**
     * What it does: Lists active Warehouse Workers for reassignment.
     * Input: None.
     * Output: The workers with their active task counts.
     */
    @GetMapping("/worker-workload")
    public AdminViewWarehouseWorkerWorkloadResponse adminViewWarehouseWorkerWorkload() {
        return adminPickingService.adminViewWarehouseWorkerWorkload();
    }

    // ===== SECTION 4: POST /api/admin/picking-tasks/{pickingTaskID}/reassign =====

    /**
     * What it does: Changes the worker of an active picking task without altering its progress.
     * Input: The task ID, new worker ID and reason.
     * Output: The saved worker and decision details, a missing task error or a conflict.
     */
    @PostMapping("/{pickingTaskID}/reassign")
    public AdminDecidedRowResponse adminReassignPickingTask(@PathVariable Long pickingTaskID,
            @RequestBody AdminReassignPickingTaskRequest adminReassignPickingTaskRequest) {
        return adminPickingService.adminReassignPickingTask(pickingTaskID, adminReassignPickingTaskRequest);
    }
}
