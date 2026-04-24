package org.fanta.corte.datamodel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CampionatoTest {

    // Zero home advantage + neutral legs keeps scores predictable in tests.
    private static final BigDecimal HOME_ADV    = BigDecimal.ZERO;
    private static final int        GOAL_LIMIT  = 66;
    private static final int        GOAL_OFFSET = 6;

    // Scores guaranteed to produce 2 goals (72) and 0 goals (60).
    private static final BigDecimal SCORE_2G = BigDecimal.valueOf(72);
    private static final BigDecimal SCORE_0G = BigDecimal.valueOf(60);
    // Score producing 1 goal — used for draws and tiebreak.
    private static final BigDecimal SCORE_1G_HIGH = BigDecimal.valueOf(71); // 1 goal, higher total
    private static final BigDecimal SCORE_1G_LOW  = BigDecimal.valueOf(66); // 1 goal, lower total

    private int matchdayCounter = 0;

    /** Adds a neutral fixture to the campionato and calls calculate(). */
    private void addFixture(Campionato c, Player home, Player away,
                            BigDecimal homeScore, BigDecimal awayScore) {
        int day = ++matchdayCounter;
        home.addResult(day, homeScore);
        away.addResult(day, awayScore);

        Giornata g = new Giornata(c);
        g.setId(day);
        g.setNeutral(true);

        Partita p = new Partita(g, home, away);
        p.calculate(day);
        g.getPartite().add(p);
        c.getGiornate().add(g);
    }

    private Campionato newCampionato() {
        matchdayCounter = 0;
        return new Campionato(HOME_ADV, GOAL_LIMIT, GOAL_OFFSET);
    }

    // ── Points logic ──────────────────────────────────────────────────────────

    @Test
    void win_givesThreePoints_loss_givesZero() {
        Campionato c = newCampionato();
        Player winner = new Player("Winner", "W");
        Player loser  = new Player("Loser",  "L");

        addFixture(c, winner, loser, SCORE_2G, SCORE_0G);

        List<Map.Entry<Player, Integer>> standings = new ArrayList<>(c.calculate().entrySet());
        assertEquals("Winner", standings.get(0).getKey().getName());
        assertEquals(3, standings.get(0).getValue());
        assertEquals("Loser",  standings.get(1).getKey().getName());
        assertEquals(0, standings.get(1).getValue());
    }

    @Test
    void draw_givesOnePointEach() {
        Campionato c = newCampionato();
        Player p1 = new Player("P1", "1");
        Player p2 = new Player("P2", "2");

        addFixture(c, p1, p2, SCORE_2G, SCORE_2G); // equal goals → draw

        for (int pts : c.calculate().values()) {
            assertEquals(1, pts);
        }
    }

    // ── Ranking order ─────────────────────────────────────────────────────────

    @Test
    void rankingOrderIsCorrect() {
        Campionato c = newCampionato();
        Player p1 = new Player("P1", "1");
        Player p2 = new Player("P2", "2");
        Player p3 = new Player("P3", "3");

        // P3 beats P1 and P2 (6 pts); P1 beats P2 (3 pts); P2 gets 0 pts
        addFixture(c, p3, p1, SCORE_2G, SCORE_0G);
        addFixture(c, p3, p2, SCORE_2G, SCORE_0G);
        addFixture(c, p1, p2, SCORE_2G, SCORE_0G);

        List<Map.Entry<Player, Integer>> standings = new ArrayList<>(c.calculate().entrySet());
        assertEquals("P3", standings.get(0).getKey().getName());
        assertEquals(6,    standings.get(0).getValue());
        assertEquals("P1", standings.get(1).getKey().getName());
        assertEquals(3,    standings.get(1).getValue());
        assertEquals("P2", standings.get(2).getKey().getName());
        assertEquals(0,    standings.get(2).getValue());
    }

    // ── Tiebreaker ───────────────────────────────────────────────────────────

    @Test
    void tiedPoints_resolvedByHigherTotalScore() {
        Campionato c = newCampionato();
        Player high = new Player("High", "H");
        Player low  = new Player("Low",  "L");

        // Both score 1 goal → draw → 1 point each.
        // high.totalPoints=71, low.totalPoints=66 → high ranked first.
        addFixture(c, high, low, SCORE_1G_HIGH, SCORE_1G_LOW);

        List<Map.Entry<Player, Integer>> standings = new ArrayList<>(c.calculate().entrySet());
        assertEquals(1, standings.get(0).getValue());
        assertEquals(1, standings.get(1).getValue());
        assertEquals("High", standings.get(0).getKey().getName(),
                "Higher total score should win the tiebreak");
    }
}
