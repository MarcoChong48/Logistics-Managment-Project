package com.lms.backend.admin.dashboard;

import static com.lms.backend.admin.dashboard.AdminDashboardRule.adminCountActiveOrder;
import static com.lms.backend.admin.dashboard.AdminDashboardRule.adminCountOpenCaseTotal;
import static com.lms.backend.admin.delivery.AdminDeliveryCaseService.adminCheckFailedDeliveryCaseFilter;

import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.admin.delivery.AdminDeliveryCaseService;
import com.lms.backend.admin.delivery.AdminSearchFailedDeliveryCaseRow;
import com.lms.backend.admin.product.AdminProductService;
import com.lms.backend.admin.product.AdminSearchProductRow;
import com.lms.backend.admin.refund.AdminRefundService;
import com.lms.backend.admin.shortage.AdminShortageCaseStatus;
import com.lms.backend.admin.shortage.AdminShortageService;
import com.lms.backend.admin.user.AdminUserService;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.value.SharedOrderStatus;

@Service
public class AdminDashboardService {

    // ===== SECTION 1: OTHER CLASSES THIS CLASS USES =====

    private final CustomerOrderRepository adminDashboardOrderRepository;
    private final AdminProductService adminDashboardProductService;
    private final AdminShortageService adminDashboardShortageService;
    private final AdminDeliveryCaseService adminDashboardDeliveryCaseService;
    private final AdminRefundService adminDashboardRefundService;
    private final AdminUserService adminDashboardUserService;

    /**
     * What it does: Connects the dashboard to the same data and rules as the Admin lists.
     * Input: The order repository and the linked lists' services.
     * Output: A dashboard and menu counter service.
     */
    public AdminDashboardService(CustomerOrderRepository adminDashboardOrderRepository,
            AdminProductService adminDashboardProductService, AdminShortageService adminDashboardShortageService,
            AdminDeliveryCaseService adminDashboardDeliveryCaseService, AdminRefundService adminDashboardRefundService,
            AdminUserService adminDashboardUserService) {
        this.adminDashboardOrderRepository = adminDashboardOrderRepository;
        this.adminDashboardProductService = adminDashboardProductService;
        this.adminDashboardShortageService = adminDashboardShortageService;
        this.adminDashboardDeliveryCaseService = adminDashboardDeliveryCaseService;
        this.adminDashboardRefundService = adminDashboardRefundService;
        this.adminDashboardUserService = adminDashboardUserService;
    }

    // ===== SECTION 2: adminViewDashboard =====

