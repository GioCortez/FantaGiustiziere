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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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

	private final BergerAlgorithm bergerAlgorithm = new BergerAlgorithm();
	private final Map<String, Player> players;
	private final BigDecimal homeAdvantage;
	private final long calendarsToPrint = 11;

	public CalendarPermutator(Map<String, Player> players, BigDecimal homeAdvantage) {
		this.players = players;
		this.homeAdvantage = homeAdvantage;
	}

	// -------------------------------------------------------------------------
	// Public API
	// -------------------------------------------------------------------------

	/** Single-thread entry point (backward-compatible). */
	public int permuteCalendars(long permutationLimits) {
		return permuteCalendars(permutationLimits, 1);
	}

	/**
	 * @param permutationLimits max permutations to process (0 = unlimited)
	 * @param threads           1 = single-thread; -1 = auto (availableProcessors); N > 1 = fixed pool
	 */
	public int permuteCalendars(long permutationLimits, int threads) {
		PartialResult finalResult = computePermutations(permutationLimits, threads);
		logStatistics(finalResult, permutationLimits);
		writeResultFiles(finalResult);
		return finalResult.permutationCounter;
	}

	/**
	 * Runs the permutation and returns the raw result without any I/O side effects.
	 * Useful for testing and for callers that need the statistics directly.
	 *
	 * @param permutationLimits max permutations to process (0 = unlimited)
	 * @param threads           1 = single-thread; -1 = auto (availableProcessors); N > 1 = fixed pool
	 */
	public PartialResult computePermutations(long permutationLimits, int threads) {
		String[] originalElements = players.keySet().toArray(new String[0]);
		int n = originalElements.length;

		if (threads == 1) {
			return runSingleThread(originalElements, n, permutationLimits);
		} else {
			return runMultiThread(originalElements, n, permutationLimits, threads);
		}
	}

	// -------------------------------------------------------------------------
	// Single-thread execution
	// -------------------------------------------------------------------------

	private PartialResult runSingleThread(String[] elements, int n, long limit) {
		PartialResult result = new PartialResult();
		try {
			printAllRecursive(n, elements, 0, limit, result);
		} catch (LimitReachedException e) {
			LOGGER.info("Limit ({}) reached", limit);
		}
		return result;
	}

	// -------------------------------------------------------------------------
	// Multi-thread execution
	// -------------------------------------------------------------------------

	private PartialResult runMultiThread(String[] originalElements, int n, long limit, int threads) {
		int actualThreads = resolveThreadCount(threads, n);
		LOGGER.info("Running with {} threads ({} partitions)", actualThreads, n);

		ExecutorService executor = Executors.newFixedThreadPool(actualThreads);
		List<Future<PartialResult>> futures = new ArrayList<>();

		// Split the global limit evenly across the n partitions so the total number of
		// processed permutations stays close to the requested limit (0 = unlimited).
		long limitPerPartition = limit > 0 ? Math.max(1, limit / n) : 0;

		// Partitioning strategy: fix one distinct player at position 0 per partition.
		// Each partition then permutes the remaining n-1 positions independently,
		// producing (n-1)! permutations. n partitions × (n-1)! = n! total — full coverage.
		for (int i = 0; i < n; i++) {
			String[] seed = originalElements.clone();
			// Bring player i to index 0; the rest of the array keeps its relative order.
			swap(seed, 0, i);
			final long taskLimit = limitPerPartition;
			final String[] taskElements = seed;
			futures.add(executor.submit(() -> {
				// Each task gets its own PartialResult — no shared mutable state, no locks needed.
				PartialResult result = new PartialResult();
				try {
					// offset=1: Heap's algorithm operates on indices [1..n-1], leaving index 0 fixed.
					printAllRecursive(n - 1, taskElements, 1, taskLimit, result);
				} catch (LimitReachedException e) {
					// Normal exit path when a per-partition limit is set.
				}
				return result;
			}));
		}

		// Stop accepting new tasks and wait for all partitions to finish.
		executor.shutdown();
		try {
			executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOGGER.warn("Multi-thread execution interrupted");
		}

		// Combine all per-thread partial results into a single final result.
		PartialResult merged = new PartialResult();
		for (Future<PartialResult> future : futures) {
			try {
				mergeInto(merged, future.get());
			} catch (InterruptedException | ExecutionException e) {
				LOGGER.error("Error collecting thread result: {}", e.getMessage(), e);
			}
		}
		return merged;
	}

	private int resolveThreadCount(int threads, int n) {
		// -1 (or any negative value) means "use all available CPU cores".
		int resolved = threads <= 0 ? Runtime.getRuntime().availableProcessors() : threads;
		// Cap at n: there are only n independent partitions, so extra threads would sit idle.
		return Math.min(resolved, n);
	}

	private void mergeInto(PartialResult target, PartialResult source) {
		target.permutationCounter += source.permutationCounter;

		// Sum position-frequency arrays element-by-element for each player.
		for (Entry<Player, long[]> entry : source.statistics.entrySet()) {
			target.statistics.merge(entry.getKey(), entry.getValue(), (a, b) -> {
				long[] combined = new long[a.length];
				for (int i = 0; i < a.length; i++) {
					combined[i] = a[i] + b[i];
				}
				return combined;
			});
		}

		// Concatenate sample calendars per player, then trim so the list never exceeds
		// calendarsToPrint entries (each thread independently capped, merge can double them).
		for (Entry<Player, List<Campionato>> entry : source.calendarsToBePrinted.entrySet()) {
			target.calendarsToBePrinted.merge(entry.getKey(), new ArrayList<>(entry.getValue()), (a, b) -> {
				a.addAll(b);
				return a.size() > calendarsToPrint ? a.subList(0, (int) calendarsToPrint) : a;
			});
		}
	}

	// -------------------------------------------------------------------------
	// Heap's algorithm (offset-aware)
	// -------------------------------------------------------------------------

	/**
	 * Heap's algorithm — generates all permutations of elements[offset..offset+n-1]
	 * in-place, leaving elements[0..offset-1] untouched.
	 *
	 * <p>The {@code offset} parameter is the key addition for multi-threading:
	 * <ul>
	 *   <li>offset=0 — classic behaviour, permutes the whole array (single-thread path).</li>
	 *   <li>offset=1 — permutes only positions 1..n-1, keeping position 0 fixed as the
	 *       "partition anchor" chosen by the caller (multi-thread path).</li>
	 * </ul>
	 * Swaps are adjusted by {@code offset} so they always target the active sub-array.
	 */
	public void printAllRecursive(int n, String[] elements, int offset, long limit, PartialResult result) {
		if (n == 1) {
			processPermutation(elements, result);
			if (limit > 0 && result.permutationCounter >= limit) {
				throw new LimitReachedException("limit reached!");
			}
		} else {
			for (int i = 0; i < n - 1; i++) {
				printAllRecursive(n - 1, elements, offset, limit, result);
				// Heap's swap rule: when n is even rotate element i out; when n is odd always
				// rotate element at the start of the active sub-array (index offset+0).
				if (n % 2 == 0) {
					swap(elements, offset + i, offset + n - 1);
				} else {
					swap(elements, offset, offset + n - 1);
				}
			}
			printAllRecursive(n - 1, elements, offset, limit, result);
		}
	}

	private void processPermutation(String[] elements, PartialResult result) {
		LOGGER.debug("{} -> Calculating calendar from ordered elements: {}", result.permutationCounter, elements);
		Campionato c = bergerAlgorithm.runAlgoritmoDiBerger2(elements, players, homeAdvantage);
		Map<Player, Integer> classifica = c.calculate();

		int posizione = 0;
		for (Entry<Player, Integer> entry : classifica.entrySet()) {
			result.statistics.computeIfAbsent(entry.getKey(), k -> new long[classifica.size()]);
			long[] positions = result.statistics.get(entry.getKey());
			positions[posizione]++;

			if (posizione == 0 && positions[0] <= calendarsToPrint) {
				result.calendarsToBePrinted
						.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
						.add(c);
			}
			posizione++;
		}
		result.permutationCounter++;
	}

	// -------------------------------------------------------------------------
	// Output
	// -------------------------------------------------------------------------

	private void logStatistics(PartialResult result, long permutationLimits) {
		for (Entry<Player, long[]> entry : result.statistics.entrySet()) {
			long[] totals = entry.getValue();
			LOGGER.info("Relative Statistics for: {} -> {}", entry.getKey(), totals);
			int[] percent = new int[totals.length];
			for (int i = 0; i < totals.length; i++) {
				percent[i] = (int) (totals[i] * 100.0 / result.permutationCounter + 0.5);
			}
			LOGGER.info("Percent Statistics for : {} -> {}", entry.getKey(), percent);
		}
	}

	private void writeResultFiles(PartialResult result) {
		String filePath = "results" + File.separator;
		for (Entry<Player, List<Campionato>> entry : result.calendarsToBePrinted.entrySet()) {
			List<Campionato> campionati = entry.getValue();
			if (CollectionUtils.isNotEmpty(campionati)) {
				String filename = filePath + entry.getKey().getName() + ".txt";
				File f = new File(filename);
				f.getParentFile().mkdirs();
				try (BufferedWriter writer = new BufferedWriter(new FileWriter(f))) {
					for (Campionato c : campionati) {
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
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private void swap(String[] elements, int i, int j) {
		String tmp = elements[i];
		elements[i] = elements[j];
		elements[j] = tmp;
	}

	// -------------------------------------------------------------------------
	// PartialResult — thread-local accumulator
	// -------------------------------------------------------------------------

	/**
	 * Holds the results produced by a single permutation run (one thread or the
	 * whole single-thread execution). Keeping this state local to each thread
	 * means threads never write to shared memory, so no synchronisation is needed.
	 * After all threads finish, multiple PartialResult instances are merged into one.
	 */
	public static class PartialResult {
		/** For each player: how many times they finished in each league position. */
		public Map<Player, long[]> statistics = new HashMap<>();
		/** Sample winning calendars to write to output files, capped at calendarsToPrint per player. */
		public Map<Player, List<Campionato>> calendarsToBePrinted = new HashMap<>();
		/** Total number of permutations processed by this result. */
		public int permutationCounter = 0;
	}

}
