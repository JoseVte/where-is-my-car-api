package io.josrom.whereismycar.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import io.josrom.whereismycar.support.IntegrationTest;

class SecurityIntegrationTest extends IntegrationTest {

    @ParameterizedTest
    @ValueSource(strings = {"/api/me", "/api/categories", "/api/categories/1", "/api/saved-locations", "/api/saved-locations/1"})
    void protectedEndpointsRequireAToken(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    @Test
    void registerIsPublic() throws Exception {
        // 400 (validation) instead of 401 proves the request reached the controller
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void apiDocsArePublic() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void corsAllowsTheConfiguredOrigin() throws Exception {
        mockMvc.perform(options("/api/categories")
                        .header("Origin", "http://localhost:4200")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:4200"));
    }

    @Test
    void corsRejectsOtherOrigins() throws Exception {
        mockMvc.perform(options("/api/categories")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
