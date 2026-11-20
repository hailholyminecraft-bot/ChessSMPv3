package com.chesssmp.abilities;

import com.chesssmp.classes.PieceType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

final class QueenAbilities {

    private QueenAbilities() {
    }

    static void register(AbilityManager m) {
        // 1 - Royal Tempo: strength and speed
        m.register(PieceType.QUEEN, 1, c -> {
            c.effect(c.player(), PotionEffectType.STRENGTH, "strength-level", 1);
            c.effect(c.player(), PotionEffectType.SPEED, "speed-level", 2);
            return true;
        });

        // 2 - Royal Decree: weaken and slow nearby enemies (needs at least one valid target)
        m.register(PieceType.QUEEN, 2, c -> {
            List<Player> enemies = c.enemies(c.radius(8));
            if (enemies.isEmpty()) {
                return c.fail("ability-no-enemies");
            }
            for (Player enemy : enemies) {
                c.effect(enemy, PotionEffectType.WEAKNESS, "weakness-level", 1);
                c.effect(enemy, PotionEffectType.SLOWNESS, "slowness-level", 1);
                c.targetFx(enemy, "smoke");
            }
            return true;
        });

        // 3 - Queen's Gambit: more offense, less protection
        m.register(PieceType.QUEEN, 3, c -> {
            c.effect(c.player(), PotionEffectType.STRENGTH, "strength-level", 2);
            double penalty = Math.max(0, c.cfg().getDouble("damage-taken-increase", 0.3));
            c.manager().setDamageMod(c.player().getUniqueId(), 1.0 + penalty, (long) (c.durationSeconds() * 1000));
            return true;
        });
    }
}
