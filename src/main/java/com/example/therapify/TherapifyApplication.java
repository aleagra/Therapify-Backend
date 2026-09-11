package com.example.therapify;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableAsync
@EnableCaching
@EnableScheduling
@SpringBootApplication
public class TherapifyApplication {

	static {
		// Must run before the JPA/Hibernate context starts: pgjdbc issues a
		// "SET TimeZone" using the JVM default, and "America/Buenos_Aires" (the OS
		// default on some hosts) is a legacy alias postgres:16's tzdata rejects.
		// A static initializer (not a main() line) so this also applies when
		// @SpringBootTest builds the context directly, bypassing main().
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
	}

	public static void main(String[] args) {
		SpringApplication.run(TherapifyApplication.class, args);
	}

}
