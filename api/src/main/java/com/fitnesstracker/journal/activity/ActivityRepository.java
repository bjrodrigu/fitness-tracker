package com.fitnesstracker.journal.activity;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ActivityRepository extends MongoRepository<Activity, String> {

    Optional<Activity> findByIdAndUserId(String id, String userId);

    Optional<Activity> findByUserIdAndExternalId(String userId, String externalId);

    List<Activity> findByUserId(String userId, Sort sort);

    long countByUserId(String userId);

    boolean existsByIdAndUserId(String id, String userId);

    void deleteByIdAndUserId(String id, String userId);
}
