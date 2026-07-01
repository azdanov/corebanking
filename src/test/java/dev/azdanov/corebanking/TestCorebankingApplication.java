package dev.azdanov.corebanking;

import org.springframework.boot.SpringApplication;

public class TestCorebankingApplication {

	public static void main(String[] args) {
		SpringApplication.from(CorebankingApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
