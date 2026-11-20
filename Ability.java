package com.chesssmp.abilities;

import com.chesssmp.classes.PieceType;

public record Ability(PieceType piece, int slot, AbilityAction action) {

    public String id() {
        return piece.key() + "_" + slot;
    }
}
