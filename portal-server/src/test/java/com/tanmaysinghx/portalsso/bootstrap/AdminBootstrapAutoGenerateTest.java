package com.tanmaysinghx.portalsso.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.tanmaysinghx.portalsso.security.password.PasswordPolicy;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.service.RoleService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class AdminBootstrapAutoGenerateTest {

    private static Path tempHome;

    @BeforeAll
    static void setUpHome() throws IOException {
        tempHome = Files.createTempDirectory("portal-autogen-test");
        System.setProperty("PORTAL_HOME", tempHome.toString());
        System.setProperty("portal.home", tempHome.toString());
    }

    @AfterAll
    static void cleanUpHome() {
        System.clearProperty("PORTAL_HOME");
        System.clearProperty("portal.home");
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:bootstrap-autogen;MODE=PostgreSQL");
        registry.add("app.seed.test-data", () -> "false");
        registry.add("app.bootstrap.auto-generate", () -> "true");
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PasswordPolicy passwordPolicy;

    @Test
    void initialAdminAccountAndPasswordFileAreCreated() throws IOException {
        assertThat(userRepository.count()).isEqualTo(1);

        User admin = userRepository.findByEmail("admin@localhost").orElseThrow();
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getRoles()).extracting("name").contains(RoleService.ADMIN_ROLE, "ROLE_USER");

        Path passwordFile = tempHome.resolve("secrets").resolve("initialAdminPassword");
        assertThat(Files.exists(passwordFile)).isTrue();

        String rawPassword = Files.readString(passwordFile).trim();
        assertThat(passwordPolicy.failures(rawPassword)).isEmpty();
        assertThat(rawPassword.length()).isGreaterThanOrEqualTo(AdminBootstrapper.MIN_PASSWORD_LENGTH);
        assertThat(passwordEncoder.matches(rawPassword, admin.getPasswordHash())).isTrue();
    }
}
