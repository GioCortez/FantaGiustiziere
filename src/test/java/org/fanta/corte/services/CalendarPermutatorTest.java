package org.fanta.corte.services;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.fanta.corte.datamodel.Player;
import org.fanta.corte.services.CalendarPermutator.PartialResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CalendarPermutatorTest {

    // 4 players → 4! = 24 permutations; Berger double round-robin needs (n-1)*2 = 6 match days.
    private static final int PLAYER_COUNT = 4;
    private static final int MATCH_DAYS = (PLAYER_COUNT - 1) * 2;
    private static final BigDecimal HOME_ADVANTAGE = BigDecimal.valueOf(2);

    // Per-player, per-day scores (indexed [player 0-based][day 0-based]).
    // All values stay < 106 so that adding home advantage (2) never hits the 108 ceiling.
    //
    // IMPORTANT: each player must have a DISTINCT sum so that ScoreComparator's secondary
    // tiebreaker (getTotalPoints()) is always decisive.  When totals are equal the comparator
    // returns 0, the sort falls back to HashMap iteration order, and that order depends on
    // Player identity-hashcodes — which differ between the two createPlayers() calls used by
    // the single-thread and multi-thread runs, causing spurious test failures.
    private static final int[][] PLAYER_SCORES = {
        {60, 67, 73, 79, 85, 60},   // Player1 total = 424
        {60, 67, 73, 79, 85, 67},   // Player2 total = 431
        {60, 67, 73, 79, 85, 73},   // Player3 total = 437
        {60, 67, 73, 79, 85, 79},   // Player4 total = 443
    };

    /**
     * Builds a fresh set of synthetic players each time it is called.
     * Using LinkedHashMap to guarantee a stable insertion order (important for Heap's algorithm).
     */
    private Map<String, Player> createPlayers() {
        Map<String, Player> players = new LinkedHashMap<>();
        for (int i = 0; i < PLAYER_COUNT; i++) {
            Player p = new Player("Player" + (i + 1), "P" + (i + 1));
            for (int day = 1; day <= MATCH_DAYS; day++) {
                p.addResult(day, BigDecimal.valueOf(PLAYER_SCORES[i][day - 1]));
            }
            players.put(p.getName(), p);
        }
        return players;
    }

    @Test
    void permutationLimitIsEnforced_singleThread() {
        // 4! = 24 total; a limit of 5 must stop early and report exactly 5 processed.
        int limit = 5;
        PartialResult result = new CalendarPermutator(createPlayers(), HOME_ADVANTAGE)
                .computePermutations(limit, 1);
        assertEquals(limit, result.permutationCounter,
                "Single-thread: should process exactly 'limit' permutations");
    }

    @Test
    void permutationLimitIsEnforced_multiThread() {
        // The bug: with the old code (check AFTER processPermutation) each thread could
        // process one extra permutation before seeing the throw, so multi-thread runs
        // produced limit+N results (N = thread count). The fix moves the check BEFORE
        // processPermutation so the atomic decrement acts as a slot gate.
        int limit = 5;
        PartialResult result = new CalendarPermutator(createPlayers(), HOME_ADVANTAGE)
                .computePermutations(limit, -1); // -1 = auto thread count
        assertEquals(limit, result.permutationCounter,
                "Multi-thread: should process exactly 'limit' permutations, not limit+N");
    }

    @Test
    void singleAndMultiThreadProduceSameStatistics() {
        // Run single-thread (limit=-1 → all 24 permutations)
        PartialResult single = new CalendarPermutator(createPlayers(), HOME_ADVANTAGE)
                .computePermutations(-1, 1);

        // Run multi-thread (limit=-1 → all 24 permutations across n partitions)
        PartialResult multi = new CalendarPermutator(createPlayers(), HOME_ADVANTAGE)
                .computePermutations(-1, -1);

        // Total permutation count must match
        assertEquals(single.permutationCounter, multi.permutationCounter,
                "Both modes must process the same total number of permutations");

        // Each player's position-frequency array must be identical
        assertEquals(single.statistics.size(), multi.statistics.size(),
                "Both results must track the same number of players");

        for (Map.Entry<Player, long[]> entry : single.statistics.entrySet()) {
            String name = entry.getKey().getName();
            long[] singleStats = entry.getValue();

            Player multiPlayer = multi.statistics.keySet().stream()
                    .filter(p -> p.getName().equals(name))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Player '" + name + "' missing from multi-thread result"));

            long[] multiStats = multi.statistics.get(multiPlayer);
            assertArrayEquals(singleStats, multiStats,
                    "Position statistics differ for player '" + name + "': "
                    + "single=" + Arrays.toString(singleStats)
                    + " multi=" + Arrays.toString(multiStats));
        }
    }
}
