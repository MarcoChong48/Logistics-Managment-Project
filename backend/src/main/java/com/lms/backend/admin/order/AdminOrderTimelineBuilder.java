package com.lms.backend.admin.order;

import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_ATTEMPT_OUTCOME_FAILED;
import static com.lms.backend.shared.value.SharedPlaceholderWord.SHARED_FAILURE_REASON_UNABLE_TO_CONTACT;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.lms.backend.shared.database.CustomerOrder;
import com.lms.backend.shared.database.DeliveryAssignmentHistory;
import com.lms.backend.shared.database.OrderDeliveryDetail;
import com.lms.backend.shared.database.PackageCustodyEvent;
import com.lms.backend.shared.database.ShortageCase;
import com.lms.backend.shared.value.SharedInstructionSource;

public final class AdminOrderTimelineBuilder {

    // ===== SECTION 1: TIMELINE BUILDER =====

    /**
     * What it does: Keeps this pure builder from being created as an object.
     * Input: None.
     * Output: No object is needed.
     */
    private AdminOrderTimelineBuilder() {
    }

    /**
     * What it does: Combines the order's dated events into one oldest-first timeline.
     * Input: The order and the detail rows already fetched from the shared tables.
     * Output: Time, kind, display text and optional actor for each dated event.
     * Placeholder: PI-05 provides provisional event, outcome and request words.
     */
    public static List<AdminViewOrderTimelineRow> adminBuildOrderTimeline(CustomerOrder adminBuildOrderTimelineOrder,
            String adminBuildOrderTimelineClientUsername, List<OrderDeliveryDetail> adminBuildOrderTimelineVersionList,
            List<AdminViewOrderPaymentRow> adminBuildOrderTimelinePaymentList,
            List<AdminViewOrderRefundRow> adminBuildOrderTimelineRefundList,
            AdminViewOrderPickingTask adminBuildOrderTimelinePickingTask,
            List<AdminViewOrderPickingAssignmentRow> adminBuildOrderTimelinePickingAssignmentList,
            List<Object[]> adminBuildOrderTimelineCustodyEventList,
            List<AdminViewOrderDeliveryAttemptRow> adminBuildOrderTimelineAttemptList,
            List<AdminViewOrderClientInstructionRow> adminBuildOrderTimelineInstructionList,
            List<Object[]> adminBuildOrderTimelineShortageList,
            List<Object[]> adminBuildOrderTimelineDeliveryAssignmentList) {
        List<AdminViewOrderTimelineRow> adminBuildOrderTimelineRowList = new ArrayList<>();

        // Delivery details contribute their version and saved time only.
        adminAddOrderTimelineEntry(adminBuildOrderTimelineRowList, adminBuildOrderTimelineOrder.createdTime,
                "ORDER_CREATED", "Order created by client " + adminBuildOrderTimelineClientUsername + ".", null);
        for (OrderDeliveryDetail adminBuildOrderTimelineVersion : adminBuildOrderTimelineVersionList) {
            adminAddOrderTimelineEntry(adminBuildOrderTimelineRowList, adminBuildOrderTimelineVersion.createdTime,
                    "DELIVERY_DETAILS_SAVED",
                    "Delivery details version " + adminBuildOrderTimelineVersion.versionNo + " saved.", null);
        }
        adminAddOrderTimelineFinance(adminBuildOrderTimelineRowList,
                adminBuildOrderTimelinePaymentList, adminBuildOrderTimelineRefundList);
        adminAddOrderTimelinePicking(adminBuildOrderTimelineRowList, adminBuildOrderTimelinePickingTask,
                adminBuildOrderTimelinePickingAssignmentList);
        adminAddOrderTimelineDelivery(adminBuildOrderTimelineRowList, adminBuildOrderTimelineCustodyEventList,
                adminBuildOrderTimelineAttemptList);
        adminAddOrderTimelineCases(adminBuildOrderTimelineRowList, adminBuildOrderTimelineInstructionList,
                adminBuildOrderTimelineShortageList, adminBuildOrderTimelineDeliveryAssignmentList);

        // Stable ordering preserves the order of related milestones saved at the same instant.
        adminBuildOrderTimelineRowList.sort(
                Comparator.comparing(AdminViewOrderTimelineRow::adminViewOrderTimelineTime));
        return List.copyOf(adminBuildOrderTimelineRowList);
    }

    // ===== SECTION 2: HELPER FUNCTIONS =====

