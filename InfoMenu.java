package com.chesssmp.gui;

import com.chesssmp.ChessSMP;
import com.chesssmp.Messages;
import com.chesssmp.classes.PieceType;
import com.chesssmp.classes.Stat;
import com.chesssmp.events.Side;
import com.chesssmp.statistics.Rank;
import com.chesssmp.storage.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Clickable version of /chessinfo. Click an ability to use it. */
public final class InfoMenu extends Menu {

    private static final int[] ABILITY_SLOTS = {20, 22, 24};

    public InfoMenu(ChessSMP plugin, Player viewer) {
        super(plugin, viewer, 4, Component.text("Chess Info"));
        PlayerData d = plugin.data().of(viewer);
        PieceType piece = d.piece();
        Rank rank = plugin.progression().rank(d);
        Side side = plugin.events().sideOf(viewer.getUniqueId());

        List<Component> profile = new ArrayList<>();
        profile.add(line("Role", plugin.classes().role(piece)));
        profile.add(line("Playstyle", plugin.classes().playstyle(piece)));
        profile.add(Component.empty());
        profile.add(line("Team", side == null ? "None" : plugin.events().sideName(side)));
        profile.add(line("Rank", plugin.progression().title(rank) + " (" + rank.display() + ")"));
        profile.add(line("Chess Points", String.valueOf(d.points())));
        inventory.setItem(4, Items.glow(Items.make(plugin.classes().icon(piece), plugin.classes().label(piece), profile)));

        List<Component> stats = new ArrayList<>();
        for (Stat stat : Stat.values()) {
            int v = plugin.classes().stat(piece, stat);
            stats.add(Component.text(stat.display() + " ", NamedTextColor.GRAY)
                    .append(Component.text("\u2588".repeat(v), NamedTextColor.GREEN))
                    .append(Component.text("\u2591".repeat(10 - v), NamedTextColor.DARK_GRAY))
                    .append(Component.text(" " + v, NamedTextColor.WHITE)));
        }
        inventory.setItem(11, Items.make(Material.BOOK, Component.text("Statistics", NamedTextColor.GOLD), stats));

        List<Component> record = new ArrayList<>();
        record.add(line("Captures (kills)", String.valueOf(d.kills())));
        record.add(line("Losses (deaths)", String.valueOf(d.deaths())));
        record.add(line("Current streak", String.valueOf(d.streak())));
        record.add(line("Best streak", String.valueOf(d.bestStreak())));
        record.add(line("Events won", d.eventWins() + " / " + d.eventsPlayed()));
        inventory.setItem(15, Items.make(Material.GOLD_INGOT, Component.text("Record", NamedTextColor.GOLD), record));

        for (int slot = 1; slot <= 3; slot++) {
            ConfigurationSection a = plugin.classes().ability(piece, slot);
            if (a == null) {
                continue;
            }
            Material icon = Material.matchMaterial(a.getString("icon", "PAPER"));
            long remain = plugin.abilities().remainingMillis(viewer.getUniqueId(), piece, slot);
            double duration = a.getDouble("duration", 0);
            double cooldown = Math.max(a.getDouble("cooldown", 30), duration);
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(a.getString("description", ""), NamedTextColor.GRAY));
            lore.add(Component.empty());
            lore.add(line("Activation", "/chess ability " + slot));
            lore.add(line("Cooldown", fmt(cooldown) + "s"));
            lore.add(line("Duration", duration > 0 ? fmt(duration) + "s" : "Instant"));
            lore.add(remain > 0
                    ? Component.text("On cooldown: " + Messages.duration(remain), NamedTextColor.RED)
                    : Component.text("Ready - click to use", NamedTextColor.GREEN));
            inventory.setItem(ABILITY_SLOTS[slot - 1], Items.make(icon == null ? Material.PAPER : icon,
                    Component.text("Ability " + slot + ": " + a.getString("name", "?"), plugin.classes().color(piece)), lore));
        }

        inventory.setItem(29, Items.make(Material.ENDER_EYE, Component.text("Change piece", NamedTextColor.AQUA),
                List.of(Component.text("Click to choose another piece", NamedTextColor.GRAY))));
        inventory.setItem(31, Items.make(Material.BARRIER, Component.text("Close", NamedTextColor.RED), List.of()));
    }

    private static Component line(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GRAY).append(Component.text(value, NamedTextColor.WHITE));
    }

    private static String fmt(double v) {
        return v == Math.floor(v) ? String.valueOf((long) v) : String.format(Locale.ROOT, "%.1f", v);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        for (int i = 0; i < ABILITY_SLOTS.length; i++) {
            if (ABILITY_SLOTS[i] == slot) {
                viewer.closeInventory();
                plugin.abilities().use(viewer, i + 1);
                return;
            }
        }
        if (slot == 29) {
            viewer.closeInventory();
            viewer.performCommand("chess choose");
        } else if (slot == 31) {
            viewer.closeInventory();
        }
    }
}
