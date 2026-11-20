package com.chesssmp.listeners;

import com.chesssmp.ChessSMP;
import com.chesssmp.Messages;
import com.chesssmp.classes.PieceType;
import com.chesssmp.statistics.Rank;
import com.chesssmp.storage.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class PlayerListener implements Listener {

    private final ChessSMP plugin;

    public PlayerListener(ChessSMP plugin) {
        this.plugin = plugin;
    }

    /** Shared by the join event and plugin enable (so /reload keeps working). */
    public void handleJoin(Player p) {
        PlayerData d = plugin.data().of(p);
        plugin.nameTags().refresh(p);
        plugin.events().onJoin(p);
        plugin.grandmaster().sync(p);
        plugin.mace().deliverPending(p);
        if (!d.chosen()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) {
                    plugin.messages().send(p, "choose-hint");
                }
            }, 40L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        handleJoin(p);
        if (!plugin.getConfig().getBoolean("join-messages.enabled", true)) {
            return;
        }
        List<String> templates = plugin.getConfig().getStringList("join-messages.messages");
        if (templates.isEmpty()) {
            return;
        }
        PlayerData d = plugin.data().of(p);
        PieceType piece = d.piece() == null ? PieceType.PAWN : d.piece();
        Rank rank = plugin.progression().rank(d);
        event.joinMessage(plugin.messages().mm(templates.get(ThreadLocalRandom.current().nextInt(templates.size())),
                Messages.text("player", p.getName()),
                Messages.text("emoji", plugin.classes().emoji(piece)),
                Messages.comp("piece", plugin.classes().label(piece)),
                Messages.text("rank", plugin.progression().title(rank))));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        if (plugin.getConfig().getBoolean("combat.punish-logout", false) && plugin.combat().isTagged(id) && !p.isDead()) {
            p.setHealth(0.0);
        }
        plugin.events().onQuit(p);
        plugin.abilities().clear(p);
        plugin.clock().forget(id);
        plugin.grandmaster().forget(id);
        plugin.mace().forget(id);
        plugin.trader().forget(id);
        plugin.combat().clear(id);
        plugin.nameTags().remove(p);
        plugin.data().markDirty();
    }
}
