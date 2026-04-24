package org.fanta.corte.controllers;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

    // In-memory session store: token → session data. Cleaned up lazily on each /parse call.
    private final ConcurrentHashMap<String, ParseSession> sessions = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Step 1: parse the uploaded file, return player list and a session token
    // -------------------------------------------------------------------------

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> parse(@RequestParam("file") MultipartFile file) throws IOException {
        evictStaleSessions();

        Path tempFile = Files.createTempFile("fanta-", ".xlsx");
        try {
            file.transferTo(tempFile);

            Map<String, Player> players = ResultsParser.readExcel(
                    tempFile.toString(), BigDecimal.ZERO, false);

            if (players.isEmpty()) {
                Files.deleteIfExists(tempFile);
                return ResponseEntity.badRequest().body(
                        "Nessun giocatore trovato nel file. Verifica che il file sia nel formato corretto.");
            }

            int playerCount = players.size();
            int matchdayCount = players.values().stream()
                    .findFirst()
                    .map(p -> p.getResults().size())
                    .orElse(0);

            String token = UUID.randomUUID().toString();
            sessions.put(token, new ParseSession(tempFile, playerCount, Instant.now()));

            LOGGER.info("Parsed file {}: {} players, {} matchdays, token={}",
                    file.getOriginalFilename(), playerCount, matchdayCount, token);

            Map<String, int[]> standingsData = ResultsParser.readStandings(tempFile.toString());

            ParseResult result = new ParseResult();
            result.token = token;
            result.playerCount = playerCount;
            result.matchdayCount = matchdayCount;
            result.players = players.values().stream()
                    .map(p -> {
                        ParseResult.PlayerSummary ps = new ParseResult.PlayerSummary();
                        ps.name = p.getName();
                        ps.totalScore = p.getTotalPoints().doubleValue();
                        return ps;
                    })
                    .sorted(Comparator.comparingDouble((ParseResult.PlayerSummary ps) -> ps.totalScore).reversed())
                    .collect(Collectors.toList());

            result.standings = standingsData.entrySet().stream()
                    .map(e -> {
                        ParseResult.StandingRow row = new ParseResult.StandingRow();
                        row.player = e.getKey();
                        int[] s = e.getValue();
                        row.played = s[0];
                        row.won   = s[1];
                        row.drawn = s[2];
                        row.lost  = s[3];
                        row.goalsFor     = s[4];
                        row.goalsAgainst = s[5];
                        row.goalDiff = s[4] - s[5];
                        row.points   = 3 * s[1] + s[2];
                        Player p = players.get(e.getKey());
                        row.totalScore = p != null ? p.getTotalPoints().doubleValue() : 0.0;
                        return row;
                    })
                    .sorted((a, b) -> {
                        if (b.points != a.points)   return Integer.compare(b.points, a.points);
                        if (b.goalDiff != a.goalDiff) return Integer.compare(b.goalDiff, a.goalDiff);
                        return Double.compare(b.totalScore, a.totalScore);
                    })
                    .collect(Collectors.toList());

            return ResponseEntity.ok(result);

        } catch (InvalidFormatException | IllegalArgumentException | IllegalStateException e) {
            Files.deleteIfExists(tempFile);
            LOGGER.error("Error parsing file: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Step 2: validate goal results with the chosen home advantage
    // -------------------------------------------------------------------------

    @PostMapping("/validate")
    public ResponseEntity<?> validate(
            @RequestParam("token") String token,
            @RequestParam(defaultValue = "2")  BigDecimal homeAdvantage,
            @RequestParam(defaultValue = "66") int goalLimit,
            @RequestParam(defaultValue = "6")  int goalOffset
    ) throws IOException {
        if (homeAdvantage.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body("Il vantaggio casalingo non può essere negativo.");
        }
        if (goalLimit < 66) {
            return ResponseEntity.badRequest().body("La soglia gol non può essere inferiore a 66.");
        }
        if (goalOffset <= 0) {
            return ResponseEntity.badRequest().body("L'intervallo gol deve essere un intero maggiore di 0.");
        }
        ParseSession session = sessions.get(token); // peek only — session stays alive for /run
        if (session == null) {
            return ResponseEntity.badRequest().body(
                    "Sessione scaduta o non trovata. Carica di nuovo il file.");
        }
        try {
            ResultsParser.readExcel(session.tempFile.toString(),
                    homeAdvantage, true, goalLimit, goalOffset);
            LOGGER.info("Validation OK: token={} homeAdvantage={} goalLimit={} goalOffset={}",
                    token, homeAdvantage, goalLimit, goalOffset);
            return ResponseEntity.ok().build();
        } catch (InvalidFormatException | IllegalArgumentException | IllegalStateException e) {
            LOGGER.error("Validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Step 3: run permutations using a previously parsed session
    // -------------------------------------------------------------------------

    @PostMapping("/run")
    public ResponseEntity<?> run(
            @RequestParam("token") String token,
            @RequestParam(defaultValue = "2")  BigDecimal homeAdvantage,
            @RequestParam(defaultValue = "-1") int threads,
            @RequestParam(defaultValue = "-1") long permutationLimit,
            @RequestParam(defaultValue = "66") int goalLimit,
            @RequestParam(defaultValue = "6")  int goalOffset
    ) throws IOException {
        if (homeAdvantage.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body("Il vantaggio casalingo non può essere negativo.");
        }
        if (goalLimit < 66) {
            return ResponseEntity.badRequest().body("La soglia gol non può essere inferiore a 66.");
        }
        if (goalOffset <= 0) {
            return ResponseEntity.badRequest().body("L'intervallo gol deve essere un intero maggiore di 0.");
        }
        ParseSession session = sessions.remove(token);
        if (session == null) {
            return ResponseEntity.badRequest().body(
                    "Sessione scaduta o non trovata. Carica di nuovo il file.");
        }

        try {
            LOGGER.info("Running: playerCount={} homeAdvantage={} goalLimit={} goalOffset={} threads={} limit={}",
                    session.playerCount, homeAdvantage, goalLimit, goalOffset, threads, permutationLimit);

            Map<String, Player> players = ResultsParser.readExcel(
                    session.tempFile.toString(), homeAdvantage, false);
            CalendarPermutator permutator = new CalendarPermutator(players, homeAdvantage, goalLimit, goalOffset);
            PartialResult result = permutator.computePermutations(permutationLimit, threads);

            return ResponseEntity.ok(RunResult.from(result));

        } catch (InvalidFormatException | IllegalArgumentException | IllegalStateException e) {
            LOGGER.error("Error processing request: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } finally {
            Files.deleteIfExists(session.tempFile);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void evictStaleSessions() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        sessions.entrySet().removeIf(e -> {
            if (e.getValue().createdAt.isBefore(cutoff)) {
                try { Files.deleteIfExists(e.getValue().tempFile); } catch (IOException ignored) {}
                return true;
            }
            return false;
        });
    }

    private static class ParseSession {
        final Path tempFile;
        final int playerCount;
        final Instant createdAt;

        ParseSession(Path tempFile, int playerCount, Instant createdAt) {
            this.tempFile = tempFile;
            this.playerCount = playerCount;
            this.createdAt = createdAt;
        }
    }
}
