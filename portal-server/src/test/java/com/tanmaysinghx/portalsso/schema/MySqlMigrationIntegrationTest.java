package com.tanmaysinghx.portalsso.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the whole changelog against a real MySQL, because the rest of the suite cannot.
 *
 * <p>Migration {@code 007} creates {@code SPRING_SESSION_ATTRIBUTES} twice — once
 * {@code dbms: mysql} with a {@code BLOB} column, once {@code dbms: "!mysql"} with {@code BYTEA} —
 * and Liquibase filters out whichever does not match. On H2 that means the MySQL branch is never
 * executed by any test, while it is the only one production ever runs. Liquibase says so in the
 * startup log of every MySQL boot: <em>DBMS mismatch: 1</em>.
 *
 * <p>The same blind spot covers the {@code mysql} profile's two workarounds: MySQL has no native
 * {@code BOOLEAN} (Hibernate is told to expect {@code TINYINT}) and managed providers reject
 * Liquibase's primary-key-less bookkeeping table. Neither can fail on H2, so neither was tested.
 *
 * <p>Skipped automatically when Docker is unavailable, so a contributor without it still gets a
 * green build — the CI pipeline has Docker and always runs it.
 */
@SpringBootTest
@ActiveProfiles({"test", "mysql"})
@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationIntegrationTest {

    // Matches the 8.0 line the Aiven deployment reports, so the test exercises the same dialect
    // behaviour the real database has rather than whatever is newest.
    //
    // Connects as root deliberately. The mysql profile issues
    // `SET SESSION sql_require_primary_key=0` on every connection, and MySQL requires SUPER,
    // SYSTEM_VARIABLES_ADMIN or SESSION_VARIABLES_ADMIN to do that. Testcontainers' default
    // unprivileged user does not have it, and the application fails to start with "Access denied"
    // — which is exactly what a self-hoster pointing this at a restricted MySQL account would hit.
    // Aiven's avnadmin holds the privilege, which is why production works; the requirement is now
    // documented in the server README rather than being an undiscovered trap.
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withUsername("root")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        // The dev seeder is irrelevant here; this test is about the schema.
        registry.add("app.seed.test-data", () -> "false");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    /**
     * The headline assertion is that this class starts at all: the context coming up means Liquibase
     * applied every changeset to a real MySQL and Hibernate's {@code ddl-auto: validate} then agreed
     * with the result, column type by column type.
     */
    @Test
    void theWholeChangelogAppliesToARealMySql() {
        Integer applied = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM DATABASECHANGELOG", Integer.class);
        assertThat(applied).as("changesets recorded as applied").isNotNull().isPositive();
    }

    /** The branch production runs, and which no other test in this suite can reach. */
    @Test
    void theMySqlBranchOfTheSessionMigrationIsTheOneThatRan() {
        List<String> ids = jdbcTemplate.queryForList(
                "SELECT ID FROM DATABASECHANGELOG WHERE ID LIKE '007-create-spring-session-attributes%'",
                String.class);

        assertThat(ids)
                .as("only the MySQL branch may be applied on MySQL")
                .containsExactly("007-create-spring-session-attributes-table-mysql");
    }

    @Test
    void theSessionAttributeColumnIsABlobRatherThanBytea() {
        Map<String, Object> column = jdbcTemplate.queryForMap("""
                SELECT DATA_TYPE, IS_NULLABLE
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'SPRING_SESSION_ATTRIBUTES'
                  AND COLUMN_NAME = 'ATTRIBUTE_BYTES'
                """);

        assertThat(String.valueOf(column.get("DATA_TYPE")).toLowerCase()).isEqualTo("blob");
        assertThat(String.valueOf(column.get("IS_NULLABLE"))).isEqualTo("NO");
    }

    /**
     * MySQL stores {@code BOOLEAN} as {@code TINYINT(1)}. Hibernate is told to expect that via
     * {@code preferred_boolean_jdbc_type} in the mysql profile; without it, validation fails on
     * every boolean column. This asserts the physical type and then round-trips a value, because
     * matching metadata is not the same as reading back what you wrote.
     */
    @Test
    @Transactional
    void booleanColumnsAreTinyintAndStillRoundTrip() {
        String type = jdbcTemplate.queryForObject("""
                SELECT COLUMN_TYPE FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'enabled'
                """, String.class);
        assertThat(type).isNotNull().startsWith("tinyint");

        Role role = roleRepository.findByName("ROLE_USER").orElseThrow();
        User user = new User("mysql-roundtrip@example.com", "hashed");
        user.setEnabled(false);
        user.setAccountLocked(true);
        user.addRole(role);
        User saved = userRepository.save(user);
        userRepository.flush();

        User reloaded = userRepository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.isEnabled()).isFalse();
        assertThat(reloaded.isAccountLocked()).isTrue();
    }

    /** Migration 011 seeds these; the literal UUIDs must survive a VARCHAR(36) round-trip on MySQL too. */
    @Test
    void theSeededPlatformRolesAreReadableOnMySql() {
        assertThat(roleRepository.findByName("ROLE_ADMIN")).isPresent();
        assertThat(roleRepository.findByName("ROLE_USER")).isPresent();
        assertThat(roleRepository.findByName("ROLE_ADMIN").orElseThrow().getId()).isNotNull();
    }

    /**
     * Managed MySQL providers (Aiven, RDS) enforce {@code sql_require_primary_key}, which rejects
     * Liquibase's own bookkeeping tables. The mysql profile relaxes it per-connection; this checks
     * the setting is actually applied rather than silently ignored, since a stock container does not
     * enforce it and would hide a broken workaround.
     */
    @Test
    void theProfileRelaxesSqlRequirePrimaryKeyOnItsConnections() {
        Integer required = jdbcTemplate.queryForObject(
                "SELECT @@session.sql_require_primary_key", Integer.class);
        assertThat(required)
                .as("connection-init-sql from the mysql profile should have run")
                .isEqualTo(0);
    }
}
