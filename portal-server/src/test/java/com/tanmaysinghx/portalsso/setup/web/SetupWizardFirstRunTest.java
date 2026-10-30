package com.tanmaysinghx.portalsso.setup.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tanmaysinghx.portalsso.setup.SetupKeyService;
import com.tanmaysinghx.portalsso.setup.web.SetupController.SetupInitializeRequest;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import com.tanmaysinghx.portalsso.user.service.RoleService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * A fresh install end to end: no administrator, a setup key on disk, and the wizard used the way an
 * operator would. Ordered because the key is single use and the last step closes the wizard.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SetupWizardFirstRunTest {

    private static final String ADMIN_EMAIL = "owner@example.com";
    private static final String ADMIN_PASSWORD = "Correct-Horse-Battery-9";
    private static Path tempHome;

    @BeforeAll
    static void setUpHome() throws IOException {
        tempHome = Files.createTempDirectory("portal-setup-wizard-test");
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
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:setup-wizard-first-run;MODE=PostgreSQL");
        registry.add("app.seed.test-data", () -> "false");
        registry.add("app.bootstrap.auto-generate", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SetupKeyService setupKeyService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String keyOnDisk() throws IOException {
        return Files.readString(tempHome.resolve("secrets").resolve("initialAdminPassword")).trim();
    }

    private ResultActions initialize(String key, String email, String password) throws Exception {
        return mockMvc.perform(post("/api/public/setup/initialize")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new SetupInitializeRequest(key, email, password, "Ada", "Owner"))));
    }

    @Test
    @Order(1)
    void statusTellsTheConsoleToShowTheWizard() throws Exception {
        mockMvc.perform(get("/api/public/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupRequired").value(true))
                .andExpect(jsonPath("$.setupKeyIssued").value(true))
                .andExpect(jsonPath("$.setupKeyFile").value(setupKeyService.keyFile().toString()));
    }

    @Test
    @Order(2)
    void wrongKeyIsRefusedAndCreatesNothing() throws Exception {
        initialize("not-the-setup-key", ADMIN_EMAIL, ADMIN_PASSWORD)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRTL-1010"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @Order(3)
    void weakPasswordIsRefusedWithoutSpendingTheKey() throws Exception {
        initialize(keyOnDisk(), ADMIN_EMAIL, "short1A!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PRTL-2011"));

        assertThat(userRepository.count()).isZero();
        assertThat(setupKeyService.isIssued()).isTrue();
    }

    @Test
    @Order(4)
    void anExistingAccountIsNeverTakenOver() throws Exception {
        User bystander = new User("bystander@example.com", passwordEncoder.encode("Their-Own-Password-1"));
        bystander.setEnabled(true);
        userRepository.save(bystander);

        initialize(keyOnDisk(), "bystander@example.com", ADMIN_PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRTL-2003"));

        User unchanged = userRepository.findByEmail("bystander@example.com").orElseThrow();
        assertThat(passwordEncoder.matches("Their-Own-Password-1", unchanged.getPasswordHash())).isTrue();
        assertThat(unchanged.getRoles()).extracting("name").doesNotContain(RoleService.ADMIN_ROLE);
        assertThat(setupKeyService.isIssued()).isTrue();
    }

    @Test
    @Order(5)
    void correctKeyCreatesTheAdministratorAndIsSpent() throws Exception {
        String key = keyOnDisk();

        initialize(key, "Owner@Example.com", ADMIN_PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(ADMIN_EMAIL));

        User admin = userRepository.findByEmail(ADMIN_EMAIL).orElseThrow();
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getFirstName()).isEqualTo("Ada");
        assertThat(admin.getRoles()).extracting("name").contains(RoleService.ADMIN_ROLE, "ROLE_USER");
        assertThat(passwordEncoder.matches(ADMIN_PASSWORD, admin.getPasswordHash())).isTrue();

        assertThat(setupKeyService.isIssued()).isFalse();
        assertThat(Files.exists(setupKeyService.keyFile())).as("spent key file is deleted").isFalse();

        // Replaying the same key cannot mint a second administrator.
        initialize(key, "second@example.com", ADMIN_PASSWORD).andExpect(status().isConflict());
        assertThat(userRepository.findByEmail("second@example.com")).isEmpty();
    }

    @Test
    @Order(6)
    void theNewAdministratorCanSignIn() throws Exception {
        mockMvc.perform(get("/api/public/setup/status"))
                .andExpect(jsonPath("$.setupRequired").value(false))
                .andExpect(jsonPath("$.setupKeyFile").doesNotExist());

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", ADMIN_EMAIL)
                        .param("password", ADMIN_PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }
}
