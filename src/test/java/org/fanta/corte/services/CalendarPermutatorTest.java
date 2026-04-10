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

    // Scores stay well below 106 so that adding home advantage (2) never hits the 108 ceiling.
    private static final BigDecimal[] SCORE_POOL = {
        BigDecimal.valueOf(60),
        BigDecimal.valueOf(67),
        BigDecimal.valueOf(73),
        BigDecimal.valueOf(79),
        BigDecimal.valueOf(85),
        BigDecimal.valueOf(91),
    };

    /**
     * Builds a fresh set of synthetic players each time it is called.
     * Using LinkedHashMap to guarantee a stable insertion order (important for Heap's algorithm).
     */
    private Map<String, Player> createPlayers() {
        Map<String, Player> players = new LinkedHashMap<>();
        for (int i = 1; i <= PLAYER_COUNT; i++) {
            Player p = new Player("Player" + i, "P" + i);
            for (int day = 1; day <= MATCH_DAYS; day++) {
                // Rotate through the pool so each player has a distinct score pattern.
                p.addResult(day, SCORE_POOL[(i + day) % SCORE_POOL.length]);
            }
            players.put(p.getName(), p);
        }
        return players;
    }

    @Test
    void singleAndMultiThreadProduceSameStatistics() {
        // Run single-thread (limit=0 → all 24 permutations)
        PartialResult single = new CalendarPermutator(createPlayers(), HOME_ADVANTAGE)
                .computePermutations(0, 1);

        // Run multi-thread (limit=0 → all 24 permutations across n partitions)
        PartialResult multi = new CalendarPermutator(createPlayers(), HOME_ADVANTAGE)
                .computePermutations(0, -1);

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
