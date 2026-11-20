package com.chesssmp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Reads MiniMessage templates from config.yml ("messages.*"). */
public final class Messages {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final ChessSMP plugin;

    public Messages(ChessSMP plugin) {
        this.plugin = plugin;
    }

    public Component mm(String text, TagResolver... resolvers) {
        return MM.deserialize(text, resolvers);
    }

    /** Message without the chat prefix. */
    public Component component(String key, TagResolver... resolvers) {
        String raw = plugin.getConfig().getString("messages." + key);
        if (raw == null) {
            raw = "<red>[missing message: " + key + "]";
        }
        return MM.deserialize(raw, resolvers);
    }

    /** Message with the chat prefix. */
    public Component prefixed(String key, TagResolver... resolvers) {
        Component prefix = MM.deserialize(plugin.getConfig().getString("messages.prefix", ""));
        return prefix.append(component(key, resolvers));
    }

    public void send(CommandSender to, String key, TagResolver... resolvers) {
        to.sendMessage(prefixed(key, resolvers));
    }

    public void actionBar(Player to, String key, TagResolver... resolvers) {
        to.sendActionBar(component(key, resolvers));
    }

    public static TagResolver text(String name, String value) {
        return Placeholder.unparsed(name, value == null ? "" : value);
    }

    public static TagResolver comp(String name, Component value) {
        return Placeholder.component(name, value);
    }

    public static TagResolver num(String name, long value) {
        return Placeholder.unparsed(name, Long.toString(value));
    }

    /** 12s, 1m 05s ... */
    public static String duration(long millis) {
        long s = Math.max(0, (millis + 999) / 1000);
        if (s < 60) {
            return s + "s";
        }
        return (s / 60) + "m " + String.format("%02d", s % 60) + "s";
    }
}
