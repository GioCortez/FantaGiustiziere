package org.fanta.corte.controllers;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.fanta.corte.datamodel.Player;
import org.fanta.corte.services.CalendarPermutator;
import org.fanta.corte.services.CalendarPermutator.PartialResult;
import org.fanta.corte.services.ResultsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FantaController {

    private static final Logger LOGGER = LoggerFactory.getLogger(FantaController.class.getSimpleName());

    @PostMapping(value = "/run", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> run(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "12") int players,
            @RequestParam(defaultValue = "2") BigDecimal homeAdvantage,
            @RequestParam(defaultValue = "-1") int threads,
            @RequestParam(defaultValue = "0") long permutationLimit
    ) throws IOException {
        Path tempFile = Files.createTempFile("fanta-", ".xlsx");
        try {
            file.transferTo(tempFile);
            LOGGER.info("Running: players={} homeAdvantage={} threads={} limit={} file={}",
                    players, homeAdvantage, threads, permutationLimit, file.getOriginalFilename());

            Map<String, Player> fantaPlayers = ResultsParser.readExcel(tempFile.toString(), players, homeAdvantage);
            CalendarPermutator permutator = new CalendarPermutator(fantaPlayers, homeAdvantage);
            PartialResult result = permutator.computePermutations(permutationLimit, threads);

            return ResponseEntity.ok(RunResult.from(result));
        } catch (InvalidFormatException | IllegalArgumentException e) {
            LOGGER.error("Error processing request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }
}
