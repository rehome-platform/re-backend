package com.rehome.rehome;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class RehomeApplication {

	public static void main(String[] args) {
		SpringApplication.run(RehomeApplication.class, args);
	}

}
