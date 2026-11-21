package com.lms.backend.admin.placeholder;

import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_MOVEMENT_TYPE_RESERVATION_RELEASED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_PICKING_TASK_STATUS_CANCELLED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_RESERVATION_STATUS_HELD;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_RESERVATION_STATUS_RELEASED;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.shared.database.CustomerOrder;
import com.lms.backend.shared.database.CustomerOrderRepository;
import com.lms.backend.shared.database.InventoryRepository;
import com.lms.backend.shared.database.OrderItem;
import com.lms.backend.shared.database.OrderItemRepository;
import com.lms.backend.shared.database.PickingAssignmentHistoryRepository;
import com.lms.backend.shared.database.PickingTask;
import com.lms.backend.shared.database.PickingTaskRepository;
import com.lms.backend.shared.database.StockMovement;
import com.lms.backend.shared.database.StockMovementRepository;
import com.lms.backend.shared.database.StockReservation;
import com.lms.backend.shared.database.StockReservationRepository;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.value.SharedOrderStatus;

@Component
public class AdminOrderCancellationTemporary implements AdminOrderCancellationPlaceholder {

    // ===== SECTION 1: OTHER CLASSES THIS CLASS USES =====

    private final CustomerOrderRepository adminCancelOrderOrderRepository;
    private final OrderItemRepository adminCancelOrderOrderItemRepository;
    private final StockReservationRepository adminCancelOrderReservationRepository;
    private final InventoryRepository adminCancelOrderInventoryRepository;
    private final StockMovementRepository adminCancelOrderMovementRepository;
    private final PickingTaskRepository adminCancelOrderPickingTaskRepository;
    private final PickingAssignmentHistoryRepository adminCancelOrderAssignmentRepository;

    /**
     * What it does: Connects cancellation to the order, inventory and picking records.
     * Input: The repositories for the affected tables.
     * Output: The shared cancellation stand-in.
     */
    public AdminOrderCancellationTemporary(CustomerOrderRepository adminCancelOrderOrderRepository,
            OrderItemRepository adminCancelOrderOrderItemRepository,
            StockReservationRepository adminCancelOrderReservationRepository,
            InventoryRepository adminCancelOrderInventoryRepository,
            StockMovementRepository adminCancelOrderMovementRepository,
            PickingTaskRepository adminCancelOrderPickingTaskRepository,
            PickingAssignmentHistoryRepository adminCancelOrderAssignmentRepository) {
        this.adminCancelOrderOrderRepository = adminCancelOrderOrderRepository;
        this.adminCancelOrderOrderItemRepository = adminCancelOrderOrderItemRepository;
        this.adminCancelOrderReservationRepository = adminCancelOrderReservationRepository;
        this.adminCancelOrderInventoryRepository = adminCancelOrderInventoryRepository;
        this.adminCancelOrderMovementRepository = adminCancelOrderMovementRepository;
        this.adminCancelOrderPickingTaskRepository = adminCancelOrderPickingTaskRepository;
        this.adminCancelOrderAssignmentRepository = adminCancelOrderAssignmentRepository;
    }

    // ===== SECTION 2: adminCancelOrderPlaceholder =====

    /**
     * What it does: Cancels the order, releases only held reservations and ends its picking task.
     * Input: Order ID, Admin ID and reason in the caller's transaction.
     * Output: No return value, or a conflict if the order is missing or in a final state.
     * Placeholder: PO-01. Replace this implementation with the team's shared order cancellation.
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void adminCancelOrderPlaceholder(Long orderID, Long adminCancelOrderAdminUserID, String adminCancelOrderReason) {
        // PLACEHOLDER OUTPUT PO-01.
        // To: Client (orders) and Warehouse (stock).
        // What: Permanent cancellation and release of the order's reservations.
        // How to use: Replace this stand-in with the single cancellation function shared by all roles.
        CustomerOrder adminCancelOrderOrder = adminCancelOrderOrderRepository.findByOrderIDForUpdate(orderID)
                .orElseThrow(() -> new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_ORDER_STATE_CONFLICT",
                        "The order cannot be cancelled in its current state."));
        if (SharedOrderStatus.CANCELLED.name().equals(adminCancelOrderOrder.orderStatus)
                || SharedOrderStatus.DELIVERED.name().equals(adminCancelOrderOrder.orderStatus)) {
            throw new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_ORDER_STATE_CONFLICT",
                    "The order cannot be cancelled in its current state.");
        }

        Instant adminCancelOrderTime = Instant.now();
        adminCancelOrderOrder.orderStatus = SharedOrderStatus.CANCELLED.name();

        // Keep the inventory lock order the same when two orders contain some of the same products.
        for (OrderItem adminCancelOrderItem : adminCancelOrderOrderItemRepository.findByOrderIDOrderByProductIDAsc(orderID)) {
            for (StockReservation adminCancelOrderReservation : adminCancelOrderReservationRepository
                    .findByOrderItemIDAndStatusOrderByReservationIDAsc(
                            adminCancelOrderItem.orderItemID, SHARED_RESERVATION_STATUS_HELD)) {
                adminCancelOrderReservation.status = SHARED_RESERVATION_STATUS_RELEASED;
                adminCancelOrderReservation.releasedAt = adminCancelOrderTime;
                if (adminCancelOrderInventoryRepository.adminCancelOrderReleaseReservation(
                        adminCancelOrderItem.productID, adminCancelOrderReservation.quantity, adminCancelOrderTime) != 1) {
                    throw new IllegalStateException("Inventory is missing for product " + adminCancelOrderItem.productID);
                }

                StockMovement adminCancelOrderStockMovement = new StockMovement();
                adminCancelOrderStockMovement.productID = adminCancelOrderItem.productID;
                adminCancelOrderStockMovement.actorUserID = adminCancelOrderAdminUserID;
                adminCancelOrderStockMovement.movementType = SHARED_MOVEMENT_TYPE_RESERVATION_RELEASED;
                adminCancelOrderStockMovement.quantityDelta = 0;
                adminCancelOrderStockMovement.reservedDelta = -adminCancelOrderReservation.quantity;
                adminCancelOrderStockMovement.eventTime = adminCancelOrderTime;
                adminCancelOrderStockMovement.reason = adminCancelOrderReason;
                adminCancelOrderMovementRepository.save(adminCancelOrderStockMovement);
            }
        }

        // Close the work without touching picking quantities or scan history.
        PickingTask adminCancelOrderPickingTask = adminCancelOrderPickingTaskRepository.findByOrderIDForUpdate(orderID)
                .orElse(null);
        if (adminCancelOrderPickingTask != null) {
            adminCancelOrderAssignmentRepository.findOpenAssignmentForChange(adminCancelOrderPickingTask.pickingTaskID)
                    .ifPresent(adminCancelOrderAssignment -> adminCancelOrderAssignment.endedTime = adminCancelOrderTime);
            adminCancelOrderPickingTask.taskStatus = SHARED_PICKING_TASK_STATUS_CANCELLED;
        }
    }
}
