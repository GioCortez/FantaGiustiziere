package org.fanta.corte.datamodel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {

    @Test
    void constructor_setsNameIdAndZeroPoints() {
        Player p = new Player("Alice", "A1");
        assertEquals("Alice", p.getName());
        assertEquals("A1", p.getId());
        assertEquals(0, BigDecimal.ZERO.compareTo(p.getTotalPoints()));
    }

    @Test
    void addResult_accumulatesTotalPoints() {
        Player p = new Player("Alice", "A1");
        p.addResult(1, BigDecimal.valueOf(70));
        p.addResult(2, BigDecimal.valueOf(80));
        assertEquals(0, BigDecimal.valueOf(150).compareTo(p.getTotalPoints()));
    }

    @Test
    void addResult_storesScorePerMatchday() {
        Player p = new Player("Alice", "A1");
        p.addResult(3, BigDecimal.valueOf(65));
        assertEquals(0, BigDecimal.valueOf(65).compareTo(p.getResults().get(3)));
    }

    @Test
    void getResults_lazyInitReturnsEmptyMap() {
        Player p = new Player("Alice", "A1");
        assertNotNull(p.getResults());
        assertTrue(p.getResults().isEmpty());
    }

    @Test
    void toString_returnsName() {
        assertEquals("Alice", new Player("Alice", "A1").toString());
    }
}
