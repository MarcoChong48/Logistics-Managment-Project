package com.lms.backend.admin.shortage;

/** The Admin's reason and confirmation that Warehouse corrected the stock. */
public record AdminDecideShortageCancelRequest(
        String adminDecideShortageCancelReason,
        Boolean adminDecideShortageCancelStockCorrectionIsConfirmed) {
}
