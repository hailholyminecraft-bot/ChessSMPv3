package com.chesssmp.statistics;

import java.util.Locale;
import java.util.Map;

/** Cosmetic progression ranks. They never change combat values. */
public enum Rank {
    PAWN(0L),
    KNIGHT(100L),
    BISHOP(300L),
    ROOK(700L),
    QUEEN(1500L),
    KING(3000L);

    private final long defaultPoints;

    Rank(long defaultPoints) {
        this.defaultPoints = defaultPoints;
    }

    public long defaultPoints() {
        return defaultPoints;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String display() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }

    /** Highest rank whose threshold is reached. Missing thresholds fall back to the defaults. */
    public static Rank resolve(Map<Rank, Long> thresholds, long points) {
        Rank best = PAWN;
        long bestThreshold = Long.MIN_VALUE;
        for (Rank rank : values()) {
            long threshold = thresholds.getOrDefault(rank, rank.defaultPoints);
            if (points >= threshold && threshold >= bestThreshold) {
                best = rank;
                bestThreshold = threshold;
            }
        }
        return best;
    }
}
