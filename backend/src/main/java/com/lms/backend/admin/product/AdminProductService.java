package com.lms.backend.admin.product;

import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_CSV_MAXIMUM_ROW_COUNT;
import static com.lms.backend.admin.common.AdminCommonRule.ADMIN_TABLE_PAGE_SIZE;
import static com.lms.backend.admin.common.AdminCommonRule.adminCheckReasonText;
import static com.lms.backend.admin.common.AdminCommonRule.adminCountPage;
import static com.lms.backend.admin.common.AdminPrivateValueRule.adminHidePrivateValue;
import static com.lms.backend.admin.product.AdminProductRule.*;
import static com.lms.backend.shared.csv.SharedCSVWriter.sharedWriteCSVDownload;
import static com.lms.backend.shared.log.SharedAuditAction.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.lms.backend.admin.placeholder.AdminUnavailableStockPlaceholder;
import com.lms.backend.shared.database.Inventory;
import com.lms.backend.shared.database.InventoryRepository;
import com.lms.backend.shared.database.OrderItemRepository;
import com.lms.backend.shared.database.Product;
import com.lms.backend.shared.database.ProductRepository;
import com.lms.backend.shared.error.SharedMessageResponse;
import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedAuditLogWriter;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.value.SharedListingStatus;

@Service
public class AdminProductService {

    // ===== SECTION 1: OTHER CLASSES THIS CLASS USES =====

    private final ProductRepository adminProductRepository;
    private final InventoryRepository adminCreateProductInventoryRepository;
    private final OrderItemRepository adminPreviewProductImpactOrderItemRepository;
    private final AdminUnavailableStockPlaceholder adminSearchProductUnavailableStockPlaceholder;
    private final SharedCurrentUserReader adminProductCurrentUserReader;
    private final SharedAuditLogWriter adminProductAuditLogWriter;
    private final JdbcTemplate adminCreateProductDatabase;

    /**
     * What it does: Connects the catalogue to stock, order impact and audit storage.
     * Input: The repositories, Warehouse reader, signed-in user reader, audit writer and database.
     * Output: A catalogue service.
     */
    public AdminProductService(ProductRepository adminProductRepository,
            InventoryRepository adminCreateProductInventoryRepository,
            OrderItemRepository adminPreviewProductImpactOrderItemRepository,
            AdminUnavailableStockPlaceholder adminSearchProductUnavailableStockPlaceholder,
            SharedCurrentUserReader adminProductCurrentUserReader, SharedAuditLogWriter adminProductAuditLogWriter,
            JdbcTemplate adminCreateProductDatabase) {
        this.adminProductRepository = adminProductRepository;
        this.adminCreateProductInventoryRepository = adminCreateProductInventoryRepository;
        this.adminPreviewProductImpactOrderItemRepository = adminPreviewProductImpactOrderItemRepository;
        this.adminSearchProductUnavailableStockPlaceholder = adminSearchProductUnavailableStockPlaceholder;
        this.adminProductCurrentUserReader = adminProductCurrentUserReader;
        this.adminProductAuditLogWriter = adminProductAuditLogWriter;
        this.adminCreateProductDatabase = adminCreateProductDatabase;
    }

    // ===== SECTION 2: adminSearchProduct =====

