package com.fitnesstracker.journal.auth;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fitnesstracker.journal.activity.Activity;
import com.fitnesstracker.journal.user.Role;
import com.fitnesstracker.journal.user.User;
import com.fitnesstracker.journal.user.UserRepository;

@Service
public class AuthService {

    public static final String DEMO_EMAIL = "demo@training-journal.local";
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final MongoTemplate mongo;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordEncoder encoder, MongoTemplate mongo, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.mongo = mongo;
        this.clock = clock;
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    public User signup(String email, String password, String name) {
        String normalized = normalizeEmail(email);
        if (normalized.equals(DEMO_EMAIL)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That email is taken.");
        }
        boolean first = users.countByRole(Role.USER) == 0;
        User user;
        try {
            user = users.save(new User(null, normalized, encoder.encode(password), name.trim(), Role.USER, null,
                    clock.instant().truncatedTo(ChronoUnit.MILLIS)));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "There's already an account with that email. Log in instead.");
        }
        if (first) {
            claimOwnerless(user.id(), Criteria.where("source").ne("demo"));
        }
        return user;
    }

    public Optional<User> demoUser() {
        return users.findByEmail(DEMO_EMAIL);
    }

    /**
     * Entries saved before accounts existed have no owner. The first account created takes
     * them over, so a journal kept on this machine isn't lost when sign-in is turned on.
     */
    public long claimOwnerless(String userId, Criteria extra) {
        var q = new Query(new Criteria().andOperator(Criteria.where("userId").exists(false), extra));
        long n = mongo.updateMulti(q, Update.update("userId", userId), Activity.class).getModifiedCount();
        if (n > 0) {
            log.info("Gave {} entries without an owner to account {}.", n, userId);
        }
        return n;
    }
}
