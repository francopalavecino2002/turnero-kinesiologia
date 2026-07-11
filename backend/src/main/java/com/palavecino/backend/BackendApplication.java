package com.palavecino.backend;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BackendApplication {

	public static void main(String[] args) {
		// The JVM's default zone id can be a legacy alias (e.g. "America/Buenos_Aires")
		// that some PostgreSQL builds don't recognize, only the canonical IANA name.
		// pgjdbc sends the JVM default as the session TimeZone on every connection,
		// so it must be a name Postgres accepts, set before any datasource is created.
		System.setProperty("user.timezone", "America/Argentina/Buenos_Aires");
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	Clock clock() {
		return Clock.system(ZoneId.of("America/Argentina/Buenos_Aires"));
	}

}
