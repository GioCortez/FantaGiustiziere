package org.fanta.corte.datamodel;

import java.math.BigDecimal;

public class Partita {

	private Player casa;
	private Player trasferta;
	private BigDecimal punteggioCasa;
	private BigDecimal punteggioTrasf;
	private int goalCasa;
	private int goalTrasf;

	public Partita(Player casa, Player trasferta) {
		super();
		this.casa = casa;
		this.trasferta = trasferta;
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

	public void calculate(Integer giornata) {
		punteggioCasa = casa.getResults().get(giornata);
		// TODO: add homeAdvantage
		punteggioTrasf = trasferta.getResults().get(giornata);
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
		} else {
			throw new IllegalStateException(
					"Si vabbè quanti cazzi di punti hai fatto in una sola giornata???" + punteggio);
		}

	}

}
