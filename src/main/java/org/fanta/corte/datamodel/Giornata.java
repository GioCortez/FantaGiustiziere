package org.fanta.corte.datamodel;

import java.util.ArrayList;
import java.util.List;

public class Giornata {

	private int id;
	private List<Partita> partite;
	private final Campionato campionato;

	public Giornata(Campionato campionato) {
		super();
		this.campionato = campionato;
	}

	public int getId() {
		return id;
	}

	public void setId(int id) {
		this.id = id;
	}

	public List<Partita> getPartite() {
		if (partite == null) {
			partite = new ArrayList<>();
		}
		return partite;
	}

	public void setPartite(List<Partita> partite) {
		this.partite = partite;
	}

	public Campionato getCampionato() {
		return campionato;
	}
}
