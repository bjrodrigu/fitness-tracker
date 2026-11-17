package com.fitnesstracker.journal.activity;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ActivityRepository extends MongoRepository<Activity, String> {

    Optional<Activity> findByExternalId(String externalId);
}
