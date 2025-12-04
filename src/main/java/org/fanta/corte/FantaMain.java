package org.fanta.corte;

import org.fanta.corte.services.FantaGiustiziere;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;

@SpringBootApplication
public class FantaMain {

    public static final String SAMPLE_XLSX_FILE_PATH = "c:\\app\\Calendario_Fantacalcio-di-Via-Adda-XXXV-edizione.xlsx";
    public static final String SAMPLE_XLSX_FILE_PATH2 = "c:\\app\\Calendario_II-Your-best-Fantacalcio.xlsx";
    public static final String SAMPLE_XLSX_FILE_PATH3 = "C:\\Users\\g.cortesi\\OneDrive - Accenture\\Desktop\\Calendario_Fantacalcio-di-Via-Adda---XXXIV-edizione.xlsx";
    public static final String SAMPLE_XLSX_FILE_PATH4 = "C:\\app\\Calendario_Anno-2023-24.xlsx";

    public static void main(String[] args) {
        SpringApplication.run(FantaMain.class, args);

        FantaGiustiziere.permuteCalendars(SAMPLE_XLSX_FILE_PATH, 12, BigDecimal.valueOf(2), 100000);
    }

    @Bean
    public CommandLineRunner commandLineRunner(ApplicationContext ctx) {
        return args -> {
            System.out.println("Spring Boot application started!");
        };
    }
}