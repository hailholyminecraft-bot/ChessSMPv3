package com.chesssmp.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Splits participants into two teams. Pure logic (no Bukkit types) so it can be unit tested.
 * STRENGTH mode keeps the player counts within one of each other and greedily balances the total piece rating.
 */
public final class TeamBalancer {

    public enum Mode { STRENGTH, RANDOM }

    public record Candidate(UUID id, double rating) { }

    public record Result(List<UUID> white, List<UUID> black) { }

    private TeamBalancer() {
    }

    public static Result balance(List<Candidate> input, Mode mode, Random rng) {
        List<Candidate> list = new ArrayList<>(input);
        Collections.shuffle(list, rng);
        List<UUID> white = new ArrayList<>();
        List<UUID> black = new ArrayList<>();

        if (mode == Mode.RANDOM) {
            boolean toWhite = rng.nextBoolean();
            for (Candidate c : list) {
                (toWhite ? white : black).add(c.id());
                toWhite = !toWhite;
            }
            return new Result(white, black);
        }

        // The sort is stable, so equal ratings keep their shuffled (random) order.
        list.sort((a, b) -> Double.compare(b.rating(), a.rating()));
        double whiteSum = 0;
        double blackSum = 0;
        for (Candidate c : list) {
            boolean toWhite;
            if (white.size() < black.size()) {
                toWhite = true;
            } else if (black.size() < white.size()) {
                toWhite = false;
            } else if (whiteSum != blackSum) {
                toWhite = whiteSum < blackSum;
            } else {
                toWhite = rng.nextBoolean();
            }
            if (toWhite) {
                white.add(c.id());
                whiteSum += c.rating();
            } else {
                black.add(c.id());
                blackSum += c.rating();
            }
        }
        return new Result(white, black);
    }
}