    /**
     * What it does: Lists a page of products and their stock facts in SKU order.
     * Input: The optional keyword, status and low-stock filters and the one-based page.
     * Output: Rows and page totals, or 400 for an invalid listing status.
     * Placeholder: PI-06. Warehouse provides the number of unavailable units, currently zero.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AdminSearchProductResponse adminSearchProduct(String adminSearchProductKeyword,
            String adminSearchProductListingStatus, boolean adminSearchProductIsLowStockOnly,
            int adminSearchProductPageNumber) {
        String adminSearchProductKeywordText = adminSearchProductKeyword == null ? "" : adminSearchProductKeyword;
        SharedListingStatus listingStatus = adminReadProductListingStatus(adminSearchProductListingStatus, true);
        int adminSearchProductRequestedPageNumber =
                Math.clamp(adminSearchProductPageNumber, 1, Integer.MAX_VALUE / ADMIN_TABLE_PAGE_SIZE);

        // Warehouse's numbers are supplied in Java, so apply the low-stock filter before selecting a page.
        if (adminSearchProductIsLowStockOnly) {
            List<AdminSearchProductRow> adminSearchProductRowList =
                    adminReadLowStockProducts(adminSearchProductKeywordText, listingStatus);
            int adminSearchProductTotalPageCount = adminCountPage(adminSearchProductRowList.size());
            int adminSearchProductActualPageNumber =
                    Math.min(adminSearchProductRequestedPageNumber, adminSearchProductTotalPageCount);
            int adminSearchProductStartIndex = (adminSearchProductActualPageNumber - 1) * ADMIN_TABLE_PAGE_SIZE;
            int adminSearchProductEndIndex = Math.min(adminSearchProductStartIndex + ADMIN_TABLE_PAGE_SIZE,
                    adminSearchProductRowList.size());
            return new AdminSearchProductResponse(
                    adminSearchProductRowList.subList(adminSearchProductStartIndex, adminSearchProductEndIndex),
                    adminSearchProductActualPageNumber, adminSearchProductTotalPageCount,
                    adminSearchProductRowList.size());
        }

        // For ordinary searches the database can select the page before reading Warehouse's numbers.
        Page<Object[]> adminSearchProductPage = adminProductRepository.adminSearchProduct(
                adminSearchProductKeywordText, listingStatus,
                PageRequest.of(adminSearchProductRequestedPageNumber - 1, ADMIN_TABLE_PAGE_SIZE));
        long adminSearchProductTotalRowCount = adminSearchProductPage.getTotalElements();
        int adminSearchProductTotalPageCount = adminCountPage(adminSearchProductTotalRowCount);
        int adminSearchProductActualPageNumber =
                Math.min(adminSearchProductRequestedPageNumber, adminSearchProductTotalPageCount);
        if (adminSearchProductActualPageNumber < adminSearchProductRequestedPageNumber
                && adminSearchProductTotalRowCount > 0) {
            adminSearchProductPage = adminProductRepository.adminSearchProduct(
                    adminSearchProductKeywordText, listingStatus,
                    PageRequest.of(adminSearchProductActualPageNumber - 1, ADMIN_TABLE_PAGE_SIZE));
        }
        return new AdminSearchProductResponse(adminSearchProductPage.stream().map(this::adminReadProductRow).toList(),
                adminSearchProductActualPageNumber, adminSearchProductTotalPageCount, adminSearchProductTotalRowCount);
    }

    // ===== SECTION 3: adminCreateProduct =====

    /**
     * What it does: Creates a product, a zero inventory balance and the creation audit row.
     * Input: The fields entered in Add product.
     * Output: A success message, or a validation or duplicate SKU error.
     * Placeholder: PO-09. Client uses the active product and Warehouse uses its stock record once connected.
     */
    @Transactional
    public SharedMessageResponse adminCreateProduct(AdminCreateProductRequest adminCreateProductRequest) {
        String SKU = adminCheckSKURule(adminCreateProductRequest.adminCreateProductSKU());
        adminSaveNewProduct(SKU, adminCreateProductRequest.adminCreateProductName(),
                adminCreateProductRequest.adminCreateProductCurrentPrice(),
                adminCreateProductRequest.adminCreateProductLowStockThreshold(),
                adminCreateProductRequest.adminCreateProductListingStatus());
        return new SharedMessageResponse("Product " + SKU + " was added with stock 0.");
    }

    // ===== SECTION 4: adminEditProduct =====

