package com.chesssmp.meteor;

import com.chesssmp.ChessSMP;
import com.chesssmp.gui.ConfirmMenu;
import com.chesssmp.gui.Items;
import com.chesssmp.gui.Menu;
import com.chesssmp.items.CustomItems;
import com.chesssmp.trader.Cost;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The shop of the green meteor villager: PvP items and maxed gear. Offers come from config.yml (meteor.shop.offers). */
public final class ShopMenu extends Menu {

    private record ShopOffer(String id, ItemStack reward, String name, List<Cost> cost, String category) { }

    private final Map<Integer, ShopOffer> bySlot = new HashMap<>();

    public ShopMenu(ChessSMP plugin, Player viewer) {
        super(plugin, viewer, 6, plugin.messages().component("gui-shop-title"));
        render();
    }

    /** Builds the reward item of an offer section; null when the material is invalid. */
    static ItemStack buildReward(ChessSMP plugin, String id, ConfigurationSection s) {
        String type = s.getString("type", "item");
        ItemStack stack;
        if (type.equalsIgnoreCase("remnant")) {
            stack = plugin.items().create(CustomItems.Kind.REMNANT, Math.max(1, s.getInt("amount", 1)));
        } else if (type.equalsIgnoreCase("clock")) {
            stack = plugin.items().create(CustomItems.Kind.CLOCK, 1);
        } else if (type.equalsIgnoreCase("grandmaster_mace")) {
            stack = plugin.mace().create();
        } else {
            Material m = Material.matchMaterial(s.getString("material", ""));
            if (m == null || !m.isItem() || m.isAir()) {
                plugin.getLogger().warning("meteor.shop.offers." + id + ": unknown material");
                return null;
            }
            stack = new ItemStack(m, Math.max(1, Math.min(m.getMaxStackSize(), s.getInt("amount", 1))));
            ItemMeta meta = stack.getItemMeta();
            for (String spec : s.getStringList("enchants")) {
                String[] parts = spec.split(":");
                NamespacedKey key = NamespacedKey.fromString(parts[0].toLowerCase(Locale.ROOT));
                Enchantment enchantment = key == null ? null : Registry.ENCHANTMENT.get(key);
                if (enchantment == null) {
                    plugin.getLogger().warning("meteor.shop.offers." + id + ": unknown enchantment '" + parts[0] + "'");
                    continue;
                }
                int level = 1;
                if (parts.length > 1) {
                    try {
                        level = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException ignored) {
                        level = 1;
                    }
                }
                meta.addEnchant(enchantment, Math.max(1, level), true);
            }
            if (s.isString("name")) {
                meta.displayName(Items.noItalic(plugin.messages().mm(s.getString("name", ""))));
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private void render() {
        inventory.clear();
        bySlot.clear();
        ConfigurationSection offers = plugin.getConfig().getConfigurationSection("meteor.shop.offers");
        int slot = 0;
        if (offers != null) {
            for (String id : offers.getKeys(false)) {
                if (slot >= 45) {
                    break;
                }
                ConfigurationSection s = offers.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                ItemStack reward = buildReward(plugin, id, s);
                if (reward == null) {
                    continue;
                }
                List<Cost> costs = new ArrayList<>();
                for (String spec : s.getStringList("cost")) {
                    Cost c = Cost.parse(spec);
                    if (c != null) {
                        costs.add(c);
                    }
                }
                if (costs.isEmpty()) {
                    plugin.getLogger().warning("meteor.shop.offers." + id + ": no valid cost, skipped");
                    continue;
                }
                String name = s.getString("label", Items.pretty(reward.getType()));
                ShopOffer offer = new ShopOffer(id, reward, name, costs, s.getString("category", "Item"));
                bySlot.put(slot, offer);
                inventory.setItem(slot, display(offer));
                slot++;
            }
        }
        inventory.setItem(49, Items.make(Material.BARRIER, plugin.messages().component("gui-close"), List.of()));
    }

    private ItemStack display(ShopOffer offer) {
        ItemStack shown = offer.reward().clone();
        ItemMeta meta = shown.getItemMeta();
        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) {
            lore.addAll(meta.lore());
            lore.add(Component.empty());
        }
        lore.add(Component.text(offer.category(), NamedTextColor.DARK_GRAY));
        lore.addAll(plugin.trader().costLines(viewer, offer.cost()));
        lore.add(Component.empty());
        lore.add(plugin.messages().component("gui-click-to-buy"));
        meta.lore(lore.stream().map(Items::noItalic).toList());
        shown.setItemMeta(meta);
        return shown;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        int slot = event.getSlot();
        if (slot == 49) {
            viewer.closeInventory();
            return;
        }
        ShopOffer offer = bySlot.get(slot);
        if (offer == null) {
            return;
        }
        if (plugin.trader().needsConfirm(offer.cost())) {
            new ConfirmMenu(plugin, viewer, plugin.messages().component("gui-confirm-title"),
                    Component.text(offer.name(), NamedTextColor.WHITE), plugin.trader().costLines(viewer, offer.cost()),
                    () -> {
                        plugin.trader().buyStack(viewer, offer.cost(), offer.reward(), offer.name());
                        new ShopMenu(plugin, viewer).openLater();
                    },
                    () -> new ShopMenu(plugin, viewer).openLater()).openLater();
        } else {
            plugin.trader().buyStack(viewer, offer.cost(), offer.reward(), offer.name());
            render();
        }
    }
}
