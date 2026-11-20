package com.chesssmp.listeners;

import com.chesssmp.ChessSMP;
import com.chesssmp.meteor.ShopMenu;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/** The green meteor villager: opens the PvP shop and cannot be harmed. */
public final class WorldListener implements Listener {

    private final ChessSMP plugin;

    public WorldListener(ChessSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!plugin.meteor().isShopVillager(event.getRightClicked())) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        if (!p.hasPermission("chesssmp.player")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        new ShopMenu(plugin, p).open();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent event) {
        if (plugin.meteor().isShopVillager(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
