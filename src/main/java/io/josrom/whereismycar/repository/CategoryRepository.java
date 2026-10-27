package io.josrom.whereismycar.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import io.josrom.whereismycar.entity.Category;

@Repository
public interface CategoryRepository extends CrudRepository<Category, Integer> {
    List<Category> findByUserUuid(String userUuid);

    Optional<Category> findByIdAndUserUuid(Integer id, String userUuid);

    boolean existsByUserUuidAndLabel(String userUuid, String label);

    boolean existsByUserUuidAndLabelAndIdNot(String userUuid, String label, Integer id);
}
