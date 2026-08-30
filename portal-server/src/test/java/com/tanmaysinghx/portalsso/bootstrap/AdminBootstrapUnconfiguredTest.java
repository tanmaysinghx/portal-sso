package com.tanmaysinghx.portalsso.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.tanmaysinghx.portalsso.user.repository.RoleRepository;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * The guarantee that makes this feature safe to ship: with nothing configured, no account is
 * created. A product that invents an administrator — however it is named or whatever password it
 * generates — ships a known way in for anyone who reads the source.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:bootstrap-unconfigured;MODE=PostgreSQL",
        "app.seed.test-data=false"
})
class AdminBootstrapUnconfiguredTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Test
    void noAccountIsCreatedWhenNoCredentialsAreConfigured() {
        assertThat(userRepository.count())
                .as("nothing may create an administrator that the operator did not ask for")
                .isZero();
    }

    /** The roles still have to be there, or the operator's eventual bootstrap would fail. */
    @Test
    void thePlatformRolesAreStillSeeded() {
        assertThat(roleRepository.findByName("ROLE_ADMIN")).isPresent();
        assertThat(roleRepository.findByName("ROLE_USER")).isPresent();
    }
}
