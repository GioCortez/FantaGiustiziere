package org.fanta.corte;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@SpringBootApplication
@EnableScheduling
public class FantaMain {

	public static void main(String[] args) {
		SpringApplication.run(FantaMain.class, args);
	}

	/**
	 * Dedicated executor for async computation jobs.
	 * Using a bounded thread pool instead of the common ForkJoinPool avoids blocking
	 * non-blocking pool threads with the long awaitTermination call inside CalendarPermutator.
	 */
	@Bean(name = "computationExecutor")
	public Executor computationExecutor() {
		ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
		exec.setCorePoolSize(2);
		exec.setMaxPoolSize(4);
		exec.setQueueCapacity(10);
		exec.setThreadNamePrefix("computation-");
		exec.setWaitForTasksToCompleteOnShutdown(false);
		exec.initialize();
		return exec;
	}

}
