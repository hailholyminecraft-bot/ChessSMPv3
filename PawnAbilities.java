package com.chesssmp.abilities;

import com.chesssmp.classes.PieceType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

final class PawnAbilities {

    private PawnAbilities() {
    }

    static void register(AbilityManager m) {
        // 1 - Forward March: Speed
        m.register(PieceType.PAWN, 1, c -> {
            c.effect(c.player(), PotionEffectType.SPEED, "speed-level", 2);
            return true;
        });

        // 2 - Second Wind: instantly restore a fraction of the missing health
        m.register(PieceType.PAWN, 2, c -> {
            Player p = c.player();
            double max = AbilityManager.maxHealth(p);
            double missing = max - p.getHealth();
            if (missing < 0.5) {
                return c.fail("ability-full-health");
            }
            double fraction = Math.max(0, Math.min(1, c.cfg().getDouble("heal-missing-fraction", 0.4)));
            p.setHealth(Math.min(max, p.getHealth() + missing * fraction));
            c.targetFx(p, "heart");
            return true;
        });

        // 3 - Promotion: temporarily improved combat statistics
        m.register(PieceType.PAWN, 3, c -> {
            c.effect(c.player(), PotionEffectType.STRENGTH, "strength-level", 1);
            c.effect(c.player(), PotionEffectType.RESISTANCE, "resistance-level", 1);
            return true;
        });
    }
}
