package org.fanta.corte;

import java.math.BigDecimal;

import org.fanta.corte.services.FantaGiustiziere;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class FantaMain {
	
	public static final String SAMPLE_XLSX_FILE_PATH = "c:\\app\\Calendario_XXXI-Fantacalcio-Via-Adda.xlsx";
	public static final String SAMPLE_XLSX_FILE_PATH2 = "c:\\app\\Calendario_II-Your-best-Fantacalcio.xlsx";
	public static final String SAMPLE_XLSX_FILE_PATH3 = "C:\\Users\\g.cortesi\\OneDrive - Accenture\\Desktop\\Calendario_Fantacalcio-di-Via-Adda---XXXIV-edizione.xlsx";

	public static void main(String[] args) {
		SpringApplication.run(FantaMain.class, args);
		
		FantaGiustiziere.permuteCalendars(SAMPLE_XLSX_FILE_PATH3, 12, BigDecimal.valueOf(2), 1000l);
	}
	
	@Bean
	public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
		return args -> {
			System.out.println("Spring Boot application started!");
		};
	}
}