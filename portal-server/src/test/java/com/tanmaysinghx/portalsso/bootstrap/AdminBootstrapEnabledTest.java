package com.tanmaysinghx.portalsso.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.audit.entity.AuditAction;
import com.tanmaysinghx.portalsso.audit.repository.AuditEventRepository;
import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The scenario this feature exists for: a deployment with an empty {@code users} table and the dev
 * seeder off — which is what an operator actually gets from the jar.
 *
 * <p>Runs against its own in-memory database. The shared one is named in {@code
 * application-test.yml}, so every other test class would have already seeded an administrator into
 * it and the bootstrapper would correctly stand down, testing nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:bootstrap-enabled;MODE=PostgreSQL",
        "app.seed.test-data=false",
        "app.bootstrap.admin-email=ops@example.com",
        "app.bootstrap.admin-password=CorrectHorseBattery1!"
})
class AdminBootstrapEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private AdminBootstrapper bootstrapper;

    @Test
    void theConfiguredAdministratorIsCreatedAndCanActuallySignIn() throws Exception {
        User admin = userRepository.findByEmail("ops@example.com").orElseThrow();
        assertThat(admin.isEnabled()).isTrue();
        assertThat(admin.getRoles()).extracting(Role::getName).contains("ROLE_ADMIN");

        // The point is a usable account, not a row: assert a real form login, not a password hash.
        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "ops@example.com")
                        .param("password", "CorrectHorseBattery1!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    /** Attributed to the system, not "anonymous" — nobody reached the server over HTTP to do this. */
    @Test
    void theBootstrapIsAudited() {
        assertThat(auditEventRepository.findAll())
                .filteredOn(e -> e.getAction() == AuditAction.ADMIN_BOOTSTRAPPED)
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.getActorEmail()).isEqualTo("system");
                    assertThat(e.getTargetLabel()).isEqualTo("ops@example.com");
                });
    }

    /**
     * Leaving the configuration in place must be harmless. Re-running with an administrator already
     * present has to change nothing — otherwise every restart would rewrite the account.
     */
    @Test
    @Transactional
    void reRunningWithAnAdministratorPresentDoesNothing() {
        String hashBefore = userRepository.findByEmail("ops@example.com").orElseThrow().getPasswordHash();
        long usersBefore = userRepository.count();

        bootstrapper.run(null);

        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(userRepository.findByEmail("ops@example.com").orElseThrow().getPasswordHash())
                .isEqualTo(hashBefore);
    }

    /**
     * The recovery path: every administrator has been disabled, and the operator points the
     * bootstrap at an address that already has an account. The role is granted and the account
     * re-enabled, but the password must survive — resetting it from a config file would be a way to
     * take over a colleague's account rather than to recover the server.
     */
    @Test
    @Transactional
    void recoveringALockedOutServerGrantsAdminWithoutOverwritingThePassword() {
        userRepository.findAllWithRoles().forEach(u -> {
            u.setEnabled(false);
            userRepository.save(u);
        });

        User existing = userRepository.findByEmail("ops@example.com").orElseThrow();
        existing.getRoles().removeIf(r -> r.getName().equals("ROLE_ADMIN"));
        existing.setEnabled(false);
        String hashBefore = existing.getPasswordHash();
        userRepository.save(existing);

        bootstrapper.run(null);

        User recovered = userRepository.findByEmail("ops@example.com").orElseThrow();
        assertThat(recovered.isEnabled()).isTrue();
        assertThat(recovered.getRoles()).extracting(Role::getName).contains("ROLE_ADMIN");
        assertThat(recovered.getPasswordHash())
                .as("a config file must never be able to reset an existing password")
                .isEqualTo(hashBefore);
        assertThat(passwordEncoder.matches("CorrectHorseBattery1!", recovered.getPasswordHash())).isTrue();
        assertThat(roleRepository.findByName("ROLE_ADMIN")).isPresent();
    }
}