    /**
     * What it does: Updates the four editable catalogue fields and audits only the ones that changed.
     * Input: The product ID and proposed catalogue fields.
     * Output: A save or no-change message, or a not found, validation or conflict error.
     * Placeholder: PO-09. The catalogue update is available to Client and Warehouse when integrated.
     */
    @Transactional
    public SharedMessageResponse adminEditProduct(Long productID, AdminEditProductRequest adminEditProductRequest) {
        Product adminEditProductStoredProduct = adminReadProductForChange(productID);
        String SKU = adminCheckSKURule(adminEditProductRequest.adminEditProductSKU());
        if (!SKU.equals(adminEditProductStoredProduct.SKU) && adminProductRepository.findBySKU(SKU).isPresent()) {
            throw adminRefuseTakenSKU();
        }
        String name = adminCheckProductName(adminEditProductRequest.adminEditProductName());
        BigDecimal currentPrice = adminCheckPriceRule(adminEditProductRequest.adminEditProductCurrentPrice());
        int lowStockThreshold = adminCheckProductThreshold(adminEditProductRequest.adminEditProductLowStockThreshold());

        // Write only the changed old and new values to the audit log.
        Map<String, Object> adminEditProductOldValueMap = new LinkedHashMap<>();
        Map<String, Object> adminEditProductNewValueMap = new LinkedHashMap<>();
        adminRecordProductChange(adminEditProductOldValueMap, adminEditProductNewValueMap, "SKU",
                adminEditProductStoredProduct.SKU, SKU);
        adminRecordProductChange(adminEditProductOldValueMap, adminEditProductNewValueMap, "name",
                adminEditProductStoredProduct.name, name);
        adminRecordProductChange(adminEditProductOldValueMap, adminEditProductNewValueMap, "currentPrice",
                adminEditProductStoredProduct.currentPrice, currentPrice);
        adminRecordProductChange(adminEditProductOldValueMap, adminEditProductNewValueMap, "lowStockThreshold",
                adminEditProductStoredProduct.lowStockThreshold, lowStockThreshold);
        if (adminEditProductNewValueMap.isEmpty()) {
            return new SharedMessageResponse("Nothing was changed.");
        }

        // PLACEHOLDER OUTPUT PO-09.
        // To: Client and Warehouse.
        // What: The updated catalogue fields.
        // How to use: Client uses the new price for future orders. Warehouse checks the SKU when scanning.
        adminEditProductStoredProduct.SKU = SKU;
        adminEditProductStoredProduct.name = name;
        adminEditProductStoredProduct.currentPrice = currentPrice;
        adminEditProductStoredProduct.lowStockThreshold = lowStockThreshold;
        try {
            // Flush before auditing so a competing claim to the SKU returns the ordinary SKU refusal.
            adminProductRepository.flush();
        } catch (DataIntegrityViolationException adminEditProductError) {
            if (adminCheckProductDatabaseError(adminEditProductError, "23505")) {
                throw adminRefuseTakenSKU();
            }
            throw adminEditProductError;
        }
        adminWriteProductAudit(productID, SHARED_AUDIT_ACTION_PRODUCT_EDITED,
                adminEditProductOldValueMap, adminEditProductNewValueMap);
        return new SharedMessageResponse("Product " + SKU + " was saved.");
    }

    // ===== SECTION 5: adminPreviewProductImpact =====

    /**
     * What it does: Reads how many open orders use a product and how many await payment.
     * Input: The product ID.
     * Output: The SKU and both counts, or 404 when the product is missing.
     */
    @Transactional(readOnly = true)
    public AdminPreviewProductImpactResponse adminPreviewProductImpact(Long productID) {
        Product adminPreviewProductImpactProduct = adminProductRepository.findById(productID)
                .orElseThrow(AdminProductService::adminRefuseProductNotFound);
        return adminReadProductImpact(adminPreviewProductImpactProduct);
    }

    // ===== SECTION 6: adminDeactivateProduct =====

