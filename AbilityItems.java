package com.chesssmp.abilities;

import com.chesssmp.ChessSMP;
import com.chesssmp.gui.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional "keybind-compatible" way of using abilities: a Scepter item.
 * Right-click = ability 1, sneak + right-click = ability 2, swap-hands key (F) = ability 3.
 */
public final class AbilityItems {

    private final ChessSMP plugin;
    private final NamespacedKey key;

    public AbilityItems(ChessSMP plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "ability_wand");
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("ability-items.enabled", true);
    }

    public ItemStack create() {
        Material material = Material.matchMaterial(plugin.getConfig().getString("ability-items.material", "BLAZE_ROD"));
        if (material == null || !material.isItem()) {
            material = Material.BLAZE_ROD;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Items.noItalic(plugin.messages().mm(plugin.getConfig().getString("ability-items.name", "<gold>Chess Scepter"))));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("ability-items.lore")) {
            lore.add(Items.noItalic(plugin.messages().mm(line)));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public boolean has(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isWand(it)) {
                return true;
            }
        }
        return false;
    }

    /** @return false when the player already owns one or has no free slot. */
    public boolean give(Player p) {
        if (has(p) || p.getInventory().firstEmpty() == -1) {
            return false;
        }
        p.getInventory().addItem(create());
        return true;
    }
}
