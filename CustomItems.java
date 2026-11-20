package com.chesssmp.items;

import com.chesssmp.ChessSMP;
import com.chesssmp.gui.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Factory and detector for the simple custom items: Remnant, Chess Grandmaster title and Chess Clock. */
public final class CustomItems {

    public enum Kind {
        REMNANT("remnant", Material.ECHO_SHARD),
        TITLE("chess-grandmaster", Material.NAME_TAG),
        CLOCK("chess-clock", Material.CLOCK);

        private final String configKey;
        private final Material fallback;

        Kind(String configKey, Material fallback) {
            this.configKey = configKey;
            this.fallback = fallback;
        }

        public String configKey() {
            return configKey;
        }

        public static Kind parse(String text) {
            if (text == null) {
                return null;
            }
            String t = text.toLowerCase(Locale.ROOT).replace('_', '-');
            for (Kind k : values()) {
                if (k.configKey.equals(t) || k.name().toLowerCase(Locale.ROOT).equals(t)) {
                    return k;
                }
            }
            return null;
        }
    }

    private final ChessSMP plugin;
    private final NamespacedKey kindKey;

    public CustomItems(ChessSMP plugin) {
        this.plugin = plugin;
        this.kindKey = new NamespacedKey(plugin, "custom_item");
    }

    /** Applies the resource-pack item model (chesssmp:...) when the server uses the pack. */
    public static void applyModel(ChessSMP plugin, ItemMeta meta, String model) {
        if (model == null || model.isBlank() || !plugin.getConfig().getBoolean("resourcepack.use-item-models", true)) {
            return;
        }
        NamespacedKey key = NamespacedKey.fromString(model);
        if (key != null) {
            meta.setItemModel(key);
        }
    }

    public ItemStack create(Kind kind, int amount) {
        String base = "items." + kind.configKey() + ".";
        Material material = Material.matchMaterial(plugin.getConfig().getString(base + "material", kind.fallback.name()));
        if (material == null || !material.isItem() || material.isAir()) {
            material = kind.fallback;
        }
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Items.noItalic(plugin.messages().mm(plugin.getConfig().getString(base + "name", kind.name()))));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList(base + "lore")) {
            lore.add(Items.noItalic(plugin.messages().mm(line)));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(kind != Kind.CLOCK);
        applyModel(plugin, meta, plugin.getConfig().getString(base + "model", ""));
        meta.getPersistentDataContainer().set(kindKey, PersistentDataType.STRING, kind.name());
        item.setItemMeta(meta);
        return item;
    }

    public Kind kindOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }
        try {
            return Kind.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean is(ItemStack item, Kind kind) {
        return kindOf(item) == kind;
    }

    /** True for any ChessSMP item that must never be used as an ordinary crafting ingredient. */
    public boolean isCustom(ItemStack item) {
        return kindOf(item) != null || plugin.mace().isMace(item) || plugin.trader().isTraderItem(item)
                || plugin.abilityItems().isWand(item);
    }
}
