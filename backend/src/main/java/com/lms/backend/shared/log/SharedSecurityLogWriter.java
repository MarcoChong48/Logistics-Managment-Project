package com.lms.backend.shared.log;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.lms.backend.shared.database.SecurityLog;
import com.lms.backend.shared.database.SecurityLogRepository;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class SharedSecurityLogWriter {

    // ===== SECTION 1: OTHER CLASSES THIS CLASS USES =====

    private final SecurityLogRepository sharedWriteSecurityLogRepository;

    /**
     * What it does: Receives the repository for security log rows.
     * Input: The security log repository.
     * Output: A writer connected to the repository.
     */
    public SharedSecurityLogWriter(SecurityLogRepository sharedWriteSecurityLogRepository) {
        this.sharedWriteSecurityLogRepository = sharedWriteSecurityLogRepository;
    }

    // ===== SECTION 2: sharedWriteSecurityLog =====

    /**
     * What it does: Saves one security event even when the caller refuses the action.
     * Input: An optional user ID, an event type, a result and optional safe details.
     * Output: None. A storage error is reported to the caller.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void sharedWriteSecurityLog(Long userID, String eventType, String result, String details) {
        // Fill the event with fixed words and safe details supplied by the caller.
        SecurityLog sharedWriteSecurityLogRow = new SecurityLog();
        sharedWriteSecurityLogRow.userID = userID;
        sharedWriteSecurityLogRow.eventType = eventType;
        sharedWriteSecurityLogRow.timestamp = Instant.now();
        sharedWriteSecurityLogRow.result = result;
        if (details != null && details.codePointCount(0, details.length()) > 600) {
            details = details.substring(0, details.offsetByCodePoints(0, 600));
        }
        sharedWriteSecurityLogRow.details = details;

        // HTTP events use the remote address and browser text. A startup event has no HTTP caller.
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes
                sharedWriteSecurityLogAttributes) {
            HttpServletRequest sharedWriteSecurityLogRequest = sharedWriteSecurityLogAttributes.getRequest();
            sharedWriteSecurityLogRow.ipAddress = sharedWriteSecurityLogRequest.getRemoteAddr();
            String deviceInfo = sharedWriteSecurityLogRequest.getHeader("User-Agent");
            if (deviceInfo != null && deviceInfo.codePointCount(0, deviceInfo.length()) > 300) {
                deviceInfo = deviceInfo.substring(0, deviceInfo.offsetByCodePoints(0, 300));
            }
            sharedWriteSecurityLogRow.deviceInfo = deviceInfo;
        }

        // Commit the event separately from the action that produced it.
        sharedWriteSecurityLogRepository.save(sharedWriteSecurityLogRow);
    }
}
