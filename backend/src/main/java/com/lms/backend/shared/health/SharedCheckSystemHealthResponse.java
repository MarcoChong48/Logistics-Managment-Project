package com.lms.backend.shared.health;

/** The database status, test profile and current server time shown on the dashboard. */
public record SharedCheckSystemHealthResponse(
        boolean sharedCheckSystemHealthDatabaseIsConnected,
        int sharedCheckSystemHealthTableCount,
        boolean sharedCheckSystemHealthTestModeIsEnabled,
        String sharedCheckSystemHealthServerTime) {
}
