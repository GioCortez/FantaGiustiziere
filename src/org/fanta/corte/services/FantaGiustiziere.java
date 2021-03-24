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
	public static final String SAMPLE_XLSX_FILE_PATH2 = "c:\\app\\Calendario_II-Your-best-Fantacalcio.xlsx";
	public static final String SAMPLE_XLSX_FILE_PATH3 = "c:\\app\\Calendario_XXXII-Fantacalcio-di-via-Adda.xlsx";

	public static void main(String[] args) {

		try {
			// Step 1: reads excel of "effective results" and parse them into
			// players-results map
			Instant beforeParsing = Instant.now();

			BigDecimal homeAdvantage = BigDecimal.valueOf(2);
			Map<String, Player> fantaPlayers = ResultsParser.readExcel(SAMPLE_XLSX_FILE_PATH3, 12, homeAdvantage);

			Instant afterParsing = Instant.now();

			long timeElapsed = Duration.between(beforeParsing, afterParsing).toMillis() / 1000; // in seconds
			LOGGER.info("Seconds taken to parse the effective results: {}", timeElapsed);

			// Step 2: permute the possible calendars for the given players-results
			CalendarPermutator permutator = new CalendarPermutator(fantaPlayers, homeAdvantage);

			long permutationNumber = permutator.permuteCalendars(-1);

			Instant afterPermuting = Instant.now();

			timeElapsed = Duration.between(afterParsing, afterPermuting).toMillis() / 1000; // in seconds

			LOGGER.info("Seconds taken to permute {} calendars: {}", permutationNumber, timeElapsed);

		} catch (InvalidFormatException | IOException e) {
			LOGGER.error("An error occurred while parsing the effective results file: {}", e.getMessage(), e);
		}

	}

}
