package io.josrom.whereismycar.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import io.josrom.whereismycar.entity.Category;
import io.josrom.whereismycar.entity.SavedLocation;
import io.josrom.whereismycar.entity.User;
import io.josrom.whereismycar.support.IntegrationTest;

class SavedLocationControllerTest extends IntegrationTest {

    private User alice;
    private User bob;
    private Category home;

    @BeforeEach
    void setUp() {
        alice = createUser("alice");
        bob = createUser("bob");
        home = createCategory(alice, "Home");
    }

    /** Smallest valid body: name, coordinates and a category of alice. */
    private String minimal() {
        return """
                {"displayName": "Spot", "latitude": 40.4, "longitude": -3.7, "categoryId": %d}""".formatted(home.getId());
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Nested
    class List {
        @Test
        void returnsOnlyOwnLocationsNewestFirst() throws Exception {
            Instant now = Instant.now();
            createLocation(alice, home, "Old", now.minus(10, ChronoUnit.DAYS));
            createLocation(alice, null, "New", now);
            createLocation(alice, home, "Middle", now.minus(1, ChronoUnit.DAYS));
            createLocation(bob, null, "Bob's", now);

            mockMvc.perform(get("/api/saved-locations").with(as("alice")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].displayName", contains("New", "Middle", "Old")));
        }

        @Test
        void filtersByCategory() throws Exception {
            createLocation(alice, home, "In home", Instant.now());
            createLocation(alice, null, "Without category", Instant.now());

            mockMvc.perform(get("/api/saved-locations").param("categoryId", home.getId().toString()).with(as("alice")))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].displayName").value("In home"))
                    .andExpect(jsonPath("$[0].categoryId").value(home.getId()));
        }
    }

    @Nested
    class Get {
        @Test
        void returnsAnOwnLocation() throws Exception {
            SavedLocation location = createLocation(alice, home, "Garage", Instant.now());

            mockMvc.perform(get("/api/saved-locations/{id}", location.getId()).with(as("alice")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Garage"))
                    .andExpect(jsonPath("$.categoryId").value(home.getId()))
                    .andExpect(jsonPath("$.user").doesNotExist());
        }

        @Test
        void hidesLocationsOfOtherUsers() throws Exception {
            SavedLocation bobs = createLocation(bob, null, "Bob's", Instant.now());

            mockMvc.perform(get("/api/saved-locations/{id}", bobs.getId()).with(as("alice")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.message").value("Saved location not found"));
        }
    }

    @Nested
    class Create {
        @Test
        void createsALocationWithAllFields() throws Exception {
            mockMvc.perform(json(post("/api/saved-locations").with(as("alice")), """
                            {"displayName": " Garage ", "latitude": 40.42, "longitude": -3.705,
                             "country": "Spain", "city": "Madrid", "address": "Gran Via 28",
                             "addressData": {"postalCode": "28013"}, "date": "2026-01-15T10:30:00Z",
                             "note": "Level -2", "categoryId": %d}""".formatted(home.getId())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").isNumber())
                    .andExpect(jsonPath("$.displayName").value("Garage"))
                    .andExpect(jsonPath("$.city").value("Madrid"))
                    .andExpect(jsonPath("$.addressData.postalCode").value("28013"))
                    .andExpect(jsonPath("$.date").value("2026-01-15T10:30:00Z"))
                    .andExpect(jsonPath("$.categoryId").value(home.getId()));
        }

        @Test
        void storesALongNoteAndAFullAddress() throws Exception {
            // A reverse geocoded address has many keys: it used to overflow the column and return 500
            String addressData = java.util.stream.IntStream.range(0, 30)
                    .mapToObj(i -> "\"Key number %d\": \"Some long value for the address part %d\"".formatted(i, i))
                    .collect(java.util.stream.Collectors.joining(", ", "{", "}"));
            String note = "n".repeat(500);

            mockMvc.perform(json(post("/api/saved-locations").with(as("alice")), """
                            {"displayName": "Full", "latitude": 1, "longitude": 1, "categoryId": %d,
                             "note": "%s", "addressData": %s}""".formatted(home.getId(), note, addressData)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.note").value(note))
                    .andExpect(jsonPath("$.addressData['Key number 29']").value("Some long value for the address part 29"));
        }

        @Test
        void defaultsTheDateToNow() throws Exception {
            Instant before = Instant.now().minusSeconds(1);

            mockMvc.perform(json(post("/api/saved-locations").with(as("alice")), minimal()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.categoryId").value(home.getId()));

            SavedLocation saved = savedLocationRepository.findByUserUuidOrderByDateDesc("alice").getFirst();
            assertThat(saved.getDate().toInstant()).isAfter(before);
        }

        @Test
        void requiresACategory() throws Exception {
            mockMvc.perform(json(post("/api/saved-locations").with(as("alice")), """
                            {"displayName": "Spot", "latitude": 40.4, "longitude": -3.7}""")
                            .header("Accept-Language", "es"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.categoryId").value("no debe ser nulo"));

            assertThat(savedLocationRepository.count()).isZero();
        }

        @Test
        void validatesRequiredFieldsAndCoordinateRanges() throws Exception {
            mockMvc.perform(json(post("/api/saved-locations").with(as("alice")), """
                            {"displayName": "", "latitude": 91, "longitude": -181}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.categoryId").exists())
                    .andExpect(jsonPath("$.errors.displayName").exists())
                    .andExpect(jsonPath("$.errors.latitude").exists())
                    .andExpect(jsonPath("$.errors.longitude").exists());

            mockMvc.perform(json(post("/api/saved-locations").with(as("alice")), "{\"displayName\": \"X\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.latitude").exists())
                    .andExpect(jsonPath("$.errors.longitude").exists());
        }

        @Test
        void acceptsTheSupportedDateFormats() throws Exception {
            mockMvc.perform(json(post("/api/saved-locations").with(as("alice")), """
                            {"displayName": "Local", "latitude": 1, "longitude": 1, "date": "2026-01-15 10:30:00", "categoryId": %d}""".formatted(home.getId())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.date").value("2026-01-15T10:30:00Z"));

            mockMvc.perform(json(post("/api/saved-locations").with(as("alice")), """
                            {"displayName": "Offset", "latitude": 1, "longitude": 1, "date": "2026-01-15T12:30:00.500+02:00", "categoryId": %d}""".formatted(home.getId())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.date").value("2026-01-15T10:30:00.500Z"));
        }

        @Test
        void rejectsAnInvalidDate() throws Exception {
            mockMvc.perform(json(post("/api/saved-locations").with(as("alice")), """
                            {"displayName": "X", "latitude": 1, "longitude": 1, "date": "15/01/2026", "categoryId": %d}""".formatted(home.getId())))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.date").value("must be a date like 2026-01-15T10:30:00Z or 2026-01-15 10:30:00"));

            assertThat(savedLocationRepository.count()).isZero();
        }

        @Test
        void rejectsACategoryOfAnotherUser() throws Exception {
            Category bobs = createCategory(bob, "Bob's");

            mockMvc.perform(json(post("/api/saved-locations").with(as("alice")), """
                            {"displayName": "X", "latitude": 1, "longitude": 1, "categoryId": %d}""".formatted(bobs.getId())))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.error.message").value("Category not found"));

            assertThat(savedLocationRepository.count()).isZero();
        }

        @Test
        void requiresTheUserToExist() throws Exception {
            mockMvc.perform(json(post("/api/saved-locations").with(as("ghost")), minimal()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class Update {
        @Test
        void replacesAllFields() throws Exception {
            SavedLocation location = createLocation(alice, home, "Garage", Instant.now());
            Category work = createCategory(alice, "Work");

            mockMvc.perform(json(put("/api/saved-locations/{id}", location.getId()).with(as("alice")), """
                            {"displayName": "Renamed", "latitude": 41, "longitude": 2, "date": "2026-01-15T10:30:00Z",
                             "categoryId": %d}""".formatted(work.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.displayName").value("Renamed"))
                    .andExpect(jsonPath("$.latitude").value(41.0))
                    .andExpect(jsonPath("$.date").value("2026-01-15T10:30:00Z"))
                    .andExpect(jsonPath("$.categoryId").value(work.getId()));
        }

        @Test
        void validatesTheBody() throws Exception {
            SavedLocation location = createLocation(alice, home, "Garage", Instant.now());

            mockMvc.perform(json(put("/api/saved-locations/{id}", location.getId()).with(as("alice")), """
                            {"displayName": "", "latitude": 1, "longitude": 1, "date": "nope"}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.displayName").exists())
                    .andExpect(jsonPath("$.errors.date").exists())
                    .andExpect(jsonPath("$.errors.categoryId").exists());
        }

        @Test
        void cannotRemoveTheCategory() throws Exception {
            SavedLocation location = createLocation(alice, home, "Garage", Instant.now());

            mockMvc.perform(json(put("/api/saved-locations/{id}", location.getId()).with(as("alice")), """
                            {"displayName": "Garage", "latitude": 40, "longitude": -3, "categoryId": null}"""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.categoryId").exists());
        }

        @Test
        void cannotUpdateLocationsOfOtherUsers() throws Exception {
            SavedLocation bobs = createLocation(bob, null, "Bob's", Instant.now());

            mockMvc.perform(json(put("/api/saved-locations/{id}", bobs.getId()).with(as("alice")), minimal()))
                    .andExpect(status().isNotFound());

            assertThat(savedLocationRepository.findById(bobs.getId())).get()
                    .extracting(SavedLocation::getDisplayName).isEqualTo("Bob's");
        }
    }

    @Nested
    class Delete {
        @Test
        void deletesAnOwnLocation() throws Exception {
            SavedLocation location = createLocation(alice, home, "Garage", Instant.now());

            mockMvc.perform(delete("/api/saved-locations/{id}", location.getId()).with(as("alice")))
                    .andExpect(status().isNoContent());

            assertThat(savedLocationRepository.findById(location.getId())).isEmpty();
            assertThat(categoryRepository.findById(home.getId())).isPresent();
        }

        @Test
        void cannotDeleteLocationsOfOtherUsers() throws Exception {
            SavedLocation bobs = createLocation(bob, null, "Bob's", Instant.now());

            mockMvc.perform(delete("/api/saved-locations/{id}", bobs.getId()).with(as("alice")))
                    .andExpect(status().isNotFound());

            assertThat(savedLocationRepository.findById(bobs.getId())).isPresent();
        }
    }
}
