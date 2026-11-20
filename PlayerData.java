package com.chesssmp.storage;

import com.chesssmp.classes.PieceType;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent per-player ChessSMP data. Only touched from the main thread. */
public final class PlayerData {

    private final UUID uuid;
    private String name = "";
    private PieceType piece;
    private boolean chosen;
    private long points;
    private int kills;
    private int deaths;
    private int streak;
    private int bestStreak;
    private int abilitiesUsed;
    private int eventsPlayed;
    private int eventWins;
    private int pieceChanges;
    private long lastChoose;
    private boolean pendingMace;
    private boolean grandmaster;
    private final Set<PieceType> gmPieces = EnumSet.noneOf(PieceType.class);
    private final Set<UUID> trusted = new HashSet<>();
    private final Set<String> objectives = new HashSet<>();
    private final Map<String, Long> cooldowns = new HashMap<>();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID uuid() { return uuid; }
    public String name() { return name; }
    public void setName(String name) { this.name = name == null ? "" : name; }
    public PieceType piece() { return piece; }
    public void setPiece(PieceType piece) { this.piece = piece; }
    public boolean chosen() { return chosen; }
    public void setChosen(boolean chosen) { this.chosen = chosen; }
    public long points() { return points; }
    public void addPoints(long amount) { this.points = Math.max(0, this.points + amount); }
    public void setPoints(long points) { this.points = Math.max(0, points); }
    public int kills() { return kills; }
    public void addKill() { kills++; }
    public int deaths() { return deaths; }
    public void addDeath() { deaths++; }
    public int streak() { return streak; }
    public void setStreak(int streak) { this.streak = streak; }

    /** Increments the current streak and returns the new value. */
    public int incrementStreak() {
        streak++;
        bestStreak = Math.max(bestStreak, streak);
        return streak;
    }

    public int bestStreak() { return bestStreak; }
    public int abilitiesUsed() { return abilitiesUsed; }
    public void addAbilityUse() { abilitiesUsed++; }
    public int eventsPlayed() { return eventsPlayed; }
    public void addEventPlayed() { eventsPlayed++; }
    public int eventWins() { return eventWins; }
    public void addEventWin() { eventWins++; }
    public int pieceChanges() { return pieceChanges; }
    public void addPieceChange() { pieceChanges++; }
    public long lastChoose() { return lastChoose; }
    public void setLastChoose(long lastChoose) { this.lastChoose = lastChoose; }
    public boolean pendingMace() { return pendingMace; }
    public void setPendingMace(boolean pendingMace) { this.pendingMace = pendingMace; }
    public boolean grandmaster() { return grandmaster; }
    public void setGrandmaster(boolean grandmaster) { this.grandmaster = grandmaster; }
    public Set<PieceType> gmPieces() { return gmPieces; }
    public Set<UUID> trusted() { return trusted; }
    public boolean hasObjective(String id) { return objectives.contains(id); }
    public void addObjective(String id) { objectives.add(id); }
    public int objectiveCount() { return objectives.size(); }

    /** Epoch millis when the ability cooldown ends (0 = ready). */
    public long cooldownEnd(String abilityId) {
        return cooldowns.getOrDefault(abilityId, 0L);
    }

    public void setCooldown(String abilityId, long endMillis) {
        cooldowns.put(abilityId, endMillis);
    }

    /** Keeps the later of the current and the given cooldown end. */
    public void extendCooldown(String abilityId, long endMillis) {
        cooldowns.merge(abilityId, endMillis, Math::max);
    }

    public void clearCooldowns() {
        cooldowns.clear();
    }

    public void pruneCooldowns(long now) {
        cooldowns.values().removeIf(end -> end <= now);
    }

    public double kdr() {
        return deaths == 0 ? kills : (double) kills / deaths;
    }

    public void write(ConfigurationSection s) {
        s.set("name", name);
        s.set("piece", piece == null ? null : piece.name());
        s.set("chosen", chosen);
        s.set("points", points);
        s.set("kills", kills);
        s.set("deaths", deaths);
        s.set("streak", streak);
        s.set("best-streak", bestStreak);
        s.set("abilities-used", abilitiesUsed);
        s.set("events-played", eventsPlayed);
        s.set("event-wins", eventWins);
        s.set("piece-changes", pieceChanges);
        s.set("last-choose", lastChoose);
        s.set("pending-mace", pendingMace);
        s.set("grandmaster", grandmaster);
        List<String> pieceNames = new ArrayList<>();
        for (PieceType t : gmPieces) {
            pieceNames.add(t.name());
        }
        s.set("gm-pieces", pieceNames);
        List<String> trustedIds = new ArrayList<>();
        for (UUID u : trusted) {
            trustedIds.add(u.toString());
        }
        s.set("trusted", trustedIds);
        s.set("objectives", new ArrayList<>(objectives));
        if (!cooldowns.isEmpty()) {
            ConfigurationSection cd = s.createSection("cooldowns");
            for (Map.Entry<String, Long> e : cooldowns.entrySet()) {
                cd.set(e.getKey(), e.getValue());
            }
        }
    }

    public static PlayerData read(UUID id, ConfigurationSection s) {
        PlayerData d = new PlayerData(id);
        d.name = s.getString("name", "");
        d.piece = PieceType.parse(s.getString("piece"));
        d.chosen = s.getBoolean("chosen", false);
        d.points = s.getLong("points", 0L);
        d.kills = s.getInt("kills", 0);
        d.deaths = s.getInt("deaths", 0);
        d.streak = s.getInt("streak", 0);
        d.bestStreak = s.getInt("best-streak", 0);
        d.abilitiesUsed = s.getInt("abilities-used", 0);
        d.eventsPlayed = s.getInt("events-played", 0);
        d.eventWins = s.getInt("event-wins", 0);
        d.pieceChanges = s.getInt("piece-changes", 0);
        d.lastChoose = s.getLong("last-choose", 0L);
        d.pendingMace = s.getBoolean("pending-mace", false);
        d.grandmaster = s.getBoolean("grandmaster", false);
        for (String name : s.getStringList("gm-pieces")) {
            PieceType t = PieceType.parse(name);
            if (t != null) {
                d.gmPieces.add(t);
            }
        }
        for (String raw : s.getStringList("trusted")) {
            try {
                d.trusted.add(UUID.fromString(raw));
            } catch (IllegalArgumentException ignored) {
                // skip malformed ids
            }
        }
        d.objectives.addAll(s.getStringList("objectives"));
        ConfigurationSection cd = s.getConfigurationSection("cooldowns");
        if (cd != null) {
            for (String key : cd.getKeys(false)) {
                d.cooldowns.put(key, cd.getLong(key));
            }
        }
        return d;
    }
}
