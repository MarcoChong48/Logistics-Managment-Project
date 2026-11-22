package com.lms.backend.delivery.support;

import com.lms.backend.admin.placeholder.AdminUnavailableStockPlaceholder;
import java.util.List;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

// Use the existing Admin hook for held units recorded by Delivery returns.
@Component
@Primary
@Profile("delivery-full")
public class DeliveryUnavailableStockReader implements AdminUnavailableStockPlaceholder {
    private final JdbcTemplate db;

    public DeliveryUnavailableStockReader(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    public int adminReadUnavailableStockPlaceholder(Long productID) {
        List<Integer> records = db.queryForList(
            "SELECT delivery_unavailable_stock FROM inventory WHERE product_id=?", Integer.class, productID);
        return records.isEmpty() ? 0 : records.getFirst();
    }
}
