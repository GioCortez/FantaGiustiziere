package org.fanta.corte.services;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.collections4.CollectionUtils;
import org.fanta.corte.datamodel.Campionato;
import org.fanta.corte.datamodel.Player;
import org.fanta.corte.services.exception.LimitReachedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to create and calculate all the possible permutations of a
 * given (in the constructor) list of players and their results
 * 
 * @author g.cortesi
 *
 */
public class CalendarPermutator {

	private static final Logger LOGGER = LoggerFactory.getLogger(CalendarPermutator.class.getSimpleName());

	private int permutationCounter = 0;
	private BergerAlgorithm bergerAlgorithm = new BergerAlgorithm();
	private Map<String, Player> players;
	private BigDecimal homeAdvantage;
	private Map<Player, long[]> statistics = new HashMap<>();
	private long calendarsToPrint = 11;
	private Map<Player, List<Campionato>> calendarsToBePrinted = new HashMap<>();

	public CalendarPermutator(Map<String, Player> players, BigDecimal homeAdvantage) {
		resetCounter();
		this.players = players;
		this.homeAdvantage = homeAdvantage;
	}

	private void resetCounter() {
		permutationCounter = 0;
	}

	public int permuteCalendars(int limit) {
		resetCounter();
		Set<String> squadre = players.keySet();

		try {
			printAllRecursive(squadre.size(), squadre.toArray(new String[0]), limit);
		} catch (LimitReachedException e) {
			LOGGER.info("Limit ({}) have been reached", limit);
		}

		// Writing absolute and relative statistics for each player
		for (Entry<Player, long[]> entry : statistics.entrySet()) {
			long[] totals = entry.getValue();
			LOGGER.info("Relative Statistics for: {} -> {}", entry.getKey(), entry.getValue());
			int[] percent = new int[entry.getValue().length];
			for (int i = 0; i < totals.length; i++) {
				percent[i] = (int) (totals[i] * 100.0 / permutationCounter + 0.5);
			}
			LOGGER.info("Percent Statistics for : {} -> {}", entry.getKey(), percent);
		}

		String filePath = "results" + File.separator;
		for (Entry<Player, List<Campionato>> entry : calendarsToBePrinted.entrySet()) {

			List<Campionato> campionati = entry.getValue();
			if (CollectionUtils.isNotEmpty(campionati)) {
				String filename = filePath + entry.getKey().getName() + ".txt";
				File f = new File(filename);
				f.getParentFile().mkdirs();
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(f))) {
					for (Campionato c : entry.getValue()) {
						writer.write("Campionato: ");
						writer.newLine();
						writer.write(c.toString());
						writer.newLine();
					}
				} catch (IOException e) {
					LOGGER.error("An error occurred while writing file {}", e, e);
				}
			}

		}

		return permutationCounter;
	}

	public void printAllRecursive(int n, String[] elements, int limit) {

		if (n == 1) {
			printArray(elements);
			if (limit > 0 && permutationCounter > limit) {
				LOGGER.warn("Permutation calculation interrupted since limit {} was reached!", limit);
				throw new LimitReachedException("limit reached!");
			}
		} else {
			for (int i = 0; i < n - 1; i++) {
				printAllRecursive(n - 1, elements, limit);
				if (n % 2 == 0) {
					swap(elements, i, n - 1);
				} else {
					swap(elements, 0, n - 1);
				}
			}
			printAllRecursive(n - 1, elements, limit);
		}
	}

	private void printArray(String[] elements) {

		LOGGER.debug("{} -> Calculating calendar from ordered elements: {}", permutationCounter, elements);
		Campionato c = bergerAlgorithm.runAlgoritmoDiBerger2(elements, players, homeAdvantage);

		// Getting the classifica
		Map<Player, Integer> classifica = c.calculate();

		int posizione = 0;
		for (Entry<Player, Integer> entry : classifica.entrySet()) {

			if (!statistics.containsKey(entry.getKey())) {
				// Creating the "positions" array for this player
				statistics.put(entry.getKey(), new long[classifica.size()]);
			}

			// Incrementing the position array for this player by 1
			long[] positions = statistics.get(entry.getKey());
			long oldNumberOfPositions = positions[posizione];
			long newNumberOfPositions = oldNumberOfPositions + 1;
			positions[posizione] = newNumberOfPositions;
			statistics.put(entry.getKey(), positions);

			if (posizione == 0) {
				// It's the winner!
				LOGGER.debug("Winner: {} ({}) {}", entry.getKey(), entry.getKey().getTotalPoints(), entry.getValue());
				// TODO: Writing the campionato to excel!
				if (newNumberOfPositions < calendarsToPrint) {
					if (!calendarsToBePrinted.containsKey(entry.getKey())) {
						calendarsToBePrinted.put(entry.getKey(), new ArrayList<Campionato>());
					}
					calendarsToBePrinted.get(entry.getKey()).add(c);
				}

			} else {
				// Loser!
				LOGGER.debug("{} ({}) {}", entry.getKey(), entry.getKey().getTotalPoints(), entry.getValue());
			}

			posizione++;

		}
		permutationCounter++;
	}

	private void swap(String[] elements, int i, int j) {
		String tmp = elements[i];
		elements[i] = elements[j];
		elements[j] = tmp;

	}

	public static void main(String[] args) {
		long[] array = new long[12];
		LOGGER.info("{}", array);
	}

}
