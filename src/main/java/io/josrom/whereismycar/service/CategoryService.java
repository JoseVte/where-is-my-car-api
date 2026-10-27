package io.josrom.whereismycar.service;

import io.josrom.whereismycar.dto.CategoryRequest;
import io.josrom.whereismycar.entity.Category;
import io.josrom.whereismycar.entity.User;
import io.josrom.whereismycar.exception.ConflictException;
import io.josrom.whereismycar.exception.NotFoundException;
import io.josrom.whereismycar.repository.CategoryRepository;
import io.josrom.whereismycar.repository.SavedLocationRepository;
import io.josrom.whereismycar.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Map.entry;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final SavedLocationRepository savedLocationRepository;

    private static final List<HashMap<String, String>> DEFAULT_CATEGORIES = List.of(
            new HashMap<String, String>(Map.ofEntries(
                entry("label", Category.HOME_LABEL),
                entry("icon", "home"),
                entry("color", "#E65100")
            )),
            new HashMap<String, String>(Map.ofEntries(
                entry("label", Category.FAVORITES_LABEL),
                entry("icon", "favorite"),
                entry("color", "#E91E63")
            )),
            new HashMap<String, String>(Map.ofEntries(
                entry("label", Category.OTHERS_LABEL),
                entry("icon", "route"),
                entry("color", "#7D6E5D")
            ))
    );

    public CategoryService(CategoryRepository categoryRepository, UserRepository userRepository,
                           SavedLocationRepository savedLocationRepository) {
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.savedLocationRepository = savedLocationRepository;
    }

    public List<Category> findAll(String userUuid) {
        return categoryRepository.findByUserUuid(userUuid);
    }

    public Category find(Integer id, String userUuid) {
        return categoryRepository.findByIdAndUserUuid(id, userUuid)
                .orElseThrow(() -> new NotFoundException("error.category.not_found"));
    }

    public Category create(CategoryRequest request, String userUuid) {
        if (categoryRepository.existsByUserUuidAndLabel(userUuid, request.label())) {
            throw categoryAlreadyExists();
        }

        User user = userRepository.findById(userUuid)
                .orElseThrow(() -> new NotFoundException("error.user.not_found"));

        try {
            return categoryRepository.save(request.toEntity(user));
        } catch (DataIntegrityViolationException e) {
            // Two concurrent requests can both pass the exists check; the unique constraint is the real guard.
            throw categoryAlreadyExists();
        }
    }

    public void createDefaultCategories(User user) {
        DEFAULT_CATEGORIES.forEach(categoryData -> {
            Category category = new Category();
            category.setLabel(categoryData.get("label"));
            category.setIcon(categoryData.get("icon"));
            category.setColor(categoryData.get("color"));
            category.setUser(user);
            categoryRepository.save(category);
        });
    }

    public Category update(Integer id, CategoryRequest request, String userUuid) {
        Category category = find(id, userUuid);
        CategoryRequest changes = category.isSystem() ? request.withLabel(category.getLabel()) : request;

        if (categoryRepository.existsByUserUuidAndLabelAndIdNot(userUuid, changes.label(), id)) {
            throw categoryAlreadyExists();
        }

        try {
            return categoryRepository.save(changes.applyTo(category));
        } catch (DataIntegrityViolationException e) {
            throw categoryAlreadyExists();
        }
    }

    /** Deletes the category and all its saved locations (a location always has a category). */
    @Transactional
    public void delete(Integer id, String userUuid) {
        Category category = find(id, userUuid);

        savedLocationRepository.deleteByCategory(category);
        categoryRepository.delete(category);
    }

    private static ConflictException categoryAlreadyExists() {
        return new ConflictException("error.category.already_exists");
    }
}
