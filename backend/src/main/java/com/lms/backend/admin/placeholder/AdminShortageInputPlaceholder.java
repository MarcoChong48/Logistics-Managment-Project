package com.lms.backend.admin.placeholder;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.admin.shortage.AdminShortageCaseStatus;
import com.lms.backend.shared.database.AppUserRepository;
import com.lms.backend.shared.database.CustomerOrder;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.database.OrderItem;
import com.lms.backend.shared.database.OrderItemRepository;
import com.lms.backend.shared.database.ShortageCase;
import com.lms.backend.shared.database.ShortageCaseRepository;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.value.SharedOrderStatus;

@Component
public class AdminShortageInputPlaceholder {

    // ===== SECTION 1: DATABASE ACCESS =====

    private final CustomerOrderRepository adminShortageOrderRepository;
    private final OrderItemRepository adminShortageOrderItemRepository;
    private final AppUserRepository adminShortageUserRepository;
    private final ShortageCaseRepository adminShortageCaseRepository;

    /**
     * What it does: Connects the Warehouse shortage handoff to the shared tables.
     * Input: The order, order item, user and shortage case repositories.
     * Output: The component used by Warehouse and by the local test tools.
     */
    public AdminShortageInputPlaceholder(CustomerOrderRepository adminShortageOrderRepository,
            OrderItemRepository adminShortageOrderItemRepository, AppUserRepository adminShortageUserRepository,
            ShortageCaseRepository adminShortageCaseRepository) {
        this.adminShortageOrderRepository = adminShortageOrderRepository;
        this.adminShortageOrderItemRepository = adminShortageOrderItemRepository;
        this.adminShortageUserRepository = adminShortageUserRepository;
        this.adminShortageCaseRepository = adminShortageCaseRepository;
    }

    // ===== SECTION 2: WAREHOUSE HANDOFFS =====

