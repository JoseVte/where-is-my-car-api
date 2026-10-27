package io.josrom.whereismycar.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.josrom.whereismycar.entity.Category;
import io.josrom.whereismycar.entity.SavedLocation;
import io.josrom.whereismycar.entity.User;

/** Unit tests for the request and response records (no Spring context). */
class RequestMappingTest {
    private final User user = new User();

    @Test
    void categoryRequestTrimsTheLabelAndHandlesNull() {
        assertThat(new CategoryRequest("  Home ", "home", "#000000").label()).isEqualTo("Home");
        assertThat(new CategoryRequest(null, "home", "#000000").label()).isNull();
    }

    @Test
    void categoryRequestBuildsAndUpdatesTheEntity() {
        Category category = new CategoryRequest("Home", "home", "#000000").toEntity(user);

        assertThat(category.getUser()).isSameAs(user);
        assertThat(category.getLabel()).isEqualTo("Home");

        new CategoryRequest("Work", "work", "#FFFFFF").applyTo(category);

        assertThat(category.getLabel()).isEqualTo("Work");
        assertThat(category.getIcon()).isEqualTo("work");
        assertThat(category.getColor()).isEqualTo("#FFFFFF");
        assertThat(category.getUser()).isSameAs(user);
    }

    @Test
    void savedLocationRequestDefaultsTheDateAndCopiesAddressData() {
        Map<String, String> addressData = new HashMap<>(Map.of("postalCode", "28013"));
        // java.util.Date only keeps milliseconds
        Instant before = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        SavedLocation location = new SavedLocationRequest(" Spot ", 1.0, 2.0, null, null, null,
                addressData, null, null, null).toEntity(user, null);

        assertThat(location.getDisplayName()).isEqualTo("Spot");
        assertThat(location.getDate().toInstant()).isAfterOrEqualTo(before);
        assertThat(location.getUser()).isSameAs(user);

        addressData.put("postalCode", "changed");
        assertThat(location.getAddressData()).containsEntry("postalCode", "28013");
    }

    @Test
    void savedLocationRequestUsesTheGivenDateAndCategory() {
        Category category = new Category();

        SavedLocation location = new SavedLocationRequest("Spot", 1.0, 2.0, "Spain", "Madrid", "Gran Via",
                null, "2026-01-15T10:30:00Z", "note", 1).toEntity(user, category);

        assertThat(location.getDate().toInstant()).isEqualTo(Instant.parse("2026-01-15T10:30:00Z"));
        assertThat(location.getCategory()).isSameAs(category);
        assertThat(location.getAddressData()).isNull();
    }

    @Test
    void savedLocationResponseHandlesMissingCategoryAndDate() {
        SavedLocation location = new SavedLocation();
        location.setId(7);
        location.setDisplayName("Spot");

        SavedLocationResponse response = SavedLocationResponse.from(location);

        assertThat(response.id()).isEqualTo(7);
        assertThat(response.categoryId()).isNull();
        assertThat(response.date()).isNull();
    }
}
