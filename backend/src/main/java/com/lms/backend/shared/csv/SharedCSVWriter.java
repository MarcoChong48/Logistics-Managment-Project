package com.lms.backend.shared.csv;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

public final class SharedCSVWriter {

    // ===== SECTION 1: CONSTANTS =====

    private static final DateTimeFormatter SHARED_WRITE_CSV_FILE_TIME_FORMAT =
            DateTimeFormatter.ofPattern("uuuuMMdd-HHmmss", Locale.ROOT).withZone(ZoneOffset.UTC);

    /**
     * What it does: Keeps the pure writer from being created as an object.
     * Input: None.
     * Output: None.
     */
    private SharedCSVWriter() {
    }

    // ===== SECTION 2: sharedWriteCSV =====

    /**
     * What it does: Writes quoted CSV text with the Excel start mark and formula guards.
     * Input: The column names and the ordered rows.
     * Output: The CSV text ready to encode as UTF-8.
     */
    public static String sharedWriteCSV(List<String> sharedWriteCSVHeaderList,
            List<? extends List<?>> sharedWriteCSVRowList) {
        // The start mark lets spreadsheets recognize UTF-8 when they open the file.
        StringBuilder sharedWriteCSVResult = new StringBuilder("\uFEFF");
        sharedWriteCSVLine(sharedWriteCSVResult, sharedWriteCSVHeaderList);
        for (List<?> sharedWriteCSVRow : sharedWriteCSVRowList) {
            sharedWriteCSVLine(sharedWriteCSVResult, sharedWriteCSVRow);
        }
        return sharedWriteCSVResult.toString();
    }

    // ===== SECTION 3: CSV DOWNLOAD =====

    /**
     * What it does: Builds an HTTP attachment from the safe columns and rows.
     * Input: The header, rows, filename prefix, UTC time and optional limit message.
     * Output: The UTF-8 CSV attachment.
     */
    public static ResponseEntity<byte[]> sharedWriteCSVDownload(List<String> sharedWriteCSVDownloadHeaderList,
            List<? extends List<?>> sharedWriteCSVDownloadRowList, String sharedWriteCSVDownloadFilePrefix,
            Instant sharedWriteCSVDownloadTime, String sharedWriteCSVDownloadLimitMessage) {
        // The limit message is a single field after the exported data rows.
        List<List<?>> sharedWriteCSVDownloadContentList = new ArrayList<>(sharedWriteCSVDownloadRowList);
        if (sharedWriteCSVDownloadLimitMessage != null) {
            sharedWriteCSVDownloadContentList.add(List.of(sharedWriteCSVDownloadLimitMessage));
        }
        byte[] sharedWriteCSVDownloadBody = sharedWriteCSV(sharedWriteCSVDownloadHeaderList,
                sharedWriteCSVDownloadContentList).getBytes(StandardCharsets.UTF_8);
        String sharedWriteCSVDownloadFileName = sharedWriteCSVDownloadFilePrefix + "-"
                + SHARED_WRITE_CSV_FILE_TIME_FORMAT.format(sharedWriteCSVDownloadTime) + ".csv";
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(sharedWriteCSVDownloadFileName).build().toString())
                .body(sharedWriteCSVDownloadBody);
    }

    // ===== SECTION 4: HELPER FUNCTIONS =====

    /**
     * What it does: Appends one CSV row, escaping every field and neutralizing formula prefixes.
     * Input: The file text built so far and the fields of this row.
     * Output: None. The quoted row is added to the file text.
     */
    private static void sharedWriteCSVLine(StringBuilder sharedWriteCSVLineResult, List<?> sharedWriteCSVLineFieldList) {
        boolean sharedWriteCSVLineIsFirstField = true;
        for (Object sharedWriteCSVLineField : sharedWriteCSVLineFieldList) {
            if (!sharedWriteCSVLineIsFirstField) {
                sharedWriteCSVLineResult.append(',');
            }
            sharedWriteCSVLineIsFirstField = false;

            // A missing value is an empty quoted field. Formula prefixes are written as text.
            String sharedWriteCSVLineText = sharedWriteCSVLineField == null ? "" : sharedWriteCSVLineField.toString();
            sharedWriteCSVLineResult.append('"');
            if (!sharedWriteCSVLineText.isEmpty() && "=+-@".indexOf(sharedWriteCSVLineText.charAt(0)) >= 0) {
                sharedWriteCSVLineResult.append('\'');
            }
            sharedWriteCSVLineResult.append(sharedWriteCSVLineText.replace("\"", "\"\"")).append('"');
        }
        sharedWriteCSVLineResult.append("\r\n");
    }
}
