package com.chesssmp.scoreboard;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import com.chesssmp.Messages;
import com.chesssmp.classes.PieceType;
import com.chesssmp.events.Side;
import com.chesssmp.storage.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Chess emoji name tags using the vanilla scoreboard team system (prefix + name colour) plus the tab list.
 * All teams are named "csmp_*" so they never collide with other plugins. When a player already belongs to a team
 * of another plugin, that team is left alone and only the tab list name is changed.
 */
public final class NameTagManager {

    private static final String PREFIX = "csmp_";

    private record Key(PieceType piece, Side side) { }

    private final ChessSMP plugin;
    private final Map<Key, Team> teams = new HashMap<>();

    public NameTagManager(ChessSMP plugin) {
        this.plugin = plugin;
    }

    private Scoreboard board() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        return manager == null ? null : manager.getMainScoreboard();
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("nametag.enabled", true);
    }

    private NamedTextColor nameColor(PieceType piece, Side side) {
        if (side == null) {
            return plugin.classes().color(piece);
        }
        return plugin.events().sideColor(side);
    }

    private Team teamFor(Scoreboard board, Key key) {
        Team team = teams.get(key);
        if (team != null) {
            try {
                team.getScoreboard();
                return team;
            } catch (IllegalStateException unregistered) {
                teams.remove(key);
            }
        }
        String name = PREFIX + key.piece().ordinal() + "_" + (key.side() == null ? "n" : key.side().key().substring(0, 1));
        team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        configure(team, key);
        teams.put(key, team);
        return team;
    }

    private void configure(Team team, Key key) {
        String emoji = plugin.classes().emoji(key.piece());
        NamedTextColor pieceColor = plugin.classes().color(key.piece());
        String format = plugin.getConfig().getString("nametag.prefix-format", "<emoji> ");
        team.prefix(plugin.messages().mm(format, Messages.text("emoji", emoji)).colorIfAbsent(pieceColor));
        team.color(nameColor(key.piece(), key.side()));
    }

    /** Re-applies the name tag of one player (class change, team change, join). */
    public void refresh(Player p) {
        if (!enabled()) {
            return;
        }
        PieceType piece = plugin.data().of(p).piece();
        Scoreboard board = board();
        if (piece == null || board == null) {
            return;
        }
        Side side = plugin.events().sideOf(p.getUniqueId());

        PlayerData data = plugin.data().of(p);
        Team current = board.getEntryTeam(p.getName());
        boolean foreign = current != null && !current.getName().startsWith(PREFIX);
        boolean allowed = !foreign || plugin.getConfig().getBoolean("nametag.override-foreign-teams", false);
        if (data.grandmaster()) {
            if (allowed) {
                Team team = personalTeam(board, p);
                configurePersonal(team, data, piece, side);
                if (current == null || !current.equals(team)) {
                    team.addEntry(p.getName());
                }
            }
        } else {
            dropPersonal(p.getUniqueId());
            if (allowed) {
                Team team = teamFor(board, new Key(piece, side));
                Team now = board.getEntryTeam(p.getName());
                if (now == null || !now.equals(team)) {
                    team.addEntry(p.getName());
                }
            }
        }

        String emoji = plugin.classes().emoji(piece);
        String format = plugin.getConfig().getString("nametag.tab-format", "<emoji> <name>");
        Component tab = plugin.messages().mm(format,
                Messages.comp("emoji", Component.text(emoji, plugin.classes().color(piece))),
                Messages.comp("name", Component.text(p.getName(), data.grandmaster() ? NamedTextColor.GOLD : nameColor(piece, side))));
        if (data.grandmaster()) {
            tab = plugin.messages().mm(plugin.getConfig().getString("grandmaster.tab-prefix", "<gold>\u265A </gold>")).append(tab)
                    .append(plugin.messages().mm(" <gray>" + plugin.grandmaster().pieceString(data)));
        }
        p.playerListName(tab);
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refresh(p);
        }
    }

    private final Map<UUID, Team> personal = new HashMap<>();

    private Team personalTeam(Scoreboard board, Player p) {
        Team team = personal.get(p.getUniqueId());
        if (team != null) {
            try {
                team.getScoreboard();
                return team;
            } catch (IllegalStateException unregistered) {
                personal.remove(p.getUniqueId());
            }
        }
        String name = PREFIX + "g" + Integer.toHexString(p.getUniqueId().hashCode());
        team = board.getTeam(name);
        if (team == null) {
            team = board.registerNewTeam(name);
        }
        personal.put(p.getUniqueId(), team);
        return team;
    }

    /** Grandmaster tag: title in front of the name, collected pieces behind it. */
    private void configurePersonal(Team team, PlayerData d, PieceType piece, Side side) {
        String format = plugin.getConfig().getString("grandmaster.prefix-format", "<gold><bold>\u265A GRANDMASTER</bold></gold> ");
        Component prefix = plugin.messages().mm(format)
                .append(Component.text(plugin.classes().emoji(piece) + " ", plugin.classes().color(piece)));
        team.prefix(prefix);
        String pieces = plugin.grandmaster().pieceString(d);
        team.suffix(pieces.isEmpty() ? Component.empty() : Component.text(" " + pieces, NamedTextColor.GRAY));
        team.color(side == null ? NamedTextColor.GOLD : plugin.events().sideColor(side));
    }

    private void dropPersonal(UUID id) {
        Team team = personal.remove(id);
        if (team != null) {
            try {
                team.unregister();
            } catch (IllegalStateException ignored) {
                // already gone
            }
        }
    }

    public void remove(Player p) {
        Scoreboard board = board();
        if (board != null) {
            Team team = board.getEntryTeam(p.getName());
            if (team != null && team.getName().startsWith(PREFIX)) {
                team.removeEntry(p.getName());
            }
        }
        p.playerListName(null);
        dropPersonal(p.getUniqueId());
    }

    /** Re-reads colours/prefix format from the config. */
    public void reload() {
        for (Map.Entry<Key, Team> e : new ArrayList<>(teams.entrySet())) {
            try {
                configure(e.getValue(), e.getKey());
            } catch (IllegalStateException unregistered) {
                teams.remove(e.getKey());
            }
        }
        if (enabled()) {
            refreshAll();
        } else {
            for (Player p : Bukkit.getOnlinePlayers()) {
                remove(p);
            }
        }
    }

    /** Removes leftover csmp_* teams from a previous run (the main scoreboard is saved with the world). */
    public void cleanupStale() {
        Scoreboard board = board();
        if (board == null) {
            return;
        }
        for (Team team : new ArrayList<>(board.getTeams())) {
            if (team.getName().startsWith(PREFIX)) {
                team.unregister();
            }
        }
        teams.clear();
        personal.clear();
    }

    public void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playerListName(null);
        }
        cleanupStale();
    }

    /** Colour helper for other classes. */
    public NamedTextColor colorOf(String name, NamedTextColor fallback) {
        return Fx.color(name, fallback);
    }
}
