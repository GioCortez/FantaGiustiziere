package org.fanta.corte.services;

import java.math.BigDecimal;
import java.util.*;
import org.fanta.corte.datamodel.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BergerAlgorithmTest {

    private static final BigDecimal HOME_ADV    = BigDecimal.valueOf(2);
    private static final int        GOAL_LIMIT  = 66;
    private static final int        GOAL_OFFSET = 6;

    private static final int PLAYER_COUNT    = 4;
    private static final int LEG_SIZE        = PLAYER_COUNT - 1; // 3 matchdays per leg
    private static final int TOTAL_LEGS      = 2;
    private static final int LEGS_WITH_ADV   = 2;
    private static final int TOTAL_MATCHDAYS = LEG_SIZE * TOTAL_LEGS; // 6

    private Map<String, Player> createPlayers(int totalMatchdays) {
        Map<String, Player> players = new LinkedHashMap<>();
        for (int i = 1; i <= PLAYER_COUNT; i++) {
            Player p = new Player("P" + i, "P" + i);
            for (int day = 1; day <= totalMatchdays; day++) {
                p.addResult(day, BigDecimal.valueOf(70));
            }
            players.put(p.getName(), p);
        }
        return players;
    }

    private Campionato buildCalendar(String[] order, int totalLegs, int legsWithAdv) {
        int totalMatchdays = LEG_SIZE * totalLegs;
        return new BergerAlgorithm().runAlgoritmoDiBerger2(
                order, createPlayers(totalMatchdays), HOME_ADV,
                totalLegs, legsWithAdv, GOAL_LIMIT, GOAL_OFFSET);
    }

    private Campionato buildDefaultCalendar() {
        return buildCalendar(new String[]{"P1", "P2", "P3", "P4"}, TOTAL_LEGS, LEGS_WITH_ADV);
    }

    // ── Structure ─────────────────────────────────────────────────────────────

    @Test
    void generatesCorrectNumberOfMatchdays() {
        assertEquals(TOTAL_MATCHDAYS, buildDefaultCalendar().getGiornate().size());
    }

    @Test
    void eachPlayerAppearsExactlyOncePerMatchday() {
        for (Giornata g : buildDefaultCalendar().getGiornate()) {
            Set<String> seen = new HashSet<>();
            for (Partita p : g.getPartite()) {
                assertTrue(seen.add(p.getCasa().getName()),
                        "Duplicate home player in giornata " + g.getId());
                assertTrue(seen.add(p.getTrasferta().getName()),
                        "Duplicate away player in giornata " + g.getId());
            }
            assertEquals(PLAYER_COUNT, seen.size(),
                    "Giornata " + g.getId() + " must involve all " + PLAYER_COUNT + " players");
        }
    }

    @Test
    void eachPairMeetsExactlyTwiceInDoubleRoundRobin() {
        // Count how many times each unordered pair meets.
        Map<String, Integer> pairCount = new HashMap<>();
        for (Giornata g : buildDefaultCalendar().getGiornate()) {
            for (Partita p : g.getPartite()) {
                String a = p.getCasa().getName();
                String b = p.getTrasferta().getName();
                // Normalise so A < B alphabetically to get an unordered key.
                String key = a.compareTo(b) < 0 ? a + "-" + b : b + "-" + a;
                pairCount.merge(key, 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> e : pairCount.entrySet()) {
            assertEquals(2, e.getValue(),
                    "Pair " + e.getKey() + " should meet exactly twice");
        }
    }

    @Test
    void returnLegSwapsHomeAndAway() {
        List<Giornata> giornate = buildDefaultCalendar().getGiornate();
        for (int i = 0; i < LEG_SIZE; i++) {
            List<Partita> firstLeg  = giornate.get(i).getPartite();
            List<Partita> returnLeg = giornate.get(i + LEG_SIZE).getPartite();
            for (int j = 0; j < firstLeg.size(); j++) {
                assertEquals(firstLeg.get(j).getCasa().getName(),
                             returnLeg.get(j).getTrasferta().getName(),
                             "Return leg home should be first leg away");
                assertEquals(firstLeg.get(j).getTrasferta().getName(),
                             returnLeg.get(j).getCasa().getName(),
                             "Return leg away should be first leg home");
            }
        }
    }

    // ── Neutral legs ─────────────────────────────────────────────────────────

    @Test
    void evenLegs_allMatchdaysAreNonNeutral() {
        // 2 legs, both with advantage → no neutral matchdays
        for (Giornata g : buildDefaultCalendar().getGiornate()) {
            assertFalse(g.isNeutral(), "Giornata " + g.getId() + " should not be neutral");
        }
    }

    @Test
    void oddLegs_lastLegIsNeutral() {
        // 3 legs: legs 0 and 1 have HA, leg 2 is neutral
        int totalLegs   = 3;
        int legsWithAdv = 2; // totalLegs - 1 because odd
        Campionato c = buildCalendar(new String[]{"P1", "P2", "P3", "P4"}, totalLegs, legsWithAdv);

        List<Giornata> giornate = c.getGiornate();
        for (int i = 0; i < LEG_SIZE * 2; i++) {
            assertFalse(giornate.get(i).isNeutral(),
                    "Giornata " + (i + 1) + " (legs 0-1) should not be neutral");
        }
        for (int i = LEG_SIZE * 2; i < LEG_SIZE * totalLegs; i++) {
            assertTrue(giornate.get(i).isNeutral(),
                    "Giornata " + (i + 1) + " (leg 2) should be neutral");
        }
    }
}
