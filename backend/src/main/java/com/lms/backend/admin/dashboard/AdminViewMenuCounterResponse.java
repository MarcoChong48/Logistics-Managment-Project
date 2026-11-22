package com.lms.backend.admin.dashboard;

// ===== SECTION 1: SIDE MENU RESPONSE =====

public record AdminViewMenuCounterResponse(
        long adminViewMenuCounterOpenShortageCaseCount,
        long adminViewMenuCounterPendingClientInstructionCount,
        long adminViewMenuCounterUnresolvedRefundCount) {
}
