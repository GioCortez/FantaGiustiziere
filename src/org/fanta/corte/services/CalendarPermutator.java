package org.fanta.corte.services;

import java.util.Map;
import java.util.Set;

import org.fanta.corte.datamodel.Campionato;
import org.fanta.corte.datamodel.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CalendarPermutator {

	private static final Logger LOGGER = LoggerFactory.getLogger(CalendarPermutator.class.getSimpleName());

	private int permutationCounter = 1;
	private BergerAlgorithm bergerAlgorithm = new BergerAlgorithm();
	private Map<String, Player> players;

	public CalendarPermutator(Map<String, Player> players) {
		resetCounter();
		this.players = players;
	}

	private void resetCounter() {
		permutationCounter = 1;
	}

	public void permuteCalendars(int limit) {
		resetCounter();
		Set<String> squadre = players.keySet();

		printAllRecursive(squadre.size(), squadre.toArray(new String[0]), limit);
	}

	public void printAllRecursive(int n, String[] elements, int limit) {

		if (n == 1) {
			printArray(elements);
			if (permutationCounter > limit) {
				LOGGER.warn("Permutation calculation interrupted since limit {} was reached!", limit);
				throw new IllegalStateException("limit reached!");
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

		LOGGER.info("{} -> Calculating calendar from ordered elements: {}", permutationCounter, elements);
		Campionato c = bergerAlgorithm.runAlgoritmoDiBerger2(elements, players);

		permutationCounter++;
	}

	private void swap(String[] elements, int i, int j) {
		String tmp = elements[i];
		elements[i] = elements[j];
		elements[j] = tmp;

	}

}
