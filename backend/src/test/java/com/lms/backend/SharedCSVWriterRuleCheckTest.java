package com.lms.backend;

import static com.lms.backend.shared.csv.SharedCSVWriter.sharedWriteCSV;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SharedCSVWriterRuleCheckTest {

    // ===== SECTION 1: CSV STRUCTURE =====

    /**
     * What it does: Checks the Excel UTF-8 start mark and quoted header fields.
     * Input: Two headings with no data rows.
     * Output: The check passes when the file begins with the UTF-8 mark and the header row.
     */
    @Test
    void sharedWriteCSVStartsWithHeader() {
        byte[] sharedWriteCSVBytes = sharedWriteCSV(List.of("first", "second"), List.of())
                .getBytes(StandardCharsets.UTF_8);
        assertEquals("efbbbf", java.util.HexFormat.of().formatHex(sharedWriteCSVBytes, 0, 3));
        assertEquals("\"first\",\"second\"\r\n", new String(sharedWriteCSVBytes, 3,
                sharedWriteCSVBytes.length - 3, StandardCharsets.UTF_8));
    }

    /**
     * What it does: Checks punctuation and line breaks inside a quoted field.
     * Input: One row with a comma, a quote, and a newline in separate fields.
     * Output: The check passes when the fields remain intact and the inner quote is doubled.
     */
    @Test
    void sharedWriteCSVEscapesQuotedFields() {
        assertEquals("\uFEFF\"comma\",\"quote\",\"line\"\r\n\"a,b\",\"a\"\"b\",\"a\nb\"\r\n",
                sharedWriteCSV(List.of("comma", "quote", "line"), List.of(List.of("a,b", "a\"b", "a\nb"))));
    }

    // ===== SECTION 2: SPREADSHEET FORMULA PREFIXES =====

    /**
     * What it does: Checks that formula-like values are prefixed with a single quote.
     * Input: One field starting with an agreed formula character.
     * Output: The check passes when a spreadsheet receives that value as text.
     */
    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1+1", "-1+1", "@A1"})
    void sharedWriteCSVNeutralizesFormula(String sharedWriteCSVFormulaField) {
        assertEquals("\uFEFF\"reason\"\r\n\"'" + sharedWriteCSVFormulaField + "\"\r\n",
                sharedWriteCSV(List.of("reason"), List.of(List.of(sharedWriteCSVFormulaField))));
    }
}
