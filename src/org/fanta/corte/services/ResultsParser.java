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
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.fanta.corte.datamodel.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ResultsParser {

	private static final Logger LOGGER = LoggerFactory.getLogger(ResultsParser.class.getSimpleName());
	public static final String SAMPLE_XLSX_FILE_PATH = "c:\\app\\Calendario_XXXI-Fantacalcio-Via-Adda.xlsx";

	private static final String REGEX = "[\\d]+";
	private static final Pattern DAYTITLEPATTERN = Pattern.compile(REGEX, Pattern.MULTILINE);

	public static Map<String, Player> readExcel(String excelPath, int numberOfPlayers, BigDecimal homeAddition)
			throws InvalidFormatException, IOException {
		// Creating a Workbook from an Excel file (.xls or .xlsx)
		try (Workbook workbook = WorkbookFactory.create(new File(excelPath))) {
			Map<String, Player> fantagiocatori = new HashMap<>();

			// Getting the Sheet at index zero
			Sheet sheet = workbook.getSheetAt(0);

			int resultRows = numberOfPlayers / 2 - 1;

			// Create a DataFormatter to format and get each cell's value as String
			DataFormatter dataFormatter = new DataFormatter();

			// 1. You can obtain a rowIterator and columnIterator and iterate over them
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

						// iterating "home" results
						for (int i = 0; i <= resultRows; i++) {

							String playerName = null;
							Cell player = sheet.getRow(currentRow + i).getCell(currentColumn);
							playerName = dataFormatter.formatCellValue(player);

							Player p = fantagiocatori.get(playerName);
							if (p == null) {
								LOGGER.info("Creating new player: {}", playerName);
								p = new Player(playerName, playerName);
								fantagiocatori.put(playerName, p);
							}

							Cell result = sheet.getRow(currentRow + i).getCell(currentColumn + 1);
							BigDecimal homeResult = BigDecimal.valueOf(result.getNumericCellValue());
							p.addResult(giornataNumero, homeResult.subtract(homeAddition));
						}

						// iterating "away" results
						for (int i = 0; i <= resultRows; i++) {

							String playerName = null;
							Cell player = sheet.getRow(currentRow + i).getCell(currentColumn + 3);
							playerName = dataFormatter.formatCellValue(player);

							Player p = fantagiocatori.get(playerName);
							if (p == null) {
								LOGGER.info("Creating new player: {}", playerName);
								p = new Player(playerName, playerName);
								fantagiocatori.put(playerName, p);
							}

							Cell result = sheet.getRow(currentRow + i).getCell(currentColumn + 2);
							p.addResult(giornataNumero, BigDecimal.valueOf(result.getNumericCellValue()));
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

	public static void main(String[] args) throws IOException, InvalidFormatException {
		readExcel(SAMPLE_XLSX_FILE_PATH, 12, BigDecimal.valueOf(2));
	}
}