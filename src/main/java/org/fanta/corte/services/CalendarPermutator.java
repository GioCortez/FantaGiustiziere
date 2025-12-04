package org.fanta.corte.services;

import org.apache.commons.collections4.CollectionUtils;
import org.fanta.corte.datamodel.Campionato;
import org.fanta.corte.datamodel.Player;
import org.fanta.corte.services.exception.LimitReachedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * This class is used to create and calculate all the possible permutations of a
 * given (in the constructor) list of players and their results
 *
 * @author g.cortesi
 */
public class CalendarPermutator {

    private static final Logger LOGGER = LoggerFactory.getLogger(CalendarPermutator.class.getSimpleName());

    private final AtomicLong permutationCounter = new AtomicLong();
    private final BergerAlgorithm bergerAlgorithm = new BergerAlgorithm();
    private Map<String, Player> players;
    private BigDecimal homeAdvantage;
    private final Map<Player, LongAdder[]> statistics = new ConcurrentHashMap<>();
    private long calendarsToPrint = 11;
    private final Map<Player, List<Campionato>> calendarsToBePrinted = new ConcurrentHashMap<>();
    private final AtomicBoolean limitReached = new AtomicBoolean(false);
    private final int parallelThreshold = 3;

    public CalendarPermutator(Map<String, Player> players, BigDecimal homeAdvantage) {
        resetCounter();
        this.players = players;
        this.homeAdvantage = homeAdvantage;
    }

    private void resetCounter() {
        permutationCounter.set(0);
        statistics.clear();
        calendarsToBePrinted.clear();
        limitReached.set(false);
    }

    public long permuteCalendars(long permutationLimits) {
        resetCounter();
        Set<String> squadre = players.keySet();

        ForkJoinPool pool = new ForkJoinPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
        try {
            pool.invoke(new PermutationTask(new ArrayList<>(squadre), new ArrayList<>(), permutationLimits));
        } catch (LimitReachedException e) {
            LOGGER.info("Limit ({}) have been reached", permutationLimits);
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(1, TimeUnit.MINUTES)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warn("Interrupted while awaiting ForkJoinPool termination", e);
            }
        }

        // Writing absolute and relative statistics for each player
        for (Entry<Player, LongAdder[]> entry : statistics.entrySet()) {
            long[] totals = Arrays.stream(entry.getValue()).mapToLong(LongAdder::longValue).toArray();
            LOGGER.info("Relative Statistics for: {} -> {}", entry.getKey(), Arrays.toString(totals));
            int[] percent = new int[entry.getValue().length];
            long processed = permutationCounter.get();
            if (processed > 0) {
                for (int i = 0; i < totals.length; i++) {
                    percent[i] = (int) (totals[i] * 100.0 / processed + 0.5);
                }
            }
            LOGGER.info("Percent Statistics for : {} -> {}", entry.getKey(), Arrays.toString(percent));
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

        return permutationCounter.get();
    }

    private void processPermutation(List<String> orderedElements, long permutationLimits) {

        if (limitReached.get()) {
            return;
        }

        if (permutationLimits > 0 && permutationCounter.get() >= permutationLimits) {
            limitReached.set(true);
            return;
        }

        LOGGER.debug("{} -> Calculating calendar from ordered elements: {}", permutationCounter.get(), orderedElements);
        Campionato c = bergerAlgorithm.runAlgoritmoDiBerger2(orderedElements.toArray(new String[0]), players,
                homeAdvantage);

        Map<Player, Integer> classifica = c.calculate();

        int posizione = 0;
        for (Entry<Player, Integer> entry : classifica.entrySet()) {

            LongAdder[] positions = statistics.computeIfAbsent(entry.getKey(),
                    key -> buildPositionsArray(classifica.size()));
            positions[posizione].increment();

            if (posizione == 0) {
                LOGGER.debug("Winner: {} ({}) {}", entry.getKey(), entry.getKey().getTotalPoints(), entry.getValue());

                if (positions[posizione].longValue() <= calendarsToPrint) {
                    calendarsToBePrinted.computeIfAbsent(entry.getKey(),
                            key -> Collections.synchronizedList(new ArrayList<>())).add(c);
                }

            } else {
                LOGGER.debug("{} ({}) {}", entry.getKey(), entry.getKey().getTotalPoints(), entry.getValue());
            }

            posizione++;

        }
        long processed = permutationCounter.incrementAndGet();

        if (permutationLimits > 0 && processed >= permutationLimits) {
            LOGGER.warn("Permutation calculation interrupted since limit {} was reached!", permutationLimits);
            limitReached.set(true);
            throw new LimitReachedException("limit reached!");
        }
    }

    private LongAdder[] buildPositionsArray(int size) {
        LongAdder[] adders = new LongAdder[size];
        for (int i = 0; i < size; i++) {
            adders[i] = new LongAdder();
        }
        return adders;
    }

    private class PermutationTask extends RecursiveAction {

        private static final long serialVersionUID = 1L;

        private List<String> remaining;
        private List<String> currentPermutation;
        private long permutationLimits;

        public PermutationTask(List<String> remaining, List<String> currentPermutation, long permutationLimits) {
            this.remaining = remaining;
            this.currentPermutation = currentPermutation;
            this.permutationLimits = permutationLimits;
        }

        @Override
        protected void compute() {
            if (limitReached.get()) {
                return;
            }

            if (remaining.isEmpty()) {
                processPermutation(currentPermutation, permutationLimits);
                return;
            }

            if (remaining.size() > parallelThreshold) {
                List<PermutationTask> tasks = new ArrayList<>();
                for (int i = 0; i < remaining.size(); i++) {
                    List<String> nextCurrent = new ArrayList<>(currentPermutation);
                    nextCurrent.add(remaining.get(i));

                    List<String> nextRemaining = new ArrayList<>(remaining);
                    nextRemaining.remove(i);
                    tasks.add(new PermutationTask(nextRemaining, nextCurrent, permutationLimits));
                }
                invokeAll(tasks);
            } else {
                for (int i = 0; i < remaining.size(); i++) {
                    List<String> nextCurrent = new ArrayList<>(currentPermutation);
                    nextCurrent.add(remaining.get(i));

                    List<String> nextRemaining = new ArrayList<>(remaining);
                    nextRemaining.remove(i);
                    new PermutationTask(nextRemaining, nextCurrent, permutationLimits).compute();
                }
            }
        }
    }

    public static void main(String[] args) {
        long[] array = new long[12];
        LOGGER.info("{}", array);
    }

}
