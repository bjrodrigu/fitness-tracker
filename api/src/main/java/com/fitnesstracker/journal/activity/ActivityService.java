package com.fitnesstracker.journal.activity;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ActivityService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("startTime"));

    private final ActivityRepository repo;
    private final MongoTemplate mongo;
    private final Clock clock;

    public ActivityService(ActivityRepository repo, MongoTemplate mongo, Clock clock) {
        this.repo = repo;
        this.mongo = mongo;
        this.clock = clock;
    }

    /** Filtered list, newest first. Every filter is optional. */
    public List<Activity> search(ActivityType type, LocalDate from, LocalDate to, String text, String tag) {
        List<Criteria> all = new ArrayList<>();
        if (type != null) {
            all.add(Criteria.where("type").is(type));
        }
        if (from != null || to != null) {
            Criteria date = Criteria.where("date");
            if (from != null) {
                date = date.gte(from);
            }
            if (to != null) {
                date = date.lte(to);
            }
            all.add(date);
        }
        if (tag != null && !tag.isBlank()) {
            all.add(Criteria.where("tags").is(tag.trim()));
        }
        if (text != null && !text.isBlank()) {
            Pattern p = Pattern.compile(Pattern.quote(text.trim()), Pattern.CASE_INSENSITIVE);
            all.add(new Criteria().orOperator(
                    Criteria.where("title").regex(p),
                    Criteria.where("notes").regex(p),
                    Criteria.where("tags").regex(p),
                    Criteria.where("exercises.name").regex(p)));
        }
        Query q = new Query();
        if (!all.isEmpty()) {
            q.addCriteria(new Criteria().andOperator(all));
        }
        return mongo.find(q.with(NEWEST_FIRST), Activity.class);
    }

    public List<Activity> findAll() {
        return repo.findAll(NEWEST_FIRST);
    }

    public Activity get(String id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No entry with id " + id));
    }

    public Activity create(Activity a) {
        Instant now = now();
        String source = a.source() == null || a.source().isBlank() ? "manual" : a.source();
        return repo.save(a.withMeta(null, source, now, now));
    }

    public Activity update(String id, Activity a) {
        Activity existing = get(id);
        return repo.save(a.withMeta(id, existing.source(), existing.createdAt(), now()));
    }

    public void delete(String id) {
        if (!repo.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No entry with id " + id);
        }
        repo.deleteById(id);
    }

    /**
     * Insert or update entries from an importer (e.g. Garmin), matched on externalId.
     * Notes, tags and effort already written in the journal are kept, so re-running an
     * import doesn't wipe what you wrote.
     */
    public ImportResult importAll(List<Activity> incoming) {
        int created = 0;
        int updated = 0;
        Instant now = now();
        for (Activity a : incoming) {
            if (a.externalId() == null || a.externalId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Imported entries need an externalId");
            }
            var existing = repo.findByExternalId(a.externalId());
            if (existing.isPresent()) {
                Activity e = existing.get();
                repo.save(new Activity(e.id(), a.type(), a.date(), a.startTime(), e.title(),
                        e.notes() != null ? e.notes() : a.notes(),
                        e.effort() != null ? e.effort() : a.effort(),
                        a.durationSec(), a.distanceMi(), a.avgHeartRate(),
                        e.tags().isEmpty() ? a.tags() : e.tags(),
                        e.exercises().isEmpty() ? a.exercises() : e.exercises(),
                        e.source(), e.externalId(), e.createdAt(), now));
                updated++;
            } else {
                String source = a.source() == null ? "import" : a.source();
                repo.save(a.withMeta(null, source, now, now));
                created++;
            }
        }
        return new ImportResult(created, updated);
    }

    /** Millisecond precision, which is what MongoDB stores. */
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    public record ImportResult(int created, int updated) {
    }
}
