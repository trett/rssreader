package com.trett.rss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableAutoConfiguration
public class RssApplication {

	public static void main(String[] args) {
		SpringApplication.run(RssApplication.class, args);
	}

}

