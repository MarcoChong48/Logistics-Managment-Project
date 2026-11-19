package com.lms.backend.admin.common;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedSecurityEvent;
import com.lms.backend.shared.log.SharedSecurityLogWriter;
import com.lms.backend.shared.login.SharedCurrentUser;

@Component
public class AdminCommonRule {

    // ===== SECTION 1: SETTINGS AND LOG WRITER =====

    public static final int ADMIN_TABLE_PAGE_SIZE = 20;

    private final SharedSecurityLogWriter adminWriteRejectedActionLogWriter;

    /**
     * What it does: Connects the shared rules to the security log writer.
     * Input: The security log writer.
     * Output: An Admin common rule component.
     */
    public AdminCommonRule(SharedSecurityLogWriter adminWriteRejectedActionLogWriter) {
        this.adminWriteRejectedActionLogWriter = adminWriteRejectedActionLogWriter;
    }

    // ===== SECTION 2: REASON AND PAGE RULES =====

    /**
     * What it does: Checks the length of a reason after removing surrounding whitespace.
     * Input: The reason entered by the Admin.
     * Output: The trimmed reason, or an error when it has fewer than 5 or more than 300 characters.
     */
    public static String adminCheckReasonText(String adminCheckReasonTextValue) {
        String adminCheckReasonTextTrimmedValue = adminCheckReasonTextValue == null
                ? "" : adminCheckReasonTextValue.strip();
        int adminCheckReasonTextLength = adminCheckReasonTextTrimmedValue.codePointCount(
                0, adminCheckReasonTextTrimmedValue.length());
        if (adminCheckReasonTextLength < 5 || adminCheckReasonTextLength > 300) {
            throw new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_REASON_REQUIRED",
                    "Please type a reason of at least 5 characters.");
        }
        return adminCheckReasonTextTrimmedValue;
    }

    /**
     * What it does: Counts pages of 20 rows, including the empty first page.
     * Input: The total row count.
     * Output: The number of pages.
     */
    public static int adminCountPage(long adminCountPageTotalRowCount) {
        return Math.toIntExact(Math.max(1L, Math.ceilDiv(adminCountPageTotalRowCount, ADMIN_TABLE_PAGE_SIZE)));
    }

    // ===== SECTION 3: REJECTED ADMIN ACTIONS =====

    /**
     * What it does: Records a refused Admin write without storing input from the request.
     * Input: The mapped function name and the public error code.
     * Output: One security log row with the current account ID when available.
     */
    public void adminWriteRejectedAction(String adminWriteRejectedActionName, String adminWriteRejectedActionErrorCode) {
        Authentication adminWriteRejectedActionAuthentication = SecurityContextHolder.getContext().getAuthentication();
        Long userID = adminWriteRejectedActionAuthentication != null
                && adminWriteRejectedActionAuthentication.getPrincipal() instanceof SharedCurrentUser
                        adminWriteRejectedActionUser ? adminWriteRejectedActionUser.userID() : null;
        adminWriteRejectedActionLogWriter.sharedWriteSecurityLog(userID,
                SharedSecurityEvent.SHARED_SECURITY_EVENT_REJECTED_ADMIN_ACTION, "REJECTED",
                adminWriteRejectedActionName + ": " + adminWriteRejectedActionErrorCode);
    }
}
