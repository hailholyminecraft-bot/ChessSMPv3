package com.chesssmp;

import com.chesssmp.statistics.Rank;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RankTest {

    @Test
    void defaultThresholds() {
        Map<Rank, Long> none = new EnumMap<>(Rank.class);
        assertEquals(Rank.PAWN, Rank.resolve(none, 0));
        assertEquals(Rank.PAWN, Rank.resolve(none, 99));
        assertEquals(Rank.KNIGHT, Rank.resolve(none, 100));
        assertEquals(Rank.ROOK, Rank.resolve(none, 1499));
        assertEquals(Rank.QUEEN, Rank.resolve(none, 1500));
        assertEquals(Rank.KING, Rank.resolve(none, 100000));
    }

    @Test
    void customThresholds() {
        Map<Rank, Long> custom = new EnumMap<>(Rank.class);
        custom.put(Rank.KNIGHT, 10L);
        custom.put(Rank.BISHOP, 20L);
        assertEquals(Rank.KNIGHT, Rank.resolve(custom, 15));
        assertEquals(Rank.BISHOP, Rank.resolve(custom, 20));
    }
}
