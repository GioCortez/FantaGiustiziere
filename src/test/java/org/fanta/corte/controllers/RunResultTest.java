package org.fanta.corte.controllers;

import org.fanta.corte.datamodel.Player;
import org.fanta.corte.services.CalendarPermutator.PartialResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RunResultTest {

    private PartialResult buildResult() {
        PartialResult pr = new PartialResult();
        pr.permutationCounter = 10;

        Player alice = new Player("Alice", "A");
        Player bob   = new Player("Bob",   "B");

        // Alice finishes 1st 8 times, 2nd 2 times; Bob the opposite.
        pr.statistics.put(alice, new long[]{8, 2});
        pr.statistics.put(bob,   new long[]{2, 8});

        return pr;
    }

    @Test
    void from_setsPermutationCount() {
        assertEquals(10, RunResult.from(buildResult()).permutationCount);
    }

    @Test
    void from_createsEntryForEachPlayer() {
        assertEquals(2, RunResult.from(buildResult()).statistics.size());
    }

    @Test
    void from_computesRoundedPercentages() {
        RunResult r = RunResult.from(buildResult());
        RunResult.PlayerStats alice = r.statistics.stream()
                .filter(s -> s.player.equals("Alice"))
                .findFirst().orElseThrow();
        // 8/10 = 80%, 2/10 = 20%
        assertArrayEquals(new int[]{80, 20}, alice.percentages);
    }

    @Test
    void from_sortsByFirstPlaceCountDescending() {
        RunResult r = RunResult.from(buildResult());
        assertEquals("Alice", r.statistics.get(0).player, "Alice (8 first-places) must rank above Bob");
        assertEquals("Bob",   r.statistics.get(1).player);
    }
}
