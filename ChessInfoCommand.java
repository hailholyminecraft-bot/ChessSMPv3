package com.chesssmp.commands;

import com.chesssmp.ChessSMP;
import com.chesssmp.Messages;
import com.chesssmp.classes.PieceType;
import com.chesssmp.classes.Stat;
import com.chesssmp.events.Side;
import com.chesssmp.gui.InfoMenu;
import com.chesssmp.statistics.Rank;
import com.chesssmp.storage.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /chessinfo [piece|menu] */
public final class ChessInfoCommand implements CommandExecutor, TabCompleter {

    private final ChessSMP plugin;

    public ChessInfoCommand(ChessSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("chesssmp.player")) {
            plugin.messages().send(player, "no-permission");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("menu")) {
            new InfoMenu(plugin, player).open();
            return true;
        }
        PieceType own = plugin.classes().pieceOf(player);
        PieceType shown = own;
        if (args.length > 0) {
            shown = PieceType.parse(args[0]);
            if (shown == null) {
                plugin.messages().send(player, "choose-invalid");
                return true;
            }
        }
        show(player, shown, shown == own);
        return true;
    }

    private Component line(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    private void show(Player p, PieceType piece, boolean own) {
        Component bar = Component.text("\u2501\u2501\u2501\u2501\u2501 ", NamedTextColor.DARK_GRAY);
        p.sendMessage(bar.append(plugin.classes().label(piece).decorate(TextDecoration.BOLD))
                .append(Component.text(" \u2501\u2501\u2501\u2501\u2501", NamedTextColor.DARK_GRAY)));
        if (own) {
            PlayerData d = plugin.data().of(p);
            Side side = plugin.events().sideOf(p.getUniqueId());
            Rank rank = plugin.progression().rank(d);
            p.sendMessage(line("Piece", plugin.classes().emoji(piece) + " " + plugin.classes().displayName(piece)));
            p.sendMessage(line("Team", side == null ? "None" : plugin.events().sideName(side)));
            long next = plugin.progression().nextThreshold(rank);
            p.sendMessage(line("Chess Points", d.points() + (next < 0 ? "" : " / " + next)));
            p.sendMessage(line("Rank", plugin.progression().title(rank) + " (" + rank.display() + ")"));
            if (d.grandmaster()) {
                p.sendMessage(line("Grandmaster", plugin.grandmaster().pieces(d).size() + "/6 pieces " + plugin.grandmaster().pieceString(d)));
            }
        }
        p.sendMessage(line("Role", plugin.classes().role(piece)));
        p.sendMessage(line("Playstyle", plugin.classes().playstyle(piece)));
        p.sendMessage(Component.text("Statistics", NamedTextColor.GOLD));
        for (Stat stat : Stat.values()) {
            int v = plugin.classes().stat(piece, stat);
            p.sendMessage(Component.text("  " + String.format("%-9s", stat.display()), NamedTextColor.GRAY)
                    .append(Component.text("\u2588".repeat(v), NamedTextColor.GREEN))
                    .append(Component.text("\u2591".repeat(10 - v), NamedTextColor.DARK_GRAY))
                    .append(Component.text(" " + v, NamedTextColor.WHITE)));
        }
        p.sendMessage(Component.text("Abilities", NamedTextColor.GOLD));
        for (int slot = 1; slot <= 3; slot++) {
            ConfigurationSection a = plugin.classes().ability(piece, slot);
            if (a == null) {
                continue;
            }
            double duration = a.getDouble("duration", 0);
            double cooldown = Math.max(a.getDouble("cooldown", 30), duration);
            Component title = Component.text("Ability " + slot + ": " + a.getString("name", "?"), plugin.classes().color(piece), TextDecoration.BOLD);
            if (own) {
                title = title.append(Component.text("  [Use]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/chess ability " + slot))
                        .hoverEvent(HoverEvent.showText(Component.text("Click to use this ability"))));
            }
            p.sendMessage(title);
            p.sendMessage(line("  Description", a.getString("description", "")));
            p.sendMessage(line("  Activation", "/chess ability " + slot + (plugin.abilityItems().enabled() ? "  or the Chess Scepter (/chess give <you> ability_wand)" : "")));
            p.sendMessage(line("  Cooldown", fmt(cooldown) + " seconds"));
            p.sendMessage(line("  Duration", duration > 0 ? fmt(duration) + " seconds" : "Instant"));
            if (own) {
                long remain = plugin.abilities().remainingMillis(p.getUniqueId(), piece, slot);
                p.sendMessage(Component.text("  Status: ", NamedTextColor.GRAY).append(remain > 0
                        ? Component.text("cooldown " + Messages.duration(remain), NamedTextColor.RED)
                        : Component.text("ready", NamedTextColor.GREEN)));
            }
        }
        List<Component> buttons = new ArrayList<>();
        buttons.add(Component.text("[Open menu]", NamedTextColor.AQUA).clickEvent(ClickEvent.runCommand("/chessinfo menu"))
                .hoverEvent(HoverEvent.showText(Component.text("Clickable inventory version"))));
        buttons.add(Component.text("[Change piece]", NamedTextColor.YELLOW).clickEvent(ClickEvent.runCommand("/chess choose"))
                .hoverEvent(HoverEvent.showText(Component.text("Choose another piece"))));
        Component footer = Component.empty();
        for (Component b : buttons) {
            footer = footer.append(b).append(Component.text("  "));
        }
        p.sendMessage(footer);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.add("menu");
            for (PieceType t : PieceType.values()) {
                out.add(t.key());
            }
            out.removeIf(s -> !s.startsWith(args[0].toLowerCase(Locale.ROOT)));
        }
        return out;
    }
}
