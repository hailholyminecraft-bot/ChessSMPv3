package com.chesssmp.grandmaster;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import com.chesssmp.Messages;
import com.chesssmp.classes.PieceType;
import com.chesssmp.items.CustomItems;
import com.chesssmp.storage.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Chess Grandmaster title: crafted from 8 remnants around a nether star, collects the pieces of players it kills,
 * and with all six pieces grants permanent Speed III + Resistance I and a freezing hit.
 * Dying drops the title and resets everything.
 */
public final class GrandmasterManager {

    private static final String FREEZE_COOLDOWN = "gm_freeze";

    private final ChessSMP plugin;
    private final NamespacedKey recipeKey;
    private final Map<UUID, Long> frozen = new HashMap<>();
    private final Set<UUID> buffed = new HashSet<>();
    private BukkitTask frozenTask;

    public GrandmasterManager(ChessSMP plugin) {
        this.plugin = plugin;
        this.recipeKey = new NamespacedKey(plugin, "chess_grandmaster_title");
    }

    public NamespacedKey recipeKey() {
        return recipeKey;
    }

    // ------------------------------------------------------------------ recipe

    public void registerRecipe() {
        Bukkit.removeRecipe(recipeKey);
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, plugin.items().create(CustomItems.Kind.TITLE, 1));
        recipe.shape("RRR", "RNR", "RRR");
        recipe.setIngredient('R', new RecipeChoice.ExactChoice(plugin.items().create(CustomItems.Kind.REMNANT, 1)));
        recipe.setIngredient('N', Material.NETHER_STAR);
        Bukkit.addRecipe(recipe);
    }

    public void unregisterRecipe() {
        Bukkit.removeRecipe(recipeKey);
    }

    // ------------------------------------------------------------------ pieces

    /** Collected pieces plus the player's own current piece. */
    public Set<PieceType> pieces(PlayerData d) {
        Set<PieceType> all = EnumSet.noneOf(PieceType.class);
        all.addAll(d.gmPieces());
        if (d.piece() != null) {
            all.add(d.piece());
        }
        return all;
    }

    public boolean hasAll(PlayerData d) {
        return d.grandmaster() && pieces(d).size() == PieceType.values().length;
    }

    /** Emojis of the collected pieces (shown behind the name tag). */
    public String pieceString(PlayerData d) {
        StringBuilder sb = new StringBuilder();
        for (PieceType t : PieceType.values()) {
            if (d.gmPieces().contains(t)) {
                sb.append(plugin.classes().emoji(t));
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ title lifecycle

    /** Equips the title (the caller removes the item). */
    public boolean equip(Player p) {
        PlayerData d = plugin.data().of(p);
        if (d.grandmaster()) {
            plugin.messages().send(p, "gm-already");
            return false;
        }
        d.setGrandmaster(true);
        d.gmPieces().clear();
        plugin.data().markDirty();
        plugin.nameTags().refresh(p);
        sync(p);
        Bukkit.broadcast(plugin.messages().component("gm-equipped", Messages.text("player", p.getName())));
        Fx.sound(plugin, p.getLocation(), "ui.toast.challenge_complete", 1f, 1f);
        Fx.particle(plugin, p.getLocation().add(0, 1, 0), "totem_of_undying", 50, 0.7);
        return true;
    }

    /** A grandmaster killed somebody: they gain the victim's piece (the victim keeps theirs). */
    public void onKill(Player killer, Player victim) {
        PlayerData kd = plugin.data().of(killer);
        if (!kd.grandmaster()) {
            return;
        }
        PieceType stolen = plugin.data().of(victim).piece();
        if (stolen == null || !kd.gmPieces().add(stolen)) {
            return;
        }
        plugin.data().markDirty();
        plugin.messages().send(killer, "gm-piece-gained", Messages.comp("piece", plugin.classes().label(stolen)),
                Messages.num("count", pieces(kd).size()));
        plugin.nameTags().refresh(killer);
        sync(killer);
        if (hasAll(kd)) {
            Bukkit.broadcast(plugin.messages().component("gm-complete", Messages.text("player", killer.getName())));
        }
    }

    /** The grandmaster died: the title drops, collected pieces and the powers are gone. */
    public void onDeath(Player victim) {
        PlayerData d = plugin.data().of(victim);
        if (!d.grandmaster()) {
            return;
        }
        d.setGrandmaster(false);
        d.gmPieces().clear();
        plugin.data().markDirty();
        buffed.remove(victim.getUniqueId());
        frozen.remove(victim.getUniqueId());
        victim.getWorld().dropItemNaturally(victim.getLocation(), plugin.items().create(CustomItems.Kind.TITLE, 1));
        Bukkit.broadcast(plugin.messages().component("gm-lost", Messages.text("player", victim.getName())));
        // the name tag is refreshed after respawn (PlayerListener) - the player is dead right now
    }

    // ------------------------------------------------------------------ permanent powers

    /** Applies or removes Speed III + Resistance I depending on the grandmaster state. */
    public void sync(Player p) {
        PlayerData d = plugin.data().of(p);
        UUID id = p.getUniqueId();
        if (hasAll(d)) {
            infinite(p, PotionEffectType.SPEED, plugin.getConfig().getInt("grandmaster.speed-level", 3) - 1);
            infinite(p, PotionEffectType.RESISTANCE, plugin.getConfig().getInt("grandmaster.resistance-level", 1) - 1);
            buffed.add(id);
        } else if (buffed.remove(id)) {
            removeInfinite(p, PotionEffectType.SPEED);
            removeInfinite(p, PotionEffectType.RESISTANCE);
        }
    }

    private void infinite(Player p, PotionEffectType type, int amplifier) {
        if (amplifier < 0) {
            return;
        }
        PotionEffect existing = p.getPotionEffect(type);
        if (existing != null && existing.isInfinite() && existing.getAmplifier() == amplifier) {
            return;
        }
        p.addPotionEffect(new PotionEffect(type, PotionEffect.INFINITE_DURATION, amplifier, false, false, true));
    }

    private void removeInfinite(Player p, PotionEffectType type) {
        PotionEffect existing = p.getPotionEffect(type);
        if (existing != null && existing.isInfinite()) {
            p.removePotionEffect(type);
        }
    }

    public boolean isBuffed(UUID id) {
        return buffed.contains(id);
    }

    // ------------------------------------------------------------------ freeze hit

    public boolean isFrozen(UUID id) {
        Long until = frozen.get(id);
        if (until == null) {
            return false;
        }
        if (until < System.currentTimeMillis()) {
            frozen.remove(id);
            return false;
        }
        return true;
    }

    public void unfreeze(UUID id) {
        frozen.remove(id);
    }

    /** Tries to freeze {@code victim} with a hit from {@code attacker}. */
    public boolean tryFreeze(Player attacker, Player victim) {
        PlayerData d = plugin.data().of(attacker);
        if (!hasAll(d) || isFrozen(victim.getUniqueId())) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (d.cooldownEnd(FREEZE_COOLDOWN) > now) {
            return false;
        }
        double seconds = plugin.getConfig().getDouble("grandmaster.freeze-seconds", 15);
        double cooldown = Math.max(seconds, plugin.getConfig().getDouble("grandmaster.freeze-cooldown-seconds", 120));
        d.setCooldown(FREEZE_COOLDOWN, now + (long) (cooldown * 1000));
        plugin.data().markDirty();

        int ticks = (int) (seconds * 20);
        frozen.put(victim.getUniqueId(), now + (long) (seconds * 1000));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 9, false, false, true));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, ticks, 128, false, false, false));
        victim.setFreezeTicks(Math.max(victim.getFreezeTicks(), Math.min(ticks, victim.getMaxFreezeTicks() * 4)));
        Fx.sound(plugin, victim.getLocation(), "block.glass.break", 1f, 0.5f);
        plugin.messages().send(attacker, "gm-freeze-hit", Messages.text("victim", victim.getName()));
        plugin.messages().send(victim, "gm-frozen", Messages.text("attacker", attacker.getName()),
                Messages.num("seconds", Math.round(seconds)));
        startFrozenTask();
        return true;
    }

    private void startFrozenTask() {
        if (frozenTask != null) {
            return;
        }
        frozenTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Iterator<Map.Entry<UUID, Long>> it = frozen.entrySet().iterator();
            long now = System.currentTimeMillis();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> e = it.next();
                Player p = Bukkit.getPlayer(e.getKey());
                if (p == null || e.getValue() < now) {
                    it.remove();
                    continue;
                }
                Fx.particle(plugin, p.getLocation().add(0, 1, 0), "snowflake", 10, 0.4);
            }
            if (frozen.isEmpty()) {
                frozenTask.cancel();
                frozenTask = null;
            }
        }, 10L, 10L);
    }

    public long freezeCooldownRemaining(Player p) {
        return Math.max(0, plugin.data().of(p).cooldownEnd(FREEZE_COOLDOWN) - System.currentTimeMillis());
    }

    public void forget(UUID id) {
        frozen.remove(id);
        buffed.remove(id);
    }

    /** Spare helper so the title item can be handed out by commands. */
    public ItemStack titleItem() {
        return plugin.items().create(CustomItems.Kind.TITLE, 1);
    }
}
