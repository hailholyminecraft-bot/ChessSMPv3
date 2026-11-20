package com.chesssmp.trader;

/** Parses "MATERIAL:amount" strings (pure string logic, unit tested). */
public final class Specs {

    public record Spec(String name, int amount) { }

    private Specs() {
    }

    /** @return the parsed spec or null when the text is invalid. "DIAMOND" means amount 1. */
    public static Spec parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] parts = text.trim().split(":");
        if (parts.length > 2 || parts[0].isBlank()) {
            return null;
        }
        int amount = 1;
        if (parts.length == 2) {
            try {
                amount = Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (amount <= 0 || amount > 2304) {
            return null;
        }
        return new Spec(parts[0].trim(), amount);
    }
}
