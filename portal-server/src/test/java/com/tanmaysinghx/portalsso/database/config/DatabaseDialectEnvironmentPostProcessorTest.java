package com.tanmaysinghx.portalsso.database.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockPropertySource;

class DatabaseDialectEnvironmentPostProcessorTest {

    private final DatabaseDialectEnvironmentPostProcessor processor = new DatabaseDialectEnvironmentPostProcessor();

    @Test
    void activatesMysqlProfileWhenMysqlUrlDetected() {
        StandardEnvironment environment = new StandardEnvironment();
        MockPropertySource ps = new MockPropertySource();
        ps.setProperty("DB_URL", "jdbc:mysql://db.example.com:3306/portal?ssl-mode=REQUIRED");
        environment.getPropertySources().addFirst(ps);

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(Arrays.asList(environment.getActiveProfiles())).contains("mysql");
        assertThat(environment.getProperty("spring.jpa.properties.hibernate.type.preferred_boolean_jdbc_type"))
                .isEqualTo("TINYINT");
        assertThat(environment.getProperty("spring.datasource.hikari.connection-init-sql"))
                .isEqualTo("SET SESSION sql_require_primary_key=0");
    }

    @Test
    void ignoresNonMysqlUrl() {
        StandardEnvironment environment = new StandardEnvironment();
        MockPropertySource ps = new MockPropertySource();
        ps.setProperty("DB_URL", "jdbc:postgresql://localhost:5432/portalsso");
        environment.getPropertySources().addFirst(ps);

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(Arrays.asList(environment.getActiveProfiles())).doesNotContain("mysql");
    }
}
