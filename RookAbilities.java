package com.chesssmp.abilities;

import com.chesssmp.classes.PieceType;
import org.bukkit.potion.PotionEffectType;

final class RookAbilities {

    private RookAbilities() {
    }

    static void register(AbilityManager m) {
        // 1 - Castle: strong resistance, slowed while it lasts
        m.register(PieceType.ROOK, 1, c -> {
            c.effect(c.player(), PotionEffectType.RESISTANCE, "resistance-level", 2);
            c.effect(c.player(), PotionEffectType.SLOWNESS, "slowness-level", 1);
            return true;
        });

        // 2 - Iron Resolve: absorption hearts
        m.register(PieceType.ROOK, 2, c -> {
            c.effect(c.player(), PotionEffectType.ABSORPTION, "absorption-level", 3);
            return true;
        });

        // 3 - Fortress: reduced incoming damage
        m.register(PieceType.ROOK, 3, c -> {
            double reduction = Math.max(0, Math.min(0.9, c.cfg().getDouble("damage-reduction", 0.4)));
            c.manager().setDamageMod(c.player().getUniqueId(), 1.0 - reduction, (long) (c.durationSeconds() * 1000));
            return true;
        });
    }
}