    /**
     * What it does: Adds checkout and refund milestones with their separate amounts and states.
     * Input: The timeline plus the payment and refund rows.
     * Output: The dated entries are added to the timeline.
     */
    private static void adminAddOrderTimelineFinance(
            List<AdminViewOrderTimelineRow> adminAddOrderTimelineFinanceRowList,
            List<AdminViewOrderPaymentRow> adminAddOrderTimelineFinancePaymentList,
            List<AdminViewOrderRefundRow> adminAddOrderTimelineFinanceRefundList) {
        for (AdminViewOrderPaymentRow adminAddOrderTimelineFinancePayment : adminAddOrderTimelineFinancePaymentList) {
            adminAddOrderTimelineEntry(adminAddOrderTimelineFinanceRowList,
                    adminAddOrderTimelineFinancePayment.createdTime(),
                    "PAYMENT_STARTED", String.format(Locale.ROOT, "Checkout started (HKD %,.2f).",
                            adminAddOrderTimelineFinancePayment.amount()), null);
            if (adminAddOrderTimelineFinancePayment.acceptedTime() != null) {
                adminAddOrderTimelineEntry(adminAddOrderTimelineFinanceRowList,
                        adminAddOrderTimelineFinancePayment.acceptedTime(),
                        "PAYMENT_ACCEPTED", "Payment accepted.", null);
            } else {
                String adminAddOrderTimelineFinancePaymentStatus =
                        adminFormatOrderTimelineWord(adminAddOrderTimelineFinancePayment.paymentStatus());
                adminAddOrderTimelineEntry(adminAddOrderTimelineFinanceRowList,
                        adminAddOrderTimelineFinancePayment.endedTime(), "PAYMENT_ENDED",
                        "Checkout ended without payment (" + adminAddOrderTimelineFinancePaymentStatus + ").", null);
            }
        }
        for (AdminViewOrderRefundRow adminAddOrderTimelineFinanceRefund : adminAddOrderTimelineFinanceRefundList) {
            adminAddOrderTimelineEntry(adminAddOrderTimelineFinanceRowList,
                    adminAddOrderTimelineFinanceRefund.createdTime(),
                    "REFUND_REQUESTED", String.format(Locale.ROOT, "Refund requested (HKD %,.2f).",
                            adminAddOrderTimelineFinanceRefund.amount()), null);
            adminAddOrderTimelineEntry(adminAddOrderTimelineFinanceRowList,
                    adminAddOrderTimelineFinanceRefund.completedTime(), "REFUND_COMPLETED", "Refund completed.", null);
        }
    }

    /**
     * What it does: Adds picking assignments, start and packing milestones.
     * Input: The timeline, optional task and its assignment history.
     * Output: The dated entries are added to the timeline.
     */
    private static void adminAddOrderTimelinePicking(
            List<AdminViewOrderTimelineRow> adminAddOrderTimelinePickingRowList,
            AdminViewOrderPickingTask adminAddOrderTimelinePickingTask,
            List<AdminViewOrderPickingAssignmentRow> adminAddOrderTimelinePickingAssignmentList) {
        for (AdminViewOrderPickingAssignmentRow adminAddOrderTimelinePickingAssignment
                : adminAddOrderTimelinePickingAssignmentList) {
            adminAddOrderTimelineEntry(adminAddOrderTimelinePickingRowList,
                    adminAddOrderTimelinePickingAssignment.assignedTime(), "PICKING_ASSIGNED",
                    "Picking task assigned to "
                            + adminAddOrderTimelinePickingAssignment.adminViewOrderPickingAssignmentWorkerUsername()
                            + ".", null);
        }
        if (adminAddOrderTimelinePickingTask != null) {
            adminAddOrderTimelineEntry(adminAddOrderTimelinePickingRowList,
                    adminAddOrderTimelinePickingTask.startTime(),
                    "PICKING_STARTED", "Picking started.", null);
            adminAddOrderTimelineEntry(adminAddOrderTimelinePickingRowList,
                    adminAddOrderTimelinePickingTask.confirmPackingTime(),
                    "PACKING_CONFIRMED", "Packing confirmed.", null);
        }
    }

