package org.fanta.corte.datamodel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class Campionato {

	private List<Giornata> giornate;
	private String hashcode;
	private BigDecimal homeAdvantage;

	public Campionato(BigDecimal homeAdvantage) {
		this.homeAdvantage = homeAdvantage;
	}

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

	public BigDecimal getHomeAdvantage() {
		return homeAdvantage;
	}

	public void setHomeAdvantage(BigDecimal homeAdvantage) {
		this.homeAdvantage = homeAdvantage;
	}

	public Map<Player, Integer> calculate() {
		Map<Player, Integer> classifica = new HashMap<>();
		for (Giornata g : giornate) {
			for (Partita p : g.getPartite()) {
				if (p.getGoalCasa() > p.getGoalTrasf()) {
					// Home win!
					addPoints(classifica, p.getCasa(), 3);
				} else if (p.getGoalTrasf() > p.getGoalCasa()) {
					// Away win!
					addPoints(classifica, p.getTrasferta(), 3);
				} else {
					// Draw
					addPoints(classifica, p.getCasa(), 1);
					addPoints(classifica, p.getTrasferta(), 1);
				}
			}
		}

		return classifica.entrySet().stream().sorted(new ScoreComparator())
				.collect(Collectors.toMap(Entry::getKey, Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
	}

	private class ScoreComparator implements Comparator<Map.Entry<Player, Integer>> {

		@Override
		public int compare(Entry<Player, Integer> o1, Entry<Player, Integer> o2) {
			int valueComparison = o2.getValue().compareTo(o1.getValue());
			if (valueComparison == 0) {
				// if points comparison is the same, then go with points sum
				return o2.getKey().getTotalPoints().compareTo(o1.getKey().getTotalPoints());
			} else {
				return valueComparison;
			}
		}

	}

	private void addPoints(Map<Player, Integer> classifica, Player p, int pointsToAdd) {
		if (classifica.containsKey(p)) {
			Integer punti = classifica.get(p);
			punti = punti + pointsToAdd;
			classifica.put(p, punti);
		} else {
			classifica.put(p, Integer.valueOf(pointsToAdd));
		}
	}

}
