package org.fanta.corte.controllers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.fanta.corte.datamodel.Player;
import org.fanta.corte.services.CalendarPermutator.PartialResult;

public class RunResult {

    public int permutationCount;
    public List<PlayerStats> statistics = new ArrayList<>();

    public static RunResult from(PartialResult result) {
        RunResult r = new RunResult();
        r.permutationCount = result.permutationCounter;

        for (Map.Entry<Player, long[]> entry : result.statistics.entrySet()) {
            PlayerStats ps = new PlayerStats();
            ps.player = entry.getKey().getName();
            ps.positions = entry.getValue();
            ps.percentages = new int[ps.positions.length];
            for (int i = 0; i < ps.positions.length; i++) {
                ps.percentages[i] = (int) Math.round(ps.positions[i] * 100.0 / result.permutationCounter);
            }
            r.statistics.add(ps);
        }

        // Sort by 1st-place count descending so the table is immediately meaningful
        r.statistics.sort(Comparator.comparingLong((PlayerStats s) -> s.positions[0]).reversed());

        return r;
    }

    public static class PlayerStats {
        public String player;
        public long[] positions;
        public int[] percentages;
    }
}
