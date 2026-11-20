package com.chesssmp.classes;

import java.util.Locale;

public enum Stat {
    OFFENSE, DEFENSE, MOBILITY, SUPPORT;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public String display() {
        return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
    }

    public static Stat parse(String text) {
        if (text == null) {
            return null;
        }
        for (Stat s : values()) {
            if (s.name().equalsIgnoreCase(text.trim())) {
                return s;
            }
        }
        return null;
    }
}