    /**
     * What it does: Adds package custody and delivery visits without notes or proof data.
     * Input: The timeline, custody events and the order's delivery attempts.
     * Output: The dated entries are added to the timeline.
     * Placeholder: PI-05 supplies custody, attempt and failure reason words.
     */
    private static void adminAddOrderTimelineDelivery(
            List<AdminViewOrderTimelineRow> adminAddOrderTimelineDeliveryRowList,
            List<Object[]> adminAddOrderTimelineDeliveryCustodyList,
            List<AdminViewOrderDeliveryAttemptRow> adminAddOrderTimelineDeliveryAttemptList) {
        // PLACEHOLDER INPUT PI-05
        // From: Warehouse and Delivery.
        // What: Custody, visit outcome and failure reason words.
        // How to use: Write the agreed words to the shared rows and update SharedPlaceholderWord when they change.
        for (Object[] adminAddOrderTimelineDeliveryCustodyValues : adminAddOrderTimelineDeliveryCustodyList) {
            PackageCustodyEvent adminAddOrderTimelineDeliveryCustodyEvent =
                    (PackageCustodyEvent) adminAddOrderTimelineDeliveryCustodyValues[0];
            adminAddOrderTimelineEntry(adminAddOrderTimelineDeliveryRowList,
                    adminAddOrderTimelineDeliveryCustodyEvent.eventTime, "PACKAGE_EVENT",
                    "Package event: "
                            + adminFormatOrderTimelineWord(adminAddOrderTimelineDeliveryCustodyEvent.eventType) + ".",
                    (String) adminAddOrderTimelineDeliveryCustodyValues[1]);
        }
        for (AdminViewOrderDeliveryAttemptRow adminAddOrderTimelineDeliveryAttempt
                : adminAddOrderTimelineDeliveryAttemptList) {
            adminAddOrderTimelineEntry(adminAddOrderTimelineDeliveryRowList,
                    adminAddOrderTimelineDeliveryAttempt.startTime(),
                    "ATTEMPT_STARTED", "Delivery attempt started by "
                            + adminAddOrderTimelineDeliveryAttempt.adminViewOrderDeliveryAttemptWorkerUsername() + ".",
                    null);
            String adminAddOrderTimelineDeliveryAttemptText = "Delivery attempt ended: "
                    + adminFormatOrderTimelineWord(adminAddOrderTimelineDeliveryAttempt.outcome()) + ".";
            if (SHARED_ATTEMPT_OUTCOME_FAILED.equals(adminAddOrderTimelineDeliveryAttempt.outcome())
                    && adminAddOrderTimelineDeliveryAttempt.failureReason() != null
                    && !adminAddOrderTimelineDeliveryAttempt.failureReason().isBlank()) {
                String adminAddOrderTimelineDeliveryFailureReason =
                        adminAddOrderTimelineDeliveryAttempt.failureReason();
                adminAddOrderTimelineDeliveryAttemptText += " Reason: "
                        + (SHARED_FAILURE_REASON_UNABLE_TO_CONTACT.equals(adminAddOrderTimelineDeliveryFailureReason)
                                ? "Unable to contact client"
                                : adminFormatOrderTimelineWord(adminAddOrderTimelineDeliveryFailureReason)) + ".";
            }
            adminAddOrderTimelineEntry(adminAddOrderTimelineDeliveryRowList,
                    adminAddOrderTimelineDeliveryAttempt.endTime(),
                    "ATTEMPT_ENDED", adminAddOrderTimelineDeliveryAttemptText, null);
        }
    }

