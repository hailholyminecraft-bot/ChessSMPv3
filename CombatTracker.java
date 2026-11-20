package com.chesssmp.statistics;

import com.chesssmp.ChessSMP;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pairwise combat tags. Two players who hit each other are "hostile" to each other for a while.
 * Outside events this defines who counts as an enemy for debuff abilities and who is safe for support abilities.
 */
public final class CombatTracker {

    private final ChessSMP plugin;
    private final Map<UUID, Map<UUID, Long>> hostile = new HashMap<>();

    public CombatTracker(ChessSMP plugin) {
        this.plugin = plugin;
    }

    public void tag(UUID a, UUID b) {
        long end = System.currentTimeMillis() + plugin.getConfig().getLong("combat.tag-seconds", 20) * 1000L;
        hostile.computeIfAbsent(a, k -> new HashMap<>()).put(b, end);
        hostile.computeIfAbsent(b, k -> new HashMap<>()).put(a, end);
    }

    public boolean isHostile(UUID a, UUID b) {
        Map<UUID, Long> map = hostile.get(a);
        if (map == null) {
            return false;
        }
        Long end = map.get(b);
        if (end == null) {
            return false;
        }
        if (end < System.currentTimeMillis()) {
            map.remove(b);
            if (map.isEmpty()) {
                hostile.remove(a);
            }
            return false;
        }
        return true;
    }

    public boolean isTagged(UUID id) {
        Map<UUID, Long> map = hostile.get(id);
        if (map == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        map.values().removeIf(end -> end < now);
        if (map.isEmpty()) {
            hostile.remove(id);
            return false;
        }
        return true;
    }

    public long remainingMillis(UUID id) {
        Map<UUID, Long> map = hostile.get(id);
        if (map == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        long max = 0;
        for (long end : map.values()) {
            max = Math.max(max, end - now);
        }
        return max;
    }

    /** Removes every tag involving the player (death, quit). */
    public void clear(UUID id) {
        Map<UUID, Long> map = hostile.remove(id);
        if (map == null) {
            return;
        }
        for (UUID other : map.keySet()) {
            Map<UUID, Long> otherMap = hostile.get(other);
            if (otherMap != null) {
                otherMap.remove(id);
                if (otherMap.isEmpty()) {
                    hostile.remove(other);
                }
            }
        }
    }
}
