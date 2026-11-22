package com.lms.backend.admin.delivery;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lms.backend.admin.common.AdminDecidedRowResponse;

@RestController
@RequestMapping("/api/admin/held-packages")
public class AdminHeldPackageController {

    // ===== SECTION 1: SERVICE THIS CLASS CALLS =====

    private final AdminHeldPackageService adminHeldPackageService;

    /**
     * What it does: Connects the held-package routes to their service.
     * Input: The held-package service.
     * Output: The controller for the held-package routes.
     */
    public AdminHeldPackageController(AdminHeldPackageService adminHeldPackageService) {
        this.adminHeldPackageService = adminHeldPackageService;
    }

    // ===== SECTION 2: GET /api/admin/held-packages =====

    /**
     * What it does: Lists packages held by Delivery Workers and their current responsibility.
     * Input: The requested page number.
     * Output: The selected page of held packages.
     */
    @GetMapping
    public AdminSearchHeldPackageResponse adminSearchHeldPackage(
            @RequestParam(defaultValue = "1") int adminSearchHeldPackagePageNumber) {
        return adminHeldPackageService.adminSearchHeldPackage(adminSearchHeldPackagePageNumber);
    }

    // ===== SECTION 3: GET /api/admin/held-packages/worker-workload =====

    /**
     * What it does: Lists active Delivery Workers by physical workload.
     * Input: None.
     * Output: All active Delivery Workers with their held and responsible counts.
     */
    @GetMapping("/worker-workload")
    public AdminViewDeliveryWorkerWorkloadResponse adminViewDeliveryWorkerWorkload() {
        return adminHeldPackageService.adminViewDeliveryWorkerWorkload();
    }

    // ===== SECTION 4: POST /api/admin/held-packages/{packageID}/reassign =====

    /**
     * What it does: Records who is responsible for a held package.
     * Input: Package ID, new Delivery Worker ID and reassignment reason.
     * Output: The saved decision or the error for a missing package, invalid input or state conflict.
     */
    @PostMapping("/{packageID}/reassign")
    public AdminDecidedRowResponse adminReassignDelivery(@PathVariable Long packageID,
            @RequestBody AdminReassignDeliveryRequest adminReassignDeliveryRequest) {
        return adminHeldPackageService.adminReassignDelivery(packageID, adminReassignDeliveryRequest);
    }
}
