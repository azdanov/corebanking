package dev.azdanov.corebanking;

import org.springframework.boot.SpringApplication;

public class TestCorebankingApplication {

    static void main(String[] args) {
        SpringApplication.from(CorebankingApplication::main)
            .with(TestcontainersConfiguration.class)
            .run(args);
    }
}
