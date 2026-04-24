package org.fanta.corte.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.fanta.corte.datamodel.Campionato;
import org.fanta.corte.datamodel.Giornata;
import org.fanta.corte.datamodel.Partita;
import org.fanta.corte.datamodel.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Generates a round-robin calendar using the Berger algorithm.
 * Supports an arbitrary number of legs; legs beyond {@code legsWithAdvantage}
 * are marked as neutral (no home-field bonus is applied to either team).
 */
public class BergerAlgorithm {

	private static final Logger LOGGER = LoggerFactory.getLogger(BergerAlgorithm.class.getSimpleName());

	/**
	 * @param squadre           player names in the permuted order
	 * @param players           player lookup map (name → Player)
	 * @param homeAdvantage     points added to the home team's score on non-neutral matchdays
	 * @param totalLegs         total number of legs to generate
	 * @param legsWithAdvantage number of legs (from leg 0 onwards) that have home advantage;
	 *                          the remaining legs are neutral
	 * @param goalLimit         minimum score to register 1 goal
	 * @param goalOffset        points range per additional goal
	 */
	public Campionato runAlgoritmoDiBerger2(String[] squadre, Map<String, Player> players,
			BigDecimal homeAdvantage, int totalLegs, int legsWithAdvantage,
			int goalLimit, int goalOffset) {

		Campionato calendario = new Campionato(homeAdvantage, goalLimit, goalOffset);
		int totalTeams = squadre.length;
		int legSize = totalTeams - 1;

		// ── Leg 0: first Berger pass ──────────────────────────────────────────
		String[] casa = new String[totalTeams / 2];
		String[] trasferta = new String[totalTeams / 2];
		for (int i = 0; i < totalTeams / 2; i++) {
			casa[i] = squadre[i];
			trasferta[i] = squadre[totalTeams - 1 - i];
		}

		List<Giornata> leg0 = new ArrayList<>();
		int nextId = 0;

		for (int i = 0; i < legSize; i++) {
			nextId++;
			Giornata g = new Giornata(calendario);
			g.setId(nextId);
			// Leg 0 is never neutral (legsWithAdvantage >= 1 for all valid seasons)

			if (i % 2 == 0) {
				for (int j = 0; j < totalTeams / 2; j++) {
					addPartita(g, players.get(trasferta[j]), players.get(casa[j]), nextId);
					LOGGER.debug("g{}: {} vs {}", nextId, trasferta[j], casa[j]);
				}
			} else {
				for (int j = 0; j < totalTeams / 2; j++) {
					addPartita(g, players.get(casa[j]), players.get(trasferta[j]), nextId);
					LOGGER.debug("g{}: {} vs {}", nextId, casa[j], trasferta[j]);
				}
			}

			// Berger rotation: keep casa[0] (the "fixed" team) in place, rotate the rest
			String pivot = casa[0];
			String riporto = trasferta[trasferta.length - 1];
			trasferta = shiftRight(trasferta, casa[1]);
			casa = shiftLeft(casa, riporto);
			casa[0] = pivot;

			leg0.add(g);
			calendario.getGiornate().add(g);
		}

		// ── Legs 1 … totalLegs−1 ─────────────────────────────────────────────
		// Odd legs are the "return" fixtures (home/away swapped vs leg 0).
		// Even legs ≥ 2 repeat leg 0's home/away assignments.
		// Legs whose index ≥ legsWithAdvantage are neutral (no home bonus).
		for (int legIdx = 1; legIdx < totalLegs; legIdx++) {
			boolean isNeutral = legIdx >= legsWithAdvantage;
			boolean returnLeg = (legIdx % 2 == 1);

			for (Giornata source : leg0) {
				nextId++;
				Giornata g = new Giornata(calendario);
				g.setId(nextId);
				g.setNeutral(isNeutral);

				for (Partita p : source.getPartite()) {
					Player home = returnLeg ? p.getTrasferta() : p.getCasa();
					Player away = returnLeg ? p.getCasa()      : p.getTrasferta();
					addPartita(g, home, away, nextId);
				}
				calendario.getGiornate().add(g);
			}
		}

		LOGGER.debug("Generated calendar: {} giornate, {} legs ({} with home advantage) from: {}",
				calendario.getGiornate().size(), totalLegs, legsWithAdvantage, squadre);
		return calendario;
	}

	private void addPartita(Giornata g, Player home, Player away, int giornataId) {
		Partita p = new Partita(g, home, away);
		p.calculate(giornataId);
		g.getPartite().add(p);
	}

	private String[] shiftLeft(String[] data, String add) {
		String[] temp = new String[data.length];
		for (int i = 0; i < data.length - 1; i++) {
			temp[i] = data[i + 1];
		}
		temp[data.length - 1] = add;
		return temp;
	}

	private String[] shiftRight(String[] data, String add) {
		String[] temp = new String[data.length];
		for (int i = 1; i < data.length; i++) {
			temp[i] = data[i - 1];
		}
		temp[0] = add;
		return temp;
	}

}
