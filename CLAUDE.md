# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Build
mvn clean package

# Run (Spring Boot dev mode)
mvn spring-boot:run

# Run packaged JAR
java -jar target/FantaGiustiziere-0.0.1-SNAPSHOT.jar

# Run tests
mvn test

# Run a specific test class
mvn test -Dtest=ClassName

# Run a specific test method
mvn test -Dtest=ClassName#methodName
```

Server runs on port **5000** (configured in `application.properties`).

## Architecture

**FantaGiustiziere** is a Fantasy Football tournament calendar optimizer. Given real match-day scores from an Excel file, it exhaustively generates all possible round-robin schedule permutations and computes which schedule is most favorable for each player.

### Data Flow

```
Excel file (.xlsx)
    → ResultsParser       — reads scores per player per match day using Apache POI
    → CalendarPermutator  — iterates all player-order permutations (Heap's algorithm)
        → BergerAlgorithm — generates round-robin calendar for each permutation
        → ranking logic   — simulates all matches, scores points, ranks players
    → /results/*.txt      — one file per player listing calendars where they win
```

### Key Classes

| Class | Role |
|---|---|
| `FantaMain` | Spring Boot entry point; hardcodes input Excel path, player count (12), home advantage (2.0 pts), and permutation limit (1000) |
| `ResultsParser` | Parses XLSX sections labelled `Giornata lega N` into `Map<String, Player>` |
| `BergerAlgorithm` | Implements Berger round-robin scheduling (double round-robin with home/away alternation) |
| `CalendarPermutator` | Heap's algorithm over player list; tracks how often each player finishes in each position; throws `LimitReachedException` to stop early |
| `MainController` | Minimal REST controller (`GET /`) — currently not used for main logic |

### Data Models

- **`Player`** — name, ID, per-matchday scores, cumulative points
- **`Campionato`** — full tournament (list of `Giornata`, home advantage setting)
- **`Giornata`** — single match day (list of `Partita`)
- **`Partita`** — one fixture (home player, away player, scores, goals computed)

### Tech Stack

- Java 17, Spring Boot 3.0.6
- Apache POI 5.2.3 (Excel parsing)
- Deployed to Heroku via `Procfile`

### Important Notes

- The Excel file path in `FantaMain.java` is **hardcoded** to a local Windows path — update it when running on a different machine.
- The `/results/` directory is created at runtime in the working directory.
- There are currently no unit tests despite the test dependency being present in `pom.xml`.
