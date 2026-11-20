package com.chesssmp.events;

import com.chesssmp.ChessSMP;
import com.chesssmp.Messages;
import com.chesssmp.classes.PieceType;
import com.chesssmp.storage.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import com.chesssmp.Fx;
import com.chesssmp.scoreboard.EventSidebar;
import com.chesssmp.trader.Cost;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * The Chess War event: joining period, balanced White/Black teams, scoring, respawn rules, victory conditions,
 * final scoreboard and rewards (including the Grandmaster Mace). All state is keyed by UUID - no Player objects are
 * stored - so disconnecting players can never corrupt the event.
 */
public final class EventManager {

    public enum Phase { IDLE, JOINING, RUNNING }

    private static final class Member {
        final Side side;
        int kills;
        int deaths;
        long disconnectedAt;
        boolean eliminated;
        boolean spectating;
        GameMode restoreMode = GameMode.SURVIVAL;

        Member(Side side) {
            this.side = side;
        }
    }

    private final ChessSMP plugin;
    private final EventSidebar sidebar = new EventSidebar();
    private Phase phase = Phase.IDLE;
    private final Set<UUID> queue = new LinkedHashSet<>();
    private final Map<UUID, Member> members = new LinkedHashMap<>();
    private final int[] score = new int[Side.values().length];
    private final Set<Integer> announced = new HashSet<>();
    private long joinEnds;
    private long battleEnds;
    private int scoreLimit = 15;
    private BukkitTask ticker;
    private BukkitTask linger;

    public EventManager(ChessSMP plugin) {
        this.plugin = plugin;
        sidebar.hide(); // remove a stale sidebar from a previous crash
    }

    private FileConfiguration cfg() {
        return plugin.getConfig();
    }

    private Messages msg() {
        return plugin.messages();
    }

    // ------------------------------------------------------------------ queries

    public Phase phase() {
        return phase;
    }

    /** The side of a player, only while the battle is running. */
    public Side sideOf(UUID id) {
        if (phase != Phase.RUNNING) {
            return null;
        }
        Member m = members.get(id);
        return m == null ? null : m.side;
    }

    public boolean sameSide(Player a, Player b) {
        Side x = sideOf(a.getUniqueId());
        return x != null && x == sideOf(b.getUniqueId());
    }

    public String sideName(Side side) {
        return cfg().getString("event.teams." + side.key() + ".name",
                side.name().charAt(0) + side.name().substring(1).toLowerCase(Locale.ROOT));
    }

    public NamedTextColor sideColor(Side side) {
        return Fx.color(cfg().getString("event.teams." + side.key() + ".color"),
                side == Side.WHITE ? NamedTextColor.WHITE : NamedTextColor.DARK_GRAY);
    }

    public Component sideLabel(Side side) {
        return Component.text(sideName(side), sideColor(side));
    }

    private Location spawn(Side side) {
        return cfg().getLocation("event.spawns." + side.key());
    }

    public boolean setSpawn(Player p, Side side) {
        cfg().set("event.spawns." + side.key(), p.getLocation());
        plugin.saveConfig();
        msg().send(p, "event-spawn-set", Messages.comp("side", sideLabel(side)));
        return true;
    }

    private int activeCount(Side side) {
        int n = 0;
        for (Member m : members.values()) {
            if (m.side == side && !m.eliminated) {
                n++;
            }
        }
        return n;
    }

