package org.fanta.corte;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FantaMain {

	public static void main(String[] args) {
		SpringApplication.run(FantaMain.class, args);
	}

}
