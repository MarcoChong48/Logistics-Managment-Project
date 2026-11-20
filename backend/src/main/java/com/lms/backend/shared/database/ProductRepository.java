package com.lms.backend.shared.database;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("""
            select p.productId as productId, p.sku as sku, p.name as name,
                   p.currentPrice as currentPrice,
                   coalesce(i.physicalStock - i.reservedStock, 0) as stockBalance
            from Product p
            left join Inventory i on i.productId = p.productId
            where p.listingStatus = :listingStatus
              and locate(lower(:name), lower(p.name)) > 0
            order by p.productId
            """)
    List<ProductStock> findWithStock(@Param("listingStatus") String listingStatus, @Param("name") String name);

    interface ProductStock {
        Long getProductId();
        String getSku();
        String getName();
        BigDecimal getCurrentPrice();
        int getStockBalance();
    }
}
