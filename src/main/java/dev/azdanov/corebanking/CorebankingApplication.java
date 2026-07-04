package dev.azdanov.corebanking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CorebankingApplication {

    public static void main(String[] args) {
        SpringApplication.run(CorebankingApplication.class, args);
    }
}
