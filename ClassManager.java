package com.chesssmp.classes;

import com.chesssmp.ChessSMP;
import com.chesssmp.Fx;
import com.chesssmp.Messages;
import com.chesssmp.storage.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

/** Reads class definitions from config.yml and handles piece switching. */
public final class ClassManager {

    private final ChessSMP plugin;

    public ClassManager(ChessSMP plugin) {
        this.plugin = plugin;
    }

    private String path(PieceType p, String sub) {
        return "classes." + p.key() + "." + sub;
    }

    public String emoji(PieceType p) {
        return plugin.getConfig().getString(path(p, "emoji"), p.defaultEmoji());
    }

    public NamedTextColor color(PieceType p) {
        return Fx.color(plugin.getConfig().getString(path(p, "color"), "WHITE"), NamedTextColor.WHITE);
    }

    public String displayName(PieceType p) {
        return plugin.getConfig().getString(path(p, "display-name"), p.name().charAt(0) + p.name().substring(1).toLowerCase());
    }

    public String role(PieceType p) {
        return plugin.getConfig().getString(path(p, "role"), "");
    }

    public String playstyle(PieceType p) {
        return plugin.getConfig().getString(path(p, "playstyle"), "");
    }

    public Material icon(PieceType p) {
        Material m = Material.matchMaterial(plugin.getConfig().getString(path(p, "icon"), "PAPER"));
        return m == null ? Material.PAPER : m;
    }

    public int stat(PieceType p, Stat stat) {
        int v = plugin.getConfig().getInt(path(p, "stats." + stat.key()), 5);
        return Math.max(1, Math.min(10, v));
    }

    /** Sum of the four stats - used by the Chess War team balancer. */
    public double rating(PieceType p) {
        double sum = 0;
        for (Stat s : Stat.values()) {
            sum += stat(p, s);
        }
        return sum;
    }

    /** Ability section ("classes.knight.abilities.1") or null when the slot is not defined. */
    public ConfigurationSection ability(PieceType p, int slot) {
        return plugin.getConfig().getConfigurationSection(path(p, "abilities." + slot));
    }

    /** Coloured "emoji Name" component. */
    public Component label(PieceType p) {
        return Component.text(emoji(p) + " " + displayName(p), color(p));
    }

    public PieceType pieceOf(Player player) {
        return plugin.data().of(player).piece();
    }

    /**
     * @return the message key explaining why the player may not switch right now, or null when allowed.
     */
    public String blockReason(Player p, boolean viaTrader) {
        FileConfiguration c = plugin.getConfig();
        if (c.getBoolean("choose.blocked-in-combat", true) && plugin.combat().isTagged(p.getUniqueId())) {
            return "choose-blocked-combat";
        }
        if (!viaTrader && plugin.events().sideOf(p.getUniqueId()) != null
                && !c.getBoolean("choose.allowed-during-event", false)) {
            return "choose-blocked-event";
        }
        return null;
    }

    /** Free piece choice via /chess choose (outside events by default). */
    public boolean choose(Player p, PieceType piece) {
        FileConfiguration c = plugin.getConfig();
        if (!c.getBoolean("choose.enabled", true)) {
            plugin.messages().send(p, "choose-disabled");
            return false;
        }
        String reason = blockReason(p, false);
        if (reason != null) {
            plugin.messages().send(p, reason);
            return false;
        }
        PlayerData d = plugin.data().of(p);
        if (d.piece() == piece && d.chosen()) {
            plugin.messages().send(p, "choose-same");
            return false;
        }
        long now = System.currentTimeMillis();
        if (d.chosen() && !p.hasPermission("chesssmp.admin")) {
            long remain = d.lastChoose() + c.getLong("choose.cooldown-seconds", 300) * 1000L - now;
            if (remain > 0) {
                plugin.messages().send(p, "choose-cooldown", Messages.text("time", Messages.duration(remain)));
                return false;
            }
        }
        d.setChosen(true);
        d.setLastChoose(now);
        changePiece(p, piece);
        return true;
    }

    /**
     * Switches the player's piece immediately (trader, admin, free choice): removes the old abilities and their
     * effects, applies a safety lockout to the new abilities, and refreshes the name tag.
     */
    public void changePiece(Player p, PieceType next) {
        PlayerData d = plugin.data().of(p);
        PieceType old = d.piece();
        plugin.abilities().clear(p);
        d.setPiece(next);
        if (old != next) {
            d.addPieceChange();
        }
        long now = System.currentTimeMillis();
        d.pruneCooldowns(now);
        long lockout = (long) (plugin.getConfig().getDouble("choose.switch-lockout-seconds", 20) * 1000);
        if (lockout > 0) {
            for (int slot = 1; slot <= 3; slot++) {
                d.extendCooldown(next.key() + "_" + slot, now + lockout);
            }
        }
        plugin.nameTags().refresh(p);
        plugin.data().markDirty();
        plugin.progression().checkObjectives(p.getUniqueId());
        plugin.messages().send(p, "piece-changed", Messages.comp("piece", label(next)));
        Fx.particle(plugin, p.getLocation().add(0, 1, 0), plugin.getConfig().getString("particles.piece-change", "totem_of_undying"), 30, 0.5);
        Fx.sound(plugin, p.getLocation(), plugin.getConfig().getString("sounds.piece-change", "item.armor.equip_iron"), 1f, 1f);
    }
}
