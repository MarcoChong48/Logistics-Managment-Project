package com.lms.backend.shared.database;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lms.backend.shared.value.SharedListingStatus;

import jakarta.persistence.LockModeType;

// Shared database placeholder. Use the team's repository when it becomes available.
public interface ProductRepository extends JpaRepository<Product, Long> {

    // ===== SECTION 1: PRODUCT LOOKUPS =====

    /**
     * What it does: Reads the product with the given stored SKU.
     * Input: The normalized SKU.
     * Output: A product, or an empty result.
     */
    Optional<Product> findBySKU(String SKU);

    /**
     * What it does: Locks the current product before checking or changing it.
     * Input: The product ID.
     * Output: The product, or an empty result. A concurrent writer can hold the lock.
     */
    @Query(value = "SELECT * FROM product WHERE product_id = :productID FOR NO KEY UPDATE NOWAIT", nativeQuery = true)
    Optional<Product> findByProductIDForChange(@Param("productID") Long productID);

    // ===== SECTION 2: CATALOGUE SEARCH =====

    /**
     * What it does: Finds products by literal keyword and the optional listing status.
     * Input: A keyword, a listing status or null, and a page request.
     * Output: Products with stock balances, ordered by SKU.
     */
    @Query(value = """
            SELECT product, COALESCE(stock.physicalStock, 0), COALESCE(stock.reservedStock, 0)
            FROM Product product LEFT JOIN Inventory stock ON stock.productID = product.productID
            WHERE (LOCATE(LOWER(:adminSearchProductKeyword), LOWER(product.SKU)) > 0
                   OR LOCATE(LOWER(:adminSearchProductKeyword), LOWER(product.name)) > 0)
                AND (:listingStatus IS NULL OR product.listingStatus = :listingStatus)
            ORDER BY product.SKU
            """, countQuery = """
            SELECT COUNT(product) FROM Product product
            WHERE (LOCATE(LOWER(:adminSearchProductKeyword), LOWER(product.SKU)) > 0
                   OR LOCATE(LOWER(:adminSearchProductKeyword), LOWER(product.name)) > 0)
                AND (:listingStatus IS NULL OR product.listingStatus = :listingStatus)
            """)
    Page<Object[]> adminSearchProduct(
            @Param("adminSearchProductKeyword") String adminSearchProductKeyword,
            @Param("listingStatus") SharedListingStatus listingStatus,
            Pageable adminSearchProductPage);

    @Lock(LockModeType.PESSIMISTIC_READ)
    List<Product> findByProductIDInOrderByProductIDAsc(Collection<Long> productIds);

    @Query("""
            select p.productID as productId, p.SKU as sku, p.name as name,
                   p.currentPrice as currentPrice,
                   coalesce(i.physicalStock - i.reservedStock, 0) as stockBalance
            from Product p
            left join Inventory i on i.productID = p.productID
            where p.listingStatus = :listingStatus
              and locate(lower(:name), lower(p.name)) > 0
            order by p.productID
            """)
    List<ProductStock> findWithStock(@Param("listingStatus") SharedListingStatus listingStatus, @Param("name") String name);

    interface ProductStock {
        Long getProductId();
        String getSku();
        String getName();
        BigDecimal getCurrentPrice();
        int getStockBalance();
    }
}
