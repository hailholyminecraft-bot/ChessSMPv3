package com.chesssmp.abilities;

import com.chesssmp.classes.PieceType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

final class KingAbilities {

    private KingAbilities() {
    }

    static void register(AbilityManager m) {
        // 1 - Royal Guard: resistance, absorption and a flat damage reduction
        m.register(PieceType.KING, 1, c -> {
            c.effect(c.player(), PotionEffectType.RESISTANCE, "resistance-level", 1);
            c.effect(c.player(), PotionEffectType.ABSORPTION, "absorption-level", 2);
            double reduction = Math.max(0, Math.min(0.9, c.cfg().getDouble("damage-reduction", 0.2)));
            c.manager().setDamageMod(c.player().getUniqueId(), 1.0 - reduction, (long) (c.durationSeconds() * 1000));
            return true;
        });

        // 2 - King's Rally: buff nearby teammates (needs at least one teammate by default)
        m.register(PieceType.KING, 2, c -> {
            List<Player> targets = c.allies(c.radius(10), c.cfg().getBoolean("include-self", true));
            boolean others = targets.stream().anyMatch(t -> !t.equals(c.player()));
            if (!others && c.cfg().getBoolean("require-ally", true)) {
                return c.fail("ability-no-targets");
            }
            for (Player t : targets) {
                c.effect(t, PotionEffectType.STRENGTH, "strength-level", 1);
                c.effect(t, PotionEffectType.SPEED, "speed-level", 1);
                c.effect(t, PotionEffectType.RESISTANCE, "resistance-level", 1);
                c.targetFx(t, "happy_villager");
            }
            return true;
        });

        // 3 - Last Stand: only usable when low on health
        m.register(PieceType.KING, 3, c -> {
            Player p = c.player();
            double threshold = c.cfg().getDouble("health-threshold", 0.4);
            if (p.getHealth() / AbilityManager.maxHealth(p) > threshold) {
                return c.fail("ability-not-low-enough");
            }
            c.effect(p, PotionEffectType.STRENGTH, "strength-level", 2);
            c.effect(p, PotionEffectType.RESISTANCE, "resistance-level", 1);
            c.effect(p, PotionEffectType.REGENERATION, "regeneration-level", 1);
            return true;
        });
    }
}
