package com.fitnesstracker.journal.user;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/** An account. Email is stored lower-case. */
@Document("users")
public record User(
        @Id String id,
        @Indexed(unique = true) String email,
        String passwordHash,
        String name,
        Role role,
        GarminLink garmin,
        Instant createdAt) {

    public User withGarmin(GarminLink link) {
        return new User(id, email, passwordHash, name, role, link, createdAt);
    }
}
