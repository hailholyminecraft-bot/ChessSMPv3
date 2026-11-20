package com.chesssmp;

import com.chesssmp.abilities.AbilityItems;
import com.chesssmp.abilities.AbilityManager;
import com.chesssmp.abilities.ClockManager;
import com.chesssmp.abilities.GrandmasterMace;
import com.chesssmp.grandmaster.GrandmasterManager;
import com.chesssmp.items.CustomItems;
import com.chesssmp.meteor.MeteorManager;
import com.chesssmp.classes.ClassManager;
import com.chesssmp.commands.ChessCommand;
import com.chesssmp.commands.ChessInfoCommand;
import com.chesssmp.events.EventManager;
import com.chesssmp.events.Relations;
import com.chesssmp.gui.MenuListener;
import com.chesssmp.listeners.CombatListener;
import com.chesssmp.listeners.ItemListener;
import com.chesssmp.listeners.PlayerListener;
import com.chesssmp.listeners.WorldListener;
import com.chesssmp.scoreboard.NameTagManager;
import com.chesssmp.statistics.CombatTracker;
import com.chesssmp.statistics.ProgressionManager;
import com.chesssmp.storage.DataStore;
import com.chesssmp.trader.TraderManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** ChessSMP main class: wires the managers together. */
public final class ChessSMP extends JavaPlugin {

    private Messages messages;
    private DataStore data;
    private CombatTracker combat;
    private ClassManager classes;
    private ProgressionManager progression;
    private AbilityManager abilities;
    private AbilityItems abilityItems;
    private GrandmasterMace mace;
    private CustomItems items;
    private GrandmasterManager grandmaster;
    private ClockManager clock;
    private MeteorManager meteor;
    private TraderManager trader;
    private EventManager events;
    private Relations relations;
    private NameTagManager nameTags;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new Messages(this);
        data = new DataStore(this);
        data.load();
        combat = new CombatTracker(this);
        classes = new ClassManager(this);
        progression = new ProgressionManager(this);
        abilities = new AbilityManager(this);
        abilityItems = new AbilityItems(this);
        mace = new GrandmasterMace(this);
        items = new CustomItems(this);
        grandmaster = new GrandmasterManager(this);
        clock = new ClockManager(this);
        meteor = new MeteorManager(this);
        trader = new TraderManager(this);
        events = new EventManager(this);
        relations = new Relations(this);
        nameTags = new NameTagManager(this);

        PlayerListener playerListener = new PlayerListener(this);
        Bukkit.getPluginManager().registerEvents(playerListener, this);
        Bukkit.getPluginManager().registerEvents(new CombatListener(this), this);
        Bukkit.getPluginManager().registerEvents(new ItemListener(this), this);
        Bukkit.getPluginManager().registerEvents(new WorldListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MenuListener(), this);

        bind("chess", new ChessCommand(this));
        bind("chessinfo", new ChessInfoCommand(this));

        nameTags.cleanupStale();
        grandmaster.registerRecipe();
        meteor.startAuto();
        data.startAutosave();
        // Support /reload and late enabling: treat online players as freshly joined.
        for (Player p : Bukkit.getOnlinePlayers()) {
            playerListener.handleJoin(p);
        }
        getLogger().info("ChessSMP " + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (events != null) {
            events.shutdown();
        }
        if (meteor != null) {
            meteor.stopAuto();
        }
        if (clock != null) {
            clock.shutdown();
        }
        if (grandmaster != null) {
            grandmaster.unregisterRecipe();
        }
        if (abilities != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                abilities.clear(p);
            }
        }
        if (nameTags != null) {
            nameTags.shutdown();
        }
        if (data != null) {
            data.saveNow();
        }
    }

    /** Re-reads config.yml and refreshes everything that caches config values. */
    public void reloadAll() {
        reloadConfig();
        progression.reload();
        trader.reload();
        nameTags.reload();
        grandmaster.registerRecipe();
        meteor.startAuto();
    }

    private void bind(String name, CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml");
            return;
        }
        command.setExecutor(executor);
        if (executor instanceof TabCompleter completer) {
            command.setTabCompleter(completer);
        }
    }

    public Messages messages() { return messages; }
    public DataStore data() { return data; }
    public CombatTracker combat() { return combat; }
    public ClassManager classes() { return classes; }
    public ProgressionManager progression() { return progression; }
    public AbilityManager abilities() { return abilities; }
    public AbilityItems abilityItems() { return abilityItems; }
    public GrandmasterMace mace() { return mace; }
    public CustomItems items() { return items; }
    public GrandmasterManager grandmaster() { return grandmaster; }
    public ClockManager clock() { return clock; }
    public MeteorManager meteor() { return meteor; }
    public TraderManager trader() { return trader; }
    public EventManager events() { return events; }
    public Relations relations() { return relations; }
    public NameTagManager nameTags() { return nameTags; }
}
