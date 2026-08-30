package com.tanmaysinghx.portalsso.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tanmaysinghx.portalsso.config.TestDataSeeder;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpringSessionPersistenceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcOperations jdbcOperations;

    @Autowired
    private SessionRepository<? extends Session> sessionRepository;

    @Test
    void userLoginPersistsSessionInSpringSessionTable() throws Exception {
        // Log in as admin
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .param("username", TestDataSeeder.TEST_ADMIN_EMAIL)
                        .param("password", TestDataSeeder.TEST_ADMIN_PASSWORD)
                        .with(csrf()))
                .andReturn();

        jakarta.servlet.http.Cookie sessionCookie = loginResult.getResponse().getCookie("SESSION");
        assertThat(sessionCookie).isNotNull();

        // Verify session row exists in SPRING_SESSION database table
        Integer sessionCount = jdbcOperations.queryForObject("SELECT count(*) FROM SPRING_SESSION", Integer.class);
        assertThat(sessionCount).isGreaterThan(0);

        // Verify that authenticated user can access /api/admin/me with their session
        mockMvc.perform(get("/api/admin/me")
                        .cookie(sessionCookie))
                .andExpect(status().isOk());
    }
}
