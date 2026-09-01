package com.tanmaysinghx.portalsso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
// Drives the login_events retention job. Nothing else is scheduled yet, and retention is off by
// default, so this is inert until an operator sets app.analytics.retention.login-events-days.
@EnableScheduling
public class PortalSsoAuthServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(PortalSsoAuthServerApplication.class, args);
	}

}
