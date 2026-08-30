package com.tanmaysinghx.portalsso.registration.web;

import static com.tanmaysinghx.portalsso.registration.web.RegistrationTestSupport.body;
import static com.tanmaysinghx.portalsso.registration.web.RegistrationTestSupport.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.user.entity.Role;
import com.tanmaysinghx.portalsso.user.entity.User;
import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** Registration is the only unauthenticated write in the app, so these lean on its guarantees. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.registration.enabled=true")
class PublicRegistrationEnabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void anyoneCanRegisterAndTheAccountIsUsable() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/public/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.pendingApproval").value(false));

        User created = userRepository.findByEmail(email).orElseThrow();
        assertThat(created.isEnabled()).isTrue();
        assertThat(created.getPasswordHash()).isNotEqualTo("CorrectHorse123!");
        assertThat(created.getRoles()).extracting(Role::getName).containsExactly("ROLE_USER");
    }

    @Test
    void registeringCannotGrantAdminEvenWhenAsked() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/public/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"email":"%s","password":"CorrectHorse123!","roles":["ROLE_ADMIN"],"enabled":true}
                                 """.formatted(email)))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmail(email).orElseThrow().getRoles())
                .extracting(Role::getName)
                .containsExactly("ROLE_USER");
    }

    @Test
    void aDuplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/public/register").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(email)));

        mockMvc.perform(post("/api/public/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(email)))
                .andExpect(status().isConflict());
    }

    @Test
    void weakOrMalformedInputIsRejected() throws Exception {
        mockMvc.perform(post("/api/public/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                 {"email":"not-an-email","password":"short"}
                                 """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void emailIsNormalisedSoCaseCannotCreateDuplicates() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/public/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(email.toUpperCase())))
                .andExpect(status().isCreated());

        assertThat(userRepository.findByEmail(email)).as("stored lower-cased").isPresent();

        mockMvc.perform(post("/api/public/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(email)))
                .andExpect(status().isConflict());
    }

    @Test
    void policyReportsRegistrationIsOpen() throws Exception {
        mockMvc.perform(get("/api/public/registration-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.requiresApproval").value(false));
    }
}
