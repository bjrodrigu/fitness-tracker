package com.fitnesstracker.journal.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByEmail(String email);

    long countByRole(Role role);

    @Query("{ 'garmin.encryptedToken': { $exists: true, $ne: null } }")
    List<User> findGarminLinked();
}
