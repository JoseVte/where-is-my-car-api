package io.josrom.whereismycar.dto;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import io.josrom.whereismycar.entity.Category;
import io.josrom.whereismycar.entity.SavedLocation;
import io.josrom.whereismycar.entity.User;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Create and update body. `date` defaults to now. `categoryId` is required and must belong to the user.
 */
public record SavedLocationRequest(
        @NotBlank(groups = {OnCreate.class, OnUpdate.class})
        @Size(max = 100, groups = {OnCreate.class, OnUpdate.class})
        String displayName,
        @NotNull(groups = {OnCreate.class, OnUpdate.class})
        @DecimalMin(value = "-90.0", groups = {OnCreate.class, OnUpdate.class})
        @DecimalMax(value = "90.0", groups = {OnCreate.class, OnUpdate.class})
        Double latitude,
        @NotNull(groups = {OnCreate.class, OnUpdate.class})
        @DecimalMin(value = "-180.0", groups = {OnCreate.class, OnUpdate.class})
        @DecimalMax(value = "180.0", groups = {OnCreate.class, OnUpdate.class})
        Double longitude,
        @Size(max = 100, groups = {OnCreate.class, OnUpdate.class})
        String country,
        @Size(max = 100, groups = {OnCreate.class, OnUpdate.class})
        String city,
        @Size(max = 255, groups = {OnCreate.class, OnUpdate.class})
        String address,
        Map<String, String> addressData,
        @ValidDate(formats = {DateValidator.ISO, SavedLocationRequest.LOCAL_DATE_FORMAT},
                groups = {OnCreate.class, OnUpdate.class})
        String date,
        @Size(max = 500, groups = {OnCreate.class, OnUpdate.class})
        String note,
        @NotNull(groups = {OnCreate.class})
        @NotNull(groups = {OnCreate.class, OnUpdate.class})
        Integer categoryId
) {
    /** Also accepted for `date`, read as UTC. */
    public static final String LOCAL_DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public SavedLocationRequest {
        displayName = displayName == null ? null : displayName.trim();
    }

    public SavedLocation toEntity(User user, Category category) {
        SavedLocation location = new SavedLocation();
        location.setUser(user);

        return applyTo(location, category);
    }

    /** Copies the editable fields onto an existing location (used by update). */
    public SavedLocation applyTo(SavedLocation location, Category category) {
        location.setDisplayName(displayName);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setCountry(country);
        location.setCity(city);
        location.setAddress(address);
        location.setAddressData(addressData == null ? null : new HashMap<>(addressData));
        // Already validated, so parse() only returns null when there is no date
        Instant parsed = DateValidator.parse(date, DateValidator.ISO, LOCAL_DATE_FORMAT);
        location.setDate(Date.from(parsed == null ? Instant.now() : parsed));
        location.setNote(note);
        location.setCategory(category);

        return location;
    }
}
