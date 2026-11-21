package com.lms.backend.client.account;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ClientAccountRepository {

    private final JdbcClient database;

    ClientAccountRepository(JdbcClient database) {
        this.database = database;
    }

    Optional<ClientAccountResponse> findAccount(long userId) {
        return database.sql("""
                SELECT u.user_id, u.username, u.created_at, p.default_recipient_name, p.default_phone, p.default_address
                FROM app_user u LEFT JOIN client_profile p ON p.user_id = u.user_id
                WHERE u.user_id = :userId
                """).param("userId", userId)
                .query((row, index) -> new ClientAccountResponse(Long.toString(row.getLong("user_id")),
                        row.getString("username"), row.getObject("created_at", OffsetDateTime.class).toInstant(),
                        readDefaults(row)))
                .optional();
    }

    Optional<ClientDeliveryDefaults> findDefaults(long userId) {
        return database.sql("""
                SELECT default_recipient_name, default_phone, default_address
                FROM client_profile WHERE user_id = :userId
                """).param("userId", userId).query((row, index) -> readDefaults(row)).optional();
    }

    ClientDeliveryDefaults saveDefaults(long userId, ClientDeliveryDefaults defaults) {
        return database.sql("""
                INSERT INTO client_profile (user_id, default_recipient_name, default_phone, default_address)
                VALUES (:userId, :recipientName, :contactPhone, :deliveryAddress)
                ON CONFLICT (user_id) DO UPDATE SET
                    default_recipient_name = EXCLUDED.default_recipient_name,
                    default_phone = EXCLUDED.default_phone,
                    default_address = EXCLUDED.default_address
                RETURNING default_recipient_name, default_phone, default_address
                """).param("userId", userId)
                .param("recipientName", defaults.recipientName(), Types.VARCHAR)
                .param("contactPhone", defaults.contactPhone(), Types.VARCHAR)
                .param("deliveryAddress", defaults.deliveryAddress(), Types.VARCHAR)
                .query((row, index) -> readDefaults(row)).single();
    }

    private ClientDeliveryDefaults readDefaults(ResultSet row) throws SQLException {
        return new ClientDeliveryDefaults(row.getString("default_recipient_name"), row.getString("default_phone"),
                row.getString("default_address"));
    }
}
