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

	public static void permuteCalendars(String filePath, int numberOfPlayers, BigDecimal homeAdvantage,
			long permutationLimits, int threads) {

		try {
			Instant beforeParsing = Instant.now();

			Map<String, Player> fantaPlayers = ResultsParser.readExcel(filePath, numberOfPlayers, homeAdvantage, false);

			long timeElapsed = Duration.between(beforeParsing, Instant.now()).toMillis() / 1000;
			LOGGER.info("Seconds taken to parse the effective results: {}", timeElapsed);

			CalendarPermutator permutator = new CalendarPermutator(fantaPlayers, homeAdvantage);

			Instant beforePermuting = Instant.now();
			long permutationNumber = permutator.permuteCalendars(permutationLimits, threads);

			timeElapsed = Duration.between(beforePermuting, Instant.now()).toMillis() / 1000;
			LOGGER.info("Seconds taken to permute {} calendars: {}", permutationNumber, timeElapsed);

		} catch (InvalidFormatException | IOException e) {
			LOGGER.error("An error occurred while parsing the effective results file: {}", e.getMessage(), e);
		}
	}

}