    /**
     * What it does: Takes an active product out of the catalogue and audits the reason and impact.
     * Input: The product ID and typed reason.
     * Output: A deactivation message, or a reason, missing product or conflict error.
     * Placeholder: PO-09. Client stops offering the product to new orders once integrated.
     */
    @Transactional
    public SharedMessageResponse adminDeactivateProduct(Long productID,
            AdminDeactivateProductRequest adminDeactivateProductRequest) {
        String adminDeactivateProductReason = adminCheckReasonText(
                adminDeactivateProductRequest.adminDeactivateProductReason());
        Product adminDeactivateProductStoredProduct = adminReadProductForChange(productID);
        if (adminDeactivateProductStoredProduct.listingStatus != SharedListingStatus.ACTIVE) {
            throw adminRefuseProductState("Only an active product can be deactivated.");
        }
        long adminDeactivateProductOpenOrderCount =
                adminReadProductImpact(adminDeactivateProductStoredProduct).adminPreviewProductImpactOpenOrderCount();

        // PLACEHOLDER OUTPUT PO-09.
        // To: Client.
        // What: The inactive listing status. Existing orders and stock remain untouched.
        // How to use: Client excludes this product from products for new orders when integrated.
        adminDeactivateProductStoredProduct.listingStatus = SharedListingStatus.INACTIVE;
        adminWriteProductAudit(productID, SHARED_AUDIT_ACTION_PRODUCT_DEACTIVATED,
                Map.of("listingStatus", SharedListingStatus.ACTIVE.name()),
                Map.of("listingStatus", SharedListingStatus.INACTIVE.name(), "reason", adminDeactivateProductReason,
                        "openOrderCount", adminDeactivateProductOpenOrderCount));
        return new SharedMessageResponse("Product " + adminDeactivateProductStoredProduct.SKU + " was deactivated.");
    }

    // ===== SECTION 7: adminActivateProduct =====

    /**
     * What it does: Returns an inactive product to the active catalogue and audits the status change.
     * Input: The product ID.
     * Output: An activation message, or a missing product or conflict error.
     * Placeholder: PO-09. Client can offer the product again when ordering is integrated.
     */
    @Transactional
    public SharedMessageResponse adminActivateProduct(Long productID) {
        Product adminActivateProductStoredProduct = adminReadProductForChange(productID);
        if (adminActivateProductStoredProduct.listingStatus != SharedListingStatus.INACTIVE) {
            throw adminRefuseProductState("Only an inactive product can be activated.");
        }

        // PLACEHOLDER OUTPUT PO-09.
        // To: Client.
        // What: The active listing status.
        // How to use: Client can list this product for new orders when integrated.
        adminActivateProductStoredProduct.listingStatus = SharedListingStatus.ACTIVE;
        adminWriteProductAudit(productID, SHARED_AUDIT_ACTION_PRODUCT_ACTIVATED,
                Map.of("listingStatus", SharedListingStatus.INACTIVE.name()),
                Map.of("listingStatus", SharedListingStatus.ACTIVE.name()));
        return new SharedMessageResponse("Product " + adminActivateProductStoredProduct.SKU + " was activated.");
    }

    // ===== SECTION 8: adminDownloadProductCSV =====

