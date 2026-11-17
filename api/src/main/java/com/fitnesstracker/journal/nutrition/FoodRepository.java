package com.fitnesstracker.journal.nutrition;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface FoodRepository extends MongoRepository<FoodEntry, String> {
    List<FoodEntry> findByUserIdAndDate(String userId, LocalDate date, Sort sort);

    /** Both ends included (Spring's derived "Between" leaves them out). */
    @Query("{ 'userId': ?0, 'date': { $gte: ?1, $lte: ?2 } }")
    List<FoodEntry> findByUserIdAndDateBetween(String userId, LocalDate from, LocalDate to);

    Optional<FoodEntry> findByIdAndUserId(String id, String userId);

    long countByUserId(String userId);
}
