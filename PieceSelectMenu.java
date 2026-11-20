package com.chesssmp.gui;

import com.chesssmp.ChessSMP;
import com.chesssmp.classes.ClassManager;
import com.chesssmp.classes.PieceType;
import com.chesssmp.classes.Stat;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** Lets the player pick one of the six pieces. Used for /chess choose (free) and the trader (paid). */
public final class PieceSelectMenu extends Menu {

    private static final int[] SLOTS = {10, 11, 12, 14, 15, 16};

    private final BiConsumer<Player, PieceType> onPick;
    private final Runnable onBack;

    public PieceSelectMenu(ChessSMP plugin, Player viewer, Component title, List<Component> extraLore,
                           BiConsumer<Player, PieceType> onPick, Runnable onBack) {
        super(plugin, viewer, 3, title);
        this.onPick = onPick;
        this.onBack = onBack;
        ClassManager classes = plugin.classes();
        PieceType current = classes.pieceOf(viewer);
        PieceType[] pieces = PieceType.values();
        for (int i = 0; i < pieces.length; i++) {
            PieceType piece = pieces[i];
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(classes.role(piece), NamedTextColor.GRAY));
            lore.add(Component.empty());
            for (Stat stat : Stat.values()) {
                lore.add(Component.text(stat.display() + ": ", NamedTextColor.GRAY)
                        .append(Component.text(String.valueOf(classes.stat(piece, stat)), NamedTextColor.WHITE)));
            }
            lore.add(Component.empty());
            if (piece == current) {
                lore.add(plugin.messages().component("gui-current-piece"));
            } else {
                lore.addAll(extraLore);
            }
            ItemStack item = Items.make(classes.icon(piece), classes.label(piece), lore);
            inventory.setItem(SLOTS[i], piece == current ? Items.glow(item) : item);
        }
        if (onBack != null) {
            inventory.setItem(22, Items.make(Material.ARROW, plugin.messages().component("gui-back"), List.of()));
        }
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == 22 && onBack != null) {
            onBack.run();
            return;
        }
        PieceType[] pieces = PieceType.values();
        for (int i = 0; i < SLOTS.length; i++) {
            if (SLOTS[i] == slot) {
                onPick.accept(viewer, pieces[i]);
                return;
            }
        }
    }
}
