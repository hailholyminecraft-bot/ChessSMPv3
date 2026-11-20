package com.chesssmp.statistics;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import com.chesssmp.Messages;
import com.chesssmp.classes.PieceType;
import com.chesssmp.storage.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Chess Points, ranks, killstreaks and objectives. Ranks are cosmetic: titles and kill effects only. */
public final class ProgressionManager {

    private final ChessSMP plugin;
    private final EnumMap<Rank, Long> thresholds = new EnumMap<>(Rank.class);
    private final Map<String, Long> lastKill = new HashMap<>();

    public ProgressionManager(ChessSMP plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        thresholds.clear();
        for (Rank r : Rank.values()) {
            thresholds.put(r, plugin.getConfig().getLong("progression.ranks." + r.key() + ".points", r.defaultPoints()));
        }
    }

    public Rank rank(long points) {
        return Rank.resolve(thresholds, points);
    }

    public Rank rank(PlayerData d) {
        return rank(d.points());
    }

    public String title(Rank r) {
        return plugin.getConfig().getString("progression.ranks." + r.key() + ".title", r.display());
    }

    /** @return points needed for the next rank or -1 when already at the top. */
    public long nextThreshold(Rank current) {
        int next = current.ordinal() + 1;
        if (next >= Rank.values().length) {
            return -1;
        }
        return thresholds.getOrDefault(Rank.values()[next], Rank.values()[next].defaultPoints());
    }

    /** Adds (or removes) Chess Points and announces rank-ups. Works for offline players too. */
    public void addPoints(UUID id, long amount) {
        PlayerData d = plugin.data().find(id);
        if (d == null || amount == 0) {
            return;
        }
        Rank before = rank(d);
        d.addPoints(amount);
        Rank after = rank(d);
        plugin.data().markDirty();
        if (after.ordinal() > before.ordinal()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.sendMessage(plugin.messages().prefixed("rank-up",
                        Messages.text("rank", display(after)), Messages.text("title", title(after))));
                Fx.sound(plugin, p.getLocation(), "entity.player.levelup", 1f, 1f);
            }
        }
    }

    public String display(Rank r) {
        return r.display();
    }

    /** Called first when a player dies. @return the killstreak that just ended. */
    public int recordDeath(Player victim) {
        PlayerData d = plugin.data().of(victim);
        int ended = d.streak();
        d.addDeath();
        d.setStreak(0);
        plugin.data().markDirty();
        return ended;
    }

    /** Called when {@code killer} killed {@code victim}. */
    public void recordKill(Player killer, Player victim, int victimEndedStreak) {
        FileConfiguration cfg = plugin.getConfig();
        PlayerData kd = plugin.data().of(killer);
        PlayerData vd = plugin.data().of(victim);
        kd.addKill();
        int streak = kd.incrementStreak();

        long points = cfg.getLong("points.kill", 5);
        if (vd.piece() == PieceType.KING) {
            points += cfg.getLong("points.king-kill-bonus", 10);
        }
        // Anti-farming: the same victim only awards points once per cooldown window.
        long now = System.currentTimeMillis();
        String key = killer.getUniqueId() + ":" + victim.getUniqueId();
        Long last = lastKill.get(key);
        long window = cfg.getLong("points.same-victim-cooldown-seconds", 300) * 1000L;
        if (last != null && now - last < window) {
            points = 0;
        }
        lastKill.put(key, now);
        if (lastKill.size() > 2000) {
            lastKill.values().removeIf(t -> now - t > 3_600_000L);
        }
        if (points > 0) {
            addPoints(killer.getUniqueId(), points);
        }
        plugin.data().markDirty();

        PieceType victimPiece = vd.piece() == null ? PieceType.PAWN : vd.piece();
        killer.sendActionBar(plugin.messages().component("kill-actionbar",
                Messages.text("emoji", plugin.classes().emoji(victimPiece)),
                Messages.text("victim", victim.getName()),
                Messages.comp("victim_piece", plugin.classes().label(victimPiece)),
                Messages.num("points", points)));

        Rank rank = rank(kd);
        Fx.particle(plugin, victim.getLocation().add(0, 1, 0),
                cfg.getString("progression.ranks." + rank.key() + ".kill-effect", ""), 20, 0.5);

        PieceType killerPiece = kd.piece() == null ? PieceType.PAWN : kd.piece();
        String announce = cfg.getString("killstreak.announcements." + streak);
        if (announce != null && !announce.isBlank()) {
            Bukkit.broadcast(plugin.messages().mm(announce,
                    Messages.text("player", killer.getName()),
                    Messages.text("emoji", plugin.classes().emoji(killerPiece)),
                    Messages.num("streak", streak)));
        }
        if (victimEndedStreak >= cfg.getInt("killstreak.shutdown-min", 5)) {
            Bukkit.broadcast(plugin.messages().component("killstreak-shutdown",
                    Messages.text("killer", killer.getName()),
                    Messages.text("victim", victim.getName()),
                    Messages.num("streak", victimEndedStreak)));
        }
        checkObjectives(killer.getUniqueId());
    }

    /** Awards every configured objective whose goal has been reached (each only once). */
    public void checkObjectives(UUID id) {
        PlayerData d = plugin.data().find(id);
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("objectives");
        if (d == null || section == null) {
            return;
        }
        for (String objectiveId : section.getKeys(false)) {
            if (d.hasObjective(objectiveId)) {
                continue;
            }
            ConfigurationSection o = section.getConfigurationSection(objectiveId);
            if (o == null) {
                continue;
            }
            long value = switch (o.getString("type", "KILLS").toUpperCase(Locale.ROOT)) {
                case "KILLS" -> d.kills();
                case "BEST_STREAK" -> d.bestStreak();
                case "EVENT_WINS" -> d.eventWins();
                case "EVENTS_PLAYED" -> d.eventsPlayed();
                case "ABILITIES_USED" -> d.abilitiesUsed();
                case "PIECE_CHANGES" -> d.pieceChanges();
                case "POINTS" -> d.points();
                default -> -1;
            };
            if (value < 0 || value < o.getLong("amount", 1)) {
                continue;
            }
            d.addObjective(objectiveId);
            long reward = o.getLong("reward", 0);
            addPoints(id, reward);
            plugin.data().markDirty();
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.sendMessage(plugin.messages().prefixed("objective-complete",
                        Messages.text("name", o.getString("name", objectiveId)),
                        Messages.text("description", o.getString("description", "")),
                        Messages.num("points", reward)));
                Fx.sound(plugin, p.getLocation(), "ui.toast.challenge_complete", 1f, 1f);
            }
        }
    }

    public Component rankComponent(Rank r) {
        return Component.text(title(r) + " (" + r.display() + ")");
    }
}
