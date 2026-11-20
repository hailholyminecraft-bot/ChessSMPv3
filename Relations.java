package com.chesssmp.events;

import com.chesssmp.ChessSMP;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Who counts as a teammate or an enemy for abilities.
 * During Chess War the two teams decide. Outside events, only players that fought each other recently
 * (combat tag) are enemies, so debuffs never hit peaceful players; support abilities help everybody who is not hostile.
 */
public final class Relations {

    private final ChessSMP plugin;

    public Relations(ChessSMP plugin) {
        this.plugin = plugin;
    }

    private boolean valid(Player p) {
        return p.isOnline() && !p.isDead() && p.getGameMode() != GameMode.SPECTATOR;
    }

    public boolean isTeammate(Player a, Player b) {
        if (a.equals(b) || !valid(b)) {
            return false;
        }
        Side sa = plugin.events().sideOf(a.getUniqueId());
        Side sb = plugin.events().sideOf(b.getUniqueId());
        if (sa != null || sb != null) {
            return sa != null && sa == sb;
        }
        return plugin.getConfig().getBoolean("abilities.support-non-event-players", true)
                && !plugin.combat().isHostile(a.getUniqueId(), b.getUniqueId());
    }

    public boolean isEnemy(Player a, Player b) {
        if (a.equals(b) || !valid(b)) {
            return false;
        }
        Side sa = plugin.events().sideOf(a.getUniqueId());
        Side sb = plugin.events().sideOf(b.getUniqueId());
        if (sa != null && sb != null) {
            return sa != sb;
        }
        if (sa != null || sb != null) {
            return false;
        }
        return plugin.combat().isHostile(a.getUniqueId(), b.getUniqueId());
    }
}
