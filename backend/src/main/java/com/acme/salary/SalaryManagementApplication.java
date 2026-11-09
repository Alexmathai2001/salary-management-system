package com.acme.salary;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SalaryManagementApplication {

	public static void main(String[] args) {
		// SQLite has no native DATE type: the driver stores java.sql.Date as epoch millis at *local* midnight.
		// Pinning the JVM to UTC makes stored dates independent of the host timezone (seed on a laptop, run in cloud).
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(SalaryManagementApplication.class, args);
	}

}