    /**
     * What it does: Summarizes the Admin lists as one database snapshot.
     * Input: None.
     * Output: The order, stock, case and account counts and up to five low-stock products.
     * Placeholder: PI-06 reads unavailable stock and PI-08 reads the shared workflow rows.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminViewDashboardResponse adminViewDashboard() {
        // PLACEHOLDER INPUT PI-08
        // From: Client, Warehouse, Delivery and Payment.
        // What: The shared workflow rows counted here and on the linked lists.
        // How to use: The other roles write these rows. The test tools supply local examples.
        Map<String, Long> adminViewDashboardOrderStatusCountMap =
                adminDashboardOrderRepository.adminViewDashboardOrderStatusCount().stream()
                        .collect(Collectors.toMap(AdminViewDashboardOrderStatusCount::orderStatus,
                                AdminViewDashboardOrderStatusCount::adminViewDashboardOrderCount));
        List<AdminViewDashboardOrderStatusCount> adminViewDashboardOrderStatusCountList =
                Arrays.stream(SharedOrderStatus.values()).map(orderStatus -> new AdminViewDashboardOrderStatusCount(
                        orderStatus.name(), adminViewDashboardOrderStatusCountMap.getOrDefault(orderStatus.name(), 0L)))
                        .toList();

        // PLACEHOLDER INPUT PI-06
        // From: Warehouse.
        // What: Product units not available for sale.
        // How to use: Replace the catalogue's unavailable-stock stand-in, which returns zero until integration.
        List<AdminSearchProductRow> adminViewDashboardLowStockRowList =
                adminDashboardProductService.adminReadLowStockProducts("", null);
        List<AdminViewDashboardLowStockProduct> adminViewDashboardLowStockProductList =
                adminViewDashboardLowStockRowList.stream()
                        .sorted(Comparator.comparingInt(AdminSearchProductRow::adminSearchProductAvailableStock)
                                .thenComparing(AdminSearchProductRow::SKU))
                        .limit(5)
                        .map(adminViewDashboardLowStockRow -> new AdminViewDashboardLowStockProduct(
                                adminViewDashboardLowStockRow.productID(), adminViewDashboardLowStockRow.SKU(),
                                adminViewDashboardLowStockRow.name(), adminViewDashboardLowStockRow.lowStockThreshold(),
                                adminViewDashboardLowStockRow.adminSearchProductAvailableStock()))
                        .toList();

        // Work queues share one delivery read and the same filtering rules as the separate lists.
        List<AdminSearchFailedDeliveryCaseRow> adminViewDashboardFailedDeliveryCaseRowList =
                adminDashboardDeliveryCaseService.adminReadFailedDeliveryCases("ALL", Instant.now());
        AdminViewMenuCounterResponse adminViewDashboardMenuCounter = adminReadMenuCounter(
                adminCountFailedDeliveryCase(adminViewDashboardFailedDeliveryCaseRowList, "NEEDS_DECISION"));
        long adminViewDashboardWaitingShortageCaseCount = adminDashboardShortageService.adminSearchShortageCase(
                AdminShortageCaseStatus.WAITING_RESTOCK.name(), 1).adminSearchShortageCaseTotalRowCount();
        long adminViewDashboardLockedUserCount = adminDashboardUserService.adminSearchUser(null, null, "LOCKED_NOW", 1)
                .adminSearchUserTotalRowCount();
        long adminViewDashboardOpenCaseTotalCount = adminCountOpenCaseTotal(
                adminViewDashboardMenuCounter.adminViewMenuCounterOpenShortageCaseCount(),
                adminViewDashboardMenuCounter.adminViewMenuCounterPendingClientInstructionCount(),
                adminViewDashboardMenuCounter.adminViewMenuCounterUnresolvedRefundCount());
        return new AdminViewDashboardResponse(adminViewDashboardOrderStatusCountList,
                adminCountActiveOrder(adminViewDashboardOrderStatusCountMap), adminViewDashboardLowStockRowList.size(),
                adminViewDashboardLowStockProductList,
                adminViewDashboardMenuCounter.adminViewMenuCounterOpenShortageCaseCount(),
                adminViewDashboardWaitingShortageCaseCount,
                adminViewDashboardMenuCounter.adminViewMenuCounterPendingClientInstructionCount(),
                adminCountFailedDeliveryCase(adminViewDashboardFailedDeliveryCaseRowList, "NO_CLIENT_REQUEST"),
                adminCountFailedDeliveryCase(adminViewDashboardFailedDeliveryCaseRowList, "FLAGGED"),
                adminViewDashboardMenuCounter.adminViewMenuCounterUnresolvedRefundCount(),
                adminViewDashboardLockedUserCount, adminViewDashboardOpenCaseTotalCount);
    }

    // ===== SECTION 3: adminViewMenuCounter =====

    /**
     * What it does: Reads the three work queues summarized in the side menu.
     * Input: None.
     * Output: The work counts, without any data change or audit log.
     * Placeholder: PI-08 reads the shared workflow rows.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminViewMenuCounterResponse adminViewMenuCounter() {
        // PLACEHOLDER INPUT PI-08
        // From: Client, Warehouse, Delivery and Payment.
        // What: The open shortage cases, pending client requests and unresolved refunds.
        // How to use: The other roles write these rows. The test tools supply local examples.
        long adminViewMenuCounterPendingClientInstructionCount = adminDashboardDeliveryCaseService
                .adminSearchFailedDeliveryCase("NEEDS_DECISION", 1).adminSearchFailedDeliveryCaseTotalRowCount();
        return adminReadMenuCounter(adminViewMenuCounterPendingClientInstructionCount);
    }

    // ===== SECTION 4: HELPER FUNCTIONS =====

    /**
     * What it does: Counts failed deliveries with the list's filter rule.
     * Input: The current case rows and the filter word.
     * Output: The number of matching cases.
     */
    private long adminCountFailedDeliveryCase(
            List<AdminSearchFailedDeliveryCaseRow> adminCountFailedDeliveryCaseRowList,
            String adminCountFailedDeliveryCaseFilter) {
        return adminCountFailedDeliveryCaseRowList.stream().filter(adminCountFailedDeliveryCaseRow ->
                adminCheckFailedDeliveryCaseFilter(adminCountFailedDeliveryCaseRow, adminCountFailedDeliveryCaseFilter))
                .count();
    }

    /**
     * What it does: Reads the other two work counts using the same filters as their lists.
     * Input: The pending client request count already worked out by the failed delivery list rule.
     * Output: The three work counts for the dashboard and the side menu.
     */
    private AdminViewMenuCounterResponse adminReadMenuCounter(long adminReadMenuCounterPendingClientInstructionCount) {
        long adminReadMenuCounterOpenShortageCaseCount = adminDashboardShortageService.adminSearchShortageCase(
                AdminShortageCaseStatus.OPEN.name(), 1).adminSearchShortageCaseTotalRowCount();
        long adminReadMenuCounterUnresolvedRefundCount = adminDashboardRefundService.adminSearchRefund(true, 1)
                .adminSearchRefundTotalRowCount();
        return new AdminViewMenuCounterResponse(adminReadMenuCounterOpenShortageCaseCount,
                adminReadMenuCounterPendingClientInstructionCount, adminReadMenuCounterUnresolvedRefundCount);
    }
}
