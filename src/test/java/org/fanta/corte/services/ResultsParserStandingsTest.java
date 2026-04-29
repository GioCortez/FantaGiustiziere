package org.fanta.corte.services;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ResultsParser.readStandings() and ResultsParser.readMatchRecords().
 *
 * Test Excel layout (2 players, 2 matchdays):
 *   Giornata 1: P1 home (70.0) vs P2 away (65.0)  → P1 wins 1-0
 *   Giornata 2: P2 home (72.0) vs P1 away (68.0)  → P2 wins 2-1
 *
 * Goal calculation with limit=66, offset=6:
 *   getGoals(70) = 1,  getGoals(65) = 0
 *   getGoals(72) = 2,  getGoals(68) = 1
 */
class ResultsParserStandingsTest {

    @TempDir
    Path tempDir;

    private Path twoMatchdayExcel() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            sheet.createRow(0).createCell(0).setCellValue("Giornata lega 1");
            Row d1 = sheet.createRow(1);
            d1.createCell(0).setCellValue("P1");
            d1.createCell(1).setCellValue(70.0);
            d1.createCell(2).setCellValue(65.0);
            d1.createCell(3).setCellValue("P2");
            sheet.createRow(2).createCell(0).setCellValue("Giornata lega 2");
            Row d2 = sheet.createRow(3);
            d2.createCell(0).setCellValue("P2");
            d2.createCell(1).setCellValue(72.0);
            d2.createCell(2).setCellValue(68.0);
            d2.createCell(3).setCellValue("P1");
            Path file = tempDir.resolve("standings.xlsx");
            try (FileOutputStream fos = new FileOutputStream(file.toFile())) {
                wb.write(fos);
            }
            return file;
        }
    }

    // ── readStandings ─────────────────────────────────────────────────────────

    @Test
    void readStandings_returnsEntryForEachPlayer() throws Exception {
        Map<String, int[]> standings = ResultsParser.readStandings(twoMatchdayExcel().toString());
        assertEquals(2, standings.size());
        assertTrue(standings.containsKey("P1"));
        assertTrue(standings.containsKey("P2"));
    }

    @Test
    void readStandings_correctWinLossRecord() throws Exception {
        Map<String, int[]> standings = ResultsParser.readStandings(twoMatchdayExcel().toString());
        // Both players win once and lose once across the two giornate.
        for (String player : new String[]{"P1", "P2"}) {
            int[] s = standings.get(player);
            assertEquals(2, s[0], player + " played");
            assertEquals(1, s[1], player + " won");
            assertEquals(0, s[2], player + " drawn");
            assertEquals(1, s[3], player + " lost");
        }
    }

    @Test
    void readStandings_correctGoalTotals() throws Exception {
        // P1: scores 1 goal in G1 (home) + 1 goal in G2 (away) = 2 for; concedes 0+2 = 2 against
        // P2: scores 0 goals in G1 (away) + 2 goals in G2 (home) = 2 for; concedes 1+1 = 2 against
        Map<String, int[]> standings = ResultsParser.readStandings(twoMatchdayExcel().toString());
        for (String player : new String[]{"P1", "P2"}) {
            int[] s = standings.get(player);
            assertEquals(2, s[4], player + " goals for");
            assertEquals(2, s[5], player + " goals against");
        }
    }

    // ── readMatchRecords ──────────────────────────────────────────────────────

    @Test
    void readMatchRecords_returnsOneRecordPerMatch() throws Exception {
        List<ResultsParser.MatchRecord> records =
                ResultsParser.readMatchRecords(twoMatchdayExcel().toString());
        assertEquals(2, records.size());
    }

    @Test
    void readMatchRecords_correctMatchData() throws Exception {
        List<ResultsParser.MatchRecord> records =
                ResultsParser.readMatchRecords(twoMatchdayExcel().toString());
        records.sort((a, b) -> Integer.compare(a.giornata, b.giornata));

        ResultsParser.MatchRecord r1 = records.get(0);
        assertEquals(1,    r1.giornata);
        assertEquals("P1", r1.homePlayer);
        assertEquals("P2", r1.awayPlayer);
        assertEquals(70.0, r1.homeScore, 0.01);
        assertEquals(65.0, r1.awayScore, 0.01);

        ResultsParser.MatchRecord r2 = records.get(1);
        assertEquals(2,    r2.giornata);
        assertEquals("P2", r2.homePlayer);
        assertEquals("P1", r2.awayPlayer);
        assertEquals(72.0, r2.homeScore, 0.01);
        assertEquals(68.0, r2.awayScore, 0.01);
    }

    @Test
    void readMatchRecords_bothLegsAreNonNeutral() throws Exception {
        // 2 giornate → totalLegs=2 (even) → legsWithAdvantage=2 → no neutral legs
        List<ResultsParser.MatchRecord> records =
                ResultsParser.readMatchRecords(twoMatchdayExcel().toString());
        for (ResultsParser.MatchRecord r : records) {
            assertFalse(r.neutral, "Giornata " + r.giornata + " should not be neutral");
        }
    }
}
