package com.lionfinance.ironkey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class IronkeyApplication {

	public static void main(String[] args) {
		SpringApplication.run(IronkeyApplication.class, args);
	}

}
