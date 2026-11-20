package com.chesssmp.trader;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import com.chesssmp.Messages;
import com.chesssmp.classes.PieceType;
import com.chesssmp.gui.Items;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Piece Trader item and all trading logic.
 * Transaction safety: menus cancel every click; a purchase first counts the resources, then removes them, then
 * gives the reward - all on the main thread in one tick - and a 250 ms per-player guard stops click spam.
 * Only plain items (no custom name/enchants) count as payment, so special items can never be spent.
 */
public final class TraderManager {

    public record Offer(String id, Material material, int amount, String name, String category, List<Cost> cost) { }

    private final ChessSMP plugin;
    private final NamespacedKey traderKey;
    private final NamespacedKey ownerKey;
    private final List<Offer> offers = new ArrayList<>();
    private final List<Cost> pieceCost = new ArrayList<>();
    private final Map<UUID, Long> lastAction = new HashMap<>();

    public TraderManager(ChessSMP plugin) {
        this.plugin = plugin;
        this.traderKey = new NamespacedKey(plugin, "piece_trader");
        this.ownerKey = new NamespacedKey(plugin, "piece_trader_owner");
        reload();
    }

    public void reload() {
        offers.clear();
        pieceCost.clear();
        for (String spec : plugin.getConfig().getStringList("trader.change-piece.cost")) {
            Cost c = Cost.parse(spec);
            if (c == null) {
                plugin.getLogger().warning("trader.change-piece.cost: invalid entry '" + spec + "'");
            } else {
                pieceCost.add(c);
            }
        }
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("trader.offers");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(id);
            if (s == null) {
                continue;
            }
            Material material = Material.matchMaterial(s.getString("material", ""));
            if (material == null || !material.isItem() || material.isAir()) {
                plugin.getLogger().warning("trader.offers." + id + ": unknown material");
                continue;
            }
            List<Cost> costs = new ArrayList<>();
            for (String spec : s.getStringList("cost")) {
                Cost c = Cost.parse(spec);
                if (c != null) {
                    costs.add(c);
                } else {
                    plugin.getLogger().warning("trader.offers." + id + ": invalid cost '" + spec + "'");
                }
            }
            if (costs.isEmpty()) {
                plugin.getLogger().warning("trader.offers." + id + ": no valid cost, offer skipped");
                continue;
            }
            offers.add(new Offer(id, material, Math.max(1, s.getInt("amount", 1)),
                    s.getString("name", Items.pretty(material)), s.getString("category", "Item"), costs));
        }
    }

    public List<Offer> offers() { return offers; }
    public List<Cost> pieceCost() { return pieceCost; }

    // ------------------------------------------------------------------ the physical item

    public ItemStack createItem(Player owner) {
        Material material = Material.matchMaterial(plugin.getConfig().getString("trader.material", "EMERALD"));
        if (material == null || !material.isItem() || material.isAir()) {
            material = Material.EMERALD;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Items.noItalic(plugin.messages().mm(plugin.getConfig().getString("trader.display-name", "<green>\u265F Piece Trader"))));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("trader.lore")) {
            lore.add(Items.noItalic(plugin.messages().mm(line)));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(traderKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        item.setItemMeta(meta);
        return item;
    }

    public boolean isTraderItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(traderKey, PersistentDataType.BYTE);
    }

    /** @return the owner UUID stored on the item or null. */
    public UUID ownerOf(ItemStack item) {
        if (!isTraderItem(item)) {
            return null;
        }
        String raw = item.getItemMeta().getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public boolean hasItem(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (isTraderItem(it)) {
                return true;
            }
        }
        return false;
    }

    /** Gives a trader item. Only one per player, and only when there is a free slot (no duplication by spamming). */
    public boolean give(Player target) {
        if (hasItem(target)) {
            plugin.messages().send(target, "trader-already");
            return false;
        }
        if (target.getInventory().firstEmpty() == -1) {
            plugin.messages().send(target, "trader-no-space");
            return false;
        }
        target.getInventory().addItem(createItem(target));
        plugin.messages().send(target, "trader-given");
        return true;
    }

    public void open(Player p) {
        new TraderMenu(plugin, p).open();
    }

    // ------------------------------------------------------------------ transactions

    public static boolean isPlain(ItemStack item, Material m) {
        return item != null && item.getType() == m && !item.hasItemMeta();
    }

    public int count(Player p, Material m) {
        PlayerInventory inv = p.getInventory();
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack it = inv.getItem(i);
            if (isPlain(it, m)) {
                total += it.getAmount();
            }
        }
        return total;
    }

    public List<Cost> missing(Player p, List<Cost> costs) {
        List<Cost> out = new ArrayList<>();
        for (Cost c : costs) {
            int have = count(p, c.material());
            if (have < c.amount()) {
                out.add(new Cost(c.material(), c.amount() - have));
            }
        }
        return out;
    }

    public boolean needsConfirm(List<Cost> costs) {
        int total = 0;
        for (Cost c : costs) {
            total += c.amount();
        }
        return total >= plugin.getConfig().getInt("trader.confirm-threshold", 10);
    }

    private void remove(Player p, Material m, int amount) {
        PlayerInventory inv = p.getInventory();
        int left = amount;
        for (int i = 0; i < 36 && left > 0; i++) {
            ItemStack it = inv.getItem(i);
            if (!isPlain(it, m)) {
                continue;
            }
            if (it.getAmount() <= left) {
                left -= it.getAmount();
                inv.setItem(i, null);
            } else {
                it.setAmount(it.getAmount() - left);
                inv.setItem(i, it);
                left = 0;
            }
        }
    }

    private void giveOrDrop(Player p, ItemStack stack) {
        for (ItemStack leftover : p.getInventory().addItem(stack).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), leftover);
        }
    }

    private boolean guard(Player p) {
        long now = System.currentTimeMillis();
        Long last = lastAction.get(p.getUniqueId());
        if (last != null && now - last < 250) {
            return false;
        }
        lastAction.put(p.getUniqueId(), now);
        return true;
    }

    /** Verifies and takes the payment. Nothing is taken unless the whole price can be paid. */
    private boolean pay(Player p, List<Cost> costs) {
        List<Cost> missing = missing(p, costs);
        if (!missing.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Cost c : missing) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(c.amount()).append("x ").append(Items.pretty(c.material()));
            }
            plugin.messages().send(p, "trader-cant-afford", Messages.text("missing", sb.toString()));
            Fx.sound(plugin, p.getLocation(), "entity.villager.no", 1f, 1f);
            return false;
        }
        for (Cost c : costs) {
            remove(p, c.material(), c.amount());
        }
        return true;
    }

    public boolean buyOffer(Player p, Offer offer) {
        if (!guard(p)) {
            return false;
        }
        if (!pay(p, offer.cost())) {
            return false;
        }
        giveOrDrop(p, new ItemStack(offer.material(), offer.amount()));
        plugin.messages().send(p, "trader-purchased", Messages.text("item", offer.amount() + "x " + offer.name()));
        Fx.sound(plugin, p.getLocation(), "entity.villager.trade", 1f, 1f);
        return true;
    }

    public boolean buyPiece(Player p, PieceType piece) {
        if (!guard(p)) {
            return false;
        }
        if (plugin.classes().pieceOf(p) == piece) {
            plugin.messages().send(p, "choose-same");
            return false;
        }
        String reason = plugin.classes().blockReason(p, true);
        if (reason != null) {
            plugin.messages().send(p, reason);
            return false;
        }
        if (!pay(p, pieceCost)) {
            return false;
        }
        plugin.classes().changePiece(p, piece);
        Fx.sound(plugin, p.getLocation(), "entity.villager.trade", 1f, 1f);
        return true;
    }


    /** Generic purchase of an arbitrary stack (meteor shop). Same transaction rules as the trader. */
    public boolean buyStack(Player p, List<Cost> costs, ItemStack reward, String displayName) {
        if (!guard(p)) {
            return false;
        }
        if (!pay(p, costs)) {
            return false;
        }
        giveOrDrop(p, reward.clone());
        plugin.messages().send(p, "trader-purchased", Messages.text("item", displayName));
        Fx.sound(plugin, p.getLocation(), "entity.villager.trade", 1f, 1f);
        return true;
    }

    public List<Component> costLines(Player p, List<Cost> costs) {
        List<Component> lines = new ArrayList<>();
        for (Cost c : costs) {
            int have = count(p, c.material());
            boolean ok = have >= c.amount();
            lines.add(Component.text((ok ? "\u2714 " : "\u2718 ") + c.amount() + "x " + Items.pretty(c.material())
                    + " (" + have + ")", ok ? net.kyori.adventure.text.format.NamedTextColor.GREEN
                    : net.kyori.adventure.text.format.NamedTextColor.RED));
        }
        return lines;
    }

    public void forget(UUID id) {
        lastAction.remove(id);
    }
}
