package com.mft.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MftIntegrationEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(MftIntegrationEngineApplication.class, args);
	}

}
