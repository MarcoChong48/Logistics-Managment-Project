package hk.edu.hkmu.lms.delivery.access;

import hk.edu.hkmu.lms.delivery.support.ApiException;
import hk.edu.hkmu.lms.delivery.support.Rows;
import jakarta.servlet.http.HttpServletRequest;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AccountSecurity {
    private final JdbcTemplate db;
    private final PasswordEncoder encoder;
    private final Clock clock;

    AccountSecurity(JdbcTemplate db, PasswordEncoder encoder, Clock clock) {
        this.db = db;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional
    public boolean changePassword(String actor, String currentPassword, String newPassword,
                                  String confirmPassword, HttpServletRequest request) {
        if (currentPassword == null || currentPassword.isEmpty()) {
            throw ApiException.bad("Enter your current password.");
        }
        validateNewPassword(newPassword);
        if (!newPassword.equals(confirmPassword)) {
            throw ApiException.bad("The new passwords do not match.");
        }
        if (newPassword.equals(currentPassword)) {
            throw ApiException.bad("Choose a different new password.");
        }

        Rows.lock(db);
        var account = Rows.requiredRow(db, "SELECT * FROM app_account WHERE id=?", actor);
        if (!"ACTIVE".equals(Rows.text(account, "ACCOUNT_STATUS"))
                || !"DELIVERY".equals(Rows.text(account, "ROLE"))) {
            throw ApiException.forbidden();
        }
        if (!matches(currentPassword, Rows.text(account, "PASSWORD_HASH"), encoder)) {
            securityEvent(request, actor, "PASSWORD_CHANGE", "FAILED");
            return false;
        }
        db.update("UPDATE app_account SET password_hash=?, failed_count=0, lock_until=NULL WHERE id=?",
            encoder.encode(newPassword), actor);
        securityEvent(request, actor, "PASSWORD_CHANGE", "SUCCESS");
        return true;
    }

    boolean isCurrentPassword(String actor, PasswordRevision revision) {
        var row = Rows.optionalRow(db, "SELECT password_hash FROM app_account WHERE id=?", actor);
        return row != null && revision.equals(PasswordRevision.of(Rows.text(row, "PASSWORD_HASH")));
    }

    static boolean matches(String password, String hash, PasswordEncoder encoder) {
        return fitsBcrypt(password) && encoder.matches(password, hash);
    }

    private static boolean fitsBcrypt(String password) {
        return password.length() <= 72
            && StandardCharsets.UTF_8.newEncoder().canEncode(password)
            && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    private static void validateNewPassword(String password) {
        if (password == null || !fitsBcrypt(password) || password.codePointCount(0, password.length()) < 8
                || password.chars().noneMatch(c -> c >= 'A' && c <= 'Z')
                || password.chars().noneMatch(c -> c >= 'a' && c <= 'z')
                || password.chars().noneMatch(c -> c >= '0' && c <= '9')) {
            throw ApiException.bad("Use 8 to 72 characters, with at least one capital letter A to Z, "
                + "one small letter a to z and one digit 0 to 9. The password can use at most 72 bytes.");
        }
    }

    void securityEvent(HttpServletRequest request, String actor, String type, String result) {
        String device = request.getHeader("User-Agent");
        if (device != null && device.length() > 300) device = device.substring(0, 300);
        db.update("""
            INSERT INTO security_event(id,account_id,event_type,event_result,occurred_at,ip_address,device_info)
            VALUES(?,?,?,?,?,?,?)
            """, UUID.randomUUID().toString(), actor, type, result,
            clock.instant().toString(), request.getRemoteAddr(), device);
    }

    // A password change expires old sessions without storing the password hash in the session.
    record PasswordRevision(String fingerprint) implements Serializable {
        static PasswordRevision of(String passwordHash) {
            try {
                var sha256 = MessageDigest.getInstance("SHA-256");
                var digest = sha256.digest(passwordHash.getBytes(StandardCharsets.UTF_8));
                return new PasswordRevision(HexFormat.of().formatHex(digest));
            } catch (NoSuchAlgorithmException error) {
                throw new IllegalStateException("SHA-256 is unavailable.", error);
            }
        }

        @Override
        public String toString() {
            return "[PROTECTED]";
        }
    }
}
