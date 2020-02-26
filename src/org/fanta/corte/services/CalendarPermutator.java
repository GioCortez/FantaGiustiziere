package org.fanta.corte.services;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

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
		for (Entry<Player, long[]> entry : statistics.entrySet()) {
			long[] totals = entry.getValue();
			LOGGER.info("Relative Statistics for: {} -> {}", entry.getKey(), entry.getValue());
			int[] percent = new int[entry.getValue().length];
			for (int i = 0; i < totals.length; i++) {
				percent[i] = (int) (totals[i] * 100.0 / permutationCounter + 0.5);
			}
			LOGGER.info("Percent Statistics for : {} -> {}", entry.getKey(), percent);
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
		Map<Player, Integer> classifica = c.calculate();
		Map<Player, Integer> sortedMap = classifica.entrySet().stream().sorted(new ScoreComparator())
				.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		int posizione = 0;
		for (Entry<Player, Integer> entry : sortedMap.entrySet()) {
			LOGGER.debug("{} ({}) {}", entry.getKey(), entry.getKey().getTotalPoints(), entry.getValue());

			if (!statistics.containsKey(entry.getKey())) {
				statistics.put(entry.getKey(), new long[12]);
			}
			long[] posizioni = statistics.get(entry.getKey());
			posizioni[posizione] = posizioni[posizione] + 1;
			statistics.put(entry.getKey(), posizioni);
			posizione++;
		}
		permutationCounter++;
	}

	private class ScoreComparator implements Comparator<Map.Entry<Player, Integer>> {

		@Override
		public int compare(Entry<Player, Integer> o1, Entry<Player, Integer> o2) {
			int valueComparison = o2.getValue().compareTo(o1.getValue());
			if (valueComparison == 0) {
				// if points comparison is the same, then go with points sum
				return o2.getKey().getTotalPoints().compareTo(o1.getKey().getTotalPoints());
			} else {
				return valueComparison;
			}
		}

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
