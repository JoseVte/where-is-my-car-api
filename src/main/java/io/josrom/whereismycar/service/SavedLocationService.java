package io.josrom.whereismycar.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.josrom.whereismycar.dto.SavedLocationRequest;
import io.josrom.whereismycar.dto.SavedLocationResponse;
import io.josrom.whereismycar.entity.Category;
import io.josrom.whereismycar.entity.SavedLocation;
import io.josrom.whereismycar.entity.User;
import io.josrom.whereismycar.exception.NotFoundException;
import io.josrom.whereismycar.repository.SavedLocationRepository;
import io.josrom.whereismycar.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class SavedLocationService {
    private final SavedLocationRepository savedLocationRepository;
    private final UserRepository userRepository;
    private final CategoryService categoryService;

    public SavedLocationService(SavedLocationRepository savedLocationRepository, UserRepository userRepository,
                                CategoryService categoryService) {
        this.savedLocationRepository = savedLocationRepository;
        this.userRepository = userRepository;
        this.categoryService = categoryService;
    }

    /** Newest first. `categoryId` is an optional filter. */
    public List<SavedLocationResponse> findAll(String userUuid, Integer categoryId) {
        List<SavedLocation> locations = categoryId == null
                ? savedLocationRepository.findByUserUuidOrderByDateDesc(userUuid)
                : savedLocationRepository.findByUserUuidAndCategoryIdOrderByDateDesc(userUuid, categoryId);

        return locations.stream().map(SavedLocationResponse::from).toList();
    }

    public SavedLocationResponse find(Integer id, String userUuid) {
        return SavedLocationResponse.from(findEntity(id, userUuid));
    }

    @Transactional
    public SavedLocationResponse create(SavedLocationRequest request, String userUuid) {
        User user = userRepository.findById(userUuid)
                .orElseThrow(() -> new NotFoundException("error.user.not_found"));

        SavedLocation location = request.toEntity(user, categoryOf(request, userUuid));

        return SavedLocationResponse.from(savedLocationRepository.save(location));
    }

    @Transactional
    public SavedLocationResponse update(Integer id, SavedLocationRequest request, String userUuid) {
        SavedLocation location = findEntity(id, userUuid);

        request.applyTo(location, categoryOf(request, userUuid));

        return SavedLocationResponse.from(savedLocationRepository.save(location));
    }

    @Transactional
    public void delete(Integer id, String userUuid) {
        savedLocationRepository.delete(findEntity(id, userUuid));
    }

    private SavedLocation findEntity(Integer id, String userUuid) {
        return savedLocationRepository.findByIdAndUserUuid(id, userUuid)
                .orElseThrow(() -> new NotFoundException("error.saved_location.not_found"));
    }

    /** A category of another user is reported as not found, so ids of other users are not revealed. */
    private Category categoryOf(SavedLocationRequest request, String userUuid) {
        return request.categoryId() == null ? null : categoryService.find(request.categoryId(), userUuid);
    }
}
