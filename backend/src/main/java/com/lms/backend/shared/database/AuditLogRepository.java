package com.lms.backend.shared.database;

import org.springframework.data.jpa.repository.JpaRepository;

// Shared database placeholder. Use the team's repository once it provides the audit mapping.
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}