    private List<Player> onlineMembers() {
        List<Player> out = new ArrayList<>();
        for (UUID id : members.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                out.add(p);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ start / join / leave / stop

    public boolean start(CommandSender by, Integer joinSecondsOverride) {
        if (phase != Phase.IDLE) {
            msg().send(by, "event-already-running");
            return false;
        }
        cancelLinger();
        sidebar.hide();
        queue.clear();
        members.clear();
        java.util.Arrays.fill(score, 0);
        announced.clear();
        int joinSeconds = joinSecondsOverride != null ? Math.max(5, joinSecondsOverride)
                : Math.max(5, cfg().getInt("event.join-period-seconds", 60));
        joinEnds = System.currentTimeMillis() + joinSeconds * 1000L;
        scoreLimit = Math.max(1, cfg().getInt("event.score-limit", 15));
        phase = Phase.JOINING;
        for (int mark : cfg().getIntegerList("event.countdown-marks")) {
            if (mark >= joinSeconds) {
                announced.add(mark);
            }
        }
        if (cfg().getBoolean("event.auto-join-all", false)) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("chesssmp.player")) {
                    queue.add(p.getUniqueId());
                }
            }
        }
        Component announce = msg().component("event-announce", Messages.num("seconds", joinSeconds),
                        Messages.num("limit", scoreLimit))
                .clickEvent(ClickEvent.runCommand("/chess event join"))
                .hoverEvent(HoverEvent.showText(msg().component("event-announce-hover")));
        Bukkit.broadcast(announce);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(msg().component("event-title-join"), msg().component("event-subtitle-join")));
            Fx.sound(plugin, p.getLocation(), "event.raid.horn", 0.6f, 1f);
        }
        startTicker();
        return true;
    }

    public void join(Player p) {
        UUID id = p.getUniqueId();
        if (!p.hasPermission("chesssmp.player")) {
            msg().send(p, "no-permission");
            return;
        }
        switch (phase) {
            case IDLE -> msg().send(p, "event-none");
            case JOINING -> {
                if (queue.add(id)) {
                    msg().send(p, "event-joined", Messages.num("count", queue.size()));
                } else {
                    msg().send(p, "event-already-joined");
                }
            }
            case RUNNING -> {
                if (members.containsKey(id)) {
                    msg().send(p, "event-already-joined");
                } else if (!cfg().getBoolean("event.allow-late-join", true)) {
                    msg().send(p, "event-closed");
                } else {
                    lateJoin(p);
                }
            }
        }
    }

    private void lateJoin(Player p) {
        int w = activeCount(Side.WHITE);
        int b = activeCount(Side.BLACK);
        Side side;
        if (w != b) {
            side = w < b ? Side.WHITE : Side.BLACK;
        } else if (score[0] != score[1]) {
            side = score[0] < score[1] ? Side.WHITE : Side.BLACK; // help the team that is behind
        } else {
            side = new Random().nextBoolean() ? Side.WHITE : Side.BLACK;
        }
        enroll(p, side);
        msg().send(p, "event-late-joined", Messages.comp("side", sideLabel(side)));
    }

    private void enroll(Player p, Side side) {
        Member m = new Member(side);
        GameMode mode = p.getGameMode();
        m.restoreMode = mode == GameMode.SPECTATOR ? GameMode.SURVIVAL : mode;
        members.put(p.getUniqueId(), m);
        plugin.data().of(p).addEventPlayed();
        plugin.data().markDirty();
        plugin.nameTags().refresh(p);
        Location spawn = spawn(side);
        if (spawn != null) {
            p.teleport(spawn);
        }
    }

    public void leave(Player p) {
        UUID id = p.getUniqueId();
        if (queue.remove(id)) {
            msg().send(p, "event-left");
            return;
        }
        Member m = members.remove(id);
        if (m == null) {
            msg().send(p, "event-not-in");
            return;
        }
        restore(p, m);
        plugin.nameTags().refresh(p);
        msg().send(p, "event-left");
        checkForfeit();
    }

    public boolean stop(CommandSender by) {
        if (phase == Phase.IDLE) {
            msg().send(by, "event-none");
            return false;
        }
        abort("event-stopped");
        return true;
    }

    public void status(CommandSender to) {
        long now = System.currentTimeMillis();
        switch (phase) {
            case IDLE -> msg().send(to, "event-status-idle");
            case JOINING -> msg().send(to, "event-status-joining",
                    Messages.num("players", queue.size()), Messages.text("time", Messages.duration(joinEnds - now)));
            case RUNNING -> {
                msg().send(to, "event-status-running",
                        Messages.comp("side_white", sideLabel(Side.WHITE)), Messages.num("white_score", score[0]),
                        Messages.comp("side_black", sideLabel(Side.BLACK)), Messages.num("black_score", score[1]),
                        Messages.num("limit", scoreLimit), Messages.text("time", Messages.duration(battleEnds - now)),
                        Messages.num("white_players", activeCount(Side.WHITE)),
                        Messages.num("black_players", activeCount(Side.BLACK)));
                if (to instanceof Player p) {
                    Member m = members.get(p.getUniqueId());
                    if (m != null) {
                        msg().send(to, "event-status-you", Messages.comp("side", sideLabel(m.side)),
                                Messages.num("kills", m.kills), Messages.num("deaths", m.deaths));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ ticking

    private void startTicker() {
        cancelTicker();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void cancelTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    private void cancelLinger() {
        if (linger != null) {
            linger.cancel();
            linger = null;
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (phase == Phase.JOINING) {
            long left = (joinEnds - now + 999) / 1000;
            if (left <= 0) {
                beginBattle();
                return;
            }
            for (int mark : cfg().getIntegerList("event.countdown-marks")) {
                if (left <= mark && announced.add(mark)) {
                    Bukkit.broadcast(msg().component("event-countdown", Messages.num("seconds", left),
                            Messages.num("players", queue.size())));
                }
            }
        } else if (phase == Phase.RUNNING) {
            if (now >= battleEnds) {
                finishByTime();
                return;
            }
            expireDisconnected(now);
            if (phase != Phase.RUNNING) {
                return;
            }
            long left = (battleEnds - now + 999) / 1000;
            for (int mark : cfg().getIntegerList("event.time-warnings")) {
                if (left <= mark && announced.add(mark)) {
                    broadcastToMembers(msg().component("event-time-warning", Messages.text("time", Messages.duration(left * 1000))));
                }
            }
            updateSidebar(false);
        } else {
            cancelTicker();
        }
    }

    private void broadcastToMembers(Component c) {
        for (Player p : onlineMembers()) {
            p.sendMessage(c);
        }
    }

    private void expireDisconnected(long now) {
        long grace = cfg().getLong("event.disconnect-grace-seconds", 120) * 1000L;
        List<UUID> expired = new ArrayList<>();
        for (Map.Entry<UUID, Member> e : members.entrySet()) {
            Member m = e.getValue();
            if (m.disconnectedAt > 0 && now - m.disconnectedAt > grace) {
                expired.add(e.getKey());
            }
        }
        if (expired.isEmpty()) {
            return;
        }
        for (UUID id : expired) {
            members.remove(id);
            PlayerData d = plugin.data().find(id);
            broadcastToMembers(msg().component("event-disconnect-removed",
                    Messages.text("player", d == null ? "?" : d.name())));
        }
        checkForfeit();
    }

    // ------------------------------------------------------------------ battle

    private void beginBattle() {
        List<Player> players = new ArrayList<>();
        for (UUID id : queue) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && p.isOnline()) {
                players.add(p);
            }
        }
        int min = Math.max(2, cfg().getInt("event.min-players", 2));
        if (players.size() < min) {
            Bukkit.broadcast(msg().component("event-cancelled-min", Messages.num("min", min)));
            resetState();
            return;
        }
        List<TeamBalancer.Candidate> candidates = new ArrayList<>();
        for (Player p : players) {
            PieceType piece = plugin.data().of(p).piece();
            candidates.add(new TeamBalancer.Candidate(p.getUniqueId(), plugin.classes().rating(piece)));
        }
        TeamBalancer.Mode mode = "RANDOM".equalsIgnoreCase(cfg().getString("event.balance-mode", "STRENGTH"))
                ? TeamBalancer.Mode.RANDOM : TeamBalancer.Mode.STRENGTH;
        TeamBalancer.Result result = TeamBalancer.balance(candidates, mode, new Random());

        phase = Phase.RUNNING; // sideOf() works from here on
        scoreLimit = Math.max(1, cfg().getInt("event.score-limit", 15));
        battleEnds = System.currentTimeMillis() + Math.max(1, cfg().getLong("event.time-limit-minutes", 20)) * 60_000L;
        announced.clear();
        long totalSeconds = (battleEnds - System.currentTimeMillis()) / 1000;
        for (int mark : cfg().getIntegerList("event.time-warnings")) {
            if (mark >= totalSeconds) {
                announced.add(mark);
            }
        }
        queue.clear();
        for (UUID id : result.white()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                enroll(p, Side.WHITE);
            }
        }
        for (UUID id : result.black()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                enroll(p, Side.BLACK);
            }
        }
        for (Player p : onlineMembers()) {
            Member m = members.get(p.getUniqueId());
            p.showTitle(Title.title(msg().component("event-title-start"),
                    msg().component("event-subtitle-start", Messages.comp("side", sideLabel(m.side)))));
            Fx.sound(plugin, p.getLocation(), "entity.ender_dragon.growl", 0.6f, 1.2f);
            plugin.progression().checkObjectives(p.getUniqueId());
        }
        Bukkit.broadcast(msg().component("event-start", Messages.comp("side_white", sideLabel(Side.WHITE)),
                Messages.comp("side_black", sideLabel(Side.BLACK)), Messages.num("limit", scoreLimit),
                Messages.num("white_players", activeCount(Side.WHITE)), Messages.num("black_players", activeCount(Side.BLACK))));
        plugin.data().markDirty();
        updateSidebar(false);
    }

    /** Called by the death listener for every player death. */
    public void onDeath(Player victim, Player killer) {
        if (phase != Phase.RUNNING) {
            return;
        }
        Member vm = members.get(victim.getUniqueId());
        if (vm == null) {
            return;
        }
        vm.deaths++;
        Member km = killer == null || killer.equals(victim) ? null : members.get(killer.getUniqueId());
        if (km != null && km.side != vm.side) {
            boolean king = plugin.data().of(victim).piece() == PieceType.KING;
            int points = Math.max(0, cfg().getInt("event.points.kill", 1))
                    + (king ? Math.max(0, cfg().getInt("event.points.king-kill-bonus", 2)) : 0);
            score[km.side.ordinal()] += points;
            km.kills++;
            broadcastToMembers(msg().component("event-capture",
                    Messages.text("killer", killer.getName()), Messages.text("victim", victim.getName()),
                    Messages.comp("killer_piece", plugin.classes().label(plugin.classes().pieceOf(killer))),
                    Messages.comp("victim_piece", plugin.classes().label(plugin.classes().pieceOf(victim))),
                    Messages.comp("side", sideLabel(km.side)), Messages.num("points", points)));
            if (king) {
                Bukkit.broadcast(msg().component("event-king-captured", Messages.text("victim", victim.getName()),
                        Messages.comp("side", sideLabel(vm.side)), Messages.comp("killer_side", sideLabel(km.side))));
            }
            if (score[km.side.ordinal()] >= scoreLimit) {
                finish(km.side);
                return;
            }
        }
        if ("ELIMINATION".equalsIgnoreCase(cfg().getString("event.respawn.mode", "INSTANT"))) {
            vm.eliminated = true;
            checkForfeit();
        }
        updateSidebar(false);
    }

    public void handleRespawn(PlayerRespawnEvent event) {
        Player p = event.getPlayer();
        Member m = phase == Phase.RUNNING ? members.get(p.getUniqueId()) : null;
        if (m == null) {
            return;
        }
        Location spawn = spawn(m.side);
        if (spawn != null) {
            event.setRespawnLocation(spawn);
        }
        String mode = cfg().getString("event.respawn.mode", "INSTANT").toUpperCase(Locale.ROOT);
        UUID id = p.getUniqueId();
        if (m.eliminated) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(id);
                if (online != null && members.get(id) == m) {
                    spectate(online, m);
                    msg().send(online, "event-eliminated");
                }
            });
        } else if (mode.equals("DELAYED")) {
            int delay = Math.max(1, cfg().getInt("event.respawn.delay-seconds", 8));
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player online = Bukkit.getPlayer(id);
                if (online == null || members.get(id) != m || m.eliminated) {
                    return;
                }
                spectate(online, m);
                msg().send(online, "event-respawn-delay", Messages.num("seconds", delay));
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    Player again = Bukkit.getPlayer(id);
                    if (again != null && members.get(id) == m && !m.eliminated && m.spectating) {
                        restore(again, m);
                    }
                }, delay * 20L);
            });
        }
    }

    private void spectate(Player p, Member m) {
        m.spectating = true;
        p.setGameMode(GameMode.SPECTATOR);
    }

    private void restore(Player p, Member m) {
        if (m.spectating || p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(m.restoreMode);
        }
        m.spectating = false;
    }

    public void onQuit(Player p) {
        UUID id = p.getUniqueId();
        if (phase == Phase.JOINING) {
            queue.remove(id);
            return;
        }
        Member m = phase == Phase.RUNNING ? members.get(id) : null;
        if (m == null) {
            return;
        }
        if (cfg().getBoolean("event.remove-on-disconnect", false)) {
            members.remove(id);
            restore(p, m);
            checkForfeit();
            return;
        }
        m.disconnectedAt = System.currentTimeMillis();
        if (m.spectating && !m.eliminated) {
            restore(p, m); // never leave somebody stuck in spectator mode
        }
    }

    public void onJoin(Player p) {
        Member m = phase == Phase.RUNNING ? members.get(p.getUniqueId()) : null;
        if (m == null) {
            return;
        }
        m.disconnectedAt = 0;
        plugin.nameTags().refresh(p);
        if (m.eliminated) {
            Bukkit.getScheduler().runTask(plugin, () -> spectate(p, m));
        }
        msg().send(p, "event-rejoined", Messages.comp("side", sideLabel(m.side)));
    }

    // ------------------------------------------------------------------ finishing

    private void checkForfeit() {
        if (phase != Phase.RUNNING) {
            return;
        }
        int w = activeCount(Side.WHITE);
        int b = activeCount(Side.BLACK);
        if (w == 0 && b == 0) {
            abort("event-cancelled-empty");
        } else if (w == 0) {
            finish(Side.BLACK);
        } else if (b == 0) {
            finish(Side.WHITE);
        }
    }

    private void finishByTime() {
        if (score[0] > score[1]) {
            finish(Side.WHITE);
        } else if (score[1] > score[0]) {
            finish(Side.BLACK);
        } else {
            finish(null);
        }
    }

    private void finish(Side winner) {
        if (phase != Phase.RUNNING) {
            return;
        }
        List<Component> board = buildBoard(winner);
        ConfigurationSection rewards = cfg().getConfigurationSection("event.rewards");
        List<UUID> ids = new ArrayList<>(members.keySet());

        // MVP of the winning team = most kills in this event.
        UUID mvp = null;
        int best = -1;
        if (winner != null) {
            for (UUID id : ids) {
                Member m = members.get(id);
                if (m.side == winner && m.kills > best) {
                    best = m.kills;
                    mvp = id;
                }
            }
        }
        String maceMode = cfg().getString("grandmaster-mace.recipient", "MVP").toUpperCase(Locale.ROOT);

        for (UUID id : ids) {
            Member m = members.get(id);
            reward(id, rewards == null ? null : rewards.getConfigurationSection("participants"));
            if (winner != null && m.side == winner) {
                PlayerData d = plugin.data().find(id);
                if (d != null) {
                    d.addEventWin();
                }
                reward(id, rewards == null ? null : rewards.getConfigurationSection("winners"));
                boolean gets = maceMode.equals("ALL_WINNERS") || (maceMode.equals("MVP") && id.equals(mvp));
                if (gets) {
                    plugin.mace().award(id);
                }
            } else if (winner != null) {
                reward(id, rewards == null ? null : rewards.getConfigurationSection("losers"));
            }
        }

        Map<String, Integer> finalLines = sidebarLines();
        Component title = msg().component("event-sidebar-final");
        for (Player p : onlineMembers()) {
            Member m = members.get(p.getUniqueId());
            Component sub = winner == null ? msg().component("event-subtitle-draw")
                    : msg().component(m.side == winner ? "event-subtitle-win" : "event-subtitle-lose");
            p.showTitle(Title.title(winner == null ? msg().component("event-title-draw")
                    : msg().component("event-title-winner", Messages.comp("side", sideLabel(winner))), sub));
        }
        for (Component line : board) {
            Bukkit.broadcast(line);
        }

        phase = Phase.IDLE;
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                restore(p, members.get(id));
                plugin.nameTags().refresh(p);
            }
            plugin.progression().checkObjectives(id);
        }
        members.clear();
        queue.clear();
        cancelTicker();
        plugin.data().markDirty();
        showFinalSidebar(title, finalLines);
    }

    /** Ends the event without winner, rewards or statistics (admin stop, everybody left, shutdown). */
    private void abort(String messageKey) {
        if (messageKey != null) {
            Bukkit.broadcast(msg().component(messageKey));
        }
        List<UUID> ids = new ArrayList<>(members.keySet());
        Map<UUID, Member> old = new LinkedHashMap<>(members);
        resetState();
        for (UUID id : ids) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                restore(p, old.get(id));
                plugin.nameTags().refresh(p);
            }
        }
    }

    private void resetState() {
        phase = Phase.IDLE;
        members.clear();
        queue.clear();
        cancelTicker();
        cancelLinger();
        sidebar.hide();
    }

    /** Called when the plugin is disabled. */
    public void shutdown() {
        if (phase != Phase.IDLE) {
            for (UUID id : new ArrayList<>(members.keySet())) {
                Player p = Bukkit.getPlayer(id);
                Member m = members.get(id);
                if (p != null && m != null && (m.spectating || p.getGameMode() == GameMode.SPECTATOR)) {
                    p.setGameMode(m.restoreMode);
                }
            }
            phase = Phase.IDLE;
            members.clear();
            queue.clear();
        }
        cancelTicker();
        cancelLinger();
        sidebar.hide();
    }

    // ------------------------------------------------------------------ rewards, board, sidebar

    private void reward(UUID id, ConfigurationSection s) {
        if (s == null) {
            return;
        }
        long points = s.getLong("chess-points", 0);
        if (points != 0) {
            plugin.progression().addPoints(id, points);
        }
        Player p = Bukkit.getPlayer(id);
        if (p == null) {
            return; // items and commands are only handed out to online players
        }
        for (String spec : s.getStringList("items")) {
            Cost c = Cost.parse(spec);
            if (c != null) {
                for (ItemStack leftover : p.getInventory().addItem(new ItemStack(c.material(), c.amount())).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), leftover);
                }
            }
        }
        for (String command : s.getStringList("commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", p.getName()));
        }
    }

    private List<Component> buildBoard(Side winner) {
        List<Component> lines = new ArrayList<>();
        lines.add(msg().component("event-final-header"));
        if (winner == null) {
            lines.add(msg().component("event-final-draw", Messages.num("white_score", score[0]), Messages.num("black_score", score[1])));
        } else {
            lines.add(msg().component("event-final-winner", Messages.comp("side", sideLabel(winner)),
                    Messages.num("white_score", score[0]), Messages.num("black_score", score[1])));
        }
        int top = Math.max(1, cfg().getInt("event.final-board-players", 5));
        for (Side side : Side.values()) {
            lines.add(msg().component("event-final-side", Messages.comp("side", sideLabel(side)),
                    Messages.num("score", score[side.ordinal()])));
            List<Map.Entry<UUID, Member>> list = new ArrayList<>();
            for (Map.Entry<UUID, Member> e : members.entrySet()) {
                if (e.getValue().side == side) {
                    list.add(e);
                }
            }
            list.sort((a, b) -> Integer.compare(b.getValue().kills, a.getValue().kills));
            for (int i = 0; i < Math.min(top, list.size()); i++) {
                UUID id = list.get(i).getKey();
                Member m = list.get(i).getValue();
                PlayerData d = plugin.data().find(id);
                PieceType piece = d == null || d.piece() == null ? PieceType.PAWN : d.piece();
                lines.add(msg().component("event-final-player",
                        Messages.comp("piece", plugin.classes().label(piece)),
                        Messages.text("player", d == null ? "?" : d.name()),
                        Messages.num("kills", m.kills), Messages.num("deaths", m.deaths)));
            }
        }
        return lines;
    }

    private Map<String, Integer> sidebarLines() {
        Map<String, Integer> lines = new LinkedHashMap<>();
        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacySection();
        for (Side side : Side.values()) {
            lines.put(legacy.serialize(sideLabel(side)), score[side.ordinal()]);
        }
        lines.put(legacy.serialize(msg().component("event-sidebar-goal")), scoreLimit);
        return lines;
    }

    private void updateSidebar(boolean ignored) {
        if (!cfg().getBoolean("event.sidebar", true) || phase != Phase.RUNNING) {
            return;
        }
        long left = Math.max(0, battleEnds - System.currentTimeMillis());
        sidebar.update(msg().component("event-sidebar-title", Messages.text("time", Messages.duration(left))), sidebarLines());
    }

    private void showFinalSidebar(Component title, Map<String, Integer> lines) {
        if (!cfg().getBoolean("event.sidebar", true)) {
            return;
        }
        int seconds = cfg().getInt("event.final-sidebar-seconds", 15);
        if (seconds <= 0) {
            sidebar.hide();
            return;
        }
        sidebar.update(title, lines);
        cancelLinger();
        linger = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sidebar.hide();
            linger = null;
        }, seconds * 20L);
    }
}
