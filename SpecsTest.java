package com.chesssmp;

import com.chesssmp.trader.Specs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SpecsTest {

    @Test
    void parsesNameAndAmount() {
        Specs.Spec s = Specs.parse("DIAMOND:6");
        assertNotNull(s);
        assertEquals("DIAMOND", s.name());
        assertEquals(6, s.amount());
    }

    @Test
    void amountDefaultsToOne() {
        assertEquals(1, Specs.parse("EMERALD").amount());
    }

    @Test
    void rejectsInvalidInput() {
        assertNull(Specs.parse(""));
        assertNull(Specs.parse(null));
        assertNull(Specs.parse("DIAMOND:abc"));
        assertNull(Specs.parse("DIAMOND:0"));
        assertNull(Specs.parse("DIAMOND:-3"));
        assertNull(Specs.parse("A:1:2"));
    }
}
