package com.fitnesstracker.journal.nutrition;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GoalRepository extends MongoRepository<NutritionGoal, String> {
}
