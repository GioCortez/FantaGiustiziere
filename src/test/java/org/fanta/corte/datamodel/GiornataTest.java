package org.fanta.corte.datamodel;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GiornataTest {

    private Campionato newCampionato() {
        return new Campionato(BigDecimal.ZERO, 66, 6);
    }

    @Test
    void constructor_storesCampionato() {
        Campionato c = newCampionato();
        assertSame(c, new Giornata(c).getCampionato());
    }

    @Test
    void id_setterGetter() {
        Giornata g = new Giornata(newCampionato());
        g.setId(7);
        assertEquals(7, g.getId());
    }

    @Test
    void neutral_defaultFalse_setterGetter() {
        Giornata g = new Giornata(newCampionato());
        assertFalse(g.isNeutral());
        g.setNeutral(true);
        assertTrue(g.isNeutral());
    }

    @Test
    void getPartite_lazyInitReturnsEmptyList() {
        Giornata g = new Giornata(newCampionato());
        assertNotNull(g.getPartite());
        assertTrue(g.getPartite().isEmpty());
    }

    @Test
    void setPartite_replacesPartiteList() {
        Giornata g = new Giornata(newCampionato());
        List<Partita> list = new ArrayList<>();
        g.setPartite(list);
        assertSame(list, g.getPartite());
    }
}