    /**
     * What it does: Downloads filtered catalogue rows in SKU order and audits the download.
     * Input: The optional keyword, listing status and low-stock filters.
     * Output: A CSV attachment with up to 10,000 rows, or 400 for an invalid listing status.
     * Placeholder: PI-06. Warehouse provides the number of unavailable units, currently zero.
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public ResponseEntity<byte[]> adminDownloadProductCSV(String adminSearchProductKeyword,
            String adminSearchProductListingStatus, boolean adminSearchProductIsLowStockOnly) {
        String adminDownloadProductCSVKeyword = adminSearchProductKeyword == null ? "" : adminSearchProductKeyword;
        SharedListingStatus listingStatus = adminReadProductListingStatus(adminSearchProductListingStatus, true);
        List<AdminSearchProductRow> adminDownloadProductCSVProductList;
        long adminDownloadProductCSVTotalRowCount;
        if (adminSearchProductIsLowStockOnly) {
            adminDownloadProductCSVProductList = adminReadLowStockProducts(
                    adminDownloadProductCSVKeyword, listingStatus);
            adminDownloadProductCSVTotalRowCount = adminDownloadProductCSVProductList.size();
        } else {
            Page<Object[]> adminDownloadProductCSVPage = adminProductRepository.adminSearchProduct(
                    adminDownloadProductCSVKeyword, listingStatus, PageRequest.of(0, ADMIN_CSV_MAXIMUM_ROW_COUNT));
            adminDownloadProductCSVProductList = adminDownloadProductCSVPage.stream()
                    .map(this::adminReadProductRow).toList();
            adminDownloadProductCSVTotalRowCount = adminDownloadProductCSVPage.getTotalElements();
        }
        List<List<Object>> adminDownloadProductCSVRowList = adminDownloadProductCSVProductList.stream()
                .limit(ADMIN_CSV_MAXIMUM_ROW_COUNT)
                .map(adminDownloadProductCSVRow -> List.<Object>of(adminDownloadProductCSVRow.productID(),
                        adminDownloadProductCSVRow.SKU(), adminDownloadProductCSVRow.name(),
                        adminDownloadProductCSVRow.currentPrice(), adminDownloadProductCSVRow.lowStockThreshold(),
                        adminDownloadProductCSVRow.listingStatus(), adminDownloadProductCSVRow.createdTime(),
                        adminDownloadProductCSVRow.physicalStock(), adminDownloadProductCSVRow.reservedStock(),
                        adminDownloadProductCSVRow.adminSearchProductUnavailableStock(),
                        adminDownloadProductCSVRow.adminSearchProductAvailableStock(),
                        adminDownloadProductCSVRow.adminSearchProductReservationShortfall(),
                        adminDownloadProductCSVRow.adminSearchProductIsLowStock())).toList();

        // Record the filters and row count, then pass the raw values to the shared CSV writer.
        Map<String, Object> adminDownloadProductCSVFilterMap = new LinkedHashMap<>();
        if (!adminDownloadProductCSVKeyword.isEmpty()) {
            adminDownloadProductCSVFilterMap.put("adminSearchProductKeyword",
                    adminHidePrivateValue(adminDownloadProductCSVKeyword));
        }
        if (listingStatus != null) {
            adminDownloadProductCSVFilterMap.put("adminSearchProductListingStatus", listingStatus.name());
        }
        adminDownloadProductCSVFilterMap.put("adminSearchProductIsLowStockOnly", adminSearchProductIsLowStockOnly);
        adminProductAuditLogWriter.sharedWriteAuditLog(adminProductCurrentUserReader.sharedReadCurrentUser().userID(),
                SHARED_AUDIT_ACTION_CSV_DOWNLOADED, "SYSTEM", "CSV_PRODUCTS", null,
                Map.of("rowCount", adminDownloadProductCSVRowList.size(), "filters", adminDownloadProductCSVFilterMap));

        String adminDownloadProductCSVLimitMessage = adminDownloadProductCSVTotalRowCount > ADMIN_CSV_MAXIMUM_ROW_COUNT
                ? "Only the first 10000 products by SKU are included." : null;
        return sharedWriteCSVDownload(List.of("productID", "SKU", "name", "currentPrice", "lowStockThreshold",
                "listingStatus", "createdTime", "physicalStock", "reservedStock", "adminSearchProductUnavailableStock",
                "adminSearchProductAvailableStock", "adminSearchProductReservationShortfall",
                "adminSearchProductIsLowStock"), adminDownloadProductCSVRowList, "admin-products", Instant.now(),
                adminDownloadProductCSVLimitMessage);
    }

    // ===== SECTION 9: HELPER FUNCTIONS =====

    /**
     * What it does: Finds the products that are below their stock thresholds.
     * Input: The keyword and optional listing status.
     * Output: Matching low-stock rows in SKU order.
     * Placeholder: PI-06. Warehouse provides the number of unavailable units, currently zero.
     */
    private List<AdminSearchProductRow> adminReadLowStockProducts(String adminSearchProductKeyword,
            SharedListingStatus listingStatus) {
        return adminProductRepository.adminSearchProduct(adminSearchProductKeyword, listingStatus, Pageable.unpaged())
                .stream().map(this::adminReadProductRow).filter(AdminSearchProductRow::adminSearchProductIsLowStock)
                .toList();
    }

