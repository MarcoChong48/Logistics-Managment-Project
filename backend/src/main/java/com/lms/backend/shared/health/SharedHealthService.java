package com.lms.backend.shared.health;

import java.time.Instant;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SharedHealthService {

    // ===== SECTION 1: OTHER CLASSES THIS CLASS USES =====

    private final JdbcTemplate sharedCheckSystemHealthDatabase;
    private final Environment sharedCheckSystemHealthEnvironment;

    /**
     * What it does: Receives the database query and profile services.
     * Input: The configured JDBC helper and the current Spring environment.
     * Output: A service ready to check health.
     */
    public SharedHealthService(JdbcTemplate sharedCheckSystemHealthDatabase,
                               Environment sharedCheckSystemHealthEnvironment) {
        this.sharedCheckSystemHealthDatabase = sharedCheckSystemHealthDatabase;
        this.sharedCheckSystemHealthEnvironment = sharedCheckSystemHealthEnvironment;
    }

    // ===== SECTION 2: sharedCheckSystemHealth =====

    /**
     * What it does: Checks that the LMS database can answer and counts its tables.
     * Input: None.
     * Output: The connection status, table count, active test mode and UTC server time.
     */
    public SharedCheckSystemHealthResponse sharedCheckSystemHealth() {
        // Read the profile even when the database is unavailable.
        boolean sharedCheckSystemHealthTestModeIsEnabled =
                sharedCheckSystemHealthEnvironment.acceptsProfiles(Profiles.of("admintest"));

        // Count the application's tables, excluding views and PostgreSQL's own tables.
        try {
            int sharedCheckSystemHealthTableCount = sharedCheckSystemHealthDatabase.queryForObject("""
                    SELECT count(*)
                    FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                    """, int.class);
            return new SharedCheckSystemHealthResponse(true, sharedCheckSystemHealthTableCount,
                    sharedCheckSystemHealthTestModeIsEnabled, Instant.now().toString());
        } catch (DataAccessException sharedCheckSystemHealthDatabaseError) {
            // Return the same fields without exposing connection details or credentials.
            return new SharedCheckSystemHealthResponse(false, 0,
                    sharedCheckSystemHealthTestModeIsEnabled, Instant.now().toString());
        }
    }
}
