package com.tanmaysinghx.portalsso.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.tanmaysinghx.portalsso.setup.SetupKeyService;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
    private SetupKeyService setupKeyService;

    @Test
    void issuesASetupKeyAndCreatesNoAccount() throws IOException {
        // No account with a password the operator never chose: they create their own in the wizard.
        assertThat(userRepository.count()).isZero();
        assertThat(setupKeyService.isIssued()).isTrue();

        Path keyFile = tempHome.resolve("secrets").resolve("initialAdminPassword");
        assertThat(setupKeyService.keyFile()).isEqualTo(keyFile);
        assertThat(Files.exists(keyFile)).isTrue();

        String key = Files.readString(keyFile).trim();
        assertThat(key).hasSizeGreaterThanOrEqualTo(43);
        assertThat(setupKeyService.consume("not-the-key")).isFalse();
        assertThat(setupKeyService.consume(key)).isTrue();
        assertThat(setupKeyService.consume(key)).as("single use").isFalse();
    }
}
