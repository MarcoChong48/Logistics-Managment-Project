package com.lms.backend;

// TEMPORARY TEST CODE - remove before integration
// Stage 4 checks catalogue flows with the sample stock tool on the isolated flow database.

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeReader;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@TestMethodOrder(MethodOrderer.MethodName.class)
class AdminProductFlowCheckTest extends SharedFlowCheck {

    // ===== SECTION 1: CATALOGUE AND STOCK =====

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 checks that the earlier sample tool's products appear in the catalogue.
    /**
     * What it does: Checks the Products page and searches the earlier sample products.
     * Input: The Stage 3 picking fixtures.
     * Output: A result for the initial product rows and absence of stock inputs.
     */
    @Test
    @DisplayName("S4-T01")
    void checkS4T01() {
        Session checkS4T01Admin = admin();
        String checkS4T01Page = checkS4T01Admin.get("/admin/admin-products.html").expect(200).body();
        assertTrue(checkS4T01Page.contains("adminSearchProductResultTable"));
        assertFalse(checkS4T01Page.matches("(?is).*<input[^>]+(?:PhysicalStock|ReservedStock|AvailableStock).*"));
        assertTrue(adminSearchProducts(checkS4T01Admin, "?adminSearchProductKeyword=TEST-MUG-").size() > 0);
        assertTrue(adminSearchProducts(checkS4T01Admin, "?adminSearchProductKeyword=TEST-PEN-").size() > 0);
        reportPartial("The visible Products layout is checked by hand in S4-H02.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 verifies Add product and its zero stock row.
    /**
     * What it does: Adds a product and verifies the search result and database stock.
     * Input: The mug fields of S4-T02.
     * Output: A result for SKU normalization, price, flags and inventory.
     */
    @Test
    @DisplayName("S4-T02")
    void checkS4T02() {
        Session checkS4T02Admin = admin();
        assertEquals("Product MUG-01 was added with stock 0.", checkS4T02Admin.post("/api/admin/products",
                adminCreateProductBody("mug-01", "Travel mug", 89.5, 8)).expect(200).json().path("message").asText());
        JsonNode checkS4T02Product = adminSearchExactProduct(checkS4T02Admin, "MUG-01");
        assertEquals("Travel mug", checkS4T02Product.path("name").asText());
        assertEquals(0, new BigDecimal("89.50").compareTo(checkS4T02Product.path("currentPrice").decimalValue()));
        assertEquals("ACTIVE", checkS4T02Product.path("listingStatus").asText());
        assertEquals(8, checkS4T02Product.path("lowStockThreshold").asInt());
        assertEquals(0, checkS4T02Product.path("physicalStock").asInt(-1));
        assertEquals(0, checkS4T02Product.path("reservedStock").asInt(-1));
        assertEquals(0, checkS4T02Product.path("adminSearchProductAvailableStock").asInt(-1));
        assertTrue(checkS4T02Product.path("adminSearchProductIsLowStock").asBoolean());
        assertEquals(Map.of("sku", "MUG-01", "physical_stock", 0, "reserved_stock", 0),
                adminReadProductStock("MUG-01"));
        reportPartial("The HKD formatting and low-stock flag appearance are checked by hand in S4-H02.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 verifies the catalogue validation errors.
    /**
     * What it does: Attempts each invalid Add product submission and counts the stored products.
     * Input: The nine invalid field combinations of S4-T03.
     * Output: A result for the nine errors and the unchanged catalogue.
     */
    @Test
    @DisplayName("S4-T03")
    void checkS4T03() {
        Session checkS4T03Admin = admin();
        long checkS4T03OriginalCount = adminCountProducts();
        adminRefuseCreateProduct(checkS4T03Admin, 400, "ADMIN_SKU_INVALID", "M", "Test mug", 50, 8);
        adminRefuseCreateProduct(checkS4T03Admin, 400, "ADMIN_SKU_INVALID", "MUG 02", "Test mug", 50, 8);
        adminRefuseCreateProduct(checkS4T03Admin, 409, "ADMIN_SKU_TAKEN", "mug-01", "Test mug", 50, 8);
        adminRefuseCreateProduct(checkS4T03Admin, 400, "ADMIN_PRODUCT_INPUT_INVALID", "MUG-02", "A", 50, 8);
        adminRefuseCreateProduct(checkS4T03Admin, 400, "ADMIN_PRODUCT_INPUT_INVALID", "MUG-02", "Test mug", 0, 8);
        adminRefuseCreateProduct(checkS4T03Admin, 400, "ADMIN_PRODUCT_INPUT_INVALID", "MUG-02", "Test mug", -5, 8);
        adminRefuseCreateProduct(checkS4T03Admin, 400, "ADMIN_PRODUCT_INPUT_INVALID", "MUG-02", "Test mug", 12.345, 8);
        adminRefuseCreateProduct(checkS4T03Admin, 400, "ADMIN_PRODUCT_INPUT_INVALID", "MUG-02", "Test mug", 50, -1);
        adminRefuseCreateProduct(checkS4T03Admin, 400, "ADMIN_PRODUCT_INPUT_INVALID", "MUG-02", "Test mug", 50, 2.5);
        assertEquals(checkS4T03OriginalCount, adminCountProducts());
        reportPartial("Inline dialog feedback is checked by hand in S4-H03.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 creates three stock states through the temporary tool.
    /**
     * What it does: Checks equal-to-threshold stock, low stock, filtering and shortfall.
     * Input: The existing mug and the three stock states of S4-T04.
     * Output: A result for balances, the low-stock filter and the shortfall.
     */
    @Test
    @DisplayName("S4-T04")
    void checkS4T04() {
        Session checkS4T04Admin = admin();
        adminSetProductStock(checkS4T04Admin, "MUG-01", 10, 2);
        JsonNode checkS4T04EqualProduct = adminSearchExactProduct(checkS4T04Admin, "MUG-01");
        assertEquals(8, checkS4T04EqualProduct.path("adminSearchProductAvailableStock").asInt(-1));
        assertFalse(checkS4T04EqualProduct.path("adminSearchProductIsLowStock").asBoolean(true));

        adminSetProductStock(checkS4T04Admin, "MUG-01", 10, 3);
        JsonNode checkS4T04LowProduct = adminSearchExactProduct(checkS4T04Admin, "MUG-01");
        assertEquals(7, checkS4T04LowProduct.path("adminSearchProductAvailableStock").asInt(-1));
        assertTrue(checkS4T04LowProduct.path("adminSearchProductIsLowStock").asBoolean());
        JsonNode checkS4T04FilteredRows = adminSearchProducts(checkS4T04Admin,
                "?adminSearchProductIsLowStockOnly=true&adminSearchProductKeyword=mug-01");
        assertTrue(adminReadProductRows(checkS4T04FilteredRows).stream()
                .anyMatch(checkS4T04Row -> "MUG-01".equals(checkS4T04Row.path("SKU").asText())));
        assertTrue(adminReadProductRows(checkS4T04FilteredRows).stream()
                .allMatch(checkS4T04Row -> checkS4T04Row.path("adminSearchProductIsLowStock").asBoolean()));

        adminSetProductStock(checkS4T04Admin, "MUG-01", 5, 8);
        JsonNode checkS4T04ShortProduct = adminSearchExactProduct(checkS4T04Admin, "MUG-01");
        assertEquals(0, checkS4T04ShortProduct.path("adminSearchProductAvailableStock").asInt(-1));
        assertEquals(3, checkS4T04ShortProduct.path("adminSearchProductReservationShortfall").asLong(-1));
        reportPartial("The URL, checkbox after reload, and visible flags are checked by hand in S4-H04.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 verifies the threshold's effect on stored stock flags.
    /**
     * What it does: Checks that threshold zero never produces a low-stock result.
     * Input: The mug with zero available stock.
     * Output: A result for thresholds zero and eight.
     */
    @Test
    @DisplayName("S4-T05")
    void checkS4T05() {
        Session checkS4T05Admin = admin();
        String checkS4T05ProductPath = "/api/admin/products/" + adminReadProductID("MUG-01");
        checkS4T05Admin.put(checkS4T05ProductPath, adminEditProductBody("MUG-01", "Travel mug", 89.5, 0))
                .expect(200);
        assertFalse(adminSearchExactProduct(checkS4T05Admin, "MUG-01").path("adminSearchProductIsLowStock")
                .asBoolean(true));
        checkS4T05Admin.put(checkS4T05ProductPath, adminEditProductBody("MUG-01", "Travel mug", 89.5, 8))
                .expect(200);
        assertTrue(adminSearchExactProduct(checkS4T05Admin, "MUG-01").path("adminSearchProductIsLowStock")
                .asBoolean());
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 verifies that edits audit just the changed fields.
    /**
     * What it does: Edits the mug, checks its newest audit values, and repeats an identical save.
     * Input: The new name and price of S4-T06.
     * Output: A result for old and new values and absence of a duplicate audit row.
     */
    @Test
    @DisplayName("S4-T06")
    void checkS4T06() {
        Session checkS4T06Admin = admin();
        long productID = adminReadProductID("MUG-01");
        String checkS4T06ProductPath = "/api/admin/products/" + productID;
        Map<String, Object> checkS4T06Body = adminEditProductBody("MUG-01", "Travel mug large", 95, 8);
        checkS4T06Admin.put(checkS4T06ProductPath, checkS4T06Body).expect(200);
        JsonNode checkS4T06Product = adminSearchExactProduct(checkS4T06Admin, "MUG-01");
        assertEquals("Travel mug large", checkS4T06Product.path("name").asText());
        assertEquals(0, new BigDecimal("95.00").compareTo(checkS4T06Product.path("currentPrice").decimalValue()));

        JsonNode checkS4T06Audit = adminSearchProductAudit(checkS4T06Admin, "PRODUCT_EDITED").get(0);
        assertEquals(String.valueOf(productID), checkS4T06Audit.path("targetID").asText());
        JsonMapper checkS4T06Mapper = JsonMapper.builder().build();
        JsonNode checkS4T06Old = checkS4T06Mapper.readTree(checkS4T06Audit.path("oldValue").asText());
        JsonNode checkS4T06New = checkS4T06Mapper.readTree(checkS4T06Audit.path("newValue").asText());
        assertEquals(2, checkS4T06Old.size());
        assertEquals(2, checkS4T06New.size());
        assertEquals("Travel mug", checkS4T06Old.path("name").asText());
        assertEquals("Travel mug large", checkS4T06New.path("name").asText());
        assertEquals(0, new BigDecimal("89.50").compareTo(checkS4T06Old.path("currentPrice").decimalValue()));
        assertEquals(0, new BigDecimal("95.00").compareTo(checkS4T06New.path("currentPrice").decimalValue()));

        long checkS4T06AuditCount = adminCountProductEditAudit(productID);
        assertEquals("Nothing was changed.",
                checkS4T06Admin.put(checkS4T06ProductPath, checkS4T06Body).expect(200).json().path("message").asText());
        assertEquals(checkS4T06AuditCount, adminCountProductEditAudit(productID));
        reportPartial("Visible save and no-change feedback are checked by hand in S4-H05.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 verifies the existing unit prices survive a catalogue price change.
    /**
     * What it does: Increases a sampled product price while comparing the saved order item prices.
     * Input: The earliest sample mug order items.
     * Output: A result for preserved historical prices.
     */
    @Test
    @DisplayName("S4-T07")
    void checkS4T07() {
        Session checkS4T07Admin = admin();
        List<Map<String, Object>> checkS4T07OriginalItemList = adminReadSampleMugItemPrices();
        assertFalse(checkS4T07OriginalItemList.isEmpty(), "A previous stage must create sample order items.");
        long productID = ((Number) checkS4T07OriginalItemList.getFirst().get("product_id")).longValue();
        String SKU = (String) checkS4T07OriginalItemList.getFirst().get("sku");
        JsonNode checkS4T07Product = adminSearchExactProduct(checkS4T07Admin, SKU);
        BigDecimal checkS4T07NewPrice = checkS4T07Product.path("currentPrice").decimalValue().add(BigDecimal.TEN);
        checkS4T07Admin.put("/api/admin/products/" + productID,
                adminEditProductBody(SKU, checkS4T07Product.path("name").asText(), checkS4T07NewPrice,
                        checkS4T07Product.path("lowStockThreshold").asInt())).expect(200);
        assertEquals(checkS4T07OriginalItemList, adminReadSampleMugItemPrices());
        assertEquals(0, checkS4T07NewPrice.compareTo(
                adminSearchExactProduct(checkS4T07Admin, SKU).path("currentPrice").decimalValue()));
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 verifies SKU edits and case-insensitive search.
    /**
     * What it does: Renames the mug SKU and searches for the old and new spellings.
     * Input: The mug with SKU MUG-01 and the new SKU MUG-001.
     * Output: A result for the catalogue search after the rename.
     */
    @Test
    @DisplayName("S4-T08")
    void checkS4T08() {
        Session checkS4T08Admin = admin();
        long productID = adminReadProductID("MUG-01");
        checkS4T08Admin.put("/api/admin/products/" + productID,
                adminEditProductBody("MUG-001", "Travel mug large", 95, 8)).expect(200);
        assertFalse(adminReadProductRows(adminSearchProducts(checkS4T08Admin, "?adminSearchProductKeyword=MUG-01"))
                .stream().anyMatch(checkS4T08Row -> "MUG-01".equals(checkS4T08Row.path("SKU").asText())));
        assertEquals(productID, adminSearchExactProduct(checkS4T08Admin, "mug-001").path("productID").asLong());
        reportPartial("The warning on the Edit dialog is checked by hand in S4-H05.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 checks that catalogue editing cannot change stock.
    /**
     * What it does: Submits the edit with an unexpected physicalStock field and queries inventory.
     * Input: The renamed mug, including the submitted stock number of S4-T09.
     * Output: A result for the unchanged stock protected by the catalogue route.
     */
    @Test
    @DisplayName("S4-T09")
    void checkS4T09() {
        Session checkS4T09Admin = admin();
        checkS4T09Admin.put("/api/admin/products/" + adminReadProductID("MUG-001"),
                Map.of("adminEditProductSKU", "MUG-001", "adminEditProductName", "Travel mug large",
                        "adminEditProductCurrentPrice", 95, "adminEditProductLowStockThreshold", 8,
                        "physicalStock", 999)).expect(200);
        assertEquals(Map.of("sku", "MUG-001", "physical_stock", 5, "reserved_stock", 8),
                adminReadProductStock("MUG-001"));
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 checks activation and deactivation while preserving existing orders and inventory.
    /**
     * What it does: Previews the stock listing impact, deactivates the product and reactivates it.
     * Input: The product from the earlier order fixture.
     * Output: A result for the order impact, listing status and untouched order and stock data.
     */
    @Test
    @DisplayName("S4-T10")
    void checkS4T10() {
        Session checkS4T10Admin = admin();
        String SKU = (String) adminReadSampleMugItemPrices().getFirst().get("sku");
        long productID = adminReadProductID(SKU);
        String checkS4T10ProductPath = "/api/admin/products/" + productID;
        List<Map<String, Object>> checkS4T10OrderCountList = adminReadProductOrderCounts();
        Map<String, Object> checkS4T10Stock = adminReadProductStock(SKU);

        JsonNode checkS4T10Impact = checkS4T10Admin.get(checkS4T10ProductPath + "/impact").expect(200).json();
        assertEquals(SKU, checkS4T10Impact.path("SKU").asText());
        assertEquals(productID, checkS4T10Impact.path("productID").asLong());
        long checkS4T10OpenCount = database.sql("""
                SELECT count(*) FROM customer_order JOIN order_item USING (order_id)
                WHERE product_id = :productID AND order_status NOT IN ('DELIVERED', 'CANCELLED')
                """).param("productID", productID).query(Long.class).single();
        long checkS4T10UnpaidCount = database.sql("""
                SELECT count(*) FROM customer_order JOIN order_item USING (order_id)
                WHERE product_id = :productID AND order_status = 'AWAITING_PAYMENT'
                """).param("productID", productID).query(Long.class).single();
        assertEquals(checkS4T10OpenCount, checkS4T10Impact.path("adminPreviewProductImpactOpenOrderCount").asLong(-1));
        assertEquals(checkS4T10UnpaidCount,
                checkS4T10Impact.path("adminPreviewProductImpactAwaitingPaymentOrderCount").asLong(-1));

        checkS4T10Admin.post(checkS4T10ProductPath + "/deactivate", Map.of("adminDeactivateProductReason",
                "Stage 4 sample deactivation")).expect(200);
        assertEquals("INACTIVE", adminSearchExactProduct(checkS4T10Admin, SKU).path("listingStatus").asText());
        assertEquals(checkS4T10OrderCountList, adminReadProductOrderCounts());
        assertEquals(checkS4T10Stock, adminReadProductStock(SKU));
        checkS4T10Admin.post(checkS4T10ProductPath + "/activate", null).expect(200);
        assertEquals("ACTIVE", adminSearchExactProduct(checkS4T10Admin, SKU).path("listingStatus").asText());
        reportPartial("S4-H06 covers dialog wording, red confirmation, visible statuses and the Activate layout.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 decodes the QR returned by the live product endpoint.
    /**
     * What it does: Reads the live QR image and checks its size and exact contents.
     * Input: The product whose SKU was changed to MUG-001.
     * Output: A result for the PNG image and decoded SKU.
     */
    @Test
    @DisplayName("S4-T11")
    void checkS4T11() throws Exception {
        Session checkS4T11Admin = admin();
        Reply checkS4T11Answer = checkS4T11Admin
                .get("/api/admin/products/" + adminReadProductID("MUG-001") + "/qr-code").expect(200);
        assertEquals("image/png", checkS4T11Answer.header("Content-Type"));
        BufferedImage checkS4T11Image = ImageIO.read(new ByteArrayInputStream(checkS4T11Answer.bytes()));
        assertNotNull(checkS4T11Image);
        assertEquals(300, checkS4T11Image.getWidth());
        assertEquals(300, checkS4T11Image.getHeight());
        int[] checkS4T11PixelList = checkS4T11Image.getRGB(0, 0, 300, 300, null, 0, 300);
        RGBLuminanceSource checkS4T11Source = new RGBLuminanceSource(300, 300, checkS4T11PixelList);
        assertEquals("MUG-001", new QRCodeReader().decode(
                new BinaryBitmap(new HybridBinarizer(checkS4T11Source))).getText());
        assertEquals("Travel mug large", adminSearchExactProduct(checkS4T11Admin, "MUG-001").path("name").asText());
        reportPartial("The label window, phone scan, native print view and return focus are in S4-H07.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 imports the prescribed partial-success sample.
    /**
     * What it does: Uploads valid and invalid product rows and checks the response, catalogue and stock.
     * Input: The seven physical CSV lines of S4-T12.
     * Output: A result for added products and per-line refusal messages.
     */
    @Test
    @DisplayName("S4-T12")
    void checkS4T12() {
        Session checkS4T12Admin = admin();
        long checkS4T12OriginalCount = adminCountProducts();
        JsonNode checkS4T12Result = checkS4T12Admin.upload("/api/admin/products/import", "adminImportProductCSVFile",
                """
                        SKU,name,currentPrice,lowStockThreshold
                        PEN-01,Blue pen,12.00,20
                        BAG-01,"Bag, large",150,5
                        MUG-001,Second mug,50,5
                        BAD SKU,Wrong code,10,1
                        CUP-01,Cup,abc,3
                        PEN-01,Another pen,13,2
                        """).expect(200).json();
        assertEquals(2, checkS4T12Result.path("adminImportProductCSVCreatedCount").asInt());
        assertEquals(4, checkS4T12Result.path("adminImportProductCSVRejectedCount").asInt());
        List<JsonNode> checkS4T12RejectedLineList =
                adminReadProductRows(checkS4T12Result.path("adminImportProductCSVRejectedLineList"));
        assertEquals(List.of(4, 5, 6, 7), checkS4T12RejectedLineList.stream()
                .map(checkS4T12Line -> checkS4T12Line.path("adminImportProductCSVLineNumber").asInt()).toList());
        assertEquals(List.of("This SKU already exists.", "The SKU may hold only letters, digits and hyphens.",
                "The price is not a number.", "This SKU appears earlier in the file."),
                checkS4T12RejectedLineList.stream()
                        .map(checkS4T12Line -> checkS4T12Line.path("adminImportProductCSVRejectReason").asText())
                        .toList());

        assertEquals(checkS4T12OriginalCount + 2, adminCountProducts());
        assertEquals("Blue pen", adminSearchExactProduct(checkS4T12Admin, "PEN-01").path("name").asText());
        assertEquals("Bag, large", adminSearchExactProduct(checkS4T12Admin, "BAG-01").path("name").asText());
        for (String SKU : List.of("PEN-01", "BAG-01")) {
            assertEquals("ACTIVE", adminSearchExactProduct(checkS4T12Admin, SKU).path("listingStatus").asText());
            assertEquals(Map.of("sku", SKU, "physical_stock", 0, "reserved_stock", 0), adminReadProductStock(SKU));
        }
        reportPartial("The visible inline import summary and rejected line table are in S4-H08.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 refuses a file whose header cannot be parsed as the required product format.
    /**
     * What it does: Uploads a file with the wrong header and checks there are no writes.
     * Input: The wrongly delimited header from S4-T13.
     * Output: A result for the whole-file error and unchanged row count.
     */
    @Test
    @DisplayName("S4-T13")
    void checkS4T13() {
        Session checkS4T13Admin = admin();
        long checkS4T13OriginalCount = adminCountProducts();
        checkS4T13Admin.upload("/api/admin/products/import", "adminImportProductCSVFile", "sku;name\n")
                .expect(400).code("ADMIN_IMPORT_FILE_INVALID");
        String checkS4T13Header = "SKU,name,currentPrice,lowStockThreshold\n";
        checkS4T13Admin.upload("/api/admin/products/import", "adminImportProductCSVFile",
                checkS4T13Header + "TOO-MANY,Number of lines,12,0\n".repeat(501))
                .expect(400).code("ADMIN_IMPORT_FILE_INVALID");
        checkS4T13Admin.upload("/api/admin/products/import", "adminImportProductCSVFile",
                checkS4T13Header + "X".repeat(1048576)).expect(400).code("ADMIN_IMPORT_FILE_INVALID");
        assertEquals(checkS4T13OriginalCount, adminCountProducts());
        reportPartial("The inline file refusal is in S4-H08.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 downloads and reads the filtered catalogue CSV.
    /**
     * What it does: Compares the filtered CSV with the corresponding JSON search.
     * Input: The pen keyword and the current catalogue and stock balances.
     * Output: A result for the attachment name, columns and matching pen data.
     */
    @Test
    @DisplayName("S4-T14")
    void checkS4T14() {
        Session checkS4T14Admin = admin();
        String checkS4T14Filter = "?adminSearchProductKeyword=pen";
        Reply checkS4T14Answer = checkS4T14Admin.get("/api/admin/products/csv" + checkS4T14Filter).expect(200);
        assertTrue(checkS4T14Answer.header("Content-Disposition")
                .matches("(?s).*admin-products-[0-9]{8}-[0-9]{6}\\.csv.*"));
        List<String> checkS4T14CSVLineList = checkS4T14Answer.body().lines().toList();
        String checkS4T14Header = checkS4T14CSVLineList.getFirst();
        assertTrue(checkS4T14Header.startsWith("\ufeff\"productID\",\"SKU\",\"name\",\"currentPrice\""));
        assertTrue(checkS4T14Header.contains("\"physicalStock\",\"reservedStock\""));

        // Query all pages so additional order fixtures can add pen products without weakening this check.
        List<JsonNode> checkS4T14JSONRowList = new ArrayList<>();
        int checkS4T14PageNumber = 1;
        int checkS4T14TotalPageCount;
        do {
            JsonNode checkS4T14Page = checkS4T14Admin.get("/api/admin/products" + checkS4T14Filter
                    + "&adminSearchProductPageNumber=" + checkS4T14PageNumber).expect(200).json();
            checkS4T14JSONRowList.addAll(adminReadProductRows(checkS4T14Page.path("adminSearchProductRowList")));
            checkS4T14TotalPageCount = checkS4T14Page.path("adminSearchProductTotalPageCount").asInt();
            checkS4T14PageNumber++;
        } while (checkS4T14PageNumber <= checkS4T14TotalPageCount);
        assertFalse(checkS4T14JSONRowList.isEmpty());
        assertEquals(checkS4T14JSONRowList.size() + 1, checkS4T14CSVLineList.size());
        for (int checkS4T14Index = 0; checkS4T14Index < checkS4T14JSONRowList.size(); checkS4T14Index++) {
            JsonNode checkS4T14Row = checkS4T14JSONRowList.get(checkS4T14Index);
            String checkS4T14Line = checkS4T14CSVLineList.get(checkS4T14Index + 1);
            assertTrue(checkS4T14Line.startsWith("\"" + checkS4T14Row.path("productID").asLong()
                    + "\",\"" + checkS4T14Row.path("SKU").asText() + "\","));
            assertTrue(checkS4T14Line.contains("\",\"" + checkS4T14Row.path("physicalStock").asInt()
                    + "\",\"" + checkS4T14Row.path("reservedStock").asInt() + "\",\""
                    + checkS4T14Row.path("adminSearchProductUnavailableStock").asInt() + "\",\""
                    + checkS4T14Row.path("adminSearchProductAvailableStock").asInt() + "\","));
        }
        reportPartial("The download through the Products page and opening it in a spreadsheet are in S4-H09.");
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 checks that its product writes and export are reported in the audit search.
    /**
     * What it does: Searches saved audit actions and checks the import summary.
     * Input: Product and export actions from the earlier tests in this class.
     * Output: A result for each action and the import counts.
     */
    @Test
    @DisplayName("S4-T15")
    void checkS4T15() {
        Session checkS4T15Admin = admin();
        for (String action : List.of("PRODUCT_CREATED", "PRODUCT_EDITED", "PRODUCT_DEACTIVATED",
                "PRODUCT_ACTIVATED", "PRODUCT_IMPORTED")) {
            assertTrue(adminSearchProductAudit(checkS4T15Admin, action).size() > 0, action);
        }
        JsonNode checkS4T15DownloadRows = checkS4T15Admin
                .get("/api/admin/logs/audit?adminSearchAuditLogAction=CSV_DOWNLOADED"
                        + "&adminSearchAuditLogTargetID=CSV_PRODUCTS").expect(200).json()
                .path("adminSearchAuditLogRowList");
        assertTrue(checkS4T15DownloadRows.size() > 0);
        JsonNode checkS4T15Import = adminSearchProductAudit(checkS4T15Admin, "PRODUCT_IMPORTED").get(0);
        JsonNode checkS4T15Counts = JsonMapper.builder().build().readTree(checkS4T15Import.path("newValue").asText());
        assertEquals(2, checkS4T15Counts.path("createdCount").asInt());
        assertEquals(4, checkS4T15Counts.path("rejectedCount").asInt());
    }

    // TEMPORARY TEST CODE - remove before integration
    // Stage 4 tries the product search while signed in as the warehouse fixture.
    /**
     * What it does: Checks the product API refuses a Warehouse Worker session.
     * Input: The permanent credentials for the Stage 2 Warehouse Worker fixture.
     * Output: A result for the role restriction on product search.
     */
    @Test
    @DisplayName("S4-T16")
    void checkS4T16() {
        Session checkS4T16Worker = browser();
        checkS4T16Worker.login("mei_picker", FLOW_PICKER_PASSWORD).expect(200);
        checkS4T16Worker.get("/api/admin/products").expect(403).code("SHARED_ACCESS_DENIED");
    }

    // ===== SECTION 2: HELPER FUNCTIONS =====

    /**
     * What it does: Builds the JSON fields submitted by Add product.
     * Input: The four catalogue field values. The product is listed as active.
     * Output: The request body.
     */
    private Map<String, Object> adminCreateProductBody(String SKU, String name, Object currentPrice,
            Object lowStockThreshold) {
        return Map.of("adminCreateProductSKU", SKU, "adminCreateProductName", name,
                "adminCreateProductCurrentPrice", currentPrice, "adminCreateProductLowStockThreshold",
                lowStockThreshold, "adminCreateProductListingStatus", "ACTIVE");
    }

    /**
     * What it does: Builds the JSON fields submitted by Edit product.
     * Input: The proposed catalogue field values.
     * Output: The request body.
     */
    private Map<String, Object> adminEditProductBody(String SKU, String name, Object currentPrice,
            Object lowStockThreshold) {
        return Map.of("adminEditProductSKU", SKU, "adminEditProductName", name, "adminEditProductCurrentPrice",
                currentPrice, "adminEditProductLowStockThreshold", lowStockThreshold);
    }

    /**
     * What it does: Exercises one refused Add product request.
     * Input: The Admin session, expected HTTP status and code, and the submitted fields.
     * Output: None. Verifies the exact code and a nonempty explanation.
     */
    private void adminRefuseCreateProduct(Session adminRefuseCreateProductSession, int adminRefuseCreateProductStatus,
            String adminRefuseCreateProductCode, String SKU, String name, Object currentPrice,
            Object lowStockThreshold) {
        JsonNode adminRefuseCreateProductResponse = adminRefuseCreateProductSession.post("/api/admin/products",
                adminCreateProductBody(SKU, name, currentPrice, lowStockThreshold))
                .expect(adminRefuseCreateProductStatus).code(adminRefuseCreateProductCode).json();
        assertFalse(adminRefuseCreateProductResponse.path("message").asText().isBlank());
    }

    /**
     * What it does: Sets the inventory balance using the test-mode API.
     * Input: The Admin session, SKU and the two stock counts.
     * Output: None. Checks the tool succeeded.
     */
    private void adminSetProductStock(Session adminSetProductStockSession, String SKU, int physicalStock,
            int reservedStock) {
        adminSetProductStockSession.tool("product-stock", Map.of("adminTemporarySetProductStockSKU", SKU,
                "adminTemporarySetProductStockPhysicalStock", physicalStock,
                "adminTemporarySetProductStockReservedStock", reservedStock)).expect(200);
    }

    /**
     * What it does: Sends a catalogue search as an Admin.
     * Input: The Admin session and query parameters, or an empty suffix.
     * Output: The matching row array.
     */
    private JsonNode adminSearchProducts(Session adminSearchProductsSession, String adminSearchProductsFilter) {
        return adminSearchProductsSession.get("/api/admin/products" + adminSearchProductsFilter).expect(200).json()
                .path("adminSearchProductRowList");
    }

    /**
     * What it does: Finds a row by exact SKU among the results of a keyword search.
     * Input: The Admin session and a SKU from the fixture.
     * Output: One matching row or a failed test if the row is missing.
     */
    private JsonNode adminSearchExactProduct(Session adminSearchExactProductSession, String SKU) {
        return adminReadProductRows(adminSearchProducts(adminSearchExactProductSession,
                "?adminSearchProductKeyword=" + SKU)).stream()
                .filter(adminSearchExactProductRow -> SKU.equalsIgnoreCase(adminSearchExactProductRow.path("SKU")
                        .asText()))
                .findFirst().orElseThrow(() -> new AssertionError("Search did not return product " + SKU));
    }

    /**
     * What it does: Converts a response JSON array into iterable rows.
     * Input: The array from an API response.
     * Output: All response rows in order.
     */
    private List<JsonNode> adminReadProductRows(JsonNode adminReadProductRowsArray) {
        assertTrue(adminReadProductRowsArray.isArray());
        return StreamSupport.stream(adminReadProductRowsArray.spliterator(), false).toList();
    }

    /**
     * What it does: Reads the product's database ID by exact SKU.
     * Input: The stored SKU.
     * Output: The product ID.
     */
    private long adminReadProductID(String SKU) {
        return database.sql("SELECT product_id FROM product WHERE sku = :SKU").param("SKU", SKU)
                .query(Long.class).single();
    }

    /**
     * What it does: Reads the actual inventory row for one product.
     * Input: The stored SKU.
     * Output: The SKU, physical stock and reserved stock.
     */
    private Map<String, Object> adminReadProductStock(String SKU) {
        return database.sql("""
                SELECT p.sku, i.physical_stock, i.reserved_stock
                FROM product p JOIN inventory i USING (product_id)
                WHERE p.sku = :SKU
                """).param("SKU", SKU).query().singleRow();
    }

    /**
     * What it does: Counts saved products on the isolated database.
     * Input: None.
     * Output: The current count.
     */
    private long adminCountProducts() {
        return database.sql("SELECT count(*) FROM product").query(Long.class).single();
    }

    /**
     * What it does: Finds saved audit rows by action through the Logs API.
     * Input: The Admin session and audit action.
     * Output: The audit row array.
     */
    private JsonNode adminSearchProductAudit(Session adminSearchProductAuditSession, String action) {
        return adminSearchProductAuditSession.get("/api/admin/logs/audit?adminSearchAuditLogAction=" + action)
                .expect(200).json().path("adminSearchAuditLogRowList");
    }

    /**
     * What it does: Counts just the edit audit rows of the selected product.
     * Input: The product ID.
     * Output: The audit row count.
     */
    private long adminCountProductEditAudit(long productID) {
        return database.sql("""
                SELECT count(*) FROM audit_log WHERE action = 'PRODUCT_EDITED' AND target_id = :targetID
                """).param("targetID", String.valueOf(productID)).query(Long.class).single();
    }

    /**
     * What it does: Reads the persisted unit price of the first three mug order items.
     * Input: None. Earlier flow tests supply the sample orders.
     * Output: The product IDs, SKUs, item IDs and prices in order-item order.
     */
    private List<Map<String, Object>> adminReadSampleMugItemPrices() {
        return database.sql("""
                SELECT p.product_id, p.sku, oi.order_item_id, oi.unit_price
                FROM order_item oi JOIN product p USING (product_id)
                WHERE p.sku LIKE 'TEST-MUG%' ORDER BY oi.order_item_id LIMIT 3
                """).query().listOfRows();
    }

    /**
     * What it does: Counts existing orders grouped by stored order status.
     * Input: None.
     * Output: A list of status and count rows.
     */
    private List<Map<String, Object>> adminReadProductOrderCounts() {
        return database.sql("""
                SELECT order_status, count(*) FROM customer_order GROUP BY order_status ORDER BY order_status
                """).query().listOfRows();
    }
}