    /**
     * What it does: Saves a verified report as the active case for an order line.
     * Input: The order ID, product ID, missing quantity, report note and reporting worker ID.
     * Output: The new case ID, or a rule error if the report is refused.
     */
    @Transactional
    public long adminReceiveShortageReportPlaceholder(long orderID, long productID, int missingQuantity,
            String reportNote, long reportedByWorkerID) {
        // PLACEHOLDER INPUT PI-01
        // From: Warehouse.
        // What: The worker has verified a shortage and paused work on the order.
        // How to use: Call this function with the report. The temporary test tool calls it until Warehouse is connected.
        // The order lock keeps reporting and order decisions in the same order.
        CustomerOrder adminReceiveShortageReportOrder = adminShortageOrderRepository.findByOrderIDForUpdate(orderID)
                .orElseThrow(() -> new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_ORDER_STATE_CONFLICT",
                        "The order cannot receive a shortage report in its current state."));
        List<ShortageCase> adminReceiveShortageReportOrderCases =
                adminShortageCaseRepository.findAllByOrderIDForUpdate(orderID);
        if (SharedOrderStatus.CANCELLED.name().equals(adminReceiveShortageReportOrder.orderStatus)
                || SharedOrderStatus.DELIVERED.name().equals(adminReceiveShortageReportOrder.orderStatus)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_ORDER_STATE_CONFLICT",
                    "The order cannot receive a shortage report in its current state.");
        }

        // The report must describe an item in the order and have a known author.
        OrderItem adminReceiveShortageReportItem =
                adminShortageOrderItemRepository.findByOrderIDAndProductID(orderID, productID)
                        .orElseThrow(() -> new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_INPUT_INVALID",
                                "The product is not an item of this order."));
        if (missingQuantity < 1 || missingQuantity > adminReceiveShortageReportItem.quantity) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_INPUT_INVALID",
                    "Enter a missing quantity between 1 and the ordered quantity.");
        }
        if (reportNote != null && reportNote.codePointCount(0, reportNote.length()) > 600) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_INPUT_INVALID",
                    "Use at most 600 characters for the shortage note.");
        }
        if (!adminShortageUserRepository.existsById(reportedByWorkerID)) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_INPUT_INVALID",
                    "The reporting user does not exist.");
        }

        // A waiting case still covers the product until it is resolved or cancelled.
        if (adminReceiveShortageReportOrderCases.stream().anyMatch(adminReceiveShortageReportExistingCase ->
                adminReceiveShortageReportExistingCase.productID == productID
                && (AdminShortageCaseStatus.OPEN.name().equals(adminReceiveShortageReportExistingCase.caseStatus)
                || AdminShortageCaseStatus.WAITING_RESTOCK.name()
                        .equals(adminReceiveShortageReportExistingCase.caseStatus)))) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_SHORTAGE_CASE_STATE_CONFLICT",
                    "An active shortage case already exists for this order and product.");
        }
        ShortageCase adminReceiveShortageReportCase = new ShortageCase();
        adminReceiveShortageReportCase.orderID = orderID;
        adminReceiveShortageReportCase.productID = productID;
        adminReceiveShortageReportCase.missingQuantity = missingQuantity;
        adminReceiveShortageReportCase.reportNote = reportNote;
        adminReceiveShortageReportCase.reportedByWorkerID = reportedByWorkerID;
        adminReceiveShortageReportCase.caseStatus = AdminShortageCaseStatus.OPEN.name();
        adminReceiveShortageReportCase.reportedTime = Instant.now();
        return adminShortageCaseRepository.save(adminReceiveShortageReportCase).shortageCaseID;
    }

    /**
     * What it does: Closes a waiting shortage case after Warehouse confirms the repair.
     * Input: The shortage case ID and the resolving worker ID.
     * Output: None. Saves the resolver and the time, or throws a rule error.
     */
    @Transactional
    public void adminReceiveShortageResolvedPlaceholder(long shortageCaseID, long resolvedByWorkerID) {
        // PLACEHOLDER INPUT PI-02
        // From: Warehouse.
        // What: The item was replaced and packing was confirmed again.
        // How to use: Call this function after packing is confirmed. The temporary test tool calls it for local checks.
        // Lock the parent order and then check the saved case under its own lock.
        long orderID = adminShortageCaseRepository.findOrderIDByShortageCaseID(shortageCaseID)
                .orElseThrow(AdminShortageInputPlaceholder::adminShortageCaseNotFound);
        adminShortageOrderRepository.findByOrderIDForUpdate(orderID)
                .orElseThrow(AdminShortageInputPlaceholder::adminShortageCaseNotFound);
        ShortageCase adminReceiveShortageResolvedCase =
                adminShortageCaseRepository.findByShortageCaseIDForUpdate(shortageCaseID)
                        .orElseThrow(AdminShortageInputPlaceholder::adminShortageCaseNotFound);
        if (!AdminShortageCaseStatus.WAITING_RESTOCK.name().equals(adminReceiveShortageResolvedCase.caseStatus)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_SHORTAGE_CASE_STATE_CONFLICT",
                    "Only a case waiting for restock can be marked resolved.");
        }
        if (!adminShortageUserRepository.existsById(resolvedByWorkerID)) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_INPUT_INVALID",
                    "The resolving user does not exist.");
        }

        // Keep the original report and waiting decision alongside the resolution.
        adminReceiveShortageResolvedCase.caseStatus = AdminShortageCaseStatus.RESOLVED.name();
        adminReceiveShortageResolvedCase.resolvedByWorkerID = resolvedByWorkerID;
        adminReceiveShortageResolvedCase.resolvedTime = Instant.now();
    }

    /**
     * What it does: Builds the error for a shortage case that cannot be found.
     * Input: None.
     * Output: The public 404 error.
     */
    private static SharedRuleException adminShortageCaseNotFound() {
        return new SharedRuleException(HttpStatus.NOT_FOUND, "ADMIN_SHORTAGE_CASE_NOT_FOUND",
                "The shortage case does not exist.");
    }
}
