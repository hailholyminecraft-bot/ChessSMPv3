package com.chesssmp.storage;

import com.chesssmp.ChessSMP;
import com.chesssmp.classes.PieceType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Keeps all player data in memory and persists it to plugins/ChessSMP/players.yml.
 * Snapshots are serialised on the main thread and written asynchronously; the file is replaced atomically
 * so a crash can never leave a half-written file behind.
 */
public final class DataStore {

    private final ChessSMP plugin;
    private final File file;
    private final Map<UUID, PlayerData> players = new HashMap<>();
    private boolean dirty;
    private final Object ioLock = new Object();
    private final AtomicLong sequence = new AtomicLong();
    private long lastWritten;

    public DataStore(ChessSMP plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
    }

    public void load() {
        players.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (Exception ex) {
            File backup = new File(file.getParentFile(), "players.yml.corrupt-" + System.currentTimeMillis());
            plugin.getLogger().log(Level.SEVERE, "players.yml could not be read; moving it to " + backup.getName(), ex);
            if (!file.renameTo(backup)) {
                plugin.getLogger().severe("Could not back up the corrupt data file!");
            }
            return;
        }
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            try {
                UUID id = UUID.fromString(key);
                players.put(id, PlayerData.read(id, s));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping invalid player entry '" + key + "' in players.yml");
            }
        }
        plugin.getLogger().info("Loaded data for " + players.size() + " players.");
    }

    public void startAutosave() {
        long period = Math.max(1, plugin.getConfig().getLong("storage.autosave-minutes", 5)) * 60L * 20L;
        Bukkit.getScheduler().runTaskTimer(plugin, this::saveAsyncIfDirty, period, period);
    }

    /** Returns the data of an online player, creating it (with the default piece) when missing. */
    public PlayerData of(Player player) {
        PlayerData d = of(player.getUniqueId(), player.getName());
        if (d.piece() == null) {
            PieceType def = PieceType.parse(plugin.getConfig().getString("default-piece", "PAWN"));
            d.setPiece(def == null ? PieceType.PAWN : def);
            markDirty();
        }
        return d;
    }

    public PlayerData of(UUID id, String name) {
        PlayerData d = players.get(id);
        if (d == null) {
            d = new PlayerData(id);
            players.put(id, d);
            markDirty();
        }
        if (name != null && !name.isEmpty() && !name.equals(d.name())) {
            d.setName(name);
            markDirty();
        }
        return d;
    }

    /** @return stored data or null. */
    public PlayerData find(UUID id) {
        return players.get(id);
    }

    public PlayerData findByName(String name) {
        for (PlayerData d : players.values()) {
            if (d.name().equalsIgnoreCase(name)) {
                return d;
            }
        }
        return null;
    }

    public Collection<PlayerData> all() {
        return players.values();
    }

    /** Wipes a player's progress (keeps the last known name). */
    public PlayerData reset(UUID id) {
        PlayerData old = players.get(id);
        PlayerData fresh = new PlayerData(id);
        if (old != null) {
            fresh.setName(old.name());
        }
        players.put(id, fresh);
        markDirty();
        return fresh;
    }

    public void markDirty() {
        dirty = true;
    }

    public void saveAsyncIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        String content = snapshot();
        long seq = sequence.incrementAndGet();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(content, seq));
    }

    /** Synchronous save (shutdown). */
    public void saveNow() {
        dirty = false;
        write(snapshot(), sequence.incrementAndGet());
    }

    private String snapshot() {
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection root = yaml.createSection("players");
        for (PlayerData d : new ArrayList<>(players.values())) {
            d.write(root.createSection(d.uuid().toString()));
        }
        return yaml.saveToString();
    }

    private void write(String content, long seq) {
        synchronized (ioLock) {
            if (seq < lastWritten) {
                return; // a newer snapshot has already been written
            }
            lastWritten = seq;
            try {
                Path target = file.toPath();
                Files.createDirectories(target.getParent());
                Path tmp = target.resolveSibling("players.yml.tmp");
                Files.writeString(tmp, content, StandardCharsets.UTF_8);
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ex) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not save players.yml", ex);
                dirty = true;
            }
        }
    }
}
