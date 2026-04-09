package org.fanta.corte;

import java.math.BigDecimal;

import org.fanta.corte.services.FantaGiustiziere;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FantaMain implements CommandLineRunner {

	@Value("${fanta.file-path}")
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

	public static void main(String[] args) {
		SpringApplication.run(FantaMain.class, args);
	}

	@Override
	public void run(String... args) {
		FantaGiustiziere.permuteCalendars(filePath, numberOfPlayers, homeAdvantage, permutationLimit, threads);
	}

}
