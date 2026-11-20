package com.chesssmp.abilities;

import com.chesssmp.classes.PieceType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

final class KnightAbilities {

    private KnightAbilities() {
    }

    static void register(AbilityManager m) {
        // 1 - L-Jump: a powerful forward leap (with short fall protection)
        m.register(PieceType.KNIGHT, 1, c -> {
            Player p = c.player();
            if (p.isInsideVehicle()) {
                return c.fail("ability-in-vehicle");
            }
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 1.0E-4) {
                return c.fail("ability-no-direction");
            }
            dir.normalize().multiply(c.cfg().getDouble("forward-strength", 1.6));
            dir.setY(c.cfg().getDouble("vertical-strength", 0.7));
            p.setVelocity(dir);
            c.manager().grantNoFall(p.getUniqueId(), (long) (c.cfg().getDouble("fall-protection", 5.0) * 1000));
            return true;
        });

        // 2 - Knight's Charge: speed, strength and jump height
        m.register(PieceType.KNIGHT, 2, c -> {
            c.effect(c.player(), PotionEffectType.SPEED, "speed-level", 2);
            c.effect(c.player(), PotionEffectType.STRENGTH, "strength-level", 1);
            c.effect(c.player(), PotionEffectType.JUMP_BOOST, "jump-level", 2);
            return true;
        });

        // 3 - Evasion: brief burst of movement speed
        m.register(PieceType.KNIGHT, 3, c -> {
            c.effect(c.player(), PotionEffectType.SPEED, "speed-level", 3);
            return true;
        });
    }
}
