package com.chesssmp.gui;

import com.chesssmp.ChessSMP;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.List;

/** Generic confirm / cancel screen. */
public final class ConfirmMenu extends Menu {

    private final Runnable onConfirm;
    private final Runnable onCancel;

    public ConfirmMenu(ChessSMP plugin, Player viewer, Component title, Component subject, List<Component> lines,
                       Runnable onConfirm, Runnable onCancel) {
        super(plugin, viewer, 3, title);
        this.onConfirm = onConfirm;
        this.onCancel = onCancel;
        inventory.setItem(11, Items.make(Material.LIME_CONCRETE,
                plugin.messages().component("gui-confirm"), List.of(plugin.messages().component("gui-confirm-lore"))));
        inventory.setItem(13, Items.make(Material.PAPER, subject, lines));
        inventory.setItem(15, Items.make(Material.RED_CONCRETE,
                plugin.messages().component("gui-cancel"), List.of(Component.text("", NamedTextColor.GRAY))));
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == 11) {
            viewer.closeInventory();
            onConfirm.run();
        } else if (slot == 15) {
            viewer.closeInventory();
            if (onCancel != null) {
                onCancel.run();
            }
        }
    }
}
