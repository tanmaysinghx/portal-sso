package com.tanmaysinghx.portalsso;

import com.tanmaysinghx.portalsso.bootstrap.AdminBootstrapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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

	private static org.springframework.context.ConfigurableApplicationContext context;
	private static String[] savedArgs = new String[0];

	public static void main(String[] args) {
		savedArgs = args != null ? args : new String[0];
		String[] normalizedArgs = normalizeArguments(args);
		initPortalDirectories();
		configureDialectAutoDetection();
		context = SpringApplication.run(PortalSsoAuthServerApplication.class, normalizedArgs);
	}

	public static void restart() {
		Thread restartThread = new Thread(() -> {
			try {
				Thread.sleep(800);
				if (context != null) {
					context.close();
				}
				initPortalDirectories();
				String[] normalizedArgs = normalizeArguments(savedArgs);
				context = SpringApplication.run(PortalSsoAuthServerApplication.class, normalizedArgs);
			} catch (Exception e) {
				// In container environments (e.g. Docker with restart: unless-stopped), exit 0 causes container restart with new config
				System.exit(0);
			}
		}, "portal-sso-restart");
		restartThread.setDaemon(false);
		restartThread.start();
	}

	public static String[] normalizeArguments(String[] args) {
		if (args == null || args.length == 0) {
			return new String[0];
		}
		List<String> normalized = new ArrayList<>();
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.startsWith("--httpPort=")) {
				normalized.add("--server.port=" + arg.substring("--httpPort=".length()));
			} else if (arg.equals("--httpPort") && i + 1 < args.length) {
				normalized.add("--server.port=" + args[++i]);
			} else if (arg.startsWith("--httpListenAddress=")) {
				normalized.add("--server.address=" + arg.substring("--httpListenAddress=".length()));
			} else if (arg.equals("--httpListenAddress") && i + 1 < args.length) {
				normalized.add("--server.address=" + args[++i]);
			} else if (arg.startsWith("--portalHome=")) {
				String home = arg.substring("--portalHome=".length());
				System.setProperty("PORTAL_HOME", home);
				System.setProperty("portal.home", home);
				normalized.add("--portal.home=" + home);
			} else if (arg.equals("--portalHome") && i + 1 < args.length) {
				String home = args[++i];
				System.setProperty("PORTAL_HOME", home);
				System.setProperty("portal.home", home);
				normalized.add("--portal.home=" + home);
			} else if (arg.startsWith("--prefix=")) {
				normalized.add("--server.servlet.context-path=" + arg.substring("--prefix=".length()));
			} else {
				normalized.add(arg);
			}
		}
		return normalized.toArray(new String[0]);
	}

	private static void initPortalDirectories() {
		try {
			Path home = AdminBootstrapper.resolvePortalHome();
			Files.createDirectories(home.resolve("data"));
			Files.createDirectories(home.resolve("secrets"));

			Path propsFile = home.resolve("portal.properties");
			if (Files.exists(propsFile) && System.getProperty("spring.config.import") == null) {
				System.setProperty("spring.config.import", "optional:file:" + propsFile.toAbsolutePath());
			}
		} catch (IOException ignored) {
		}
	}

	private static void configureDialectAutoDetection() {
		String dbUrl = System.getenv("DB_URL");
		if (dbUrl == null || dbUrl.isBlank()) {
			dbUrl = System.getenv("SPRING_DATASOURCE_URL");
		}
		if (dbUrl == null || dbUrl.isBlank()) {
			dbUrl = System.getProperty("spring.datasource.url");
		}
		if (dbUrl == null || dbUrl.isBlank()) {
			dbUrl = System.getProperty("DB_URL");
		}
		if (dbUrl == null || dbUrl.isBlank()) {
			try {
				Path propsFile = AdminBootstrapper.resolvePortalHome().resolve("portal.properties");
				if (Files.exists(propsFile)) {
					String content = Files.readString(propsFile);
					for (String line : content.split("\n")) {
						if (line.trim().startsWith("spring.datasource.url=")) {
							dbUrl = line.trim().substring("spring.datasource.url=".length()).trim();
							break;
						}
					}
				}
			} catch (Exception ignored) {
			}
		}

		if (dbUrl != null && (dbUrl.toLowerCase(java.util.Locale.ROOT).contains(":mysql:") || dbUrl.toLowerCase(java.util.Locale.ROOT).contains(":mariadb:"))) {
			System.setProperty("spring.jpa.properties.hibernate.type.preferred_boolean_jdbc_type", "TINYINT");
			System.setProperty("spring.datasource.hikari.connection-init-sql", "SET SESSION sql_require_primary_key=0");
			if (System.getProperty("spring.profiles.active") == null && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
				System.setProperty("spring.profiles.active", "mysql");
			}
		}
	}

}

