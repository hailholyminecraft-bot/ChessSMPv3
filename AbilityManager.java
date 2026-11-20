package com.chesssmp.abilities;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import com.chesssmp.Messages;
import com.chesssmp.classes.PieceType;
import com.chesssmp.storage.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Runs abilities: cooldowns (persisted as absolute timestamps so relogging or restarting never resets them),
 * the "one active ability at a time" stacking rule, damage modifiers, fall protection and feedback.
 */
public final class AbilityManager {

    private record Active(String id, long end, Set<PotionEffectType> types) { }

    private record DamageMod(double multiplier, long end) { }

    private final ChessSMP plugin;
    private final Map<String, Ability> abilities = new HashMap<>();
    private final Map<UUID, Active> active = new HashMap<>();
    private final Map<UUID, DamageMod> damageMods = new HashMap<>();
    private final Map<UUID, Long> noFall = new HashMap<>();

    public AbilityManager(ChessSMP plugin) {
        this.plugin = plugin;
        PawnAbilities.register(this);
        KnightAbilities.register(this);
        BishopAbilities.register(this);
        RookAbilities.register(this);
        QueenAbilities.register(this);
        KingAbilities.register(this);
    }

    void register(PieceType piece, int slot, AbilityAction action) {
        Ability a = new Ability(piece, slot, action);
        abilities.put(a.id(), a);
    }

    public static double maxHealth(Player p) {
        AttributeInstance attr = p.getAttribute(Attribute.MAX_HEALTH);
        return attr == null ? 20.0 : attr.getValue();
    }

    /** Remaining cooldown in millis (0 = ready). */
    public long remainingMillis(UUID id, PieceType piece, int slot) {
        PlayerData d = plugin.data().find(id);
        if (d == null) {
            return 0;
        }
        return Math.max(0, d.cooldownEnd(piece.key() + "_" + slot) - System.currentTimeMillis());
    }

