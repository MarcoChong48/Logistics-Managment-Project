package com.lms.backend.admin.delivery;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lms.backend.admin.common.AdminDecidedRowResponse;
import com.lms.backend.shared.error.SharedMessageResponse;

@RestController
@RequestMapping("/api/admin")
public class AdminDeliveryCaseController {

    // ===== SECTION 1: SERVICE THIS CLASS CALLS =====

    private final AdminDeliveryCaseService adminDeliveryCaseService;

    /**
     * What it does: Connects the delivery case web addresses to their service.
     * Input: The delivery case service.
     * Output: The controller.
     */
    public AdminDeliveryCaseController(AdminDeliveryCaseService adminDeliveryCaseService) {
        this.adminDeliveryCaseService = adminDeliveryCaseService;
    }

    // ===== SECTION 2: adminSearchFailedDeliveryCase =====

    /**
     * What it does: Lists failed delivery cases.
     * Input: The optional filter and page number.
     * Output: The matching page and its counts.
     */
    @GetMapping("/failed-delivery-cases")
    public AdminSearchFailedDeliveryCaseResponse adminSearchFailedDeliveryCase(
            @RequestParam(defaultValue = "ALL") String adminSearchFailedDeliveryCaseFilter,
            @RequestParam(defaultValue = "1") int adminSearchFailedDeliveryCasePageNumber) {
        return adminDeliveryCaseService.adminSearchFailedDeliveryCase(
                adminSearchFailedDeliveryCaseFilter, adminSearchFailedDeliveryCasePageNumber);
    }

    // ===== SECTION 3: adminSendDeliveryReminder =====

    /**
     * What it does: Sends a reminder to the eligible order's client.
     * Input: The failed order's ID.
     * Output: The saved reminder message or the rule that blocked sending.
     */
    @PostMapping("/failed-delivery-cases/{orderID}/reminder")
    public SharedMessageResponse adminSendDeliveryReminder(@PathVariable long orderID) {
        return adminDeliveryCaseService.adminSendDeliveryReminder(orderID);
    }

    // ===== SECTION 4: adminRecordClientInstruction =====

    /**
     * What it does: Records a client request that Admin received manually.
     * Input: The order ID, request type, identity confirmation and expected pending request.
     * Output: The saved decision or the rule that blocked recording.
     */
    @PostMapping("/failed-delivery-cases/{orderID}/client-instruction")
    public AdminDecidedRowResponse adminRecordClientInstruction(@PathVariable long orderID,
            @RequestBody AdminRecordClientInstructionRequest adminRecordClientInstructionRequest) {
        return adminDeliveryCaseService.adminRecordClientInstruction(orderID, adminRecordClientInstructionRequest);
    }

    // ===== SECTION 5: adminApproveRedelivery =====

    /**
     * What it does: Approves the client's request for another delivery.
     * Input: The instruction ID and optional note.
     * Output: The saved decision or the rule that blocked approval.
     */
    @PostMapping("/client-instructions/{instructionID}/approve-redelivery")
    public AdminDecidedRowResponse adminApproveRedelivery(@PathVariable long instructionID,
            @RequestBody(required = false) AdminApproveRedeliveryRequest adminApproveRedeliveryRequest) {
        return adminDeliveryCaseService.adminApproveRedelivery(instructionID, adminApproveRedeliveryRequest);
    }

    // ===== SECTION 6: adminApproveCancellation =====

    /**
     * What it does: Approves a client's cancellation request and requests any applicable refund.
     * Input: The instruction ID and optional note.
     * Output: The saved decision and optional refund ID or the rule that blocked cancellation.
     */
    @PostMapping("/client-instructions/{instructionID}/approve-cancellation")
    public AdminDecidedRowResponse adminApproveCancellation(@PathVariable long instructionID,
            @RequestBody(required = false) AdminApproveCancellationRequest adminApproveCancellationRequest) {
        return adminDeliveryCaseService.adminApproveCancellation(instructionID, adminApproveCancellationRequest);
    }

    // ===== SECTION 7: adminRejectClientInstruction =====

    /**
     * What it does: Refuses a pending request and tells the client its reason.
     * Input: The instruction ID and the Admin's reason.
     * Output: The saved decision or the rule that blocked rejection.
     */
    @PostMapping("/client-instructions/{instructionID}/reject")
    public AdminDecidedRowResponse adminRejectClientInstruction(@PathVariable long instructionID,
            @RequestBody AdminRejectClientInstructionRequest adminRejectClientInstructionRequest) {
return adminDeliveryCaseService.adminRejectClientInstruction(
                instructionID, adminRejectClientInstructionRequest);
    }

    // ===== SECTION 8: adminDownloadFailedDeliveryCaseCSV =====

    /**
     * What it does: Downloads the cases matching the Failed deliveries filter.
     * Input: The optional filter.
     * Output: The CSV attachment.
     */
    @GetMapping("/failed-delivery-cases/csv")
    public ResponseEntity<byte[]> adminDownloadFailedDeliveryCaseCSV(
            @RequestParam(defaultValue = "ALL") String adminSearchFailedDeliveryCaseFilter) {
        return adminDeliveryCaseService.adminDownloadFailedDeliveryCaseCSV(adminSearchFailedDeliveryCaseFilter);
    }
}
