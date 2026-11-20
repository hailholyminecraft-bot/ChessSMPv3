package com.chesssmp.trader;

import org.bukkit.Material;

public record Cost(Material material, int amount) {

    /** @return the cost or null when the material is unknown. */
    public static Cost parse(String text) {
        Specs.Spec spec = Specs.parse(text);
        if (spec == null) {
            return null;
        }
        Material m = Material.matchMaterial(spec.name());
        if (m == null || m.isAir() || !m.isItem()) {
            return null;
        }
        return new Cost(m, spec.amount());
    }
}
