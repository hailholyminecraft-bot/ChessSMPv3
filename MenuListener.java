package com.chesssmp.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        // getHolder(false) avoids creating block-state snapshots for every chest click on the server.
        if (!(event.getInventory().getHolder(false) instanceof Menu menu)) {
            return;
        }
        event.setCancelled(true); // shift-click, number keys, double-click collect ... everything is blocked
        if (!(event.getWhoClicked() instanceof Player player) || !player.equals(menu.viewer())) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }
        menu.onClick(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof Menu) {
            event.setCancelled(true);
        }
    }
}
