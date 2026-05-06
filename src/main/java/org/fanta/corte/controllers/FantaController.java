package org.fanta.corte.controllers;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.fanta.corte.datamodel.Player;
import org.fanta.corte.services.CalendarPermutator;
import org.fanta.corte.services.CalendarPermutator.PartialResult;
import org.fanta.corte.services.ResultsParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FantaController {

    @Value("${computation.timeout-minutes:30}")
    private long computationTimeoutMinutes;

    @Autowired
    private Executor computationExecutor;

    private static final Logger LOGGER = LoggerFactory.getLogger(FantaController.class.getSimpleName());

    private final ConcurrentHashMap<String, ParseSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Step 1: parse the uploaded file, return player list and a session token
    // -------------------------------------------------------------------------

    @PostMapping(value = "/parse", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> parse(@RequestParam("file") MultipartFile file,
                                   HttpServletRequest request) throws IOException, InvalidFormatException {

        Path tempFile = Files.createTempFile("fanta-", ".xlsx");
        boolean sessionCreated = false;
        try {
            file.transferTo(tempFile);

            Map<String, Player> players = ResultsParser.readExcel(
                    tempFile.toString(), BigDecimal.ZERO, false);

            if (players.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        "Nessun giocatore trovato nel file. Verifica che il file sia nel formato corretto.");
            }

            int playerCount = players.size();
            int matchdayCount = players.values().stream()
                    .findFirst()
                    .map(p -> p.getResults().size())
                    .orElse(0);

            long incompleteMatchdays = 0;
            if (!players.isEmpty()) {
                java.util.Set<Integer> allMatchdays = players.values().iterator().next().getResults().keySet();
                incompleteMatchdays = allMatchdays.stream()
                        .filter(g -> players.values().stream()
                                .allMatch(p -> BigDecimal.ZERO.compareTo(p.getResults().get(g)) == 0))
                        .count();
            }

            List<String> playerNames = players.keySet().stream().sorted().collect(Collectors.toList());
            String token = UUID.randomUUID().toString();
            sessions.put(token, new ParseSession(tempFile, playerCount, playerNames,
                    file.getOriginalFilename(), Instant.now()));
            sessionCreated = true;

            LOGGER.info("File uploaded — ip='{}' file='{}' players={} matchdays={} incomplete={} token={}",
                    clientIp(request), file.getOriginalFilename(), playerCount,
                    matchdayCount, incompleteMatchdays, token);

            Map<String, int[]> standingsData = ResultsParser.readStandings(tempFile.toString());

            ParseResult result = new ParseResult();
            result.token = token;
            result.playerCount = playerCount;
            result.matchdayCount = matchdayCount;
            result.incompleteMatchdays = (int) incompleteMatchdays;
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

            result.matches = ResultsParser.readMatchRecords(tempFile.toString());

            return ResponseEntity.ok(result);

        } finally {
            if (!sessionCreated) {
                Files.deleteIfExists(tempFile);
            }
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
    ) throws IOException, InvalidFormatException {
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
        ResultsParser.readExcel(session.tempFile.toString(),
                homeAdvantage, true, goalLimit, goalOffset);
        LOGGER.info("Validation OK: token={} homeAdvantage={} goalLimit={} goalOffset={}",
                token, homeAdvantage, goalLimit, goalOffset);
        return ResponseEntity.ok().build();
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
    ) {
        if (homeAdvantage.compareTo(BigDecimal.ZERO) < 0) {
            return ResponseEntity.badRequest().body("Il vantaggio casalingo non può essere negativo.");
        }
        if (goalLimit < 66) {
            return ResponseEntity.badRequest().body("La soglia gol non può essere inferiore a 66.");
        }
        if (goalOffset <= 0) {
            return ResponseEntity.badRequest().body("L'intervallo gol deve essere un intero maggiore di 0.");
        }
        if (threads < -1 || threads == 0 || threads > 64) {
            return ResponseEntity.badRequest().body("Il parametro threads deve essere -1 (auto), 1 (single-thread), o un intero tra 2 e 64.");
        }
        ParseSession session = sessions.remove(token);
        if (session == null) {
            return ResponseEntity.badRequest().body(
                    "Sessione scaduta o non trovata. Carica di nuovo il file.");
        }

        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId);
        jobs.put(jobId, job);
        CompletableFuture.runAsync(() -> {
            job.status = JobStatus.RUNNING;
            try {
                Map<String, Player> players = ResultsParser.readExcel(
                        session.tempFile.toString(), homeAdvantage, false);
                LOGGER.info("=== ANALYSIS STARTED === job={} file='{}' players={} [{}] homeAdvantage={} goalLimit={} goalOffset={} threads={} permLimit={}",
                        jobId, session.originalFilename, session.playerCount,
                        String.join(", ", session.playerNames),
                        homeAdvantage, goalLimit, goalOffset, threads,
                        permutationLimit < 0 ? "unlimited" : permutationLimit);
                CalendarPermutator permutator = new CalendarPermutator(
                        players, homeAdvantage, goalLimit, goalOffset, computationTimeoutMinutes);
                PartialResult result = permutator.computePermutations(permutationLimit, threads, job.progressCounter);
                job.result = RunResult.from(result);
                job.status = JobStatus.DONE;
                LOGGER.info("Job {} completed: {} permutations", jobId, result.permutationCounter);
            } catch (Exception e) {
                job.error = e.getMessage();
                job.status = JobStatus.ERROR;
                LOGGER.error("Job {} failed: {}", jobId, e.getMessage(), e);
            } finally {
                try {
                    Files.deleteIfExists(session.tempFile);
                } catch (IOException e) {
                    LOGGER.warn("Could not delete temp file {}: {}", session.tempFile, e.getMessage());
                }
            }
        }, computationExecutor);

        return ResponseEntity.accepted().body(Map.of("jobId", jobId));
    }

    // -------------------------------------------------------------------------
    // Step 3b: poll job status
    // -------------------------------------------------------------------------

    @GetMapping("/status/{jobId}")
    public ResponseEntity<?> status(@PathVariable String jobId) {
        Job job = jobs.get(jobId);
        if (job == null) {
            return ResponseEntity.notFound().build();
        }
        StatusResponse resp = new StatusResponse();
        resp.status    = job.status.name();
        resp.processed = job.progressCounter.get();
        resp.elapsedMs = Duration.between(job.startedAt, Instant.now()).toMillis();
        if (job.status == JobStatus.DONE)  resp.result = job.result;
        if (job.status == JobStatus.ERROR) resp.error  = job.error;
        return ResponseEntity.ok(resp);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    @Scheduled(fixedDelay = 15 * 60 * 1000) // every 15 minutes
    void evictStaleSessions() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.HOURS);
        sessions.entrySet().removeIf(e -> {
            if (e.getValue().createdAt.isBefore(cutoff)) {
                try { Files.deleteIfExists(e.getValue().tempFile); } catch (IOException ignored) {}
                LOGGER.info("Evicted stale session, temp file: {}", e.getValue().tempFile);
                return true;
            }
            return false;
        });
        jobs.entrySet().removeIf(e -> {
            if (e.getValue().createdAt.isBefore(cutoff)) {
                LOGGER.info("Evicted stale job: {}", e.getKey());
                return true;
            }
            return false;
        });
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static class ParseSession {
        final Path tempFile;
        final int playerCount;
        final List<String> playerNames;
        final String originalFilename;
        final Instant createdAt;

        ParseSession(Path tempFile, int playerCount, List<String> playerNames,
                     String originalFilename, Instant createdAt) {
            this.tempFile = tempFile;
            this.playerCount = playerCount;
            this.playerNames = playerNames;
            this.originalFilename = originalFilename;
            this.createdAt = createdAt;
        }
    }

    private enum JobStatus { PENDING, RUNNING, DONE, ERROR }

    private static class Job {
        final String id;
        volatile JobStatus status = JobStatus.PENDING;
        final AtomicLong progressCounter = new AtomicLong(0);
        final Instant startedAt = Instant.now();
        final Instant createdAt = Instant.now();
        volatile RunResult result;
        volatile String error;

        Job(String id) { this.id = id; }
    }

    private static class StatusResponse {
        public String status;
        public long processed;
        public long elapsedMs;
        public RunResult result;
        public String error;
    }
}
