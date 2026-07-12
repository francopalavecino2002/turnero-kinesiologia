package com.palavecino.backend;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class BackendApplication {

	static {
		// pgjdbc unconditionally sends TimeZone.getDefault().getID() as the "TimeZone"
		// startup parameter on every connection (ConnectionFactoryImpl#createPostgresTimeZone) -
		// there is no datasource URL property or connection option that can override or suppress
		// it. On Windows the JVM can report the legacy alias "America/Buenos_Aires", which
		// Postgres rejects with "FATAL: invalid value for parameter TimeZone". Pinning the
		// default here - in a static initializer on the class Spring always loads first, for
		// both `main()` and @SpringBootTest - guarantees the canonical IANA zone id is set
		// before any datasource is created, identically on Windows, in tests, and on a UTC
		// production server.
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("America/Argentina/Buenos_Aires")));
	}

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

	@Bean
	Clock clock() {
		return Clock.system(ZoneId.of("America/Argentina/Buenos_Aires"));
	}

}
