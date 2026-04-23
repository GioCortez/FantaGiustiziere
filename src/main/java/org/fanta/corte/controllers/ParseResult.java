package org.fanta.corte.controllers;

import java.util.List;

public class ParseResult {

    public String token;
    public int playerCount;
    public int matchdayCount;
    public List<PlayerSummary> players;
    public List<StandingRow> standings;

    public static class PlayerSummary {
        public String name;
        public double totalScore;
    }

    public static class StandingRow {
        public String player;
        public int played;
        public int won;
        public int drawn;
        public int lost;
        public int goalsFor;
        public int goalsAgainst;
        public int goalDiff;
        public int points;
        public double totalScore;
    }
}
