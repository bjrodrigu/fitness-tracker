package com.fitnesstracker.journal.nutrition;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

/** Body weight on a day. One per day per account. */
@Document("weights")
@CompoundIndex(name = "user_date", def = "{'userId': 1, 'date': 1}", unique = true)
public record WeightEntry(@Id String id, @JsonIgnore String userId, LocalDate date, double weightLb) {
}
