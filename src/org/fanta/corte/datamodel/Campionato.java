package org.fanta.corte.datamodel;

import java.util.ArrayList;
import java.util.List;

public class Campionato {

	private List<Giornata> giornate;
	private String hashcode;

	public List<Giornata> getGiornate() {
		if (giornate == null) {
			giornate = new ArrayList<>();
		}
		return giornate;
	}

	public void setGiornate(List<Giornata> giornate) {
		this.giornate = giornate;
	}

	public String getHashcode() {
		return hashcode;
	}

	public void setHashcode(String hashcode) {
		this.hashcode = hashcode;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		for (Giornata giornata : giornate) {
			sb.append("Giornata " + giornata.getId() + "\n");
			for (Partita partita : giornata.getPartite()) {
				sb.append(partita.toString() + "\n");
			}
		}
		return sb.toString();
	}

}
