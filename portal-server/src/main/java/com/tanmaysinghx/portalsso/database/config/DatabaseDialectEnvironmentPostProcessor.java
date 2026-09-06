package com.tanmaysinghx.portalsso.database.config;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Automatically inspects the configured datasource URL across environment variables,
 * system properties, and application configurations.
 *
 * <p>When MySQL or MariaDB is detected, this processor activates the {@code mysql} profile and
 * ensures {@code preferred_boolean_jdbc_type=TINYINT} and {@code SET SESSION sql_require_primary_key=0}
 * are applied, so operators never have to pass {@code -e SPRING_PROFILES_ACTIVE=mysql} manually.
 */
public class DatabaseDialectEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(DatabaseDialectEnvironmentPostProcessor.class);

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String url = environment.getProperty("spring.datasource.url");
        if (url == null || url.isBlank()) {
            url = environment.getProperty("DB_URL");
        }
        if (url == null || url.isBlank()) {
            url = environment.resolvePlaceholders("${spring.datasource.url:${DB_URL:}}");
        }

        if (url != null && (url.contains(":mysql:") || url.contains(":mariadb:"))) {
            environment.addActiveProfile("mysql");
            Map<String, Object> mysqlProps = new HashMap<>();
            mysqlProps.put("spring.jpa.properties.hibernate.type.preferred_boolean_jdbc_type", "TINYINT");
            mysqlProps.put("spring.datasource.hikari.connection-init-sql", "SET SESSION sql_require_primary_key=0");
            environment.getPropertySources().addFirst(new MapPropertySource("autoDetectedMysqlProperties", mysqlProps));
            log.info("[Portal SSO] Auto-detected MySQL database: enabled preferred_boolean_jdbc_type=TINYINT and relaxed sql_require_primary_key.");
        }
    }
}
