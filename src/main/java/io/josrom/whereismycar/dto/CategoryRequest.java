package io.josrom.whereismycar.dto;

import io.josrom.whereismycar.entity.Category;
import io.josrom.whereismycar.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
        @NotBlank @Size(max = 50) String label,
        @NotBlank @Size(max = 50) String icon,
        @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "{validation.color.hex}") String color
) {
    /** Runs before validation, so `@NotBlank` and the unique check see the trimmed label. */
    public CategoryRequest {
        label = label == null ? null : label.trim();
    }

    public CategoryRequest withLabel(String label) {
        return new CategoryRequest(label, icon, color);
    }

    public Category toEntity(User user) {
        Category category = new Category();
        category.setUser(user);

        return applyTo(category);
    }

    /** Copies the editable fields onto an existing category (used by update). */
    public Category applyTo(Category category) {
        category.setLabel(label);
        category.setIcon(icon);
        category.setColor(color);

        return category;
    }
}
