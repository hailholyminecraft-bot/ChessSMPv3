package com.chesssmp.meteor;

import com.chesssmp.ChessSMP;
import com.chesssmp.Messages;
import com.chesssmp.items.CustomItems;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * Builds a meteor site: a very large crater with a medium sized meteor in its centre. The hollow meteor holds the
 * green villager who sells PvP gear (see ShopMenu) and a barrel of remnants. Blocks are placed in small batches
 * per tick so the server does not stall.
 */
public final class MeteorManager {

    private record Op(int x, int y, int z, Material material) { }

    private final ChessSMP plugin;
    private final NamespacedKey villagerKey;
    private boolean building;
    private BukkitTask autoTask;
    private long nextAutoSpawn;

    public MeteorManager(ChessSMP plugin) {
        this.plugin = plugin;
        this.villagerKey = new NamespacedKey(plugin, "meteor_trader");
    }

    public boolean isShopVillager(Entity e) {
        return e instanceof Villager && e.getPersistentDataContainer().has(villagerKey, PersistentDataType.BYTE);
    }

    /** Spawns just the shop villager at a location (e.g. /chess meteor villager). */
    public Villager spawnVillager(Location loc) {
        return loc.getWorld().spawn(loc, Villager.class, v -> {
            v.setProfession(Villager.Profession.NITWIT);
            v.customName(plugin.messages().mm(plugin.getConfig().getString("meteor.villager.name", "<green><bold>Meteor Trader")));
            v.setCustomNameVisible(true);
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.getPersistentDataContainer().set(villagerKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    // ------------------------------------------------------------------ automatic spawning

    public void startAuto() {
        stopAuto();
        if (!plugin.getConfig().getBoolean("meteor.auto-spawn.enabled", false)) {
            return;
        }
        long interval = Math.max(10, plugin.getConfig().getLong("meteor.auto-spawn.interval-minutes", 120)) * 60_000L;
        nextAutoSpawn = System.currentTimeMillis() + interval;
        autoTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (System.currentTimeMillis() < nextAutoSpawn || building) {
                return;
            }
            nextAutoSpawn = System.currentTimeMillis() + interval;
            spawnRandom(null);
        }, 1200L, 1200L);
    }

    public void stopAuto() {
        if (autoTask != null) {
            autoTask.cancel();
            autoTask = null;
        }
    }

    /** Picks a random dry location around the world spawn and builds a meteor there. */
    public void spawnRandom(CommandSender by) {
        String worldName = plugin.getConfig().getString("meteor.auto-spawn.world", "");
        World world = worldName.isBlank() ? Bukkit.getWorlds().get(0) : Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().get(0);
        }
        int radius = Math.max(100, plugin.getConfig().getInt("meteor.auto-spawn.radius", 2000));
        Random rng = new Random();
        Location spawn = world.getSpawnLocation();
        for (int attempt = 0; attempt < 12; attempt++) {
            int x = spawn.getBlockX() + rng.nextInt(radius * 2 + 1) - radius;
            int z = spawn.getBlockZ() + rng.nextInt(radius * 2 + 1) - radius;
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            String biome = world.getBiome(x, y, z).getKey().getKey();
            if (!biome.contains("ocean") && !biome.contains("river") && !biome.contains("beach")) {
                spawn(by, world, x, z);
                return;
            }
        }
        if (by != null) {
            plugin.messages().send(by, "meteor-no-site");
        }
    }

    // ------------------------------------------------------------------ building

    public boolean spawn(CommandSender by, World world, int cx, int cz) {
        if (building) {
            if (by != null) {
                plugin.messages().send(by, "meteor-busy");
            }
            return false;
        }
        building = true;
        int crater = Math.max(10, plugin.getConfig().getInt("meteor.crater.radius", 28));
        int reach = crater + 6;
        List<CompletableFuture<?>> loads = new ArrayList<>();
        List<int[]> chunks = new ArrayList<>();
        for (int chunkX = (cx - reach) >> 4; chunkX <= (cx + reach) >> 4; chunkX++) {
            for (int chunkZ = (cz - reach) >> 4; chunkZ <= (cz + reach) >> 4; chunkZ++) {
                chunks.add(new int[]{chunkX, chunkZ});
                loads.add(world.getChunkAtAsync(chunkX, chunkZ));
                world.addPluginChunkTicket(chunkX, chunkZ, plugin);
            }
        }
        CompletableFuture.allOf(loads.toArray(new CompletableFuture[0])).whenComplete((ok, error) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (error != null) {
                        plugin.getLogger().warning("Meteor: chunks could not be loaded: " + error.getMessage());
                        release(world, chunks);
                        building = false;
                        return;
                    }
                    plan(by, world, cx, cz, chunks);
                }));
        return true;
    }

    private void release(World world, List<int[]> chunks) {
        for (int[] c : chunks) {
            world.removePluginChunkTicket(c[0], c[1], plugin);
        }
    }

    private Material floorMaterial(double t, Random rng) {
        double r = rng.nextDouble();
        if (t < 0.3) {
            return r < 0.25 ? Material.MAGMA_BLOCK : r < 0.7 ? Material.BLACKSTONE : Material.COBBLED_DEEPSLATE;
        }
        if (t < 0.65) {
            return r < 0.1 ? Material.MAGMA_BLOCK : r < 0.55 ? Material.BLACKSTONE : r < 0.8 ? Material.COARSE_DIRT : Material.GRAVEL;
        }
        return r < 0.4 ? Material.COARSE_DIRT : r < 0.7 ? Material.GRAVEL : r < 0.9 ? Material.PODZOL : Material.BLACKSTONE;
    }

    private Material rimMaterial(Random rng) {
        double r = rng.nextDouble();
        return r < 0.45 ? Material.BLACKSTONE : r < 0.75 ? Material.COBBLED_DEEPSLATE : r < 0.9 ? Material.COARSE_DIRT : Material.MAGMA_BLOCK;
    }

    private Material shellMaterial(Random rng) {
        double r = rng.nextDouble();
        if (r < 0.40) return Material.BLACKSTONE;
        if (r < 0.60) return Material.DEEPSLATE;
        if (r < 0.72) return Material.MAGMA_BLOCK;
        if (r < 0.82) return Material.OBSIDIAN;
        if (r < 0.88) return Material.CRYING_OBSIDIAN;
        if (r < 0.94) return Material.GILDED_BLACKSTONE;
        if (r < 0.97) return Material.ANCIENT_DEBRIS;
        return Material.VERDANT_FROGLIGHT;
    }

    private void plan(CommandSender by, World world, int cx, int cz, List<int[]> chunks) {
        FileConfigurationView cfg = new FileConfigurationView(plugin);
        int crater = cfg.craterRadius;
        int depth = cfg.craterDepth;
        int core = cfg.coreRadius;
        int reach = crater + 6;
        int y0 = world.getHighestBlockYAt(cx, cz, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        y0 = Math.max(world.getMinHeight() + depth + core * 2 + 8, Math.min(world.getMaxHeight() - 40, y0));
        Random rng = new Random();
        List<Op> ops = new ArrayList<>(30000);

        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > reach) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int surface = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                if (d <= crater) {
                    double t = d / crater;
                    int bowl = y0 - (int) Math.round(depth * (1 - t * t));
                    if (surface < bowl) {
                        continue; // the terrain is already lower than the bowl here
                    }
                    for (int y = bowl + 1; y <= surface + 3; y++) {
                        ops.add(new Op(x, y, z, Material.AIR));
                    }
                    ops.add(new Op(x, bowl, z, floorMaterial(t, rng)));
                    ops.add(new Op(x, bowl - 1, z, t < 0.5 ? Material.BLACKSTONE : Material.COARSE_DIRT));
                } else if (d <= crater + 5 && rng.nextDouble() < 0.55 * (1 - (d - crater) / 5.0)) {
                    int height = 1 + rng.nextInt(3);
                    for (int k = 1; k <= height; k++) {
                        ops.add(new Op(x, surface + k, z, rimMaterial(rng)));
                    }
                }
            }
        }

        // The meteor: a rough sphere lodged in the crater floor, hollow inside.
        int my = y0 - depth + core - 1;
        int cavity = core - 2;
        for (int dx = -core; dx <= core; dx++) {
            for (int dy = -core; dy <= core; dy++) {
                for (int dz = -core; dz <= core; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist > core + 0.2) {
                        continue;
                    }
                    if (dist <= cavity + 0.1) {
                        ops.add(new Op(cx + dx, my + dy, cz + dz, Material.AIR));
                    } else if (dist + rng.nextDouble() * 0.7 <= core + 0.2) {
                        ops.add(new Op(cx + dx, my + dy, cz + dz, shellMaterial(rng)));
                    }
                }
            }
        }
        int floorY = my - cavity;
        // green glow inside, entrance tunnel to the east, barrel with remnants
        int[][] lights = {{cavity, 0, 0}, {-cavity, 0, 0}, {0, 0, cavity}, {0, 0, -cavity}, {0, cavity, 0}};
        for (int[] l : lights) {
            if (!(l[0] == cavity && l[1] == 0 && l[2] == 0)) {
                ops.add(new Op(cx + l[0], my + l[1], cz + l[2], Material.VERDANT_FROGLIGHT));
            }
        }
        for (int xo = 0; xo <= core + 1; xo++) {
            for (int zo = 0; zo <= 1; zo++) {
                for (int yo = 0; yo <= 1; yo++) {
                    ops.add(new Op(cx + xo, floorY + yo, cz + zo, Material.AIR));
                }
            }
        }
        ops.add(new Op(cx - 1, floorY, cz - 1, Material.BARREL));

        final int finalY0 = y0;
        final int barrelX = cx - 1;
        final int barrelZ = cz - 1;
        final Location villagerSpot = new Location(world, cx + 0.5, floorY, cz + 0.5);
        final int perTick = Math.max(200, cfg.blocksPerTick);

        if (plugin.getConfig().getBoolean("meteor.announce", true)) {
            Bukkit.broadcast(plugin.messages().component("meteor-crash", Messages.num("x", cx), Messages.num("y", finalY0),
                    Messages.num("z", cz), Messages.text("world", world.getName())));
        }
        if (by != null) {
            plugin.messages().send(by, "meteor-building", Messages.num("blocks", ops.size()));
        }
        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                int end = Math.min(ops.size(), index + perTick);
                for (; index < end; index++) {
                    Op op = ops.get(index);
                    if (op.y() < world.getMinHeight() || op.y() >= world.getMaxHeight()) {
                        continue;
                    }
                    world.getBlockAt(op.x(), op.y(), op.z()).setType(op.material(), false);
                }
                if (index >= ops.size()) {
                    cancel();
                    finish(world, villagerSpot, barrelX, floorY, barrelZ, chunks, by);
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private void finish(World world, Location villagerSpot, int barrelX, int barrelY, int barrelZ, List<int[]> chunks, CommandSender by) {
        try {
            spawnVillager(villagerSpot);
            Block barrel = world.getBlockAt(barrelX, barrelY, barrelZ);
            if (barrel.getState() instanceof Container container) {
                int remnants = Math.max(0, plugin.getConfig().getInt("meteor.core-remnants", 3));
                if (remnants > 0) {
                    container.getInventory().addItem(plugin.items().create(CustomItems.Kind.REMNANT, remnants));
                }
            }
            world.playSound(villagerSpot, "entity.generic.explode", org.bukkit.SoundCategory.AMBIENT, 4f, 0.6f);
            if (by != null) {
                plugin.messages().send(by, "meteor-done");
            }
        } finally {
            release(world, chunks);
            building = false;
        }
    }

    /** Reads the geometry settings once per build. */
    private static final class FileConfigurationView {
        final int craterRadius;
        final int craterDepth;
        final int coreRadius;
        final int blocksPerTick;

        FileConfigurationView(ChessSMP plugin) {
            craterRadius = Math.max(10, plugin.getConfig().getInt("meteor.crater.radius", 28));
            craterDepth = Math.max(4, plugin.getConfig().getInt("meteor.crater.depth", 12));
            coreRadius = Math.max(4, Math.min(8, plugin.getConfig().getInt("meteor.core.radius", 5)));
            blocksPerTick = plugin.getConfig().getInt("meteor.blocks-per-tick", 2000);
        }
    }
}
