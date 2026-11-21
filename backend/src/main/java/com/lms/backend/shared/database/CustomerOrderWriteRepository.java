package com.lms.backend.shared.database;

import java.math.BigDecimal;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CustomerOrderWriteRepository {

    private final JdbcClient database;

    public CustomerOrderWriteRepository(JdbcClient database) {
        this.database = database;
    }

    public void insertInitialDeliveryDetail(long orderId, String recipientName, String phone, String address) {
        database.sql("""
                INSERT INTO order_delivery_detail (order_id, version_no, recipient_name, phone, address_text)
                VALUES (:orderId, 1, :recipientName, :phone, :address)
                """)
                .param("orderId", orderId)
                .param("recipientName", recipientName)
                .param("phone", phone)
                .param("address", address)
                .update();
    }

    public void insertItem(long orderId, long productId, int quantity, BigDecimal unitPrice) {
        database.sql("""
                INSERT INTO order_item (order_id, product_id, quantity, unit_price)
                VALUES (:orderId, :productId, :quantity, :unitPrice)
                """)
                .param("orderId", orderId)
                .param("productId", productId)
                .param("quantity", quantity)
                .param("unitPrice", unitPrice)
                .update();
    }

    public void insertHeldReservation(long orderItemId, long paymentId, int quantity) {
        database.sql("""
                INSERT INTO stock_reservation (order_item_id, payment_id, quantity, status)
                VALUES (:orderItemId, :paymentId, :quantity, 'HELD')
                """)
                .param("orderItemId", orderItemId)
                .param("paymentId", paymentId)
                .param("quantity", quantity)
                .update();
    }

    public void insertReservationMovement(long productId, long actorUserId, int quantity, String orderReference) {
        database.sql("""
                INSERT INTO stock_movement
                    (product_id, actor_user_id, movement_type, quantity_delta, reserved_delta, reason)
                VALUES (:productId, :actorUserId, 'RESERVE', 0, :quantity, :reason)
                """)
                .param("productId", productId)
                .param("actorUserId", actorUserId)
                .param("quantity", quantity)
                .param("reason", orderReference)
                .update();
    }
}
