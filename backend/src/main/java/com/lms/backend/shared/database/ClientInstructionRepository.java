package com.lms.backend.shared.database;

import java.util.List;
import java.util.Optional;

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

    /**
     * What it does: Finds a request's order before either row is locked.
     * Input: The instruction ID.
     * Output: The order ID, or empty when the request is missing.
     */
@Query("SELECT instruction.orderID FROM ClientInstruction instruction "
            + "WHERE instruction.instructionID = :instructionID")
    Optional<Long> findOrderIDByInstructionID(Long instructionID);

    /**
     * What it does: Reads the current request after its order is locked.
     * Input: The instruction ID and its locked order ID.
     * Output: The locked instruction, or empty when the relation no longer exists.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ClientInstruction> findByInstructionIDAndOrderID(Long instructionID, Long orderID);

    /**
     * What it does: Reads the newest pending request first for the replacement window check.
     * Input: The locked order ID and pending status word.
     * Output: All pending requests, with locks kept until commit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
List<ClientInstruction> findByOrderIDAndStatusOrderBySubmittedTimeDescInstructionIDDesc(
            Long orderID, String status);

    /**
     * What it does: Finds the newest request, regardless of its status.
     * Input: The order ID.
     * Output: Its newest instruction or empty.
     */
    Optional<ClientInstruction> findFirstByOrderIDOrderBySubmittedTimeDescInstructionIDDesc(Long orderID);

    /**
     * What it does: Checks whether the client is already waiting for a decision.
     * Input: The order ID and pending status word.
     * Output: True when a request is pending.
     */
    boolean existsByOrderIDAndStatus(Long orderID, String status);
}
