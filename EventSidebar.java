package com.chesssmp.scoreboard;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Sidebar objective shown on the main scoreboard while a Chess War event runs. */
public final class EventSidebar {

    private static final String OBJECTIVE = "csmp_event";

    private final List<String> entries = new ArrayList<>();
    private Objective objective;

    private Scoreboard board() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        return manager == null ? null : manager.getMainScoreboard();
    }

    public void update(Component title, Map<String, Integer> lines) {
        Scoreboard board = board();
        if (board == null) {
            return;
        }
        if (objective == null) {
            Objective old = board.getObjective(OBJECTIVE);
            if (old != null) {
                old.unregister();
            }
            objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, title);
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }
        objective.displayName(title);
        for (String entry : entries) {
            board.resetScores(entry);
        }
        entries.clear();
        for (Map.Entry<String, Integer> line : lines.entrySet()) {
            objective.getScore(line.getKey()).setScore(line.getValue());
            entries.add(line.getKey());
        }
    }

    public void hide() {
        Scoreboard board = board();
        if (board != null) {
            Objective existing = board.getObjective(OBJECTIVE);
            if (existing != null) {
                existing.unregister();
            }
        }
        entries.clear();
        objective = null;
    }
}
