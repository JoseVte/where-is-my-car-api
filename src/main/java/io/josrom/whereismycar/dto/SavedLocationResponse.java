package io.josrom.whereismycar.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;

import io.josrom.whereismycar.entity.SavedLocation;

/**
 * The entity is not returned directly: its category is a lazy Hibernate proxy that Jackson cannot serialize.
 */
public record SavedLocationResponse(
        Integer id,
        String displayName,
        Double latitude,
        Double longitude,
        String country,
        String city,
        String address,
        Map<String, String> addressData,
        Instant date,
        String note,
        Integer categoryId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static SavedLocationResponse from(SavedLocation location) {
        return new SavedLocationResponse(
                location.getId(),
                location.getDisplayName(),
                location.getLatitude(),
                location.getLongitude(),
                location.getCountry(),
                location.getCity(),
                location.getAddress(),
                location.getAddressData(),
                location.getDate() == null ? null : location.getDate().toInstant(),
                location.getNote(),
                // getId() on a lazy proxy does not trigger a query
                location.getCategory() == null ? null : location.getCategory().getId(),
                location.getCreatedAt(),
                location.getUpdatedAt());
    }
}
