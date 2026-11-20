package com.chesssmp.listeners;

import com.chesssmp.ChessSMP;
import com.chesssmp.Messages;
import com.chesssmp.classes.PieceType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Damage rules (trust, teams, damage modifiers), combat tags, deaths, kills, respawns and the grandmaster freeze. */
public final class CombatListener implements Listener {

    private final ChessSMP plugin;

    public CombatListener(ChessSMP plugin) {
        this.plugin = plugin;
    }

    private static Player attackerOf(Entity damager) {
        if (damager instanceof Player p) {
            return p;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackerOf(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (plugin.data().of(attacker).trusted().contains(victim.getUniqueId())) {
            event.setCancelled(true);
            plugin.messages().actionBar(attacker, "trust-protected", Messages.text("player", victim.getName()));
            return;
        }
        if (plugin.events().sameSide(attacker, victim)) {
            event.setCancelled(true);
            plugin.messages().actionBar(attacker, "friendly-fire");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHitMonitor(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attackerOf(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        plugin.combat().tag(attacker.getUniqueId(), victim.getUniqueId());
        plugin.grandmaster().tryFreeze(attacker, victim);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        UUID id = victim.getUniqueId();
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && plugin.abilities().hasNoFall(id)) {
            event.setCancelled(true);
            return;
        }
        double multiplier = plugin.abilities().damageMultiplier(id);
        if (multiplier != 1.0) {
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    // ------------------------------------------------------------------ deaths

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer != null && killer.equals(victim)) {
            killer = null;
        }
        PieceType victimPiece = plugin.data().of(victim).piece();

        // Special items are kept instead of being dropped.
        if (!event.getKeepInventory()) {
            for (ItemStack item : new ArrayList<>(event.getDrops())) {
                if (plugin.trader().isTraderItem(item) || plugin.abilityItems().isWand(item)) {
                    event.getDrops().remove(item);
                    event.getItemsToKeep().add(item);
                }
            }
        }

        int endedStreak = plugin.progression().recordDeath(victim);
        plugin.grandmaster().onDeath(victim);
        if (killer != null) {
            plugin.progression().recordKill(killer, victim, endedStreak);
            plugin.grandmaster().onKill(killer, victim);
        }
        plugin.events().onDeath(victim, killer);
        plugin.abilities().clear(victim);
        plugin.clock().forget(victim.getUniqueId());
        plugin.combat().clear(victim.getUniqueId());

        if (plugin.getConfig().getBoolean("death-messages.enabled", true) && killer != null) {
            List<String> templates = plugin.getConfig().getStringList("death-messages.pvp");
            if (!templates.isEmpty()) {
                PieceType killerPiece = plugin.data().of(killer).piece();
                event.deathMessage(plugin.messages().mm(templates.get(ThreadLocalRandom.current().nextInt(templates.size())),
                        Messages.text("killer", killer.getName()),
                        Messages.text("victim", victim.getName()),
                        Messages.comp("killer_piece", plugin.classes().label(killerPiece == null ? PieceType.PAWN : killerPiece)),
                        Messages.comp("victim_piece", plugin.classes().label(victimPiece == null ? PieceType.PAWN : victimPiece))));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        plugin.events().handleRespawn(event);
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                plugin.nameTags().refresh(p);
                plugin.grandmaster().sync(p);
            }
        });
    }

    // ------------------------------------------------------------------ freeze and grandmaster buffs

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.grandmaster().isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getX() == to.getX() && from.getZ() == to.getZ())) {
            return;
        }
        Location fixed = to.clone();
        fixed.setX(from.getX());
        fixed.setZ(from.getZ());
        event.setTo(fixed);
    }

    @EventHandler(ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (!plugin.grandmaster().isFrozen(event.getPlayer().getUniqueId())) {
            return;
        }
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            event.setCancelled(true);
        }
    }

    /** Milk or commands must not strip the permanent grandmaster effects. */
    @EventHandler(ignoreCancelled = true)
    public void onEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player p) || !plugin.grandmaster().isBuffed(p.getUniqueId())) {
            return;
        }
        EntityPotionEffectEvent.Action action = event.getAction();
        if (action != EntityPotionEffectEvent.Action.CLEARED && action != EntityPotionEffectEvent.Action.REMOVED) {
            return;
        }
        if (event.getCause() == EntityPotionEffectEvent.Cause.PLUGIN) {
            return;
        }
        PotionEffectType type = event.getModifiedType();
        if (type == null || type.equals(PotionEffectType.SPEED) || type.equals(PotionEffectType.RESISTANCE)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline() && !p.isDead()) {
                    plugin.grandmaster().sync(p);
                }
            });
        }
    }
}
