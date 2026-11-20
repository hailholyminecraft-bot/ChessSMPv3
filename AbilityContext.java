package com.chesssmp.abilities;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import com.chesssmp.classes.PieceType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Everything an ability implementation needs: caster, its config section and helper methods. */
public final class AbilityContext {

    private final ChessSMP plugin;
    private final AbilityManager manager;
    private final Player player;
    private final PieceType piece;
    private final int slot;
    private final ConfigurationSection cfg;
    private final Set<PotionEffectType> tracked = new HashSet<>();
    private String failKey = "ability-failed";

    AbilityContext(ChessSMP plugin, AbilityManager manager, Player player, PieceType piece, int slot, ConfigurationSection cfg) {
        this.plugin = plugin;
        this.manager = manager;
        this.player = player;
        this.piece = piece;
        this.slot = slot;
        this.cfg = cfg;
    }

    public ChessSMP plugin() { return plugin; }
    public AbilityManager manager() { return manager; }
    public Player player() { return player; }
    public PieceType piece() { return piece; }
    public int slot() { return slot; }
    public ConfigurationSection cfg() { return cfg; }
    String failKey() { return failKey; }
    Set<PotionEffectType> trackedEffects() { return tracked; }

    /** Marks the use as failed with the given message key and returns false for convenient chaining. */
    public boolean fail(String messageKey) {
        this.failKey = messageKey;
        return false;
    }

    /** Duration from the config in seconds. */
    public double durationSeconds() {
        return cfg.getDouble("duration", 0);
    }

    public int durationTicks() {
        return (int) Math.max(1, Math.round(durationSeconds() * 20));
    }

    public double radius(double def) {
        return cfg.getDouble("radius", def);
    }

    /**
     * Applies an effect for the configured duration. The level comes from {@code levelKey} in the ability config
     * (1 = level I, 0 = disabled). Effects are never downgraded and never stacked beyond the configured level.
     */
    public void effect(Player target, PotionEffectType type, String levelKey, int defaultLevel) {
        int level = cfg.getInt(levelKey, defaultLevel);
        if (level <= 0) {
            return;
        }
        boolean applied = manager.applyEffect(target, type, level, durationTicks());
        if (applied && target.equals(player)) {
            tracked.add(type);
        }
    }

    /** Nearby teammates (see Relations); the caster is included when requested. */
    public List<Player> allies(double radius, boolean includeSelf) {
        List<Player> out = new ArrayList<>();
        if (includeSelf) {
            out.add(player);
        }
        for (Player other : player.getWorld().getNearbyPlayers(player.getLocation(), radius)) {
            if (!other.equals(player) && plugin.relations().isTeammate(player, other)) {
                out.add(other);
            }
        }
        return out;
    }

    /** Nearby enemies (see Relations). */
    public List<Player> enemies(double radius) {
        List<Player> out = new ArrayList<>();
        for (Player other : player.getWorld().getNearbyPlayers(player.getLocation(), radius)) {
            if (!other.equals(player) && plugin.relations().isEnemy(player, other)) {
                out.add(other);
            }
        }
        return out;
    }

    /** Small particle burst on a target (heal hearts, debuff smoke ...). */
    public void targetFx(Player target, String defaultParticle) {
        Fx.particle(plugin, target.getLocation().add(0, 1, 0), cfg.getString("target-particle", defaultParticle), 8, 0.4);
    }
}