    /** Tries to use ability {@code slot} of the player's current piece. */
    public boolean use(Player p, int slot) {
        Messages msg = plugin.messages();
        if (!p.hasPermission("chesssmp.player")) {
            msg.send(p, "no-permission");
            return false;
        }
        if (p.isDead() || p.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        PlayerData d = plugin.data().of(p);
        PieceType piece = d.piece();
        Ability ability = piece == null ? null : abilities.get(piece.key() + "_" + slot);
        ConfigurationSection cfg = piece == null ? null : plugin.classes().ability(piece, slot);
        if (ability == null || cfg == null) {
            msg.send(p, "ability-unknown");
            return false;
        }
        String name = cfg.getString("name", ability.id());
        long now = System.currentTimeMillis();

        long remain = d.cooldownEnd(ability.id()) - now;
        if (remain > 0) {
            msg.actionBar(p, "ability-cooldown", Messages.text("ability", name), Messages.text("time", Messages.duration(remain)));
            Fx.sound(plugin, p.getLocation(), "block.note_block.bass", 0.6f, 0.8f);
            return false;
        }

        double durationSeconds = cfg.getDouble("duration", 0);
        FileConfiguration config = plugin.getConfig();
        Active current = active.get(p.getUniqueId());
        if (current != null && current.end() <= now) {
            active.remove(p.getUniqueId());
            current = null;
        }
        if (current != null && durationSeconds > 0 && !config.getBoolean("abilities.allow-concurrent", false)) {
            ConfigurationSection other = activeSection(current.id());
            msg.actionBar(p, "ability-already-active",
                    Messages.text("ability", other == null ? current.id() : other.getString("name", current.id())));
            return false;
        }

        AbilityContext ctx = new AbilityContext(plugin, this, p, piece, slot, cfg);
        boolean success;
        try {
            success = ability.action().execute(ctx);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Ability " + ability.id() + " failed for " + p.getName(), ex);
            success = ctx.fail("ability-error");
        }
        if (!success) {
            msg.actionBar(p, ctx.failKey());
            return false;
        }

        // The cooldown can never be shorter than the duration, otherwise an ability could be kept up forever.
        double cooldownSeconds = Math.max(cfg.getDouble("cooldown", 30), durationSeconds);
        long end = now + (long) (cooldownSeconds * 1000);
        d.setCooldown(ability.id(), end);
        if (durationSeconds > 0) {
            active.put(p.getUniqueId(), new Active(ability.id(), now + (long) (durationSeconds * 1000), ctx.trackedEffects()));
        }
        d.addAbilityUse();
        plugin.data().markDirty();

        feedback(p, piece, cfg, name, cooldownSeconds);
        scheduleReadyNotice(p, piece, ability.id(), name, end);
        plugin.progression().checkObjectives(p.getUniqueId());
        return true;
    }

    private ConfigurationSection activeSection(String abilityId) {
        int idx = abilityId.lastIndexOf('_');
        if (idx < 0) {
            return null;
        }
        PieceType piece = PieceType.parse(abilityId.substring(0, idx));
        if (piece == null) {
            return null;
        }
        try {
            return plugin.classes().ability(piece, Integer.parseInt(abilityId.substring(idx + 1)));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void feedback(Player p, PieceType piece, ConfigurationSection cfg, String name, double cooldownSeconds) {
        FileConfiguration config = plugin.getConfig();
        Fx.particle(plugin, p.getLocation().add(0, 1, 0),
                cfg.getString("particle", config.getString("particles.default-ability", "end_rod")),
                cfg.getInt("particle-count", 30), 0.6);
        Fx.sound(plugin, p.getLocation(), cfg.getString("sound", "entity.player.levelup"),
                (float) cfg.getDouble("sound-volume", 1.0), (float) cfg.getDouble("sound-pitch", 1.0));

        plugin.messages().actionBar(p, "ability-used", Messages.text("ability", name),
                Messages.text("cooldown", Messages.duration((long) (cooldownSeconds * 1000))));

        double radius = config.getDouble("abilities.announce-radius", 24);
        if (radius > 0) {
            for (Player other : p.getWorld().getNearbyPlayers(p.getLocation(), radius)) {
                if (!other.equals(p)) {
                    plugin.messages().actionBar(other, "ability-announce",
                            Messages.text("emoji", plugin.classes().emoji(piece)),
                            Messages.text("player", p.getName()),
                            Messages.text("ability", name));
                }
            }
        }
    }

    private void scheduleReadyNotice(Player p, PieceType piece, String id, String name, long end) {
        if (!plugin.getConfig().getBoolean("abilities.ready-notification", true)) {
            return;
        }
        UUID uuid = p.getUniqueId();
        long ticks = Math.max(1, (end - System.currentTimeMillis()) / 50);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player online = Bukkit.getPlayer(uuid);
            PlayerData d = plugin.data().find(uuid);
            // Skip when the piece was switched or the cooldown was extended in the meantime.
            if (online == null || d == null || d.piece() != piece || d.cooldownEnd(id) != end) {
                return;
            }
            plugin.messages().actionBar(online, "ability-ready", Messages.text("ability", name));
            Fx.sound(plugin, online.getLocation(), "entity.experience_orb.pickup", 0.7f, 1.4f);
        }, ticks);
    }

    // ---------------------------------------------------------------- effects and modifiers

    /**
     * Applies a potion effect without ever downgrading an existing stronger effect.
     *
     * @return true when the effect was applied
     */
    public boolean applyEffect(Player target, PotionEffectType type, int level, int ticks) {
        int amplifier = Math.max(0, level - 1);
        PotionEffect existing = target.getPotionEffect(type);
        if (existing != null && (existing.getAmplifier() > amplifier
                || (existing.getAmplifier() == amplifier && existing.getDuration() > ticks))) {
            return false;
        }
        target.addPotionEffect(new PotionEffect(type, ticks, amplifier, false, true, true));
        return true;
    }

    /** Sets (replaces, never multiplies) the damage taken multiplier of a player for some milliseconds. */
    public void setDamageMod(UUID id, double multiplier, long millis) {
        damageMods.put(id, new DamageMod(multiplier, System.currentTimeMillis() + millis));
    }

    public double damageMultiplier(UUID id) {
        DamageMod mod = damageMods.get(id);
        if (mod == null) {
            return 1.0;
        }
        if (mod.end() < System.currentTimeMillis()) {
            damageMods.remove(id);
            return 1.0;
        }
        return mod.multiplier();
    }

    public void grantNoFall(UUID id, long millis) {
        noFall.put(id, System.currentTimeMillis() + millis);
    }

    public boolean hasNoFall(UUID id) {
        Long end = noFall.get(id);
        if (end == null) {
            return false;
        }
        if (end < System.currentTimeMillis()) {
            noFall.remove(id);
            return false;
        }
        return true;
    }

    /** Removes every active ability state and the potion effects this plugin applied (death, switch, quit). */
    public void clear(Player p) {
        UUID id = p.getUniqueId();
        Active a = active.remove(id);
        if (a != null) {
            for (PotionEffectType type : a.types()) {
                p.removePotionEffect(type);
            }
        }
        damageMods.remove(id);
        noFall.remove(id);
    }
}
