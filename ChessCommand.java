package com.chesssmp.commands;

import com.chesssmp.ChessSMP;
import com.chesssmp.Messages;
import com.chesssmp.classes.PieceType;
import com.chesssmp.classes.Stat;
import com.chesssmp.events.Side;
import com.chesssmp.gui.PieceSelectMenu;
import com.chesssmp.items.CustomItems;
import com.chesssmp.statistics.Rank;
import com.chesssmp.storage.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** /chess and all of its sub commands. */
public final class ChessCommand implements CommandExecutor, TabCompleter {

    private static final List<String> GIVE_ITEMS = List.of("trader", "ability_wand", "grandmaster_mace",
            "chess_grandmaster", "remnant", "chess_clock");

    private final ChessSMP plugin;

    public ChessCommand(ChessSMP plugin) {
        this.plugin = plugin;
    }

    private Messages msg() {
        return plugin.messages();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "choose" -> choose(sender, args);
            case "ability", "a" -> ability(sender, args);
            case "trader" -> trader(sender, args);
            case "wand" -> wand(sender);
            case "top" -> top(sender, args);
            case "stats" -> stats(sender, args);
            case "info" -> {
                if (sender instanceof Player p) {
                    p.performCommand("chessinfo");
                } else {
                    msg().send(sender, "player-only");
                }
            }
            case "event" -> event(sender, args);
            case "trust" -> trust(sender, args, true);
            case "untrust" -> trust(sender, args, false);
            case "give" -> give(sender, args);
            case "announce" -> announce(sender, args);
            case "meteor" -> meteor(sender, args);
            case "reload" -> {
                if (!sender.hasPermission("chesssmp.admin")) {
                    msg().send(sender, "no-permission");
                } else {
                    plugin.reloadAll();
                    msg().send(sender, "reload-done");
                }
            }
            case "admin" -> admin(sender, args);
            default -> msg().send(sender, "unknown-subcommand");
        }
        return true;
    }

    // ------------------------------------------------------------------ helpers

    private Player requirePlayer(CommandSender sender) {
        if (sender instanceof Player p) {
            if (!p.hasPermission("chesssmp.player")) {
                msg().send(sender, "no-permission");
                return null;
            }
            return p;
        }
        msg().send(sender, "player-only");
        return null;
    }

    private boolean requirePerm(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        msg().send(sender, "no-permission");
        return false;
    }

    private UUID resolveId(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online.getUniqueId();
        }
        PlayerData stored = plugin.data().findByName(name);
        return stored == null ? null : stored.uuid();
    }

    private void help(CommandSender sender) {
        sender.sendMessage(msg().component("help-header"));
        for (String line : plugin.getConfig().getStringList("messages.help-lines")) {
            sender.sendMessage(msg().mm(line));
        }
        if (sender.hasPermission("chesssmp.admin")) {
            for (String line : plugin.getConfig().getStringList("messages.help-admin-lines")) {
                sender.sendMessage(msg().mm(line));
            }
        }
    }

    // ------------------------------------------------------------------ player commands

    private void choose(CommandSender sender, String[] args) {
        Player p = requirePlayer(sender);
        if (p == null) {
            return;
        }
        if (args.length < 2) {
            String reason = plugin.classes().blockReason(p, false);
            if (reason != null) {
                msg().send(p, reason);
                return;
            }
            new PieceSelectMenu(plugin, p, msg().component("gui-choose-title"), List.of(msg().component("gui-click-to-select")),
                    (player, piece) -> {
                        player.closeInventory();
                        plugin.classes().choose(player, piece);
                    }, null).open();
            return;
        }
        PieceType piece = PieceType.parse(args[1]);
        if (piece == null) {
            msg().send(p, "choose-invalid");
            return;
        }
        plugin.classes().choose(p, piece);
    }

    private void ability(CommandSender sender, String[] args) {
        Player p = requirePlayer(sender);
        if (p == null) {
            return;
        }
        int slot;
        try {
            slot = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        } catch (NumberFormatException ex) {
            slot = 0;
        }
        if (slot < 1 || slot > 3) {
            msg().send(p, "usage", Messages.text("usage", "/chess ability <1-3>"));
            return;
        }
        plugin.abilities().use(p, slot);
    }

    private void trader(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("give")) {
            if (!requirePerm(sender, "chesssmp.admin")) {
                return;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                msg().send(sender, "player-not-found", Messages.text("player", args[2]));
                return;
            }
            if (plugin.trader().give(target) && !target.equals(sender)) {
                msg().send(sender, "trader-given-other", Messages.text("player", target.getName()));
            }
            return;
        }
        Player p = requirePlayer(sender);
        if (p == null || !requirePerm(sender, "chesssmp.trader")) {
            return;
        }
        plugin.trader().give(p);
    }

    private void wand(CommandSender sender) {
        Player p = requirePlayer(sender);
        if (p == null) {
            return;
        }
        if (!plugin.abilityItems().enabled()) {
            msg().send(p, "wand-disabled");
        } else if (plugin.abilityItems().give(p)) {
            msg().send(p, "wand-given");
        } else {
            msg().send(p, "wand-already");
        }
    }

    private void top(CommandSender sender, String[] args) {
        int size = 10;
        if (args.length > 1) {
            try {
                size = Math.max(1, Math.min(25, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                size = 10;
            }
        }
        List<PlayerData> list = new ArrayList<>(plugin.data().all());
        list.sort(Comparator.comparingLong(PlayerData::points).reversed());
        sender.sendMessage(msg().component("top-header", Messages.num("count", size)));
        int rank = 1;
        for (PlayerData d : list) {
            if (rank > size) {
                break;
            }
            if (d.points() <= 0 && rank > 1) {
                break;
            }
            PieceType piece = d.piece() == null ? PieceType.PAWN : d.piece();
            sender.sendMessage(msg().component("top-line", Messages.num("rank", rank),
                    Messages.comp("piece", plugin.classes().label(piece)), Messages.text("player", d.name()),
                    Messages.num("points", d.points()),
                    Messages.text("title", plugin.progression().title(plugin.progression().rank(d)))));
            rank++;
        }
        if (rank == 1) {
            sender.sendMessage(msg().component("top-empty"));
        }
    }

    private void stats(CommandSender sender, String[] args) {
        PlayerData d;
        if (args.length > 1) {
            if (!sender.hasPermission("chesssmp.admin") && !plugin.getConfig().getBoolean("stats.public-lookup", true)) {
                msg().send(sender, "no-permission");
                return;
            }
            UUID id = resolveId(args[1]);
            d = id == null ? null : plugin.data().find(id);
            if (d == null) {
                msg().send(sender, "player-not-found", Messages.text("player", args[1]));
                return;
            }
        } else {
            Player p = requirePlayer(sender);
            if (p == null) {
                return;
            }
            d = plugin.data().of(p);
        }
        PieceType piece = d.piece() == null ? PieceType.PAWN : d.piece();
        Rank rank = plugin.progression().rank(d);
        sender.sendMessage(msg().component("stats-header", Messages.text("player", d.name()), Messages.comp("piece", plugin.classes().label(piece))));
        sender.sendMessage(msg().component("stats-line", Messages.text("label", "Rank"),
                Messages.text("value", plugin.progression().title(rank) + " (" + rank.display() + ")")));
        String[][] rows = {
                {"Chess Points", String.valueOf(d.points())},
                {"Captures / Losses", d.kills() + " / " + d.deaths() + String.format(Locale.ROOT, " (K/D %.2f)", d.kdr())},
                {"Killstreak (best)", d.streak() + " (" + d.bestStreak() + ")"},
                {"Abilities used", String.valueOf(d.abilitiesUsed())},
                {"Events won / played", d.eventWins() + " / " + d.eventsPlayed()},
                {"Piece changes", String.valueOf(d.pieceChanges())},
                {"Objectives", String.valueOf(d.objectiveCount())},
                {"Grandmaster", d.grandmaster() ? "yes " + plugin.grandmaster().pieceString(d) : "no"}};
        for (String[] row : rows) {
            sender.sendMessage(msg().component("stats-line", Messages.text("label", row[0]), Messages.text("value", row[1])));
        }
    }

    private void event(CommandSender sender, String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "start" -> {
                if (!requirePerm(sender, "chesssmp.event")) {
                    return;
                }
                Integer seconds = null;
                if (args.length > 2) {
                    try {
                        seconds = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {
                        seconds = null;
                    }
                }
                plugin.events().start(sender, seconds);
            }
            case "stop" -> {
                if (requirePerm(sender, "chesssmp.event")) {
                    plugin.events().stop(sender);
                }
            }
            case "status" -> plugin.events().status(sender);
            case "join" -> {
                Player p = requirePlayer(sender);
                if (p != null) {
                    plugin.events().join(p);
                }
            }
            case "leave" -> {
                Player p = requirePlayer(sender);
                if (p != null) {
                    plugin.events().leave(p);
                }
            }
            case "set" -> eventSet(sender, args);
            case "setspawn" -> {
                if (!requirePerm(sender, "chesssmp.event")) {
                    return;
                }
                Side side = args.length > 2 ? Side.parse(args[2]) : null;
                if (!(sender instanceof Player p) || side == null) {
                    msg().send(sender, "usage", Messages.text("usage", "/chess event setspawn <white|black>"));
                    return;
                }
                plugin.events().setSpawn(p, side);
            }
            default -> msg().send(sender, "usage", Messages.text("usage", "/chess event <start|stop|status|join|leave|set|setspawn>"));
        }
    }

    private void eventSet(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "chesssmp.event")) {
            return;
        }
        if (args.length < 4) {
            msg().send(sender, "usage", Messages.text("usage", "/chess event set <score|time|join|minplayers> <value>"));
            return;
        }
        String path = switch (args[2].toLowerCase(Locale.ROOT)) {
            case "score", "score-limit" -> "event.score-limit";
            case "time", "time-limit" -> "event.time-limit-minutes";
            case "join", "join-period" -> "event.join-period-seconds";
            case "minplayers", "min-players" -> "event.min-players";
            default -> null;
        };
        int value;
        try {
            value = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            value = -1;
        }
        if (path == null || value < 1) {
            msg().send(sender, "usage", Messages.text("usage", "/chess event set <score|time|join|minplayers> <positive number>"));
            return;
        }
        plugin.getConfig().set(path, value);
        plugin.saveConfig();
        msg().send(sender, "event-setting-changed", Messages.text("key", args[2]), Messages.num("value", value));
    }

    // ------------------------------------------------------------------ trust

    private void trust(CommandSender sender, String[] args, boolean add) {
        Player p = requirePlayer(sender);
        if (p == null) {
            return;
        }
        PlayerData d = plugin.data().of(p);
        if (add && args.length >= 2 && args[1].equalsIgnoreCase("list")) {
            if (d.trusted().isEmpty()) {
                msg().send(p, "trust-list-empty");
                return;
            }
            StringBuilder names = new StringBuilder();
            for (UUID id : d.trusted()) {
                PlayerData t = plugin.data().find(id);
                if (names.length() > 0) {
                    names.append(", ");
                }
                names.append(t == null ? id.toString().substring(0, 8) : t.name());
            }
            msg().send(p, "trust-list", Messages.text("players", names.toString()));
            return;
        }
        if (args.length < 2) {
            msg().send(p, "usage", Messages.text("usage", add ? "/chess trust <player>" : "/chess untrust <player>"));
            return;
        }
        UUID target = resolveId(args[1]);
        if (target == null) {
            msg().send(p, "player-not-found", Messages.text("player", args[1]));
            return;
        }
        if (target.equals(p.getUniqueId())) {
            msg().send(p, "trust-self");
            return;
        }
        PlayerData td = plugin.data().find(target);
        String name = td == null ? args[1] : td.name();
        if (add) {
            if (d.trusted().add(target)) {
                plugin.data().markDirty();
                msg().send(p, "trust-added", Messages.text("player", name));
                Player online = Bukkit.getPlayer(target);
                if (online != null) {
                    msg().send(online, "trust-notice", Messages.text("player", p.getName()));
                }
            } else {
                msg().send(p, "trust-already", Messages.text("player", name));
            }
        } else if (d.trusted().remove(target)) {
            plugin.data().markDirty();
            msg().send(p, "trust-removed", Messages.text("player", name));
        } else {
            msg().send(p, "trust-not-trusted", Messages.text("player", name));
        }
    }

    // ------------------------------------------------------------------ give

    private void give(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "chesssmp.admin")) {
            return;
        }
        if (args.length < 3) {
            msg().send(sender, "usage", Messages.text("usage", "/chess give <player> <" + String.join("|", GIVE_ITEMS) + "> [amount]"));
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            msg().send(sender, "player-not-found", Messages.text("player", args[1]));
            return;
        }
        int amount = 1;
        if (args.length > 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ignored) {
                amount = 1;
            }
        }
        String id = args[2].toLowerCase(Locale.ROOT).replace('-', '_');
        ItemStack stack = switch (id) {
            case "trader", "piece_trader" -> plugin.trader().createItem(target);
            case "ability_wand", "wand", "scepter" -> plugin.abilityItems().create();
            case "grandmaster_mace", "mace" -> plugin.mace().create();
            case "chess_grandmaster", "grandmaster", "title" -> plugin.items().create(CustomItems.Kind.TITLE, 1);
            case "remnant", "remnants" -> plugin.items().create(CustomItems.Kind.REMNANT, amount);
            case "chess_clock", "clock" -> plugin.items().create(CustomItems.Kind.CLOCK, 1);
            default -> null;
        };
        if (stack == null) {
            msg().send(sender, "give-unknown", Messages.text("items", String.join(", ", GIVE_ITEMS)));
            return;
        }
        for (ItemStack leftover : target.getInventory().addItem(stack).values()) {
            target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
        msg().send(sender, "give-done", Messages.text("item", id), Messages.text("player", target.getName()));
        if (!target.equals(sender)) {
            msg().send(target, "give-received", Messages.text("item", id));
        }
    }

    // ------------------------------------------------------------------ announce

    private TextColor parseColor(String text) {
        if (text.startsWith("#")) {
            return TextColor.fromHexString(text);
        }
        return NamedTextColor.NAMES.value(text.toLowerCase(Locale.ROOT));
    }

    /**
     * /chess announce [-t color] [-s color] [-d seconds] [-chat] Title text | Subtitle text
     * Text supports MiniMessage (&lt;red&gt;, &lt;gradient:red:blue&gt; ...).
     */
    private void announce(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "chesssmp.announce")) {
            return;
        }
        TextColor titleColor = parseColor(plugin.getConfig().getString("announce.default-title-color", "gold"));
        TextColor subColor = parseColor(plugin.getConfig().getString("announce.default-subtitle-color", "yellow"));
        if (titleColor == null) {
            titleColor = NamedTextColor.GOLD;
        }
        if (subColor == null) {
            subColor = NamedTextColor.YELLOW;
        }
        double seconds = plugin.getConfig().getDouble("announce.stay-seconds", 5);
        boolean chat = false;
        int i = 1;
        while (i < args.length && args[i].startsWith("-")) {
            String flag = args[i].toLowerCase(Locale.ROOT);
            if (flag.equals("-chat")) {
                chat = true;
                i++;
                continue;
            }
            if (i + 1 >= args.length) {
                break;
            }
            String value = args[i + 1];
            if (flag.equals("-t") || flag.equals("-s")) {
                TextColor color = parseColor(value);
                if (color == null) {
                    msg().send(sender, "announce-bad-color", Messages.text("color", value));
                    return;
                }
                if (flag.equals("-t")) {
                    titleColor = color;
                } else {
                    subColor = color;
                }
            } else if (flag.equals("-d")) {
                try {
                    seconds = Math.max(1, Math.min(60, Double.parseDouble(value)));
                } catch (NumberFormatException ex) {
                    msg().send(sender, "usage", Messages.text("usage", "-d <seconds>"));
                    return;
                }
            } else {
                break;
            }
            i += 2;
        }
        if (i >= args.length) {
            msg().send(sender, "usage", Messages.text("usage",
                    "/chess announce [-t titleColor] [-s subtitleColor] [-d seconds] [-chat] <title> | <subtitle>"));
            return;
        }
        String text = String.join(" ", java.util.Arrays.copyOfRange(args, i, args.length));
        String[] parts = text.split("\\|", 2);
        Component title = msg().mm(parts[0].trim()).colorIfAbsent(titleColor);
        Component subtitle = parts.length > 1 ? msg().mm(parts[1].trim()).colorIfAbsent(subColor) : Component.empty();
        Title.Times times = Title.Times.times(
                Duration.ofMillis(plugin.getConfig().getLong("announce.fade-in-ms", 500)),
                Duration.ofMillis((long) (seconds * 1000)),
                Duration.ofMillis(plugin.getConfig().getLong("announce.fade-out-ms", 1000)));
        String sound = plugin.getConfig().getString("announce.sound", "block.note_block.chime");
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(Title.title(title, subtitle, times));
            com.chesssmp.Fx.sound(plugin, p.getLocation(), sound, 1f, 1f);
            if (chat) {
                p.sendMessage(title);
                if (parts.length > 1) {
                    p.sendMessage(subtitle);
                }
            }
        }
        msg().send(sender, "announce-sent", Messages.num("count", Bukkit.getOnlinePlayers().size()));
    }

    // ------------------------------------------------------------------ meteor

    private void meteor(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "chesssmp.admin")) {
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "spawn" -> {
                if (args.length > 2 && args[2].equalsIgnoreCase("random")) {
                    plugin.meteor().spawnRandom(sender);
                } else if (sender instanceof Player p) {
                    Location l = p.getLocation();
                    plugin.meteor().spawn(sender, l.getWorld(), l.getBlockX(), l.getBlockZ());
                } else {
                    msg().send(sender, "player-only");
                }
            }
            case "villager" -> {
                if (sender instanceof Player p) {
                    plugin.meteor().spawnVillager(p.getLocation());
                    msg().send(p, "meteor-villager");
                } else {
                    msg().send(sender, "player-only");
                }
            }
            default -> msg().send(sender, "usage", Messages.text("usage", "/chess meteor <spawn [random]|villager>"));
        }
    }

    // ------------------------------------------------------------------ admin

    private void admin(CommandSender sender, String[] args) {
        if (!requirePerm(sender, "chesssmp.admin")) {
            return;
        }
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (action) {
            case "setpiece" -> {
                Player target = args.length > 3 ? Bukkit.getPlayerExact(args[2]) : null;
                PieceType piece = args.length > 3 ? PieceType.parse(args[3]) : null;
                if (target == null || piece == null) {
                    msg().send(sender, "usage", Messages.text("usage", "/chess admin setpiece <player> <piece>"));
                    return;
                }
                plugin.data().of(target).setChosen(true);
                plugin.classes().changePiece(target, piece);
                msg().send(sender, "admin-done");
            }
            case "reset" -> {
                UUID id = args.length > 2 ? resolveId(args[2]) : null;
                if (id == null) {
                    msg().send(sender, "player-not-found", Messages.text("player", args.length > 2 ? args[2] : "?"));
                    return;
                }
                plugin.data().reset(id);
                Player online = Bukkit.getPlayer(id);
                if (online != null) {
                    plugin.abilities().clear(online);
                    plugin.data().of(online);
                    plugin.grandmaster().sync(online);
                    plugin.nameTags().refresh(online);
                }
                msg().send(sender, "admin-done");
            }
            case "setpoints", "addpoints" -> {
                UUID id = args.length > 3 ? resolveId(args[2]) : null;
                long value;
                try {
                    value = args.length > 3 ? Long.parseLong(args[3]) : 0;
                } catch (NumberFormatException ex) {
                    id = null;
                    value = 0;
                }
                PlayerData d = id == null ? null : plugin.data().find(id);
                if (d == null) {
                    msg().send(sender, "usage", Messages.text("usage", "/chess admin " + action + " <player> <number>"));
                    return;
                }
                if (action.equals("setpoints")) {
                    d.setPoints(value);
                    plugin.data().markDirty();
                } else {
                    plugin.progression().addPoints(id, value);
                }
                msg().send(sender, "admin-done");
            }
            case "clearcooldowns" -> {
                Player target = args.length > 2 ? Bukkit.getPlayerExact(args[2]) : null;
                if (target == null) {
                    msg().send(sender, "usage", Messages.text("usage", "/chess admin clearcooldowns <player>"));
                    return;
                }
                plugin.data().of(target).clearCooldowns();
                plugin.data().markDirty();
                msg().send(sender, "admin-done");
            }
            case "setcooldown", "setduration" -> {
                PieceType piece = args.length > 4 ? PieceType.parse(args[2]) : null;
                int slot;
                double value;
                try {
                    slot = args.length > 4 ? Integer.parseInt(args[3]) : 0;
                    value = args.length > 4 ? Double.parseDouble(args[4]) : -1;
                } catch (NumberFormatException ex) {
                    slot = 0;
                    value = -1;
                }
                if (piece == null || slot < 1 || slot > 3 || value < 0) {
                    msg().send(sender, "usage", Messages.text("usage", "/chess admin " + action + " <piece> <1-3> <seconds>"));
                    return;
                }
                String key = action.equals("setcooldown") ? "cooldown" : "duration";
                plugin.getConfig().set("classes." + piece.key() + ".abilities." + slot + "." + key, value);
                plugin.saveConfig();
                msg().send(sender, "admin-done");
            }
            case "setstat" -> {
                PieceType piece = args.length > 4 ? PieceType.parse(args[2]) : null;
                Stat stat = args.length > 4 ? Stat.parse(args[3]) : null;
                int value;
                try {
                    value = args.length > 4 ? Integer.parseInt(args[4]) : 0;
                } catch (NumberFormatException ex) {
                    value = 0;
                }
                if (piece == null || stat == null || value < 1 || value > 10) {
                    msg().send(sender, "usage", Messages.text("usage", "/chess admin setstat <piece> <offense|defense|mobility|support> <1-10>"));
                    return;
                }
                plugin.getConfig().set("classes." + piece.key() + ".stats." + stat.key(), value);
                plugin.saveConfig();
                msg().send(sender, "admin-done");
            }
            default -> msg().send(sender, "usage", Messages.text("usage",
                    "/chess admin <setpiece|reset|setpoints|addpoints|clearcooldowns|setcooldown|setduration|setstat>"));
        }
    }

    // ------------------------------------------------------------------ tab completion

    private static void filter(List<String> list, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        list.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(p));
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            names.add(p.getName());
        }
        return names;
    }

    private List<String> pieces() {
        List<String> out = new ArrayList<>();
        for (PieceType t : PieceType.values()) {
            out.add(t.key());
        }
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("help", "choose", "ability", "trader", "wand", "top", "stats", "info", "event", "trust", "untrust"));
            if (sender.hasPermission("chesssmp.admin")) {
                out.addAll(List.of("give", "announce", "meteor", "reload", "admin"));
            } else if (sender.hasPermission("chesssmp.announce")) {
                out.add("announce");
            }
            filter(out, args[0]);
            return out;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "choose" -> out.addAll(pieces());
                case "ability" -> out.addAll(List.of("1", "2", "3"));
                case "trader" -> out.add("give");
                case "stats", "trust", "untrust", "give" -> out.addAll(onlineNames());
                case "event" -> out.addAll(List.of("start", "stop", "status", "join", "leave", "set", "setspawn"));
                case "meteor" -> out.addAll(List.of("spawn", "villager"));
                case "admin" -> out.addAll(List.of("setpiece", "reset", "setpoints", "addpoints", "clearcooldowns",
                        "setcooldown", "setduration", "setstat"));
                case "announce" -> out.addAll(List.of("-t", "-s", "-d", "-chat"));
                default -> { }
            }
            if (sub.equals("trust")) {
                out.add("list");
            }
            filter(out, args[1]);
            return out;
        }
        if (args.length == 3) {
            switch (sub) {
                case "give" -> out.addAll(GIVE_ITEMS);
                case "trader" -> out.addAll(onlineNames());
                case "event" -> {
                    if (args[1].equalsIgnoreCase("set")) {
                        out.addAll(List.of("score", "time", "join", "minplayers"));
                    } else if (args[1].equalsIgnoreCase("setspawn")) {
                        out.addAll(List.of("white", "black"));
                    }
                }
                case "meteor" -> out.add("random");
                case "announce" -> out.addAll(NamedTextColor.NAMES.keys());
                case "admin" -> {
                    switch (args[1].toLowerCase(Locale.ROOT)) {
                        case "setpiece", "reset", "setpoints", "addpoints", "clearcooldowns" -> out.addAll(onlineNames());
                        case "setcooldown", "setduration", "setstat" -> out.addAll(pieces());
                        default -> { }
                    }
                }
                default -> { }
            }
            filter(out, args[2]);
            return out;
        }
        if (args.length == 4 && sub.equals("admin")) {
            switch (args[1].toLowerCase(Locale.ROOT)) {
                case "setpiece" -> out.addAll(pieces());
                case "setstat" -> {
                    for (Stat s : Stat.values()) {
                        out.add(s.key());
                    }
                }
                case "setcooldown", "setduration" -> out.addAll(List.of("1", "2", "3"));
                default -> { }
            }
            filter(out, args[3]);
        }
        return out;
    }
}
