package com.tanmaysinghx.portalsso;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PortalSsoAuthServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(PortalSsoAuthServerApplication.class, args);
	}

}
