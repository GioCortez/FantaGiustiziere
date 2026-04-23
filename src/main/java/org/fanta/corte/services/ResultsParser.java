package org.fanta.corte.services;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.fanta.corte.datamodel.Campionato;
import org.fanta.corte.datamodel.Giornata;
import org.fanta.corte.datamodel.Partita;
import org.fanta.corte.datamodel.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResultsParser {

	private static final Logger LOGGER = LoggerFactory.getLogger(ResultsParser.class.getSimpleName());
	private static final String REGEX = "[\\d]+";
	private static final Pattern DAYTITLEPATTERN = Pattern.compile(REGEX, Pattern.MULTILINE);

	public static Map<String, Player> readExcel(String excelPath, int numberOfPlayers, BigDecimal homeAddition,
			boolean validate) throws InvalidFormatException, IOException {
		// Creating a Workbook from an Excel file (.xls or .xlsx)
		try (Workbook workbook = WorkbookFactory.create(new File(excelPath))) {
			Map<String, Player> fantagiocatori = new HashMap<>();

			// Getting the Sheet at index zero
			Sheet sheet = workbook.getSheetAt(0);

			int resultRows = numberOfPlayers / 2 - 1;

			// Create a DataFormatter to format and get each cell's value as String
			DataFormatter dataFormatter = new DataFormatter();

			// ── Pass 1: detect player count from giornata 1 and the highest giornata seen ──
			// This determines how many legs the season has and which are neutral (no home advantage).
			int maxGiornata = 0;
			int homePlayersInFirstLeg = 0;
			int firstLegDataRow = -1, firstLegDataCol = -1;

			for (Row scanRow : sheet) {
				for (Cell scanCell : scanRow) {
					String val = dataFormatter.formatCellValue(scanCell);
					if (val.contains("Giornata lega")) {
						Matcher sm = DAYTITLEPATTERN.matcher(val);
						int num = -1;
						while (sm.find()) num = Integer.parseInt(sm.group(0));
						if (num > maxGiornata) maxGiornata = num;
						if (num == 1 && firstLegDataRow < 0) {
							firstLegDataRow = scanCell.getRowIndex() + 1;
							firstLegDataCol = scanCell.getColumnIndex();
						}
					}
				}
			}
			if (firstLegDataRow >= 0) {
				for (int i = 0; ; i++) {
					Row r = sheet.getRow(firstLegDataRow + i);
					if (r == null) break;
					Cell c = r.getCell(firstLegDataCol);
					String name = dataFormatter.formatCellValue(c).trim();
					if (name.isEmpty() || name.contains("Giornata")) break;
					homePlayersInFirstLeg++;
				}
			}
			// Each home-side slot represents one match (home + away player), so total = home slots * 2
			int detectedPlayerCount = homePlayersInFirstLeg * 2;
			// legSize = round-robin matchdays per leg (each player meets every other player once)
			int legSize = detectedPlayerCount > 1 ? detectedPlayerCount - 1 : 1;
			int totalLegs = maxGiornata > 0 ? (int) Math.ceil((double) maxGiornata / legSize) : 1;
			// Rule: if total legs is odd, the surplus last leg is played at neutral ground.
			//       If total legs is even (or ≤ 1), all legs have home advantage.
			int legsWithAdvantage = (totalLegs <= 1 || totalLegs % 2 == 0) ? totalLegs : totalLegs - 1;
			LOGGER.info("Schedule: {} players, {} giornate, {} legs ({} with home advantage, {} neutral)",
					detectedPlayerCount, maxGiornata, totalLegs, legsWithAdvantage, totalLegs - legsWithAdvantage);

			// ── Pass 2: parse scores ──────────────────────────────────────────────────────
			Iterator<Row> rowIterator = sheet.rowIterator();
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();

				// Now let's iterate over the columns of the current row
				Iterator<Cell> cellIterator = row.cellIterator();

				while (cellIterator.hasNext()) {
					Cell cell = cellIterator.next();
					String cellValue = dataFormatter.formatCellValue(cell);

					if (cellValue.contains("Giornata lega")) {
						final Matcher matcher = DAYTITLEPATTERN.matcher(cellValue);
						Integer giornataNumero = null;
						while (matcher.find()) {
							giornataNumero = Integer.parseInt(matcher.group(0));
						}

						LOGGER.info("New giornata detected: {} {}", cellValue, giornataNumero);

						// First row results is below title
						int currentRow = cell.getRowIndex() + 1;
						int currentColumn = cell.getColumnIndex();

						// Leg index is 0-based; legs beyond legsWithAdvantage are neutral.
						int legIndex = giornataNumero != null ? (giornataNumero - 1) / legSize : 0;
						boolean hasHomeAdv = detectedPlayerCount > 0 && legIndex < legsWithAdvantage;

						// iterating results: col+0=home name, col+1=home score, col+2=away score, col+3=away name, col+4=goal result "X-Y"
						for (int i = 0; i <= resultRows; i++) {

							Row dataRow = sheet.getRow(currentRow + i);
							if (dataRow == null) break;

							Cell homeCell = dataRow.getCell(currentColumn);
							String homeName = dataFormatter.formatCellValue(homeCell).trim();
							if (homeName.isEmpty() || homeName.contains("Giornata")) break;

							Cell awayCell = dataRow.getCell(currentColumn + 3);
							String awayName = dataFormatter.formatCellValue(awayCell).trim();
							if (awayName.isEmpty() || awayName.contains("Giornata")) break;

							BigDecimal rawHomeScore = readScore(dataRow.getCell(currentColumn + 1), dataFormatter);
							BigDecimal rawAwayScore = readScore(dataRow.getCell(currentColumn + 2), dataFormatter);

							// Store parsed scores first — Player objects must exist before Partita.calculate()
							// is called in the validation block below.
							// Non-neutral legs: strip HA so Partita.calculate() can add it back correctly.
							// Neutral legs: store as-is (no advantage was applied in the Excel).
							Player home = fantagiocatori.computeIfAbsent(homeName, name -> {
								LOGGER.info("Creating new player: {}", name);
								return new Player(name, name);
							});
							home.addResult(giornataNumero, hasHomeAdv ? rawHomeScore.subtract(homeAddition) : rawHomeScore);

							Player away = fantagiocatori.computeIfAbsent(awayName, name -> {
								LOGGER.info("Creating new player: {}", name);
								return new Player(name, name);
							});
							away.addResult(giornataNumero, rawAwayScore);

							// Validate against the goal result column (e.g. "3-1")
							if (validate) {
								Cell goalResultCell = dataRow.getCell(currentColumn + 4);
								if (goalResultCell != null) {
									String goalResult = dataFormatter.formatCellValue(goalResultCell).trim();
									if (!goalResult.isEmpty() && goalResult.contains("-")) {
										String[] parts = goalResult.split("-");
										if (parts.length == 2) {
											try {
												int expectedHome = Integer.parseInt(parts[0].trim());
												int expectedAway = Integer.parseInt(parts[1].trim());

												// Step 1: Excel structure check.
												// col B already holds the effective score (pure + HA for non-neutral,
												// pure for neutral), so we verify getGoals(colB) matches col+4 directly.
												int directHome = Partita.getGoals(rawHomeScore);
												int directAway = Partita.getGoals(rawAwayScore);
												if (directHome != expectedHome || directAway != expectedAway) {
													throw new IllegalStateException(String.format(
															"Giornata %d, %s vs %s: struttura Excel non valida — " +
															"risultato %s ma punteggi %.1f/%.1f → %d-%d",
															giornataNumero, homeName, awayName,
															goalResult,
															rawHomeScore.doubleValue(), rawAwayScore.doubleValue(),
															directHome, directAway));
												}

												// Step 2: DTO pipeline check.
												// Build a real Campionato/Giornata/Partita using the parsed Player
												// scores and run Partita.calculate(). This catches any inconsistency
												// between score parsing and the simulation model — in particular,
												// neutral-leg handling where HA must not be applied.
												Campionato tempCamp = new Campionato(homeAddition);
												Giornata tempGiornata = new Giornata(tempCamp);
												tempGiornata.setId(giornataNumero);
												tempGiornata.setNeutral(!hasHomeAdv);
												Partita tempPartita = new Partita(tempGiornata, home, away);
												tempPartita.calculate(giornataNumero);

												if (tempPartita.getGoalCasa() != expectedHome || tempPartita.getGoalTrasf() != expectedAway) {
													throw new IllegalStateException(String.format(
															"Giornata %d, %s vs %s (%s): risultato atteso %s ma il modello calcola %d-%d",
															giornataNumero, homeName, awayName,
															hasHomeAdv ? String.format("vantaggio +%.1f", homeAddition.doubleValue()) : "neutrale",
															goalResult,
															tempPartita.getGoalCasa(), tempPartita.getGoalTrasf()));
												}

											} catch (NumberFormatException e) {
												LOGGER.warn("Could not parse goal result '{}' for giornata {}, {} vs {} — skipping validation",
														goalResult, giornataNumero, homeName, awayName);
											}
										}
									}
								}
							}
						}

					}

					LOGGER.debug("{} - {}: {}", cell.getRowIndex(), cell.getColumnIndex(), cellValue);
				}
			}

			for (Entry<String, Player> entry : fantagiocatori.entrySet()) {
				LOGGER.info("{} {}", entry.getKey(), entry.getValue().getName());
				for (Entry<Integer, BigDecimal> res : entry.getValue().getResults().entrySet()) {
					LOGGER.info("{} {}", res.getKey(), res.getValue());
				}
			}

			return fantagiocatori;
		}

	}

	/**
	 * Reads every match from the Excel and returns a standings table.
	 * The home score in col B is used as-is (it already includes any home advantage),
	 * so {@code getGoals(colB)} gives the correct goals for standings purposes.
	 *
	 * @return map from player name to int[]{played, won, drawn, lost, goalsFor, goalsAgainst}
	 */
	public static Map<String, int[]> readStandings(String excelPath, int numberOfPlayers)
			throws InvalidFormatException, IOException {
		try (Workbook workbook = WorkbookFactory.create(new File(excelPath))) {
			Map<String, int[]> standings = new HashMap<>();
			Sheet sheet = workbook.getSheetAt(0);
			int resultRows = numberOfPlayers / 2 - 1;
			DataFormatter dataFormatter = new DataFormatter();

			Iterator<Row> rowIterator = sheet.rowIterator();
			while (rowIterator.hasNext()) {
				Row row = rowIterator.next();
				Iterator<Cell> cellIterator = row.cellIterator();
				while (cellIterator.hasNext()) {
					Cell cell = cellIterator.next();
					String cellValue = dataFormatter.formatCellValue(cell);
					if (!cellValue.contains("Giornata lega")) continue;

					int currentRow = cell.getRowIndex() + 1;
					int currentColumn = cell.getColumnIndex();

					for (int i = 0; i <= resultRows; i++) {
						Row dataRow = sheet.getRow(currentRow + i);
						if (dataRow == null) break;

						String homeName = dataFormatter.formatCellValue(dataRow.getCell(currentColumn)).trim();
						if (homeName.isEmpty() || homeName.contains("Giornata")) break;

						String awayName = dataFormatter.formatCellValue(dataRow.getCell(currentColumn + 3)).trim();
						if (awayName.isEmpty() || awayName.contains("Giornata")) break;

						BigDecimal rawHomeScore = readScore(dataRow.getCell(currentColumn + 1), dataFormatter);
						BigDecimal rawAwayScore = readScore(dataRow.getCell(currentColumn + 2), dataFormatter);

						int homeGoals = Partita.getGoals(rawHomeScore);
						int awayGoals = Partita.getGoals(rawAwayScore);

						// [0]=played [1]=won [2]=drawn [3]=lost [4]=goalsFor [5]=goalsAgainst
						int[] hs = standings.computeIfAbsent(homeName, k -> new int[6]);
						int[] as_ = standings.computeIfAbsent(awayName, k -> new int[6]);

						hs[0]++; as_[0]++;
						hs[4] += homeGoals; as_[4] += awayGoals;
						hs[5] += awayGoals; as_[5] += homeGoals;

						if (homeGoals > awayGoals) {
							hs[1]++; as_[3]++;
						} else if (homeGoals == awayGoals) {
							hs[2]++; as_[2]++;
						} else {
							hs[3]++; as_[1]++;
						}
					}
				}
			}
			return standings;
		}
	}

	private static BigDecimal readScore(Cell cell, DataFormatter dataFormatter) {
		if (cell == null) {
			throw new IllegalArgumentException("Score cell is null — check Excel structure");
		}
		CellType type = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
		if (type == CellType.NUMERIC) {
			return BigDecimal.valueOf(cell.getNumericCellValue());
		}
		String raw = dataFormatter.formatCellValue(cell).trim().replace(",", ".");
		if (raw.isEmpty()) {
			throw new IllegalStateException("Score cell is empty — check Excel structure or player count setting");
		}
		try {
			return new BigDecimal(raw);
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			throw new IllegalStateException("Expected a numeric score but found: '" + raw + "'", e);
		}
	}

}