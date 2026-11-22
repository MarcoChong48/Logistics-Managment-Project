package com.lms.backend.admin.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {

    // ===== SECTION 1: SERVICE THIS CLASS CALLS =====

    private final AdminDashboardService adminDashboardService;

    /**
     * What it does: Connects the dashboard routes to the dashboard service.
     * Input: The dashboard service.
     * Output: The controller for the two Admin routes.
     */
    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    // ===== SECTION 2: GET /api/admin/dashboard =====

    /**
     * What it does: Reads the numbers and stock rows used by the dashboard.
     * Input: None.
     * Output: The dashboard counts and low-stock products.
     */
    @GetMapping("/dashboard")
    public AdminViewDashboardResponse adminViewDashboard() {
        return adminDashboardService.adminViewDashboard();
    }

    // ===== SECTION 3: GET /api/admin/menu-counters =====

    /**
     * What it does: Reads the three work counts for the side menu.
     * Input: None.
     * Output: The open shortage, pending client request and unresolved refund counts.
     */
    @GetMapping("/menu-counters")
    public AdminViewMenuCounterResponse adminViewMenuCounter() {
        return adminDashboardService.adminViewMenuCounter();
    }
}