    /**
     * What it does: Combines one catalogue row with its saved stock and Warehouse's unavailable units.
     * Input: A product followed by its physical and reserved stocks.
     * Output: The safe search row with stock calculations and the low-stock flag.
     * Placeholder: PI-06. Warehouse provides the number of unavailable units, currently zero.
     */
    private AdminSearchProductRow adminReadProductRow(Object[] adminReadProductRowValues) {
        Product adminReadProductRowProduct = (Product) adminReadProductRowValues[0];
        int physicalStock = ((Number) adminReadProductRowValues[1]).intValue();
        int reservedStock = ((Number) adminReadProductRowValues[2]).intValue();

        // PLACEHOLDER INPUT PI-06.
        // From: Warehouse.
        // What: Damaged or held stock that is included in physical stock.
        // How to use: Warehouse replaces the temporary reader. Until then its count is zero.
        int adminSearchProductUnavailableStock = adminSearchProductUnavailableStockPlaceholder
                .adminReadUnavailableStockPlaceholder(adminReadProductRowProduct.productID);
        int adminSearchProductAvailableStock =
                adminCalculateAvailableStock(physicalStock, reservedStock, adminSearchProductUnavailableStock);
        long adminSearchProductReservationShortfall =
                adminCalculateReservationShortfall(physicalStock, reservedStock, adminSearchProductUnavailableStock);
        return new AdminSearchProductRow(adminReadProductRowProduct.productID, adminReadProductRowProduct.SKU,
                adminReadProductRowProduct.name, adminReadProductRowProduct.currentPrice,
                adminReadProductRowProduct.lowStockThreshold, adminReadProductRowProduct.listingStatus,
                adminReadProductRowProduct.createdTime, physicalStock, reservedStock,
                adminSearchProductUnavailableStock, adminSearchProductAvailableStock,
                adminSearchProductReservationShortfall,
                adminCheckLowStock(adminSearchProductAvailableStock, adminReadProductRowProduct.lowStockThreshold));
    }

    /**
     * What it does: Saves a new product and its stock and audit rows in the caller's transaction.
     * Input: A checked SKU and the submitted name, price, threshold and status.
     * Output: None, or a duplicate SKU or validation error.
     * Placeholder: PO-09. Client uses the active product and Warehouse uses its inventory row when integrated.
     */
    void adminSaveNewProduct(String SKU, String adminSaveNewProductName, String adminSaveNewProductCurrentPrice,
            String adminSaveNewProductLowStockThreshold, String adminSaveNewProductListingStatus) {
        // Keep the field checks in the same order for Add product and Import CSV.
        if (adminProductRepository.findBySKU(SKU).isPresent()) {
            throw adminRefuseTakenSKU();
        }
        String name = adminCheckProductName(adminSaveNewProductName);
        BigDecimal currentPrice = adminCheckPriceRule(adminSaveNewProductCurrentPrice);
        int lowStockThreshold = adminCheckProductThreshold(adminSaveNewProductLowStockThreshold);
        SharedListingStatus listingStatus = adminReadProductListingStatus(adminSaveNewProductListingStatus, false);
        Instant createdTime = Instant.now();

        // PLACEHOLDER OUTPUT PO-09.
        // To: Client and Warehouse.
        // What: The new catalogue product and its initial stock balance.
        // How to use: Client reads active listings. Warehouse writes restocks to the stock row when integrated.
        // Let the database rule reject simultaneous claims without aborting a multi-product import.
        List<Long> adminSaveNewProductIDList = adminCreateProductDatabase.query("""
                INSERT INTO product (sku, name, current_price, low_stock_threshold, listing_status, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (sku) DO NOTHING
                RETURNING product_id
                """, (adminSaveNewProductResult, adminSaveNewProductRowNumber) ->
                        adminSaveNewProductResult.getLong("product_id"), SKU, name, currentPrice, lowStockThreshold,
                listingStatus.name(), Timestamp.from(createdTime));
        if (adminSaveNewProductIDList.isEmpty()) {
            throw adminRefuseTakenSKU();
        }
        Long productID = adminSaveNewProductIDList.getFirst();
        Inventory adminSaveNewProductInventory = new Inventory();
        adminSaveNewProductInventory.productID = productID;
        adminSaveNewProductInventory.updatedTime = createdTime;
        adminCreateProductInventoryRepository.save(adminSaveNewProductInventory);
        adminWriteProductAudit(productID, SHARED_AUDIT_ACTION_PRODUCT_CREATED, null, Map.of("SKU", SKU,
                "name", name, "currentPrice", currentPrice, "lowStockThreshold", lowStockThreshold,
                "listingStatus", listingStatus.name()));
    }

