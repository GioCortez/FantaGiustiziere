package org.fanta.corte.services;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.fanta.corte.datamodel.Campionato;
import org.fanta.corte.datamodel.Giornata;
import org.fanta.corte.datamodel.Partita;
import org.fanta.corte.datamodel.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to calculate the calendar based on the berger algorithm.
 * Main method takes a list of string as input and output a "Campionato" object,
 * which is the POJO representation of a calendar
 * 
 * @author g.cortesi
 *
 */
public class BergerAlgorithm {

	private static final Logger LOGGER = LoggerFactory.getLogger(BergerAlgorithm.class.getSimpleName());

	public Campionato runAlgoritmoDiBerger2(String[] squadre, Map<String, Player> players, BigDecimal homeAdvantage) {

		Campionato calendario = new Campionato(homeAdvantage);
		int totalTeams = squadre.length;
		int giornate = totalTeams - 1;

		/* crea gli array per le due liste in casa e fuori */
		String[] casa = new String[totalTeams / 2];
		String[] trasferta = new String[totalTeams / 2];

		for (int i = 0; i < totalTeams / 2; i++) {
			casa[i] = squadre[i];
			trasferta[i] = squadre[totalTeams - 1 - i];
		}
		int numeroGiornata = 0;
		for (int i = 0; i < giornate; i++) {
			/* stampa le partite di questa giornata */
			LOGGER.debug("{} Giornata", i + 1);
			Giornata g = new Giornata(calendario);
			numeroGiornata = i + 1;
			g.setId(numeroGiornata);

			/* alterna le partite in casa e fuori */
			if (i % 2 == 0) {
				for (int j = 0; j < totalTeams / 2; j++) {
					Partita p = new Partita(g, players.get(trasferta[j]), players.get(casa[j]));
					p.calculate(numeroGiornata);
					g.getPartite().add(p);
					LOGGER.debug("{}  {}-{}", j + 1, trasferta[j], casa[j]);
				}
			} else {
				for (int j = 0; j < totalTeams / 2; j++) {
					Partita p = new Partita(g, players.get(casa[j]), players.get(trasferta[j]));
					p.calculate(numeroGiornata);
					g.getPartite().add(p);
					LOGGER.debug("{}  {}-{}", j + 1, casa[j], trasferta[j]);
				}
			}

			// Ruota in gli elementi delle liste, tenendo fisso il primo elemento
			// Salva l'elemento fisso
			String pivot = casa[0];

			/*
			 * sposta in avanti gli elementi di "trasferta" inserendo all'inizio l'elemento
			 * casa[1] e salva l'elemento uscente in "riporto"
			 */

			String riporto = trasferta[trasferta.length - 1];
			trasferta = shiftRight(trasferta, casa[1]);

			/*
			 * sposta a sinistra gli elementi di "casa" inserendo all'ultimo posto
			 * l'elemento "riporto"
			 */

			casa = shiftLeft(casa, riporto);

			// ripristina l'elemento fisso
			casa[0] = pivot;

			calendario.getGiornate().add(g);

		}

		Iterator<Giornata> iter = calendario.getGiornate().iterator();
		List<Giornata> gironeRitorno = new ArrayList<>();
		while (iter.hasNext()) {

			Giornata g = iter.next();
			Giornata ritorno = new Giornata(calendario);
			numeroGiornata++;
			ritorno.setId(numeroGiornata);
			for (Partita p : g.getPartite()) {
				Partita rit = new Partita(ritorno, p.getTrasferta(), p.getCasa());
				rit.calculate(numeroGiornata);
				ritorno.getPartite().add(rit);
			}
			gironeRitorno.add(ritorno);
		}
		calendario.getGiornate().addAll(gironeRitorno);
		LOGGER.debug("Calculated calendario with {} giornate from ordered list: {}", calendario.getGiornate().size(),
				squadre);
		LOGGER.debug("{}", calendario);

		return calendario;
	}

	private String[] shiftLeft(String[] data, String add) {
		String[] temp = new String[data.length];
		for (int i = 0; i < data.length - 1; i++) {
			temp[i] = data[i + 1];
		}
		temp[data.length - 1] = add;
		return temp;
	}

	private String[] shiftRight(String[] data, String add) {
		String[] temp = new String[data.length];
		for (int i = 1; i < data.length; i++) {
			temp[i] = data[i - 1];
		}
		temp[0] = add;
		return temp;
	}

	public static void main(String[] args) {
		List<String> squadre = Arrays.asList("giorgio", "alberto", "gabriele", "stefano", "ivan", "riccardo",
				"francesco", "fabio", "diego", "pino", "mosta", "sergio");

//		BergerAlgorithm b = new BergerAlgorithm();
//		for (int i = 0; i < 3; i++) {
//			Collections.shuffle(squadre);
//			b.runAlgoritmoDiBerger2((String[]) squadre.toArray());
//		}
	}
}
