package org.fanta.corte.datamodel;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartitaTest {

    private static final int    GOAL_LIMIT  = 66;
    private static final int    GOAL_OFFSET = 6;
    private static final BigDecimal HOME_ADV = BigDecimal.valueOf(2);

    // ── getGoals ──────────────────────────────────────────────────────────────

    @Test
    void getGoals_belowThreshold_returnsZero() {
        assertEquals(0, Partita.getGoals(BigDecimal.valueOf(65), GOAL_LIMIT, GOAL_OFFSET));
        assertEquals(0, Partita.getGoals(BigDecimal.valueOf(0),  GOAL_LIMIT, GOAL_OFFSET));
    }

    @Test
    void getGoals_atThreshold_returnsOne() {
        assertEquals(1, Partita.getGoals(BigDecimal.valueOf(66), GOAL_LIMIT, GOAL_OFFSET));
    }

    @Test
    void getGoals_withinFirstInterval_stillReturnsOne() {
        // 71 = 66 + 5 → floor(5/6)=0 → 1 goal
        assertEquals(1, Partita.getGoals(BigDecimal.valueOf(71), GOAL_LIMIT, GOAL_OFFSET));
    }

    @Test
    void getGoals_atSecondInterval_returnsTwo() {
        // 72 = 66 + 6 → floor(6/6)=1 → 2 goals
        assertEquals(2, Partita.getGoals(BigDecimal.valueOf(72), GOAL_LIMIT, GOAL_OFFSET));
    }

    @Test
    void getGoals_highScore_returnsCorrectCount() {
        // 90 = 66 + 24 → floor(24/6)=4 → 5 goals
        assertEquals(5, Partita.getGoals(BigDecimal.valueOf(90), GOAL_LIMIT, GOAL_OFFSET));
    }

    @Test
    void getGoals_customThresholds() {
        // limit=60, offset=10: 80 → floor((80-60)/10)+1 = 2+1 = 3
        assertEquals(3, Partita.getGoals(BigDecimal.valueOf(80), 60, 10));
    }

    // ── calculate ─────────────────────────────────────────────────────────────

    /** Builds a Campionato→Giornata→Partita and calls calculate(). */
    private Partita buildAndCalculate(BigDecimal homeStored, BigDecimal awayStored, boolean neutral) {
        Campionato c = new Campionato(HOME_ADV, GOAL_LIMIT, GOAL_OFFSET);
        Giornata g = new Giornata(c);
        g.setId(1);
        g.setNeutral(neutral);

        Player home = new Player("Home", "H");
        home.addResult(1, homeStored);

        Player away = new Player("Away", "A");
        away.addResult(1, awayStored);

        Partita p = new Partita(g, home, away);
        p.calculate(1);
        return p;
    }

    @Test
    void calculate_nonNeutral_addsHomeAdvantage() {
        // home stored=64, HA=2 added → effective=66 → 1 goal
        // away stored=60 → 0 goals → home wins
        Partita p = buildAndCalculate(BigDecimal.valueOf(64), BigDecimal.valueOf(60), false);
        assertEquals(1, p.getGoalCasa());
        assertEquals(0, p.getGoalTrasf());
    }

    @Test
    void calculate_neutral_doesNotAddHomeAdvantage() {
        // home stored=64, no HA → effective=64 < 66 → 0 goals
        // away stored=66 → 1 goal → away wins
        Partita p = buildAndCalculate(BigDecimal.valueOf(64), BigDecimal.valueOf(66), true);
        assertEquals(0, p.getGoalCasa());
        assertEquals(1, p.getGoalTrasf());
    }

    @Test
    void calculate_draw_equalGoals() {
        // both 72 → 2 goals each
        Partita p = buildAndCalculate(BigDecimal.valueOf(72), BigDecimal.valueOf(72), true);
        assertEquals(2, p.getGoalCasa());
        assertEquals(2, p.getGoalTrasf());
    }

    @Test
    void calculate_missingMatchday_throwsIllegalStateException() {
        Campionato c = new Campionato(HOME_ADV, GOAL_LIMIT, GOAL_OFFSET);
        Giornata g = new Giornata(c);
        g.setId(1);

        Player home = new Player("Home", "H");
        home.addResult(1, BigDecimal.valueOf(70));

        Player away = new Player("Away", "A"); // no results added

        Partita p = new Partita(g, home, away);
        assertThrows(IllegalStateException.class, () -> p.calculate(1));
    }
}
