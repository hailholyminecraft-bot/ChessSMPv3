package com.chesssmp.abilities;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import com.chesssmp.Messages;
import com.chesssmp.storage.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Chess Clock: for a few seconds everything within a radius of the user crawls (players, mobs, arrows,
 * falling blocks, TNT). The user - and anybody they trust - is not affected.
 */
public final class ClockManager {

    private static final String COOLDOWN_ID = "chess_clock";

    private final ChessSMP plugin;
    private final Map<UUID, BukkitTask> running = new HashMap<>();

    public ClockManager(ChessSMP plugin) {
        this.plugin = plugin;
    }

    public boolean use(Player user) {
        PlayerData d = plugin.data().of(user);
        long now = System.currentTimeMillis();
        long remain = d.cooldownEnd(COOLDOWN_ID) - now;
        if (remain > 0) {
            plugin.messages().actionBar(user, "clock-cooldown", Messages.text("time", Messages.duration(remain)));
            return false;
        }
        if (running.containsKey(user.getUniqueId())) {
            return false;
        }
        double duration = plugin.getConfig().getDouble("clock.duration-seconds", 10);
        double cooldown = Math.max(duration, plugin.getConfig().getDouble("clock.cooldown-seconds", 50));
        d.setCooldown(COOLDOWN_ID, now + (long) (cooldown * 1000));
        plugin.data().markDirty();

        double radius = plugin.getConfig().getDouble("clock.radius", 20);
        int slownessAmp = Math.max(0, plugin.getConfig().getInt("clock.slowness-level", 6) - 1);
        int fatigueAmp = Math.max(0, plugin.getConfig().getInt("clock.fatigue-level", 3) - 1);
        double projectileFactor = plugin.getConfig().getDouble("clock.projectile-speed-factor", 0.25);
        int totalTicks = (int) (duration * 20);
        UUID id = user.getUniqueId();

        Fx.sound(plugin, user.getLocation(), "block.beacon.deactivate", 1.2f, 0.5f);
        Bukkit.broadcast(plugin.messages().component("clock-used", Messages.text("player", user.getName())));

        BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                Player online = Bukkit.getPlayer(id);
                if (online == null || online.isDead() || elapsed >= totalTicks) {
                    running.remove(id);
                    cancel();
                    if (online != null) {
                        plugin.messages().actionBar(online, "clock-ended");
                    }
                    return;
                }
                Location center = online.getLocation();
                // Projectiles and other fast movers are damped every run (2 ticks).
                for (Entity e : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
                    if (e instanceof Projectile projectile) {
                        if (projectile.getShooter() instanceof Player shooter && shooter.equals(online)) {
                            continue;
                        }
                        projectile.setVelocity(projectile.getVelocity().multiply(projectileFactor));
                    } else if (e instanceof FallingBlock || e instanceof TNTPrimed) {
                        Vector v = e.getVelocity();
                        e.setVelocity(v.multiply(projectileFactor));
                    }
                }
                // Living entities get short slowness every 10 ticks so anybody leaving the area recovers at once.
                if (elapsed % 10 == 0) {
                    for (LivingEntity target : center.getWorld().getNearbyLivingEntities(center, radius)) {
                        if (target.equals(online) || target instanceof ArmorStand) {
                            continue;
                        }
                        if (target instanceof Player other && (other.getGameMode() == org.bukkit.GameMode.SPECTATOR
                                || plugin.data().of(online).trusted().contains(other.getUniqueId()))) {
                            continue;
                        }
                        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, slownessAmp, false, false, false));
                        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 25, fatigueAmp, false, false, false));
                    }
                    ring(center, radius);
                }
                elapsed += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
        running.put(id, task);
        return true;
    }

    private void ring(Location center, double radius) {
        int points = 36;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            Location at = center.clone().add(Math.cos(angle) * radius, 0.3, Math.sin(angle) * radius);
            Fx.particle(plugin, at, "reverse_portal", 1, 0.0);
        }
    }

    public void forget(UUID id) {
        BukkitTask task = running.remove(id);
        if (task != null) {
            task.cancel();
        }
    }

    public void shutdown() {
        for (BukkitTask task : running.values()) {
            task.cancel();
        }
        running.clear();
    }
}
