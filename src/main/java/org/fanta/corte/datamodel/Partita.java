package org.fanta.corte.datamodel;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class Partita {

	private Player casa;
	private Player trasferta;
	private BigDecimal punteggioCasa;
	private BigDecimal punteggioTrasf;
	private int goalCasa;
	private int goalTrasf;
	private final Giornata giornata;

	public Partita(Giornata giornata, Player casa, Player trasferta) {
		super();
		this.casa = casa;
		this.trasferta = trasferta;
		this.giornata = giornata;
	}

	public Player getCasa() {
		return casa;
	}

	public void setCasa(Player casa) {
		this.casa = casa;
	}

	public Player getTrasferta() {
		return trasferta;
	}

	public void setTrasferta(Player trasferta) {
		this.trasferta = trasferta;
	}

	@Override
	public String toString() {
		return casa.getName() + " " + goalCasa + "(" + punteggioCasa + ") - " + trasferta.getName() + " " + goalTrasf
				+ "(" + punteggioTrasf + ")";
	}

	public void calculate(Integer numeroGiornata) {
		punteggioCasa = casa.getResults().get(numeroGiornata);
		if (punteggioCasa == null) {
			throw new IllegalStateException(String.format(
					"Punteggio mancante per '%s' alla giornata %d", casa.getName(), numeroGiornata));
		}
		if (!giornata.isNeutral()) {
			punteggioCasa = punteggioCasa.add(giornata.getCampionato().getHomeAdvantage());
		}
		punteggioTrasf = trasferta.getResults().get(numeroGiornata);
		if (punteggioTrasf == null) {
			throw new IllegalStateException(String.format(
					"Punteggio mancante per '%s' alla giornata %d", trasferta.getName(), numeroGiornata));
		}
		int goalLimit  = giornata.getCampionato().getGoalLimit();
		int goalOffset = giornata.getCampionato().getGoalOffset();
		goalCasa  = getGoals(punteggioCasa,  goalLimit, goalOffset);
		goalTrasf = getGoals(punteggioTrasf, goalLimit, goalOffset);
	}

	/**
	 * Converts a fantasy score to a goal count using configurable thresholds.
	 *
	 * @param punteggio  the effective score (home advantage already applied if applicable)
	 * @param goalLimit  minimum score to register 1 goal (default 66)
	 * @param goalOffset points range per additional goal (default 6)
	 */
	public static int getGoals(BigDecimal punteggio, int goalLimit, int goalOffset) {
		if (punteggio.compareTo(BigDecimal.valueOf(goalLimit)) < 0) {
			return 0;
		}
		return punteggio.subtract(BigDecimal.valueOf(goalLimit))
				.divide(BigDecimal.valueOf(goalOffset), 0, RoundingMode.FLOOR)
				.intValue() + 1;
	}

	public Giornata getGiornata() {
		return giornata;
	}

	public int getGoalCasa() {
		return goalCasa;
	}

	public void setGoalCasa(int goalCasa) {
		this.goalCasa = goalCasa;
	}

	public int getGoalTrasf() {
		return goalTrasf;
	}

	public void setGoalTrasf(int goalTrasf) {
		this.goalTrasf = goalTrasf;
	}

}
