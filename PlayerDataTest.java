package com.chesssmp;

import com.chesssmp.classes.PieceType;
import com.chesssmp.storage.PlayerData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerDataTest {

    @Test
    void roundTrip() {
        UUID id = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        PlayerData d = new PlayerData(id);
        d.setName("Alice");
        d.setPiece(PieceType.KNIGHT);
        d.setChosen(true);
        d.addPoints(123);
        d.addKill();
        d.addKill();
        d.incrementStreak();
        d.incrementStreak();
        d.setStreak(0);
        d.addDeath();
        d.addObjective("hunter");
        d.setCooldown("knight_1", 99999L);
        d.setGrandmaster(true);
        d.gmPieces().add(PieceType.QUEEN);
        d.trusted().add(friend);
        d.setPendingMace(true);

        ConfigurationSection s = new MemoryConfiguration().createSection("p");
        d.write(s);
        PlayerData r = PlayerData.read(id, s);

        assertEquals("Alice", r.name());
        assertEquals(PieceType.KNIGHT, r.piece());
        assertTrue(r.chosen());
        assertEquals(123, r.points());
        assertEquals(2, r.kills());
        assertEquals(1, r.deaths());
        assertEquals(2, r.bestStreak());
        assertTrue(r.hasObjective("hunter"));
        assertEquals(99999L, r.cooldownEnd("knight_1"));
        assertTrue(r.grandmaster());
        assertTrue(r.gmPieces().contains(PieceType.QUEEN));
        assertTrue(r.trusted().contains(friend));
        assertTrue(r.pendingMace());
    }

    @Test
    void cooldownsOnlyExtendAndPrune() {
        PlayerData d = new PlayerData(UUID.randomUUID());
        d.setCooldown("a", 1000);
        d.extendCooldown("a", 500);
        assertEquals(1000, d.cooldownEnd("a"));
        d.extendCooldown("a", 2000);
        assertEquals(2000, d.cooldownEnd("a"));
        d.pruneCooldowns(5000);
        assertEquals(0, d.cooldownEnd("a"));
    }

    @Test
    void pointsNeverGoNegative() {
        PlayerData d = new PlayerData(UUID.randomUUID());
        d.addPoints(5);
        d.addPoints(-50);
        assertEquals(0, d.points());
    }
}
