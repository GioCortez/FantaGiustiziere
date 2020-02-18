package org.fanta.corte.services;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.fanta.corte.datamodel.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FantaGiustiziere {

	private static final Logger LOGGER = LoggerFactory.getLogger(FantaGiustiziere.class.getSimpleName());

	public static final String SAMPLE_XLSX_FILE_PATH = "c:\\app\\Calendario_XXXI-Fantacalcio-Via-Adda.xlsx";

	public static void main(String[] args) {

		try {
			// Reads excel of "effective results" and parse them into players-results map
			Instant beforeParsing = Instant.now();

			Map<String, Player> fantaPlayers = ResultsParser.readExcel(SAMPLE_XLSX_FILE_PATH, 12,
					BigDecimal.valueOf(2));

			// List<String> squadre = Arrays.asList("giorgio", "alberto", "gabriele",
			// "stefano", "ivan", "riccardo",
			// "francesco", "fabio", "diego", "pino", "mosta", "sergio");

			// List<String> squadre = Arrays.asList("giorgio", "alberto", "gabriele");

			Instant afterParsing = Instant.now();

			long timeElapsed = Duration.between(beforeParsing, afterParsing).toMillis() / 1000; // in seconds
			LOGGER.info("Seconds taken to parse the effective results: {}", timeElapsed);

			CalendarPermutator permutator = new CalendarPermutator(fantaPlayers);

			try {
				permutator.permuteCalendars(3);
			} catch (IllegalStateException e) {
				LOGGER.info("Limit have been reached");
			}

			Instant afterPermuting = Instant.now();

			timeElapsed = Duration.between(afterParsing, afterPermuting).toMillis() / 1000; // in seconds

			LOGGER.info("Seconds taken to permute calendars: {}", timeElapsed);

			// Collections.shuffle(squadre);
			// for (int i=0; i < 1000; i++) {
			// runAlgoritmoDiBerger2((String[]) squadre.toArray());
			// }

		} catch (InvalidFormatException | IOException e) {
			LOGGER.error("An error occurred while parsing the effective results file: {}", e.getMessage(), e);
		}

	}

}
