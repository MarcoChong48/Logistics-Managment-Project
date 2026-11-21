package com.lms.backend.shared.database;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

// Shared database placeholder. Use the team's mapped repository once available.
public interface ClientInstructionRepository extends JpaRepository<ClientInstruction, Long> {

    // ===== SECTION 1: DATABASE ACCESS =====

    /**
     * What it does: Reads the order's request history with both recorded users when available.
     * Input: The order ID.
     * Output: Request rows and recorder and resolver usernames, newest first.
     */
    @Query("""
            SELECT instruction, recorder.username, resolver.username
            FROM ClientInstruction instruction
            LEFT JOIN AppUser recorder ON recorder.userID = instruction.recordedByUserID
            LEFT JOIN AppUser resolver ON resolver.userID = instruction.resolvedByAdminID
            WHERE instruction.orderID = :orderID
            ORDER BY instruction.submittedTime DESC, instruction.instructionID DESC
            """)
    List<Object[]> adminViewOrderClientInstructions(Long orderID);

    /**
     * What it does: Locks requests with the given status after the caller locks their order.
     * Input: The order ID and required status.
     * Output: The matching requests in instruction ID order.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ClientInstruction> findByOrderIDAndStatusOrderByInstructionIDAsc(Long orderID, String status);
}
