package com.chesssmp.abilities;

import com.chesssmp.classes.PieceType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

final class BishopAbilities {

    private static final PotionEffectType[] NEGATIVE = {
            PotionEffectType.POISON, PotionEffectType.WITHER, PotionEffectType.WEAKNESS, PotionEffectType.SLOWNESS,
            PotionEffectType.MINING_FATIGUE, PotionEffectType.BLINDNESS, PotionEffectType.NAUSEA, PotionEffectType.HUNGER
    };

    private BishopAbilities() {
    }

    static void register(AbilityManager m) {
        // 1 - Diagonal Ward: resistance + a small absorption shield
        m.register(PieceType.BISHOP, 1, c -> {
            c.effect(c.player(), PotionEffectType.RESISTANCE, "resistance-level", 1);
            c.effect(c.player(), PotionEffectType.ABSORPTION, "absorption-level", 1);
            return true;
        });

        // 2 - Sanctuary: instantly heal nearby teammates (and optionally the caster)
        m.register(PieceType.BISHOP, 2, c -> {
            double heal = c.cfg().getDouble("heal", 6.0);
            List<Player> targets = c.allies(c.radius(8), c.cfg().getBoolean("include-self", true));
            int healed = 0;
            for (Player t : targets) {
                double max = AbilityManager.maxHealth(t);
                if (t.getHealth() >= max - 0.25) {
                    continue;
                }
                t.setHealth(Math.min(max, t.getHealth() + heal));
                c.targetFx(t, "heart");
                healed++;
            }
            return healed > 0 || c.fail("ability-nothing-to-heal");
        });

        // 3 - Holy Light: regeneration for teammates and a cleanse of harmful effects
        m.register(PieceType.BISHOP, 3, c -> {
            List<Player> targets = c.allies(c.radius(8), c.cfg().getBoolean("include-self", true));
            if (targets.isEmpty()) {
                return c.fail("ability-no-targets");
            }
            boolean cleanse = c.cfg().getBoolean("cleanse", true);
            for (Player t : targets) {
                c.effect(t, PotionEffectType.REGENERATION, "regeneration-level", 2);
                if (cleanse) {
                    for (PotionEffectType bad : NEGATIVE) {
                        t.removePotionEffect(bad);
                    }
                }
                c.targetFx(t, "end_rod");
            }
            return true;
        });
    }
}
