package com.tanmaysinghx.portalsso.database.service;

import com.tanmaysinghx.portalsso.bootstrap.AdminBootstrapper;
import com.tanmaysinghx.portalsso.database.dto.DatabaseStatusResponse;
import com.tanmaysinghx.portalsso.database.dto.MigrateDatabaseRequest;
import com.tanmaysinghx.portalsso.database.dto.MigrateDatabaseResponse;
import com.tanmaysinghx.portalsso.database.dto.TestConnectionRequest;
import com.tanmaysinghx.portalsso.database.dto.TestConnectionResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

@Service
public class DatabaseMigrationService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationService.class);

    private static final List<String> MIGRATION_TABLE_ORDER = List.of(
            "roles",
            "users",
            "user_roles",
            "oauth_clients",
            "signing_keys",
            "applications",
            "application_roles",
            "login_events",
            "audit_events",
            "user_recovery_codes");

    private final DataSource activeDataSource;

    public DatabaseMigrationService(DataSource activeDataSource) {
        this.activeDataSource = activeDataSource;
    }

    public DatabaseStatusResponse getDatabaseStatus() {
        try (Connection conn = activeDataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String url = meta.getURL();
            String product = meta.getDatabaseProductName();
            String version = meta.getDatabaseProductVersion();
            String driver = meta.getDriverName();
            String user = meta.getUserName();

            boolean isEmbedded = isH2(product, url);
            String dbType = resolveDatabaseType(product, url);
            String configFilePath = resolveConfigFilePath();

            return new DatabaseStatusResponse(dbType, isEmbedded, url, user, product, version, driver, configFilePath);
        } catch (SQLException e) {
            log.error("Failed to inspect active database metadata", e);
            return new DatabaseStatusResponse("UNKNOWN", false, "unknown", "unknown", "Error: " + e.getMessage(), "", "", resolveConfigFilePath());
        }
    }

    public TestConnectionResponse testConnection(TestConnectionRequest request) {
        String targetUrl = resolveJdbcUrl(
                request.databaseType(),
                request.host(),
                request.port(),
                request.databaseName(),
                request.customJdbcUrl());

        String driverClass = resolveDriverClassName(request.databaseType(), targetUrl);

        try {
            Class.forName(driverClass);
            try (Connection conn = DriverManager.getConnection(targetUrl, request.username(), request.password())) {
                DatabaseMetaData meta = conn.getMetaData();
                String product = meta.getDatabaseProductName();
                String version = meta.getDatabaseProductVersion();
                return new TestConnectionResponse(
                        true,
                        "Successfully connected to " + product + " " + version,
                        product,
                        version,
                        targetUrl);
            }
        } catch (ClassNotFoundException e) {
            return new TestConnectionResponse(false, "JDBC Driver class not found: " + driverClass, null, null, targetUrl);
        } catch (Exception e) {
            return new TestConnectionResponse(false, "Connection failed: " + e.getMessage(), null, null, targetUrl);
        }
    }

    public MigrateDatabaseResponse migrate(MigrateDatabaseRequest request) {
        String targetUrl = resolveJdbcUrl(
                request.databaseType(),
                request.host(),
                request.port(),
                request.databaseName(),
                request.customJdbcUrl());

        String driverClass = resolveDriverClassName(request.databaseType(), targetUrl);

        DriverManagerDataSource targetDataSource = new DriverManagerDataSource();
        targetDataSource.setDriverClassName(driverClass);
        targetDataSource.setUrl(targetUrl);
        targetDataSource.setUsername(request.username());
        targetDataSource.setPassword(request.password());

        // 1. Run Liquibase on target database
        try (Connection targetConn = targetDataSource.getConnection()) {
            Database targetDb = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(targetConn));
            Liquibase liquibase = new Liquibase(
                    "db/changelog/db.changelog-master.yaml",
                    new ClassLoaderResourceAccessor(getClass().getClassLoader()),
                    targetDb);
            liquibase.update(new Contexts());
            log.info("Liquibase schema successfully updated on target database {}", targetUrl);
        } catch (Exception e) {
            log.error("Failed to execute Liquibase migrations on target database", e);
            throw new RuntimeException("Target database schema migration failed: " + e.getMessage(), e);
        }

        // 2. Copy data from source to target
        JdbcTemplate sourceJdbc = new JdbcTemplate(activeDataSource);
        JdbcTemplate targetJdbc = new JdbcTemplate(targetDataSource);

        Map<String, Integer> tableCounts = new LinkedHashMap<>();
        int totalRows = 0;

        for (String table : MIGRATION_TABLE_ORDER) {
            try {
                int migrated = copyTable(sourceJdbc, targetJdbc, table);
                tableCounts.put(table, migrated);
                totalRows += migrated;
            } catch (Exception e) {
                log.error("Failed copying table {}", table, e);
                throw new RuntimeException("Failed copying table " + table + ": " + e.getMessage(), e);
            }
        }

        // 3. Reset sequences if target is PostgreSQL
        if (targetUrl.toLowerCase(Locale.ROOT).contains("postgresql")) {
            resetPostgresSequences(targetJdbc);
        }

        // 4. Save configuration file if requested
        if (request.saveConfiguration()) {
            savePropertiesFile(targetUrl, request.username(), request.password());
        }

        String instructions = """
                Migration complete! Next steps to switch Portal SSO to your target database:
                1. If running as a systemd service, update /etc/systemd/system/portal-sso.service or environment.
                2. If running standalone:
                   java -jar portal-sso.jar --spring.datasource.url=%s --spring.datasource.username=%s --spring.datasource.password=***
                3. Or set DB_URL in .env when running with Docker Compose.
                """.formatted(targetUrl, request.username() != null ? request.username() : "");

        String configPath = request.saveConfiguration() ? resolveConfigFilePath() : null;

        return new MigrateDatabaseResponse(
                true,
                "Successfully migrated " + totalRows + " record(s) to " + request.databaseType(),
                totalRows,
                tableCounts,
                targetUrl,
                instructions,
                request.saveConfiguration(),
                configPath);
    }

    private int copyTable(JdbcTemplate sourceJdbc, JdbcTemplate targetJdbc, String table) {
        List<Map<String, Object>> rows = sourceJdbc.queryForList("SELECT * FROM " + table);
        if (rows.isEmpty()) {
            return 0;
        }

        int copied = 0;
        for (Map<String, Object> row : rows) {
            if (row.isEmpty()) {
                continue;
            }

            // Deduplicate if already exists (e.g. seeded platform roles)
            if (existsInTarget(targetJdbc, table, row)) {
                continue;
            }

            List<String> columns = new ArrayList<>(row.keySet());
            List<Object> values = new ArrayList<>();
            List<String> placeholders = new ArrayList<>();

            for (String col : columns) {
                values.add(row.get(col));
                placeholders.add("?");
            }

            String sql = "INSERT INTO " + table + " (" + String.join(", ", columns) + ") VALUES ("
                    + String.join(", ", placeholders) + ")";

            targetJdbc.update(sql, values.toArray());
            copied++;
        }
        return copied;
    }

    private boolean existsInTarget(JdbcTemplate targetJdbc, String table, Map<String, Object> row) {
        if ("roles".equalsIgnoreCase(table) && row.containsKey("name")) {
            Integer count = targetJdbc.queryForObject(
                    "SELECT COUNT(*) FROM roles WHERE name = ?", Integer.class, row.get("name"));
            return count != null && count > 0;
        }
        if (row.containsKey("id")) {
            Integer count = targetJdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE id = ?", Integer.class, row.get("id"));
            return count != null && count > 0;
        }
        if (row.containsKey("kid")) {
            Integer count = targetJdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE kid = ?", Integer.class, row.get("kid"));
            return count != null && count > 0;
        }
        if ("user_roles".equalsIgnoreCase(table) && row.containsKey("user_id") && row.containsKey("role_id")) {
            Integer count = targetJdbc.queryForObject(
                    "SELECT COUNT(*) FROM user_roles WHERE user_id = ? AND role_id = ?",
                    Integer.class, row.get("user_id"), row.get("role_id"));
            return count != null && count > 0;
        }
        if ("application_roles".equalsIgnoreCase(table) && row.containsKey("application_id") && row.containsKey("role_id")) {
            Integer count = targetJdbc.queryForObject(
                    "SELECT COUNT(*) FROM application_roles WHERE application_id = ? AND role_id = ?",
                    Integer.class, row.get("application_id"), row.get("role_id"));
            return count != null && count > 0;
        }
        return false;
    }

    private void resetPostgresSequences(JdbcTemplate targetJdbc) {
        List<String> sequenceTables = List.of("login_events", "audit_events", "user_recovery_codes");
        for (String table : sequenceTables) {
            try {
                targetJdbc.execute(
                        "SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), COALESCE(max(id), 1)) FROM " + table);
            } catch (Exception e) {
                log.debug("Could not reset postgres sequence for table {}: {}", table, e.getMessage());
            }
        }
    }

    public String resolveConfigFilePath() {
        return AdminBootstrapper.resolvePortalHome().resolve("portal.properties").toAbsolutePath().toString();
    }

    private void savePropertiesFile(String targetUrl, String username, String password) {
        try {
            Path portalHome = AdminBootstrapper.resolvePortalHome();
            Files.createDirectories(portalHome);
            Path propsFile = portalHome.resolve("portal.properties");

            StringBuilder sb = new StringBuilder();
            sb.append("# Portal SSO Database Configuration\n");
            sb.append("# Generated during database migration\n");
            sb.append("spring.datasource.url=").append(targetUrl).append("\n");
            if (username != null) {
                sb.append("spring.datasource.username=").append(username).append("\n");
            }
            if (password != null) {
                sb.append("spring.datasource.password=").append(password).append("\n");
            }

            Files.writeString(
                    propsFile,
                    sb.toString(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);

            try {
                Files.setPosixFilePermissions(propsFile, PosixFilePermissions.fromString("rw-------"));
            } catch (UnsupportedOperationException ignored) {
            }
            log.info("Saved target database configuration to {}", propsFile.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Could not save portal.properties configuration: {}", e.getMessage());
        }
    }

    public String resolveJdbcUrl(String databaseType, String host, Integer port, String dbName, String customUrl) {
        if (customUrl != null && !customUrl.isBlank()) {
            String url = customUrl.trim();
            if (url.startsWith("jdbc:h2:mem:") && !url.contains("DB_CLOSE_DELAY")) {
                url += ";DB_CLOSE_DELAY=-1";
            }
            return url;
        }

        String type = databaseType != null ? databaseType.trim().toUpperCase(Locale.ROOT) : "POSTGRESQL";
        String h = (host != null && !host.isBlank()) ? host.trim() : "localhost";
        String db = (dbName != null && !dbName.isBlank()) ? dbName.trim() : "portalsso";

        if ("MYSQL".equals(type)) {
            int p = (port != null && port > 0) ? port : 3306;
            return "jdbc:mysql://" + h + ":" + p + "/" + db + "?allowPublicKeyRetrieval=true&useSSL=false";
        }
        if ("H2".equals(type)) {
            return "jdbc:h2:mem:" + db + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
        }
        // Default to PostgreSQL
        int p = (port != null && port > 0) ? port : 5432;
        return "jdbc:postgresql://" + h + ":" + p + "/" + db;
    }

    private String resolveDriverClassName(String databaseType, String targetUrl) {
        String lowerUrl = targetUrl.toLowerCase(Locale.ROOT);
        if (lowerUrl.startsWith("jdbc:postgresql:")) {
            return "org.postgresql.Driver";
        }
        if (lowerUrl.startsWith("jdbc:mysql:")) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if (lowerUrl.startsWith("jdbc:h2:")) {
            return "org.h2.Driver";
        }
        if ("MYSQL".equalsIgnoreCase(databaseType)) {
            return "com.mysql.cj.jdbc.Driver";
        }
        if ("H2".equalsIgnoreCase(databaseType)) {
            return "org.h2.Driver";
        }
        return "org.postgresql.Driver";
    }

    private boolean isH2(String product, String url) {
        return (product != null && product.toLowerCase(Locale.ROOT).contains("h2"))
                || (url != null && url.toLowerCase(Locale.ROOT).contains(":h2:"));
    }

    private String resolveDatabaseType(String product, String url) {
        String combined = (product + " " + url).toLowerCase(Locale.ROOT);
        if (combined.contains("postgres")) {
            return "POSTGRESQL";
        }
        if (combined.contains("mysql")) {
            return "MYSQL";
        }
        if (combined.contains("h2")) {
            return "H2";
        }
        return "OTHER";
    }
}
