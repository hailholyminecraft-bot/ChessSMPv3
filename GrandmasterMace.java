package com.chesssmp.abilities;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import com.chesssmp.Messages;
import com.chesssmp.gui.Items;
import com.chesssmp.storage.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Grandmaster Mace: the trophy awarded to the winner of a Chess War event.
 * While holding it, pressing right click performs a dash in the looking direction.
 */
public final class GrandmasterMace {

    private final ChessSMP plugin;
    private final NamespacedKey key;
    private final Map<UUID, Long> cooldownEnd = new HashMap<>();

    public GrandmasterMace(ChessSMP plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "grandmaster_mace");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.MACE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Items.noItalic(plugin.messages().mm(
                plugin.getConfig().getString("grandmaster-mace.name", "<gradient:#ffd700:#ff8c00><bold>\u265A Grandmaster Mace"))));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("grandmaster-mace.lore")) {
            lore.add(Items.noItalic(plugin.messages().mm(line)));
        }
        meta.lore(lore);
        meta.setUnbreakable(plugin.getConfig().getBoolean("grandmaster-mace.unbreakable", true));
        meta.setEnchantmentGlintOverride(true);
        com.chesssmp.items.CustomItems.applyModel(plugin, meta, plugin.getConfig().getString("grandmaster-mace.model", "chesssmp:grandmaster_mace"));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public boolean isMace(ItemStack item) {
        if (item == null || item.getType() != Material.MACE || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Gives the mace now (drops it at the feet when the inventory is full). */
    public void give(Player p) {
        for (ItemStack leftover : p.getInventory().addItem(create()).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), leftover);
        }
        plugin.messages().send(p, "mace-awarded");
        Fx.sound(plugin, p.getLocation(), "ui.toast.challenge_complete", 1f, 1f);
        Fx.particle(plugin, p.getLocation().add(0, 1, 0), "totem_of_undying", 40, 0.6);
    }

    /** Awards the mace to a player id: immediately when online, otherwise on their next join. */
    public void award(UUID id) {
        Player online = plugin.getServer().getPlayer(id);
        if (online != null) {
            give(online);
            return;
        }
        PlayerData d = plugin.data().find(id);
        if (d != null) {
            d.setPendingMace(true);
            plugin.data().markDirty();
        }
    }

    /** Called on join: hands over a mace that was won while offline. */
    public void deliverPending(Player p) {
        PlayerData d = plugin.data().of(p);
        if (d.pendingMace()) {
            d.setPendingMace(false);
            plugin.data().markDirty();
            give(p);
        }
    }

    /** Right-click dash. Returns true when the dash was performed. */
    public boolean dash(Player p) {
        if (p.isInsideVehicle() || p.isDead()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long remain = cooldownEnd.getOrDefault(p.getUniqueId(), 0L) - now;
        if (remain > 0) {
            plugin.messages().actionBar(p, "mace-cooldown", Messages.text("time", Messages.duration(remain)));
            return false;
        }
        double strength = plugin.getConfig().getDouble("grandmaster-mace.dash-strength", 1.9);
        double minY = plugin.getConfig().getDouble("grandmaster-mace.dash-min-vertical", 0.2);
        Vector v = p.getLocation().getDirection().normalize().multiply(strength);
        v.setY(Math.max(v.getY(), minY));
        p.setVelocity(v);
        plugin.abilities().grantNoFall(p.getUniqueId(), 4000);
        cooldownEnd.put(p.getUniqueId(), now + (long) (plugin.getConfig().getDouble("grandmaster-mace.dash-cooldown-seconds", 6) * 1000));
        Fx.particle(plugin, p.getLocation().add(0, 1, 0), "cloud", 25, 0.4);
        Fx.sound(plugin, p.getLocation(), "entity.breeze.wind_burst", 1f, 1f);
        return true;
    }

    public void forget(UUID id) {
        cooldownEnd.remove(id);
    }
}