    /**
     * What it does: Reads a product under its write lock before applying the action's state rules.
     * Input: The product ID.
     * Output: A product, 404 when missing, or 409 when another request holds the lock.
     */
    private Product adminReadProductForChange(Long productID) {
        try {
            return adminProductRepository.findByProductIDForChange(productID)
                    .orElseThrow(AdminProductService::adminRefuseProductNotFound);
        } catch (RuntimeException adminReadProductForChangeError) {
            if (adminCheckProductDatabaseError(adminReadProductForChangeError, "55P03")) {
                throw adminRefuseProductState("The product is changing. Please refresh the page.");
            }
            throw adminReadProductForChangeError;
        }
    }

    /**
     * What it does: Reads open orders and orders awaiting payment in one statement.
     * Input: The selected product.
     * Output: The SKU and both order counts.
     */
    private AdminPreviewProductImpactResponse adminReadProductImpact(Product adminReadProductImpactProduct) {
        Object[] adminReadProductImpactCounts = adminPreviewProductImpactOrderItemRepository
                .adminPreviewProductImpact(adminReadProductImpactProduct.productID).getFirst();
        return new AdminPreviewProductImpactResponse(adminReadProductImpactProduct.productID,
                adminReadProductImpactProduct.SKU, ((Number) adminReadProductImpactCounts[0]).longValue(),
                ((Number) adminReadProductImpactCounts[1]).longValue());
    }

    /**
     * What it does: Adds an audited column only when the supplied old and new values differ.
     * Input: Both change maps, the column name and its old and new values.
     * Output: None. An actual change is added to both maps.
     */
    private void adminRecordProductChange(Map<String, Object> adminRecordProductChangeOldValueMap,
            Map<String, Object> adminRecordProductChangeNewValueMap, String adminRecordProductChangeField,
            Object adminRecordProductChangeOldValue, Object adminRecordProductChangeNewValue) {
        if (!adminRecordProductChangeOldValue.equals(adminRecordProductChangeNewValue)) {
            adminRecordProductChangeOldValueMap.put(adminRecordProductChangeField, adminRecordProductChangeOldValue);
            adminRecordProductChangeNewValueMap.put(adminRecordProductChangeField, adminRecordProductChangeNewValue);
        }
    }

    /**
     * What it does: Writes the current Admin's successful catalogue action.
     * Input: The product ID, audit action and the old and new saved values.
     * Output: One product audit row.
     */
    private void adminWriteProductAudit(Long productID, String action,
            Map<String, ?> adminWriteProductAuditOldValues, Map<String, ?> adminWriteProductAuditNewValues) {
        adminProductAuditLogWriter.sharedWriteAuditLog(adminProductCurrentUserReader.sharedReadCurrentUser().userID(),
                action, "PRODUCT", productID.toString(), adminWriteProductAuditOldValues,
                adminWriteProductAuditNewValues);
    }

    /**
     * What it does: Reads a listing status using the two stored values.
     * Input: The supplied status and whether a missing value is allowed.
     * Output: The status, or null for an omitted filter, or 400 for an invalid value.
     */
    private static SharedListingStatus adminReadProductListingStatus(String adminReadProductListingStatusValue,
            boolean adminReadProductListingStatusIsOptional) {
        if (adminReadProductListingStatusIsOptional && (adminReadProductListingStatusValue == null
                || adminReadProductListingStatusValue.isEmpty())) {
            return null;
        }
        if (SharedListingStatus.ACTIVE.name().equals(adminReadProductListingStatusValue)) {
            return SharedListingStatus.ACTIVE;
        }
        if (SharedListingStatus.INACTIVE.name().equals(adminReadProductListingStatusValue)) {
            return SharedListingStatus.INACTIVE;
        }
        throw adminRefuseProductInput("The listing status must be Active or Inactive.");
    }

