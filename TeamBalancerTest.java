package com.chesssmp;

import com.chesssmp.events.TeamBalancer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeamBalancerTest {

    private static List<TeamBalancer.Candidate> candidates(double... ratings) {
        List<TeamBalancer.Candidate> list = new ArrayList<>();
        for (double r : ratings) {
            list.add(new TeamBalancer.Candidate(UUID.randomUUID(), r));
        }
        return list;
    }

    @Test
    void teamSizesDifferByAtMostOne() {
        for (int n = 2; n <= 31; n++) {
            double[] ratings = new double[n];
            for (int i = 0; i < n; i++) {
                ratings[i] = 19 + (i % 4) * 3;
            }
            for (TeamBalancer.Mode mode : TeamBalancer.Mode.values()) {
                TeamBalancer.Result r = TeamBalancer.balance(candidates(ratings), mode, new Random(n));
                assertEquals(n, r.white().size() + r.black().size());
                assertTrue(Math.abs(r.white().size() - r.black().size()) <= 1, "mode " + mode + " n=" + n);
            }
        }
    }

    @Test
    void everyPlayerIsAssignedExactlyOnce() {
        List<TeamBalancer.Candidate> list = candidates(28, 28, 24, 24, 19, 19, 25);
        TeamBalancer.Result r = TeamBalancer.balance(list, TeamBalancer.Mode.STRENGTH, new Random(7));
        Set<UUID> seen = new HashSet<>();
        seen.addAll(r.white());
        seen.addAll(r.black());
        assertEquals(list.size(), seen.size());
    }

    @Test
    void twoStrongestPlayersEndUpOnDifferentTeams() {
        List<TeamBalancer.Candidate> list = candidates(100, 90, 1, 1);
        for (long seed = 0; seed < 20; seed++) {
            TeamBalancer.Result r = TeamBalancer.balance(list, TeamBalancer.Mode.STRENGTH, new Random(seed));
            boolean firstWhite = r.white().contains(list.get(0).id());
            boolean secondWhite = r.white().contains(list.get(1).id());
            assertTrue(firstWhite != secondWhite, "seed " + seed);
        }
    }
}
