package com.tanmaysinghx.portalsso.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaRoutingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rootAndClientSideRoutesReturnSpaIndexHtml() throws Exception {
        // Deep link: /
        MvcResult rootResult = mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(rootResult.getResponse().getForwardedUrl()).isEqualTo("index.html");

        // Deep link: /users
        MvcResult usersResult = mockMvc.perform(get("/users"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(usersResult.getResponse().getContentAsString()).contains("<app-root");

        // Deep link: /clients/new
        MvcResult clientsResult = mockMvc.perform(get("/clients/new"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(clientsResult.getResponse().getContentAsString()).contains("<app-root");
    }

    @Test
    void apiEndpointsReturnUnauthorizedWhenUnauthenticatedAndDoNotReturnHtml() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(result -> assertThat(result.getResponse().getContentType()).isNotEqualTo(MediaType.TEXT_HTML_VALUE));
    }

    @Test
    void wellKnownAndJwksEndpointsAreNotSwallowedBySpa() throws Exception {
        mockMvc.perform(get("/oauth2/jwks"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/.well-known/openid-configuration"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