    /**
     * What it does: Trims a product name and checks the limits before storing it.
     * Input: The submitted name.
     * Output: The stored name, or 400 when the field is invalid.
     */
    private static String adminCheckProductName(String adminCheckProductNameValue) {
        String name = adminCheckProductNameValue == null ? "" : adminCheckProductNameValue.strip();
        int adminCheckProductNameLength = name.codePointCount(0, name.length());
        if (adminCheckProductNameLength < ADMIN_PRODUCT_NAME_MINIMUM_LENGTH
                || adminCheckProductNameLength > ADMIN_PRODUCT_NAME_MAXIMUM_LENGTH || name.indexOf(0) >= 0) {
            throw adminRefuseProductInput("The name must have 2 to 200 characters and no null character.");
        }
        return name;
    }

    /**
     * What it does: Checks the threshold without rounding fractions into whole units.
     * Input: The threshold provided by the Admin.
     * Output: A whole number from 0 to 1,000,000, or 400 for invalid input.
     */
    private static int adminCheckProductThreshold(String adminCheckProductThresholdValue) {
        try {
            int lowStockThreshold = new BigDecimal(
                    adminCheckProductThresholdValue == null ? "" : adminCheckProductThresholdValue.strip())
                    .intValueExact();
            if (lowStockThreshold >= 0 && lowStockThreshold <= ADMIN_PRODUCT_MAXIMUM_THRESHOLD) {
                return lowStockThreshold;
            }
        } catch (ArithmeticException | NumberFormatException adminCheckProductThresholdError) {
            // Fractions and unreadable values use the same field message as values that are out of range.
        }
        throw adminRefuseProductInput("The low-stock threshold must be a whole number from 0 to 1,000,000.");
    }

    /**
     * What it does: Recognizes the database cause of a failed insert, update or row lock.
     * Input: A storage exception and the SQL state to match.
     * Output: True when one of the causes has that SQL state.
     */
    private static boolean adminCheckProductDatabaseError(RuntimeException adminCheckProductDatabaseError,
            String adminCheckProductDatabaseErrorState) {
        Throwable adminCheckProductDatabaseErrorCause = adminCheckProductDatabaseError;
        while (adminCheckProductDatabaseErrorCause != null) {
            if (adminCheckProductDatabaseErrorCause instanceof SQLException adminCheckProductDatabaseException
                    && adminCheckProductDatabaseErrorState.equals(adminCheckProductDatabaseException.getSQLState())) {
                return true;
            }
            adminCheckProductDatabaseErrorCause = adminCheckProductDatabaseErrorCause.getCause();
        }
        return false;
    }

    /**
     * What it does: Describes an invalid catalogue field.
     * Input: A message safe to show beside the form.
     * Output: A 400 catalogue input error.
     */
    private static SharedRuleException adminRefuseProductInput(String adminRefuseProductInputMessage) {
        return new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_PRODUCT_INPUT_INVALID",
                adminRefuseProductInputMessage);
    }

    /**
     * What it does: Describes a missing product.
     * Input: None.
     * Output: A 404 error.
     */
    static SharedRuleException adminRefuseProductNotFound() {
        return new SharedRuleException(HttpStatus.NOT_FOUND, "ADMIN_PRODUCT_NOT_FOUND", "This product was not found.");
    }

    /**
     * What it does: Describes a SKU already used by a product.
     * Input: None.
     * Output: A 409 duplicate SKU error.
     */
    private static SharedRuleException adminRefuseTakenSKU() {
        return new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_SKU_TAKEN", "This SKU already exists.");
    }

    /**
     * What it does: Describes a conflicting listing change.
     * Input: The public explanation of the conflict.
     * Output: A 409 listing state error.
     */
    private static SharedRuleException adminRefuseProductState(String adminRefuseProductStateMessage) {
        return new SharedRuleException(HttpStatus.CONFLICT, "ADMIN_PRODUCT_STATE_CONFLICT",
                adminRefuseProductStateMessage);
    }
}
