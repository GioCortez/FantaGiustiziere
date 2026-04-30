package org.fanta.corte.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for FantaController using the full Spring context and MockMvc.
 *
 * Test Excel: 4 players (P1-P4), 6 matchdays (2 legs × 3 matchdays) — the minimum
 * that BergerAlgorithm supports (it requires n ≥ 4). Yields 4! = 24 permutations.
 * Each player's per-day score is embedded as their cell value in the row where they
 * appear, so readExcel stores exactly {day → score} for all 6 days per player.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc
class FantaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Builds a 4-player, 6-matchday Excel.
     * Player scores per day: P1={70,71,72,73,74,75}, P2={65,66,67,68,69,70},
     *                         P3={68,69,70,71,72,73}, P4={63,64,65,66,67,68}.
     * Each player appears exactly once per giornata (home or away).
     */
    private MockMultipartFile buildValidExcel() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            // Giornata 1: P1(70) vs P2(65),  P3(68) vs P4(63)
            sheet.createRow(0).createCell(0).setCellValue("Giornata lega 1");
            addMatch(sheet, 1,  "P1", 70.0, 65.0, "P2");
            addMatch(sheet, 2,  "P3", 68.0, 63.0, "P4");
            // Giornata 2: P1(71) vs P3(69),  P2(66) vs P4(64)
            sheet.createRow(3).createCell(0).setCellValue("Giornata lega 2");
            addMatch(sheet, 4,  "P1", 71.0, 69.0, "P3");
            addMatch(sheet, 5,  "P2", 66.0, 64.0, "P4");
            // Giornata 3: P1(72) vs P4(65),  P2(67) vs P3(70)
            sheet.createRow(6).createCell(0).setCellValue("Giornata lega 3");
            addMatch(sheet, 7,  "P1", 72.0, 65.0, "P4");
            addMatch(sheet, 8,  "P2", 67.0, 70.0, "P3");
            // Giornata 4: P2(68) vs P1(73),  P4(66) vs P3(71)
            sheet.createRow(9).createCell(0).setCellValue("Giornata lega 4");
            addMatch(sheet, 10, "P2", 68.0, 73.0, "P1");
            addMatch(sheet, 11, "P4", 66.0, 71.0, "P3");
            // Giornata 5: P3(72) vs P1(74),  P4(67) vs P2(69)
            sheet.createRow(12).createCell(0).setCellValue("Giornata lega 5");
            addMatch(sheet, 13, "P3", 72.0, 74.0, "P1");
            addMatch(sheet, 14, "P4", 67.0, 69.0, "P2");
            // Giornata 6: P4(68) vs P1(75),  P3(73) vs P2(70)
            sheet.createRow(15).createCell(0).setCellValue("Giornata lega 6");
            addMatch(sheet, 16, "P4", 68.0, 75.0, "P1");
            addMatch(sheet, 17, "P3", 73.0, 70.0, "P2");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return new MockMultipartFile("file", "test.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bos.toByteArray());
        }
    }

    private static void addMatch(Sheet sheet, int rowIdx,
                                 String home, double homeScore,
                                 double awayScore, String away) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(home);
        row.createCell(1).setCellValue(homeScore);
        row.createCell(2).setCellValue(awayScore);
        row.createCell(3).setCellValue(away);
    }

    private MockMultipartFile buildEmptyExcel() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet();
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return new MockMultipartFile("file", "empty.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    bos.toByteArray());
        }
    }

    private String parseAndGetToken() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/parse").file(buildValidExcel()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("token").asText();
    }

    // ── /parse ────────────────────────────────────────────────────────────────

    @Test
    void parse_validFile_returns200WithParsedData() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/parse").file(buildValidExcel()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertFalse(json.get("token").asText().isBlank(), "token must be present");
        assertEquals(4, json.get("playerCount").asInt());
        assertEquals(6, json.get("matchdayCount").asInt());
        assertEquals(4, json.get("players").size());
        assertNotNull(json.get("standings"));
        assertNotNull(json.get("matches"));
    }

    @Test
    void parse_excelWithNoGiornate_returns400() throws Exception {
        // An Excel with no "Giornata lega" headers produces no players → 400.
        mockMvc.perform(multipart("/parse").file(buildEmptyExcel()))
                .andExpect(status().isBadRequest());
    }

    // ── /validate ─────────────────────────────────────────────────────────────

    @Test
    void validate_validToken_returns200() throws Exception {
        String token = parseAndGetToken();
        mockMvc.perform(post("/validate")
                        .param("token", token)
                        .param("homeAdvantage", "0")
                        .param("goalLimit", "66")
                        .param("goalOffset", "6"))
                .andExpect(status().isOk());
    }

    @Test
    void validate_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/validate")
                        .param("token", "no-such-session"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validate_negativeHomeAdvantage_returns400() throws Exception {
        mockMvc.perform(post("/validate")
                        .param("token", "any")
                        .param("homeAdvantage", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validate_goalLimitBelowMinimum_returns400() throws Exception {
        mockMvc.perform(post("/validate")
                        .param("token", "any")
                        .param("goalLimit", "65"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validate_goalOffsetZero_returns400() throws Exception {
        mockMvc.perform(post("/validate")
                        .param("token", "any")
                        .param("goalOffset", "0"))
                .andExpect(status().isBadRequest());
    }

    // ── /run ──────────────────────────────────────────────────────────────────

    /** Polls GET /status/{jobId} every 100 ms until DONE, then returns the result node. */
    private JsonNode pollUntilDone(String jobId) throws Exception {
        for (int i = 0; i < 100; i++) {
            Thread.sleep(100);
            MvcResult sr = mockMvc.perform(get("/status/" + jobId))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode json = objectMapper.readTree(sr.getResponse().getContentAsString());
            String s = json.get("status").asText();
            if ("DONE".equals(s))  return json.get("result");
            if ("ERROR".equals(s)) fail("Job failed: " + json.get("error").asText());
        }
        fail("Job did not complete within 10 seconds");
        return null;
    }

    @Test
    void run_validToken_returns202ThenCompletesWithResult() throws Exception {
        String token = parseAndGetToken();
        MvcResult runResult = mockMvc.perform(post("/run")
                        .param("token", token)
                        .param("homeAdvantage", "0")
                        .param("threads", "1")
                        .param("permutationLimit", "-1")
                        .param("goalLimit", "66")
                        .param("goalOffset", "6"))
                .andExpect(status().isAccepted())
                .andReturn();
        String jobId = objectMapper.readTree(runResult.getResponse().getContentAsString())
                .get("jobId").asText();
        JsonNode result = pollUntilDone(jobId);
        assertTrue(result.get("permutationCount").asInt() > 0);
        assertEquals(4, result.get("statistics").size());
    }

    @Test
    void run_missingToken_returns400() throws Exception {
        mockMvc.perform(post("/run")
                        .param("token", "bogus-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void run_negativeHomeAdvantage_returns400() throws Exception {
        mockMvc.perform(post("/run")
                        .param("token", "any")
                        .param("homeAdvantage", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void run_consumesToken_secondRunFails() throws Exception {
        String token = parseAndGetToken();
        mockMvc.perform(post("/run")
                        .param("token", token)
                        .param("homeAdvantage", "0")
                        .param("threads", "1")
                        .param("goalLimit", "66")
                        .param("goalOffset", "6"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/run")
                        .param("token", token)
                        .param("homeAdvantage", "0")
                        .param("threads", "1")
                        .param("goalLimit", "66")
                        .param("goalOffset", "6"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validate_preservesToken_thenRunSucceeds() throws Exception {
        String token = parseAndGetToken();
        mockMvc.perform(post("/validate")
                        .param("token", token)
                        .param("homeAdvantage", "0")
                        .param("goalLimit", "66")
                        .param("goalOffset", "6"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/run")
                        .param("token", token)
                        .param("homeAdvantage", "0")
                        .param("threads", "1")
                        .param("goalLimit", "66")
                        .param("goalOffset", "6"))
                .andExpect(status().isAccepted());
    }
}
