package io.josrom.whereismycar.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import io.josrom.whereismycar.entity.Category;
import io.josrom.whereismycar.support.IntegrationTest;

class MeControllerTest extends IntegrationTest {

    @Test
    void createsTheUserFromTheTokenOnFirstCall() throws Exception {
        mockMvc.perform(get("/api/me").with(as("abc")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uuid").value("abc"))
                .andExpect(jsonPath("$.email").value("abc@example.com"))
                .andExpect(jsonPath("$.displayName").value("User abc"));

        assertThat(userRepository.findById("abc")).isPresent();
    }

    @Test
    void updatesEmailAndNameWithoutDuplicatingTheUser() throws Exception {
        createUser("abc");

        mockMvc.perform(get("/api/me").with(jwt().jwt(token -> token.subject("abc")
                        .claim("email", "new@example.com").claim("name", "New name"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("new@example.com"))
                .andExpect(jsonPath("$.displayName").value("New name"));

        assertThat(userRepository.count()).isEqualTo(1);
    }

    @Test
    void createsTheDefaultCategoriesWithANewUser() throws Exception {
        mockMvc.perform(get("/api/me").with(as("abc"))).andExpect(status().isOk());

        assertThat(categoryRepository.findByUserUuid("abc"))
                .extracting(Category::getLabel)
                .containsExactlyInAnyOrder(Category.HOME_LABEL, Category.FAVORITES_LABEL, Category.OTHERS_LABEL);
    }

    @Test
    void doesNotDuplicateTheDefaultCategoriesOnLaterCalls() throws Exception {
        mockMvc.perform(get("/api/me").with(as("abc"))).andExpect(status().isOk());
        mockMvc.perform(get("/api/me").with(as("abc"))).andExpect(status().isOk());

        assertThat(categoryRepository.findByUserUuid("abc")).hasSize(3);
    }

    @Test
    void doesNotRecreateCategoriesTheUserDeleted() throws Exception {
        mockMvc.perform(get("/api/me").with(as("abc"))).andExpect(status().isOk());
        categoryRepository.deleteAll(categoryRepository.findByUserUuid("abc"));

        mockMvc.perform(get("/api/me").with(as("abc"))).andExpect(status().isOk());

        assertThat(categoryRepository.findByUserUuid("abc")).isEmpty();
    }

    @Test
    void doesNotAddCategoriesToAnExistingUser() throws Exception {
        createUser("abc");

        mockMvc.perform(get("/api/me").with(as("abc"))).andExpect(status().isOk());

        assertThat(categoryRepository.findByUserUuid("abc")).isEmpty();
    }

    @Test
    void doesNotExposeAnyPassword() throws Exception {
        mockMvc.perform(get("/api/me").with(as("abc")))
                .andExpect(jsonPath("$.password").doesNotExist());
    }
}
