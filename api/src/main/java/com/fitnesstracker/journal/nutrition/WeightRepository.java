package com.fitnesstracker.journal.nutrition;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface WeightRepository extends MongoRepository<WeightEntry, String> {
    List<WeightEntry> findByUserIdOrderByDateAsc(String userId);

    Optional<WeightEntry> findByUserIdAndDate(String userId, LocalDate date);

    void deleteByUserIdAndDate(String userId, LocalDate date);
}
