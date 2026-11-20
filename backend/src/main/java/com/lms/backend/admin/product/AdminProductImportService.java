package com.lms.backend.admin.product;

import static com.lms.backend.admin.product.AdminProductRule.ADMIN_PRODUCT_IMPORT_MAXIMUM_FILE_BYTES;
import static com.lms.backend.admin.product.AdminProductRule.ADMIN_PRODUCT_IMPORT_MAXIMUM_LINE_COUNT;
import static com.lms.backend.admin.product.AdminProductRule.adminCheckSKURule;
import static com.lms.backend.shared.log.SharedAuditAction.SHARED_AUDIT_ACTION_PRODUCT_IMPORTED;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lms.backend.shared.error.SharedRuleException;
import com.lms.backend.shared.log.SharedAuditLogWriter;
import com.lms.backend.shared.login.SharedCurrentUserReader;
import com.lms.backend.shared.value.SharedListingStatus;

@Service
public class AdminProductImportService {

    // ===== SECTION 1: OTHER CLASSES THIS CLASS USES =====

    private final AdminProductService adminImportProductCSVProductService;
    private final SharedCurrentUserReader adminImportProductCSVCurrentUserReader;
    private final SharedAuditLogWriter adminImportProductCSVAuditLogWriter;
    private final JdbcTemplate adminImportProductCSVDatabase;

    /**
     * What it does: Connects the CSV importer to product creation and audit storage.
     * Input: The product service, session reader, audit writer and database.
     * Output: An importer that uses the Add product rules.
     */
    public AdminProductImportService(AdminProductService adminImportProductCSVProductService,
            SharedCurrentUserReader adminImportProductCSVCurrentUserReader,
            SharedAuditLogWriter adminImportProductCSVAuditLogWriter, JdbcTemplate adminImportProductCSVDatabase) {
        this.adminImportProductCSVProductService = adminImportProductCSVProductService;
        this.adminImportProductCSVCurrentUserReader = adminImportProductCSVCurrentUserReader;
        this.adminImportProductCSVAuditLogWriter = adminImportProductCSVAuditLogWriter;
        this.adminImportProductCSVDatabase = adminImportProductCSVDatabase;
    }

    // ===== SECTION 2: adminImportProductCSV =====

    /**
     * What it does: Imports valid product lines and lists the rejected lines.
     * Input: The uploaded UTF-8 file.
     * Output: The created and rejected counts and line errors, or 400 for an invalid whole file.
     * Placeholder: PO-09. Client and Warehouse can use the imported products when integrated.
     */
    @Transactional
    public AdminImportProductCSVResponse adminImportProductCSV(MultipartFile adminImportProductCSVFile) {
        List<String> adminImportProductCSVLineList = adminReadProductCSVFile(adminImportProductCSVFile);
        Set<String> adminImportProductCSVSeenSKUList = new HashSet<>();
        List<AdminImportProductCSVRejectedLine> adminImportProductCSVRejectedLineList = new ArrayList<>();
        int adminImportProductCSVCreatedCount = 0;

        // Imports with overlapping SKUs wait for each other in case their files have different SKU orders.
        adminImportProductCSVDatabase.queryForObject(
                "SELECT 1 FROM pg_advisory_xact_lock(hashtextextended(?, 0))", Integer.class,
                AdminProductImportService.class.getName());
        for (int adminImportProductCSVIndex = 1; adminImportProductCSVIndex < adminImportProductCSVLineList.size();
                adminImportProductCSVIndex++) {
            try {
                adminSaveProductCSVLine(adminImportProductCSVLineList.get(adminImportProductCSVIndex),
                        adminImportProductCSVSeenSKUList);
                adminImportProductCSVCreatedCount++;
            } catch (SharedRuleException adminImportProductCSVError) {
                // Keep only safe field feedback and the physical line number.
                adminImportProductCSVRejectedLineList.add(new AdminImportProductCSVRejectedLine(
                        adminImportProductCSVIndex + 1, adminImportProductCSVError.getMessage()));
            }
        }

        // The import summary is stored with the individual product audit rows.
        int adminImportProductCSVRejectedCount = adminImportProductCSVRejectedLineList.size();
        adminImportProductCSVAuditLogWriter.sharedWriteAuditLog(
                adminImportProductCSVCurrentUserReader.sharedReadCurrentUser().userID(),
                SHARED_AUDIT_ACTION_PRODUCT_IMPORTED, "SYSTEM", "PRODUCT_IMPORT", null,
                Map.of("createdCount", adminImportProductCSVCreatedCount,
                        "rejectedCount", adminImportProductCSVRejectedCount));
        return new AdminImportProductCSVResponse(adminImportProductCSVCreatedCount, adminImportProductCSVRejectedCount,
                adminImportProductCSVRejectedLineList);
    }

    // ===== SECTION 3: HELPER FUNCTIONS =====

    /**
     * What it does: Checks file size, encoding, header and maximum line count before importing anything.
     * Input: The uploaded file.
     * Output: The text split into physical lines, or 400 ADMIN_IMPORT_FILE_INVALID.
     */
    private static List<String> adminReadProductCSVFile(MultipartFile adminReadProductCSVFileUpload) {
        if (adminReadProductCSVFileUpload == null
                || adminReadProductCSVFileUpload.getSize() > ADMIN_PRODUCT_IMPORT_MAXIMUM_FILE_BYTES) {
            throw adminRefuseProductCSVFile();
        }
        String adminReadProductCSVFileText;
        try {
            adminReadProductCSVFileText = StandardCharsets.UTF_8.newDecoder()
                    .decode(ByteBuffer.wrap(adminReadProductCSVFileUpload.getBytes())).toString();
        } catch (IOException adminReadProductCSVFileError) {
            throw adminRefuseProductCSVFile();
        }
        List<String> adminReadProductCSVFileLineList = adminReadProductCSVFileText.lines().toList();
        if (adminReadProductCSVFileLineList.isEmpty()
                || !"SKU,name,currentPrice,lowStockThreshold".equals(adminReadProductCSVFileLineList.getFirst())
                || adminReadProductCSVFileLineList.size() - 1 > ADMIN_PRODUCT_IMPORT_MAXIMUM_LINE_COUNT) {
            throw adminRefuseProductCSVFile();
        }
        return adminReadProductCSVFileLineList;
    }

