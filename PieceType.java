package com.chesssmp.classes;

import java.util.Locale;

public enum PieceType {
    PAWN("\u265F"),
    KNIGHT("\u265E"),
    BISHOP("\u265D"),
    ROOK("\u265C"),
    QUEEN("\u265B"),
    KING("\u265A");

    private final String defaultEmoji;

    PieceType(String defaultEmoji) {
        this.defaultEmoji = defaultEmoji;
    }

    public String defaultEmoji() {
        return defaultEmoji;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** @return the piece or null if the text is not a piece name. */
    public static PieceType parse(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        for (PieceType type : values()) {
            if (type.name().equalsIgnoreCase(t) || type.defaultEmoji.equals(t)) {
                return type;
            }
        }
        return null;
    }
}
