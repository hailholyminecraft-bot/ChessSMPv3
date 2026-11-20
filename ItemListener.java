package com.chesssmp.listeners;

import com.chesssmp.ChessSMP;
import com.chesssmp.items.CustomItems;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Use of the custom items (trader, scepter, mace, clock, title) and protection against misuse/duplication. */
public final class ItemListener implements Listener {

    private final ChessSMP plugin;

    public ItemListener(ChessSMP plugin) {
        this.plugin = plugin;
    }

    private boolean isProtected(ItemStack item) {
        return plugin.trader().isTraderItem(item) || plugin.abilityItems().isWand(item);
    }

    // ------------------------------------------------------------------ using the items

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        Player p = event.getPlayer();
        EquipmentSlot hand = event.getHand();

        if (plugin.trader().isTraderItem(item)) {
            event.setCancelled(true);
            if (hand != EquipmentSlot.HAND && p.getInventory().getItemInMainHand().getType() != Material.AIR) {
                return;
            }
            UUID owner = plugin.trader().ownerOf(item);
            if (!p.hasPermission("chesssmp.trader") && !p.hasPermission("chesssmp.player")) {
                plugin.messages().send(p, "no-permission");
            } else if (owner != null && !owner.equals(p.getUniqueId()) && plugin.getConfig().getBoolean("trader.bind-to-owner", true)) {
                plugin.messages().send(p, "trader-not-owner");
            } else {
                plugin.trader().open(p);
            }
            return;
        }

        if (plugin.abilityItems().isWand(item)) {
            event.setCancelled(true);
            if (hand == EquipmentSlot.HAND && plugin.abilityItems().enabled()) {
                plugin.abilities().use(p, p.isSneaking() ? 2 : 1);
            }
            return;
        }

        if (plugin.mace().isMace(item)) {
            if (hand != EquipmentSlot.HAND) {
                return;
            }
            Block clicked = event.getClickedBlock();
            if (clicked != null && clicked.getType().isInteractable() && !p.isSneaking()) {
                return; // let doors, chests ... work normally
            }
            event.setCancelled(true);
            plugin.mace().dash(p);
            return;
        }

        CustomItems.Kind kind = plugin.items().kindOf(item);
        if (kind == null) {
            return;
        }
        if (kind == CustomItems.Kind.CLOCK) {
            event.setCancelled(true);
            if (hand == EquipmentSlot.HAND) {
                plugin.clock().use(p);
            }
        } else if (kind == CustomItems.Kind.TITLE) {
            event.setCancelled(true);
            if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) {
                return;
            }
            if (plugin.grandmaster().equip(p)) {
                ItemStack inHand = hand == EquipmentSlot.OFF_HAND ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
                ItemStack rest = inHand.getAmount() > 1 ? inHand.asQuantity(inHand.getAmount() - 1) : new ItemStack(Material.AIR);
                if (hand == EquipmentSlot.OFF_HAND) {
                    p.getInventory().setItemInOffHand(rest);
                } else {
                    p.getInventory().setItemInMainHand(rest);
                }
            }
        } else {
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (plugin.abilityItems().isWand(p.getInventory().getItemInMainHand()) && plugin.abilityItems().enabled()) {
            event.setCancelled(true);
            plugin.abilities().use(p, 3);
        }
    }

    // ------------------------------------------------------------------ crafting rules

    private Set<Material> blockedResults() {
        Set<Material> blocked = new HashSet<>();
        for (String name : plugin.getConfig().getStringList("crafting.blocked-results")) {
            Material m = Material.matchMaterial(name.toUpperCase(Locale.ROOT));
            if (m != null) {
                blocked.add(m);
            }
        }
        return blocked;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null) {
            return;
        }
        ItemStack result = recipe.getResult();
        if (blockedResults().contains(result.getType()) && plugin.getConfig().getBoolean("crafting.block-enabled", true)) {
            event.getInventory().setResult(null);
            return;
        }
        boolean isTitleRecipe = recipe instanceof Keyed keyed && keyed.getKey().equals(plugin.grandmaster().recipeKey());
        if (isTitleRecipe) {
            return;
        }
        // Remnants, the trader item, scepter ... can never be used as ingredients of vanilla recipes.
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (ingredient != null && plugin.items().isCustom(ingredient)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        Recipe recipe = event.getRecipe();
        if (plugin.getConfig().getBoolean("crafting.block-enabled", true) && blockedResults().contains(recipe.getResult().getType())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ anti-misuse / anti-duplication

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getInventory();
        if (top.getType() == InventoryType.CRAFTING || top.getType() == InventoryType.CREATIVE) {
            return;
        }
        if (top.getHolder(false) instanceof com.chesssmp.gui.Menu) {
            return; // menus are handled by MenuListener
        }
        boolean clickedTop = event.getClickedInventory() != null && event.getClickedInventory().equals(top);
        ClickType click = event.getClick();
        Player p = event.getWhoClicked() instanceof Player player ? player : null;

        boolean block = false;
        if (click.isShiftClick() && !clickedTop && isProtected(event.getCurrentItem())) {
            block = true;
        } else if (clickedTop && isProtected(event.getCursor())) {
            block = true;
        } else if (clickedTop && click == ClickType.NUMBER_KEY && p != null
                && isProtected(p.getInventory().getItem(event.getHotbarButton()))) {
            block = true;
        } else if (clickedTop && click == ClickType.SWAP_OFFHAND && p != null
                && isProtected(p.getInventory().getItemInOffHand())) {
            block = true;
        }
        if (block) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getType() == InventoryType.CRAFTING || !isProtected(event.getOldCursor())) {
            return;
        }
        int topSize = event.getInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.trader().isTraderItem(event.getItemDrop().getItemStack())
                && !plugin.getConfig().getBoolean("trader.allow-drop", false)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (plugin.items().isCustom(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.items().isCustom(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    /** Name tags, item frames and villager trades must not eat our items. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Player p = event.getPlayer();
        ItemStack inHand = event.getHand() == EquipmentSlot.OFF_HAND ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
        if (plugin.items().isCustom(inHand)) {
            event.setCancelled(true);
        }
    }
}
