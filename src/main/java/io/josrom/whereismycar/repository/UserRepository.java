package io.josrom.whereismycar.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import io.josrom.whereismycar.entity.User;

@Repository
public interface UserRepository extends CrudRepository<User, String> {
}