    /**
     * What it does: Checks one row and applies the same creation rules as Add product.
     * Input: The physical line and the set of normalized SKUs already read from the file.
     * Output: None, or a rule error for the row.
     * Placeholder: PO-09. Client uses the active product and Warehouse uses its new stock row when integrated.
     */
    private void adminSaveProductCSVLine(String adminSaveProductCSVLineText,
            Set<String> adminSaveProductCSVLineSKUSet) {
        List<String> adminSaveProductCSVLineFieldList = adminReadProductCSVLine(adminSaveProductCSVLineText);
        if (adminSaveProductCSVLineFieldList.size() != 4) {
            throw adminRefuseProductCSVLine("This line must hold four fields.");
        }
        String SKU = adminCheckSKURule(adminSaveProductCSVLineFieldList.getFirst());
        if (!adminSaveProductCSVLineSKUSet.add(SKU)) {
            throw adminRefuseProductCSVLine("This SKU appears earlier in the file.");
        }

        // PLACEHOLDER OUTPUT PO-09.
        // To: Client and Warehouse.
        // What: The imported active product, with initial stock zero.
        // How to use: Client lists active products. Warehouse can restock the new inventory row when integrated.
        adminImportProductCSVProductService.adminSaveNewProduct(SKU, adminSaveProductCSVLineFieldList.get(1),
                adminSaveProductCSVLineFieldList.get(2), adminSaveProductCSVLineFieldList.get(3),
                SharedListingStatus.ACTIVE.name());
    }

    /**
     * What it does: Splits a physical CSV line, including quoted commas and doubled quotes.
     * Input: A single product line from the file.
     * Output: A list of field texts, or an error for mismatched quotes.
     */
    private static List<String> adminReadProductCSVLine(String adminReadProductCSVLineText) {
        List<String> adminReadProductCSVLineFieldList = new ArrayList<>();
        StringBuilder adminReadProductCSVLineField = new StringBuilder();
        boolean adminReadProductCSVLineIsQuoted = false;
        boolean adminReadProductCSVLineHasClosingQuote = false;
        for (int adminReadProductCSVLineIndex = 0; adminReadProductCSVLineIndex < adminReadProductCSVLineText.length();
                adminReadProductCSVLineIndex++) {
            char adminReadProductCSVLineCharacter = adminReadProductCSVLineText.charAt(adminReadProductCSVLineIndex);

            // A doubled quote inside a quoted field stands for one literal quote.
            if (adminReadProductCSVLineIsQuoted) {
                if (adminReadProductCSVLineCharacter != '"') {
                    adminReadProductCSVLineField.append(adminReadProductCSVLineCharacter);
                } else if (adminReadProductCSVLineIndex + 1 < adminReadProductCSVLineText.length()
                        && adminReadProductCSVLineText.charAt(adminReadProductCSVLineIndex + 1) == '"') {
                    adminReadProductCSVLineField.append('"');
                    adminReadProductCSVLineIndex++;
                } else {
                    adminReadProductCSVLineIsQuoted = false;
                    adminReadProductCSVLineHasClosingQuote = true;
                }
                continue;
            }

            // A comma outside quotes always ends a field.
            if (adminReadProductCSVLineCharacter == ',') {
                adminReadProductCSVLineFieldList.add(adminReadProductCSVLineField.toString());
                adminReadProductCSVLineField.setLength(0);
                adminReadProductCSVLineHasClosingQuote = false;
            } else if (adminReadProductCSVLineHasClosingQuote) {
                throw adminRefuseProductCSVLine("A comma must follow a quoted field.");
            } else if (adminReadProductCSVLineCharacter == '"') {
                if (!adminReadProductCSVLineField.isEmpty()) {
                    throw adminRefuseProductCSVLine("A quoted field must start with a double quote.");
                }
                adminReadProductCSVLineIsQuoted = true;
            } else {
                adminReadProductCSVLineField.append(adminReadProductCSVLineCharacter);
            }
        }
        if (adminReadProductCSVLineIsQuoted) {
            throw adminRefuseProductCSVLine("This line has an unclosed double quote.");
        }
        adminReadProductCSVLineFieldList.add(adminReadProductCSVLineField.toString());
        return adminReadProductCSVLineFieldList;
    }

    /**
     * What it does: Describes a whole-file refusal.
     * Input: None.
     * Output: A shared 400 error for an invalid CSV file.
     */
    private static SharedRuleException adminRefuseProductCSVFile() {
        return new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_IMPORT_FILE_INVALID",
                "The file must be UTF-8, at most 1 MB, with the required header and at most 500 product lines.");
    }

    /**
     * What it does: Describes an individual line that cannot be imported.
     * Input: A safe reason the line was rejected.
     * Output: A rule exception that the importer records against the line.
     */
    private static SharedRuleException adminRefuseProductCSVLine(String adminRefuseProductCSVLineMessage) {
        return new SharedRuleException(HttpStatus.BAD_REQUEST, "ADMIN_PRODUCT_INPUT_INVALID",
                adminRefuseProductCSVLineMessage);
    }
}
