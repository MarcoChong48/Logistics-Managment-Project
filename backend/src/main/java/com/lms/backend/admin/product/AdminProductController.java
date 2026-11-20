package com.lms.backend.admin.product;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lms.backend.shared.error.SharedMessageResponse;

@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    // ===== SECTION 1: SERVICE THIS CLASS CALLS =====

    private final AdminProductService adminProductService;
    private final AdminProductImportService adminProductImportService;
    private final AdminProductQRCodeService adminProductQRCodeService;

    /**
     * What it does: Connects the product routes to the catalogue, importer and QR encoder.
     * Input: The three product services.
     * Output: A controller for product requests.
     */
    public AdminProductController(AdminProductService adminProductService,
            AdminProductImportService adminProductImportService, AdminProductQRCodeService adminProductQRCodeService) {
        this.adminProductService = adminProductService;
        this.adminProductImportService = adminProductImportService;
        this.adminProductQRCodeService = adminProductQRCodeService;
    }

    // ===== SECTION 2: GET /api/admin/products =====

    /**
     * What it does: Searches the catalogue with the submitted filters and page.
     * Input: The optional keyword, status, low-stock flag and one-based page.
     * Output: One page of product and stock rows with totals.
     */
    @GetMapping
    public AdminSearchProductResponse adminSearchProduct(
            @RequestParam(name = "adminSearchProductKeyword", required = false) String adminSearchProductKeyword,
            @RequestParam(name = "adminSearchProductListingStatus", required = false)
            String adminSearchProductListingStatus,
            @RequestParam(name = "adminSearchProductIsLowStockOnly", defaultValue = "false")
            boolean adminSearchProductIsLowStockOnly,
            @RequestParam(name = "adminSearchProductPageNumber", defaultValue = "1") int adminSearchProductPageNumber) {
        return adminProductService.adminSearchProduct(adminSearchProductKeyword, adminSearchProductListingStatus,
                adminSearchProductIsLowStockOnly, adminSearchProductPageNumber);
    }

    // ===== SECTION 3: POST /api/admin/products =====

    /**
     * What it does: Adds a product with stock zero.
     * Input: The fields from Add product.
     * Output: A success message or a rule error.
     */
    @PostMapping
    public SharedMessageResponse adminCreateProduct(@RequestBody AdminCreateProductRequest adminCreateProductRequest) {
        return adminProductService.adminCreateProduct(adminCreateProductRequest);
    }

    // ===== SECTION 4: PUT /api/admin/products/{productID} =====

    /**
     * What it does: Edits the selected product's catalogue fields.
     * Input: The product ID and edited fields.
     * Output: A save or no-change message, or a rule error.
     */
    @PutMapping("/{productID}")
    public SharedMessageResponse adminEditProduct(@PathVariable("productID") Long productID,
            @RequestBody AdminEditProductRequest adminEditProductRequest) {
        return adminProductService.adminEditProduct(productID, adminEditProductRequest);
    }

    // ===== SECTION 5: GET /api/admin/products/{productID}/impact =====

    /**
     * What it does: Counts the open orders that contain the requested product.
     * Input: The product ID.
     * Output: Its SKU and order counts, or a missing product error.
     */
    @GetMapping("/{productID}/impact")
    public AdminPreviewProductImpactResponse adminPreviewProductImpact(@PathVariable("productID") Long productID) {
        return adminProductService.adminPreviewProductImpact(productID);
    }

    // ===== SECTION 6: POST /api/admin/products/{productID}/deactivate =====

    /**
     * What it does: Marks an active product inactive with the recorded reason.
     * Input: The product ID and deactivation reason.
     * Output: A success message or a rule error.
     */
    @PostMapping("/{productID}/deactivate")
    public SharedMessageResponse adminDeactivateProduct(@PathVariable("productID") Long productID,
            @RequestBody AdminDeactivateProductRequest adminDeactivateProductRequest) {
        return adminProductService.adminDeactivateProduct(productID, adminDeactivateProductRequest);
    }

    // ===== SECTION 7: POST /api/admin/products/{productID}/activate =====

    /**
     * What it does: Returns an inactive product to the active catalogue.
     * Input: The product ID.
     * Output: A success message or a rule error.
     */
    @PostMapping("/{productID}/activate")
    public SharedMessageResponse adminActivateProduct(@PathVariable("productID") Long productID) {
        return adminProductService.adminActivateProduct(productID);
    }

    // ===== SECTION 8: GET /api/admin/products/{productID}/qr-code =====

    /**
     * What it does: Sends a PNG label for the saved product SKU.
     * Input: The product ID.
     * Output: The QR image or a missing product error.
     */
    @GetMapping("/{productID}/qr-code")
    public ResponseEntity<byte[]> adminViewProductQRCode(@PathVariable("productID") Long productID) {
        // An edit may change the SKU while leaving the product ID and label URL unchanged.
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(CacheControl.noStore())
                .body(adminProductQRCodeService.adminViewProductQRCode(productID));
    }

    // ===== SECTION 9: POST /api/admin/products/import =====

    /**
     * What it does: Processes the uploaded catalogue file and returns a result for its lines.
     * Input: The multipart field that holds the product CSV file.
     * Output: Creation and rejection counts and line details, or a bad file error.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminImportProductCSVResponse adminImportProductCSV(
            @RequestParam(name = "adminImportProductCSVFile", required = false)
            MultipartFile adminImportProductCSVFile) {
        return adminProductImportService.adminImportProductCSV(adminImportProductCSVFile);
    }

    // ===== SECTION 10: GET /api/admin/products/csv =====

    /**
     * What it does: Downloads the matching catalogue rows across pages.
     * Input: The optional keyword, status and low-stock filters.
     * Output: The filtered CSV file or an input error.
     */
    @GetMapping("/csv")
    public ResponseEntity<byte[]> adminDownloadProductCSV(
            @RequestParam(name = "adminSearchProductKeyword", required = false) String adminSearchProductKeyword,
            @RequestParam(name = "adminSearchProductListingStatus", required = false)
            String adminSearchProductListingStatus,
            @RequestParam(name = "adminSearchProductIsLowStockOnly", defaultValue = "false")
            boolean adminSearchProductIsLowStockOnly) {
        return adminProductService.adminDownloadProductCSV(adminSearchProductKeyword, adminSearchProductListingStatus,
                adminSearchProductIsLowStockOnly);
    }
}
