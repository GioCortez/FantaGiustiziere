package org.fanta.corte;

import java.math.BigDecimal;

import org.fanta.corte.services.FantaGiustiziere;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FantaMain implements CommandLineRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(FantaMain.class.getSimpleName());

	@Value("${fanta.filePath:}")
	private String filePath;

	@Value("${fanta.players:12}")
	private int numberOfPlayers;

	@Value("${fanta.home-advantage:2}")
	private BigDecimal homeAdvantage;

	@Value("${fanta.permutation-limit:1000}")
	private long permutationLimit;

	/**
	 * Number of threads to use for permutation:
	 *   1  = single-thread (original behaviour)
	 *  -1  = auto (Runtime.availableProcessors)
	 *   N  = fixed pool of N threads (capped at number of players)
	 */
	@Value("${fanta.threads:-1}")
	private int threads;

	@Value("${server.port:5000}")
	private int serverPort;

	public static void main(String[] args) {
		SpringApplication.run(FantaMain.class, args);
	}

	@Override
	public void run(String... args) {
		if (filePath == null || filePath.isBlank()) {
			LOGGER.info("No fanta.filePath configured — running in web mode. Use the UI at http://localhost:{}", serverPort);
			return;
		}
		LOGGER.info("Starting with configuration:");
		LOGGER.info("  fanta.filePath          = {}", filePath);
		LOGGER.info("  fanta.players           = {}", numberOfPlayers);
		LOGGER.info("  fanta.home-advantage    = {}", homeAdvantage);
		LOGGER.info("  fanta.permutation-limit = {}", permutationLimit);
		LOGGER.info("  fanta.threads           = {}", threads);
		FantaGiustiziere.permuteCalendars(filePath, numberOfPlayers, homeAdvantage, permutationLimit, threads);
	}

}
