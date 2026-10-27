package io.josrom.whereismycar.repository;

import java.util.List;
import java.util.Optional;

import io.josrom.whereismycar.entity.Category;
import io.josrom.whereismycar.entity.SavedLocation;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SavedLocationRepository extends CrudRepository<SavedLocation, Integer> {
    boolean existsByUserUuid(String userUuid);

    List<SavedLocation> findByUserUuidOrderByDateDesc(String userUuid);

    List<SavedLocation> findByUserUuidAndCategoryIdOrderByDateDesc(String userUuid, Integer categoryId);

    Optional<SavedLocation> findByIdAndUserUuid(Integer id, String userUuid);

    @Modifying
    @Query("delete from SavedLocation s where s.category = :category")
    void deleteByCategory(@Param("category") Category category);
}
