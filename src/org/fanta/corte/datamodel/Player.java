package org.fanta.corte.datamodel;

import java.math.BigDecimal;
import java.util.Map;
import java.util.TreeMap;

public class Player {

	private String name;
	private String id;
	private Map<Integer, BigDecimal> results;

	public Player(String name, String id) {
		super();
		this.name = name;
		this.id = id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Map<Integer, BigDecimal> getResults() {
		if (results == null) {
			results = new TreeMap<>();
		}
		return results;
	}

	public void setResults(Map<Integer, BigDecimal> results) {
		this.results = results;
	}

}
