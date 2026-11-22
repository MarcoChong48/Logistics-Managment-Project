package com.lms.backend.admin.placeholder;

// PI-07 returns the provider outcome and an optional public explanation.
public record AdminRefundProviderAnswer(
        AdminRefundProviderOutcome adminCheckRefundProviderStatusOutcome,
        String adminCheckRefundProviderStatusMessage) {
}
