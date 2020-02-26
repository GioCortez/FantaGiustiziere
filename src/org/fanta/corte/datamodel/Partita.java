package org.fanta.corte.datamodel;

import java.math.BigDecimal;

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
		punteggioCasa = punteggioCasa.add(giornata.getCampionato().getHomeAdvantage());
		punteggioTrasf = trasferta.getResults().get(numeroGiornata);
		goalCasa = getGoals(punteggioCasa);
		goalTrasf = getGoals(punteggioTrasf);
	}

	public int getGoals(BigDecimal punteggio) {
		if (punteggio.compareTo(BigDecimal.valueOf(66)) < 0) {
			return 0;
		} else if (punteggio.compareTo(BigDecimal.valueOf(72)) < 0) {
			return 1;
		} else if (punteggio.compareTo(BigDecimal.valueOf(78)) < 0) {
			return 2;
		} else if (punteggio.compareTo(BigDecimal.valueOf(84)) < 0) {
			return 3;
		} else if (punteggio.compareTo(BigDecimal.valueOf(90)) < 0) {
			return 4;
		} else if (punteggio.compareTo(BigDecimal.valueOf(96)) < 0) {
			return 5;
		} else if (punteggio.compareTo(BigDecimal.valueOf(102)) < 0) {
			return 6;
		} else if (punteggio.compareTo(BigDecimal.valueOf(108)) < 0) {
			return 7;
		} else {
			throw new IllegalStateException(
					"Si vabbè quanti cazzi di punti hai fatto in una sola giornata???" + punteggio);
		}

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
