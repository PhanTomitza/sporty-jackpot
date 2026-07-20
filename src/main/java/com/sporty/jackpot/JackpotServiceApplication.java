package com.sporty.jackpot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class JackpotServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(JackpotServiceApplication.class, args);
	}

}
