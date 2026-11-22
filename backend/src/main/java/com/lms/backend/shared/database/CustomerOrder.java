package com.lms.backend.shared.database;

import java.math.BigDecimal;
import java.time.Instant;

import com.lms.backend.shared.value.SharedOrderStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Shared database placeholder. Use the team's mapped CustomerOrder class once it is available.
@Entity
@Table(name = "customer_order")
public class CustomerOrder {

    // ===== SECTION 1: DATABASE FIELDS =====

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    public Long orderID;

    @Column(name = "order_reference", nullable = false, length = 60)
    public String orderReference;

    @Column(name = "client_user_id", nullable = false)
    public Long clientUserID;

    @Column(name = "order_status", nullable = false, length = 40)
    public String orderStatus;

    @Column(name = "created_at", nullable = false)
    public Instant createdTime;

    @Column(name = "payment_deadline", nullable = false)
    public Instant paymentDeadline;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    public BigDecimal totalAmount;

    public boolean isAwaitingPayment() {
        return SharedOrderStatus.AWAITING_PAYMENT.name().equals(orderStatus);
    }

    // Payment writers must check this while holding the order row lock.
    public boolean canAcceptPaymentAt(Instant now) {
        return isAwaitingPayment() && paymentDeadline.isAfter(now);
    }
}
