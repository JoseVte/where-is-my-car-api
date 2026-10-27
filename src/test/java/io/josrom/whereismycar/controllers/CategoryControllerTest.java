package io.josrom.whereismycar.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.josrom.whereismycar.entity.Category;
import io.josrom.whereismycar.entity.SavedLocation;
import io.josrom.whereismycar.entity.User;
import io.josrom.whereismycar.support.IntegrationTest;

class CategoryControllerTest extends IntegrationTest {
    private static final String VALID = """
            {"label": "Beach", "icon": "beach", "color": "#00BCD4"}""";

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        alice = createUser("alice");
        bob = createUser("bob");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Nested
    class List {
        @Test
        void returnsOnlyTheCategoriesOfTheUser() throws Exception {
            createCategory(alice, "Home");
            createCategory(alice, "Work");
            createCategory(bob, "Bob's");

            mockMvc.perform(get("/api/categories").with(as("alice")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)))
                    .andExpect(jsonPath("$[*].label", containsInAnyOrder("Home", "Work")))
                    .andExpect(jsonPath("$[0].user").doesNotExist());
        }
    }

    @Nested
    class Get {
        @Test
        void returnsAnOwnCategory() throws Exception {
            Category home = createCategory(alice, "Home");

            mockMvc.perform(get("/api/categories/{id}", home.getId()).with(as("alice")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.label").value("Home"));
        }

        @Test
        void hidesCategoriesOfOtherUsers() throws Exception {
            Category bobs = createCategory(bob, "Bob's");

            mockMvc.perform(get("/api/categories/{id}", bobs.getId()).with(as("alice")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.message").value("Category not found"));
        }

        @Test
        void rejectsANonNumericId() throws Exception {
            mockMvc.perform(get("/api/categories/abc").with(as("alice")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.message").value("Invalid request"));
        }
    }

    @Nested
    class Create {
        @Test
        void createsACategoryWithATrimmedLabel() throws Exception {
            mockMvc.perform(json(post("/api/categories").with(as("alice")), """
                            {"label": "  Beach ", "icon": "beach", "color": "#00BCD4"}"""))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.label").value("Beach"));

            assertThat(categoryRepository.findByUserUuid("alice")).extracting(Category::getLabel).containsExactly("Beach");
        }

        @Test
        void validatesEveryField() throws Exception {
            mockMvc.perform(json(post("/api/categories").with(as("alice")), """
                            {"label": "   ", "color": "red"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.label").value("must not be blank"))
                    .andExpect(jsonPath("$.errors.icon").value("must not be blank"))
                    .andExpect(jsonPath("$.errors.color").value("must be a hex color like #E65100"));

            assertThat(categoryRepository.count()).isZero();
        }

        @Test
        void rejectsLabelsLongerThanFiftyCharacters() throws Exception {
            mockMvc.perform(json(post("/api/categories").with(as("alice")),
                            "{\"label\": \"" + "a".repeat(51) + "\", \"icon\": \"x\", \"color\": \"#000000\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.label").exists());
        }

        @Test
        void rejectsADuplicateLabelIgnoringCase() throws Exception {
            createCategory(alice, "Beach");

            mockMvc.perform(json(post("/api/categories").with(as("alice")), """
                            {"label": "beach", "icon": "x", "color": "#000000"}"""))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.message").value("A category with this label already exists"));
        }

        @Test
        void allowsTheSameLabelForDifferentUsers() throws Exception {
            createCategory(bob, "Beach");

            mockMvc.perform(json(post("/api/categories").with(as("alice")), VALID))
                    .andExpect(status().isCreated());
        }

        @Test
        void requiresTheUserToExist() throws Exception {
            mockMvc.perform(json(post("/api/categories").with(as("ghost")), VALID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.message").value("User not found, call GET /api/me first"));
        }

        @Test
        void rejectsMalformedJson() throws Exception {
            mockMvc.perform(json(post("/api/categories").with(as("alice")), "{\"label\":"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.message").value("Invalid request"));
        }
    }

    @Nested
    class Update {
        @Test
        void replacesTheFields() throws Exception {
            Category home = createCategory(alice, "Home");

            mockMvc.perform(json(put("/api/categories/{id}", home.getId()).with(as("alice")), """
                            {"label": " House ", "icon": "house", "color": "#FFFFFF"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.label").value("House"))
                    .andExpect(jsonPath("$.icon").value("house"))
                    .andExpect(jsonPath("$.color").value("#FFFFFF"));
        }

        @Test
        void allowsKeepingTheSameLabel() throws Exception {
            Category home = createCategory(alice, "Home");

            mockMvc.perform(json(put("/api/categories/{id}", home.getId()).with(as("alice")), """
                            {"label": "home", "icon": "house", "color": "#FFFFFF"}"""))
                    .andExpect(status().isOk());
        }

        @Test
        void keepsTheLabelOfDefaultCategories() throws Exception {
            Category home = createCategory(alice, Category.HOME_LABEL);

            mockMvc.perform(json(put("/api/categories/{id}", home.getId()).with(as("alice")), """
                            {"label": "House", "icon": "house", "color": "#FFFFFF"}"""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.label").value(Category.HOME_LABEL))
                    .andExpect(jsonPath("$.icon").value("house"))
                    .andExpect(jsonPath("$.color").value("#FFFFFF"));
        }

        @Test
        void rejectsTheLabelOfAnotherCategory() throws Exception {
            createCategory(alice, "Home");
            Category work = createCategory(alice, "Work");

            mockMvc.perform(json(put("/api/categories/{id}", work.getId()).with(as("alice")), """
                            {"label": "Home", "icon": "x", "color": "#000000"}"""))
                    .andExpect(status().isConflict());
        }

        @Test
        void cannotUpdateCategoriesOfOtherUsers() throws Exception {
            Category bobs = createCategory(bob, "Bob's");

            mockMvc.perform(json(put("/api/categories/{id}", bobs.getId()).with(as("alice")), VALID))
                    .andExpect(status().isNotFound());

            assertThat(categoryRepository.findById(bobs.getId())).get().extracting(Category::getLabel).isEqualTo("Bob's");
        }

        @Test
        void validatesTheBody() throws Exception {
            Category home = createCategory(alice, "Home");

            mockMvc.perform(json(put("/api/categories/{id}", home.getId()).with(as("alice")), "{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        void patchIsNotSupported() throws Exception {
            Category home = createCategory(alice, "Home");

            mockMvc.perform(json(patch("/api/categories/{id}", home.getId()).with(as("alice")), VALID))
                    .andExpect(status().isMethodNotAllowed());
        }
    }

    @Nested
    class Delete {
        @Test
        void deletesTheCategoryAndItsLocationsOnly() throws Exception {
            Category home = createCategory(alice, "Home");
            Category work = createCategory(alice, "Work");
            SavedLocation inHome = createLocation(alice, home, "Garage", Instant.now());
            SavedLocation inWork = createLocation(alice, work, "Office", Instant.now());

            mockMvc.perform(delete("/api/categories/{id}", home.getId()).with(as("alice")))
                    .andExpect(status().isNoContent());

            assertThat(categoryRepository.findById(home.getId())).isEmpty();
            assertThat(savedLocationRepository.findById(inHome.getId())).isEmpty();
            assertThat(savedLocationRepository.findById(inWork.getId())).isPresent();
            assertThat(categoryRepository.findById(work.getId())).isPresent();
            assertThat(userRepository.findById("alice")).isPresent();
        }

        @Test
        void cannotDeleteCategoriesOfOtherUsers() throws Exception {
            Category bobs = createCategory(bob, "Bob's");
            SavedLocation bobsLocation = createLocation(bob, bobs, "Bob's spot", Instant.now());

            mockMvc.perform(delete("/api/categories/{id}", bobs.getId()).with(as("alice")))
                    .andExpect(status().isNotFound());

            assertThat(categoryRepository.findById(bobs.getId())).isPresent();
            assertThat(savedLocationRepository.findById(bobsLocation.getId())).isPresent();
        }
    }

    @Nested
    class Translations {
        @Test
        void usesSpanishWhenRequested() throws Exception {
            mockMvc.perform(json(post("/api/categories").with(as("alice")), "{\"color\": \"red\"}")
                            .header("Accept-Language", "es-ES,es;q=0.9"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.label").value("no debe estar vacío"))
                    .andExpect(jsonPath("$.errors.color").value("debe ser un color hexadecimal como #E65100"));

            mockMvc.perform(get("/api/categories/999999").with(as("alice")).header("Accept-Language", "es"))
                    .andExpect(jsonPath("$.error.message").value("Categoría no encontrada"));
        }

        @Test
        void fallsBackToEnglishForUnsupportedLanguages() throws Exception {
            mockMvc.perform(get("/api/categories/999999").with(as("alice")).header("Accept-Language", "fr"))
                    .andExpect(jsonPath("$.error.message").value("Category not found"));
        }

        @Test
        void picksTheFirstSupportedLanguageOfTheHeader() throws Exception {
            mockMvc.perform(get("/api/categories/999999").with(as("alice")).header("Accept-Language", "de,es;q=0.5"))
                    .andExpect(jsonPath("$.error.message").value("Categoría no encontrada"));
        }
    }
}
