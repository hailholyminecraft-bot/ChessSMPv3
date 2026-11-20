package com.chesssmp.events;

import java.util.Locale;

public enum Side {
    WHITE, BLACK;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Side opposite() {
        return this == WHITE ? BLACK : WHITE;
    }

    public static Side parse(String text) {
        if (text == null) {
            return null;
        }
        for (Side s : values()) {
            if (s.name().equalsIgnoreCase(text.trim())) {
                return s;
            }
        }
        return null;
    }
}
