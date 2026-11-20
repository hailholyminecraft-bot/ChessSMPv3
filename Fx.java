package com.chesssmp;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.SoundCategory;
import org.bukkit.World;

import java.util.Locale;

/** Small helpers for config-driven particles, sounds and colors. */
public final class Fx {

    private Fx() {
    }

    public static NamedTextColor color(String name, NamedTextColor fallback) {
        if (name == null) {
            return fallback;
        }
        NamedTextColor c = NamedTextColor.NAMES.value(name.toLowerCase(Locale.ROOT));
        return c == null ? fallback : c;
    }

    public static void particle(ChessSMP plugin, Location loc, String name, int count, double spread) {
        if (!plugin.getConfig().getBoolean("particles.enabled", true)) {
            return;
        }
        World world = loc.getWorld();
        if (world == null || name == null || name.isBlank() || count <= 0) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        if (key == null) {
            return;
        }
        Particle particle = Registry.PARTICLE_TYPE.get(key);
        // Particles that need extra data (dust, blocks ...) are not supported by the simple config.
        if (particle == null || particle.getDataType() != Void.class) {
            return;
        }
        world.spawnParticle(particle, loc, count, spread, spread, spread, 0.02);
    }

    public static void sound(ChessSMP plugin, Location loc, String key, float volume, float pitch) {
        if (!plugin.getConfig().getBoolean("sounds.enabled", true)) {
            return;
        }
        World world = loc.getWorld();
        if (world == null || key == null || key.isBlank()) {
            return;
        }
        world.playSound(loc, key, SoundCategory.PLAYERS, volume, pitch);
    }
}
