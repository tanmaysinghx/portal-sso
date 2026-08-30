package com.tanmaysinghx.portalsso.registration.web;

import static com.tanmaysinghx.portalsso.registration.web.RegistrationTestSupport.body;
import static com.tanmaysinghx.portalsso.registration.web.RegistrationTestSupport.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** The default posture: registration closed. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "app.registration.enabled=false")
class PublicRegistrationDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registrationIsRefusedAndNoAccountIsCreated() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/public/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body(email)))
                .andExpect(status().isForbidden());

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    @Test
    void policyReportsRegistrationIsClosedSoTheUiCanHideTheLink() throws Exception {
        mockMvc.perform(get("/api/public/registration-policy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }
}
