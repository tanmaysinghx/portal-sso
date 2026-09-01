package com.tanmaysinghx.portalsso.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the caching split that made the console usable.
 *
 * <p>Spring Security stamps {@code no-cache, no-store, max-age=0, must-revalidate} on every response
 * by default. Applied to Angular's content-hashed bundles that meant the browser re-downloaded every
 * chunk on every navigation — and each of those requests also went through the security chain,
 * which reads and writes the session, which with Spring Session JDBC is two database round-trips.
 * Against a remote database that was measured at four SQL statements and ~770ms to serve a single
 * JavaScript file.
 *
 * <p>The split is easy to undo by accident — adding a path to the wrong filter chain is enough — and
 * the symptom is a slow console rather than a failing test, so it is asserted here.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class StaticAssetCachingTest {

    @Autowired
    private MockMvc mockMvc;

    /** Content-hashed, so a changed file is a different URL and caching it forever is safe. */
    @Test
    void hashedBundlesAreCachedImmutably() throws Exception {
        mockMvc.perform(get("/main-ABCD1234.js"))
                .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"));
    }

    @Test
    void stylesheetsAreCachedTheSameWay() throws Exception {
        mockMvc.perform(get("/styles-ABCD1234.css"))
                .andExpect(header().string("Cache-Control", "public, max-age=31536000, immutable"));
    }

    /**
     * index.html must never be cached: it names the hashed bundles, so a stale copy would point at
     * files a deployment has already replaced.
     */
    @Test
    void theEntryPointIsNeverCached() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
    }

    /** API responses carry user data and must keep the restrictive default. */
    @Test
    void apiResponsesAreNeverCached() throws Exception {
        mockMvc.perform(get("/api/admin/me"))
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
    }

    /**
     * Assets are served without authentication — they are the same bundle every visitor downloads
     * before signing in. If this ever returns 401 the static chain has stopped matching, which is
     * also the moment the session round-trips come back.
     */
    @Test
    void assetsDoNotRequireAuthentication() throws Exception {
        mockMvc.perform(get("/main-ABCD1234.js")).andExpect(status().isNotFound());
    }
}
