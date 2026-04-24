package org.fanta.corte.services;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.fanta.corte.datamodel.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResultsParserTest {

    @TempDir
    Path tempDir;

    private static final BigDecimal HOME_ADV    = BigDecimal.ZERO;
    private static final int        GOAL_LIMIT  = 66;
    private static final int        GOAL_OFFSET = 6;

    // Alice 70 → 1 + floor((70-66)/6) = 1 goal
    // Bob   78 → 1 + floor((78-66)/6) = 3 goals
    private static final double ALICE_SCORE          = 70.0;
    private static final double BOB_SCORE            = 78.0;
    private static final String CORRECT_GOAL_RESULT  = "1-3";

    // ── Excel builders ────────────────────────────────────────────────────────

    /**
     * One giornata: header "Giornata lega 1", then one data row
     * [Alice | ALICE_SCORE | BOB_SCORE | Bob | goalResult].
     * Pass {@code null} for goalResult to omit column 4.
     */
    private Path singleGiornataExcel(String goalResult) throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Giornata lega 1");
            Row data = sheet.createRow(1);
            data.createCell(0).setCellValue("Alice");
            data.createCell(1).setCellValue(ALICE_SCORE);
            data.createCell(2).setCellValue(BOB_SCORE);
            data.createCell(3).setCellValue("Bob");
            if (goalResult != null) data.createCell(4).setCellValue(goalResult);
            return writeToTemp(wb, "single.xlsx");
        }
    }

    /**
     * Two giornate where different players appear in each:
     *   Giornata 1: Alice vs Bob
     *   Giornata 2: Alice vs Charlie
     * Bob and Charlie are each missing one matchday → Pass 3 must throw.
     */
    private Path missingScoreExcel() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Giornata lega 1");
            Row d1 = sheet.createRow(1);
            d1.createCell(0).setCellValue("Alice");
            d1.createCell(1).setCellValue(70.0);
            d1.createCell(2).setCellValue(78.0);
            d1.createCell(3).setCellValue("Bob");
            sheet.createRow(2).createCell(0).setCellValue("Giornata lega 2");
            Row d2 = sheet.createRow(3);
            d2.createCell(0).setCellValue("Alice");
            d2.createCell(1).setCellValue(70.0);
            d2.createCell(2).setCellValue(78.0);
            d2.createCell(3).setCellValue("Charlie");
            return writeToTemp(wb, "missing.xlsx");
        }
    }

    private Path writeToTemp(Workbook wb, String filename) throws Exception {
        Path file = tempDir.resolve(filename);
        try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
            wb.write(fos);
        }
        return file;
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void parsesCorrectPlayerCount() throws Exception {
        Path excel = singleGiornataExcel(null);
        Map<String, Player> players = ResultsParser.readExcel(
                excel.toString(), HOME_ADV, false, GOAL_LIMIT, GOAL_OFFSET);
        assertEquals(2, players.size());
        assertTrue(players.containsKey("Alice"));
        assertTrue(players.containsKey("Bob"));
    }

    @Test
    void parsesScoresCorrectly() throws Exception {
        Path excel = singleGiornataExcel(null);
        Map<String, Player> players = ResultsParser.readExcel(
                excel.toString(), HOME_ADV, false, GOAL_LIMIT, GOAL_OFFSET);
        // With HOME_ADV=ZERO the stored score equals the raw score (nothing stripped).
        assertEquals(0, BigDecimal.valueOf(ALICE_SCORE).compareTo(
                players.get("Alice").getResults().get(1)),
                "Alice's stored score should equal raw score when home advantage is zero");
        assertEquals(0, BigDecimal.valueOf(BOB_SCORE).compareTo(
                players.get("Bob").getResults().get(1)),
                "Bob's stored score should equal raw score");
    }

    @Test
    void validate_passesWhenGoalResultMatches() throws Exception {
        Path excel = singleGiornataExcel(CORRECT_GOAL_RESULT);
        assertDoesNotThrow(() ->
                ResultsParser.readExcel(excel.toString(), HOME_ADV, true, GOAL_LIMIT, GOAL_OFFSET));
    }

    @Test
    void validate_throwsWhenGoalResultMismatches() throws Exception {
        Path excel = singleGiornataExcel("5-5"); // computed result is 1-3
        assertThrows(IllegalStateException.class, () ->
                ResultsParser.readExcel(excel.toString(), HOME_ADV, true, GOAL_LIMIT, GOAL_OFFSET));
    }

    @Test
    void pass3_throwsWhenPlayerMissingMatchday() throws Exception {
        // Bob appears in giornata 1 only; Charlie in giornata 2 only.
        // Pass 3 must detect the gap and throw for at least one of them.
        Path excel = missingScoreExcel();
        assertThrows(IllegalStateException.class, () ->
                ResultsParser.readExcel(excel.toString(), HOME_ADV, false, GOAL_LIMIT, GOAL_OFFSET));
    }
}
