package com.chesssmp.gui;

import com.chesssmp.ChessSMP;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Base class of every ChessSMP inventory menu. All clicks inside a menu are cancelled by {@link MenuListener},
 * so items can never be taken out or moved around - this is what makes the trader GUI dupe-proof.
 */
public abstract class Menu implements InventoryHolder {

    protected final ChessSMP plugin;
    protected final Player viewer;
    protected final Inventory inventory;

    protected Menu(ChessSMP plugin, Player viewer, int rows, Component title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player viewer() {
        return viewer;
    }

    public void open() {
        viewer.openInventory(inventory);
    }

    /** Opens on the next tick - safe to call from inside a click handler. */
    public void openLater() {
        Bukkit.getScheduler().runTask(plugin, this::open);
    }

    /** Called for clicks on the menu's own (top) inventory only. */
    public abstract void onClick(InventoryClickEvent event);
}