    /**
     * What it does: Adds client requests, shortage decisions and delivery responsibility changes.
     * Input: The timeline and the three sets of history rows.
     * Output: The dated entries are added to the timeline.
     */
    private static void adminAddOrderTimelineCases(List<AdminViewOrderTimelineRow> adminAddOrderTimelineCasesRowList,
            List<AdminViewOrderClientInstructionRow> adminAddOrderTimelineCasesInstructionList,
            List<Object[]> adminAddOrderTimelineCasesShortageList,
            List<Object[]> adminAddOrderTimelineCasesAssignmentList) {
        for (AdminViewOrderClientInstructionRow adminAddOrderTimelineCasesInstruction
                : adminAddOrderTimelineCasesInstructionList) {
            String adminAddOrderTimelineCasesSource =
                    SharedInstructionSource.ADMIN_MANUAL.name().equals(adminAddOrderTimelineCasesInstruction.source())
                            ? "Admin" : adminFormatOrderTimelineWord(adminAddOrderTimelineCasesInstruction.source());
            adminAddOrderTimelineEntry(adminAddOrderTimelineCasesRowList,
                    adminAddOrderTimelineCasesInstruction.submittedTime(),
                    "CLIENT_REQUEST", "Client request: "
                            + adminFormatOrderTimelineWord(adminAddOrderTimelineCasesInstruction.instructionType())
                            + " (entered through " + adminAddOrderTimelineCasesSource + ").", null);
            adminAddOrderTimelineEntry(adminAddOrderTimelineCasesRowList,
                    adminAddOrderTimelineCasesInstruction.resolvedTime(),
                    "CLIENT_REQUEST_RESOLVED", "Request "
                            + adminFormatOrderTimelineWord(adminAddOrderTimelineCasesInstruction.status())
                                    .toLowerCase(Locale.ROOT)
                            + ".",
                    adminAddOrderTimelineCasesInstruction.adminViewOrderClientInstructionResolvedByUsername());
        }
        for (Object[] adminAddOrderTimelineCasesShortageValues : adminAddOrderTimelineCasesShortageList) {
            ShortageCase adminAddOrderTimelineCasesShortage =
                    (ShortageCase) adminAddOrderTimelineCasesShortageValues[0];
            adminAddOrderTimelineEntry(adminAddOrderTimelineCasesRowList,
                    adminAddOrderTimelineCasesShortage.reportedTime,
                    "SHORTAGE_REPORTED", "Shortage reported: " + adminAddOrderTimelineCasesShortage.missingQuantity
                            + " of " + adminAddOrderTimelineCasesShortageValues[1] + ".",
                    (String) adminAddOrderTimelineCasesShortageValues[2]);
            adminAddOrderTimelineEntry(adminAddOrderTimelineCasesRowList,
                    adminAddOrderTimelineCasesShortage.decidedTime,
                    "SHORTAGE_DECIDED", "Shortage decision: "
                            + adminFormatOrderTimelineWord(adminAddOrderTimelineCasesShortage.decision) + ".",
                    (String) adminAddOrderTimelineCasesShortageValues[3]);
            adminAddOrderTimelineEntry(adminAddOrderTimelineCasesRowList,
                    adminAddOrderTimelineCasesShortage.resolvedTime,
                    "SHORTAGE_RESOLVED", "Shortage solved by Warehouse.",
                    (String) adminAddOrderTimelineCasesShortageValues[4]);
        }
        for (Object[] adminAddOrderTimelineCasesAssignmentValues : adminAddOrderTimelineCasesAssignmentList) {
            DeliveryAssignmentHistory adminAddOrderTimelineCasesAssignment =
                    (DeliveryAssignmentHistory) adminAddOrderTimelineCasesAssignmentValues[0];
            String adminAddOrderTimelineCasesPreviousWorker = adminAddOrderTimelineCasesAssignmentValues[1] == null
                    ? "no worker" : (String) adminAddOrderTimelineCasesAssignmentValues[1];
            adminAddOrderTimelineEntry(adminAddOrderTimelineCasesRowList,
                    adminAddOrderTimelineCasesAssignment.assignedTime,
                    "DELIVERY_REASSIGNED",
                    "Delivery responsibility moved from " + adminAddOrderTimelineCasesPreviousWorker
                            + " to " + adminAddOrderTimelineCasesAssignmentValues[2] + ".",
                    (String) adminAddOrderTimelineCasesAssignmentValues[3]);
        }
    }

    /**
     * What it does: Appends a milestone when its timestamp is available.
     * Input: The timeline, time, kind, display text and optional actor.
     * Output: One entry is appended, or none when the time is empty.
     */
    private static void adminAddOrderTimelineEntry(List<AdminViewOrderTimelineRow> adminAddOrderTimelineEntryRowList,
            Instant adminViewOrderTimelineTime, String adminViewOrderTimelineKind, String adminViewOrderTimelineText,
            String adminViewOrderTimelineActorUsername) {
        if (adminViewOrderTimelineTime != null) {
            adminAddOrderTimelineEntryRowList.add(new AdminViewOrderTimelineRow(adminViewOrderTimelineTime,
                    adminViewOrderTimelineKind, adminViewOrderTimelineText, adminViewOrderTimelineActorUsername));
        }
    }

    /**
     * What it does: Turns a stored word into the plain words used in the timeline.
     * Input: A stored status, kind or decision.
     * Output: Sentence case words. Missing values show a dash.
     */
    private static String adminFormatOrderTimelineWord(String adminFormatOrderTimelineWordValue) {
        if (adminFormatOrderTimelineWordValue == null || adminFormatOrderTimelineWordValue.isBlank()) {
            return "-";
        }
        String adminFormatOrderTimelineWordLowercase = adminFormatOrderTimelineWordValue.replace('_', ' ')
                .toLowerCase(Locale.ROOT);
        return adminFormatOrderTimelineWordLowercase.substring(0, 1).toUpperCase(Locale.ROOT)
                + adminFormatOrderTimelineWordLowercase.substring(1);
    }
}
