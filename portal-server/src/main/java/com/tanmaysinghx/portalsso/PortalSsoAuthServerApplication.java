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

	public static void main(String[] args) {
		String[] normalizedArgs = normalizeArguments(args);
		initPortalDirectories();
		SpringApplication.run(PortalSsoAuthServerApplication.class, normalizedArgs);
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

}

