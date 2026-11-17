package com.fitnesstracker.journal.garmin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fitnesstracker.journal.activity.ActivityService;
import com.fitnesstracker.journal.garmin.GarminSyncClient.GarminAuthException;
import com.fitnesstracker.journal.garmin.GarminSyncClient.GarminUnavailableException;
import com.fitnesstracker.journal.user.GarminLink;
import com.fitnesstracker.journal.user.GarminLink.SyncStatus;
import com.fitnesstracker.journal.user.User;
import com.fitnesstracker.journal.user.UserRepository;

/** Linking a Garmin account, and pulling new activities from it into that user's journal. */
@Service
public class GarminService {

    private static final Logger log = LoggerFactory.getLogger(GarminService.class);
    /** How far back the first sync after linking goes. */
    static final int FIRST_SYNC_DAYS = 365;
    /** Later syncs re-read a few days, so edits made on the watch afterwards come through. */
    static final int OVERLAP_DAYS = 3;
    static final Duration MIN_MANUAL_GAP = Duration.ofMinutes(1);

    private final UserRepository users;
    private final ActivityService activities;
    private final GarminSyncClient client;
    private final TokenCipher cipher;
    private final Clock clock;

    public GarminService(UserRepository users, ActivityService activities, GarminSyncClient client,
            TokenCipher cipher, Clock clock) {
        this.users = users;
        this.activities = activities;
        this.client = client;
        this.cipher = cipher;
        this.clock = clock;
    }

    public record Status(boolean available, boolean linked, Instant linkedAt, Instant lastSyncAt,
            SyncStatus lastSyncStatus, String lastSyncMessage, Integer lastSyncNew) {
    }

    /** Result of a link attempt: either linked, or Garmin wants the code it just sent. */
    public record LinkOutcome(boolean needsCode, String mfaId, Status status) {
    }

    public Status status(String userId) {
        GarminLink g = user(userId).garmin();
        boolean linked = g != null && g.encryptedToken() != null;
        return linked
                ? new Status(cipher.available(), true, g.linkedAt(), g.lastSyncAt(), g.lastSyncStatus(),
                        g.lastSyncMessage(), g.lastSyncNew())
                : new Status(cipher.available(), false, null, null, null, null, null);
    }

    public LinkOutcome link(String userId, String email, String password) {
        requireAvailable();
        return finish(userId, call(() -> client.link(email, password)));
    }

    public LinkOutcome finishLink(String userId, String mfaId, String code) {
        requireAvailable();
        return finish(userId, call(() -> client.finishLink(mfaId, code)));
    }

    public void unlink(String userId) {
        User u = user(userId);
        users.save(u.withGarmin(null));
    }

    /** Sync now, from the settings page. */
    public Status syncNow(String userId) {
        User u = user(userId);
        GarminLink g = u.garmin();
        if (g == null || g.encryptedToken() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Link a Garmin account first.");
        }
        if (g.lastSyncAt() != null && g.lastSyncAt().plus(MIN_MANUAL_GAP).isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Synced less than a minute ago.");
        }
        sync(u);
        return status(userId);
    }

    /**
     * Pulls activities for one user and records how it went. Never throws for Garmin problems;
     * they're saved on the account and shown on the settings page.
     */
    public synchronized void sync(User u) {
        GarminLink g = u.garmin();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.systemDefault()));
        LocalDate since = g.lastOkAt() == null
                ? today.minusDays(FIRST_SYNC_DAYS)
                : LocalDate.ofInstant(g.lastOkAt(), ZoneId.systemDefault()).minusDays(OVERLAP_DAYS);
        GarminLink result;
        try {
            var fetched = client.fetch(cipher.decrypt(u.id(), g.encryptedToken()), since);
            var imported = activities.importAll(u.id(), fetched.entries());
            result = g.withResult(now, SyncStatus.OK, null, imported.created());
            if (fetched.token() != null) {
                result = result.withToken(cipher.encrypt(u.id(), fetched.token()));
            }
            log.info("Garmin sync for {}: {} new, {} updated.", u.id(), imported.created(), imported.updated());
        } catch (GarminAuthException e) {
            result = g.withResult(now, SyncStatus.NEEDS_RELINK,
                    "Garmin signed this journal out. Link your Garmin account again.", null);
            log.info("Garmin sync for {}: token rejected.", u.id());
        } catch (GarminUnavailableException e) {
            result = g.withResult(now, SyncStatus.FAILED, e.getMessage(), null);
            log.warn("Garmin sync for {} failed: {}", u.id(), e.getMessage());
        } catch (RuntimeException e) {
            result = g.withResult(now, SyncStatus.FAILED, "Sync failed. The next scheduled run will try again.", null);
            log.warn("Garmin sync for {} failed", u.id(), e);
        }
        // Re-read so a link or unlink that happened during the sync isn't overwritten.
        User fresh = user(u.id());
        if (fresh.garmin() != null && g.encryptedToken().equals(fresh.garmin().encryptedToken())) {
            users.save(fresh.withGarmin(result));
        }
    }

    private LinkOutcome finish(String userId, GarminSyncClient.LinkResult r) {
        if ("needs_code".equals(r.status())) {
            return new LinkOutcome(true, r.mfaId(), status(userId));
        }
        if (r.token() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Garmin didn't send a login token.");
        }
        User u = user(userId);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        User linked = users.save(u.withGarmin(
                new GarminLink(cipher.encrypt(userId, r.token()), now, null, null, null, null, null)));
        sync(linked);
        return new LinkOutcome(false, null, status(userId));
    }

    private GarminSyncClient.LinkResult call(java.util.function.Supplier<GarminSyncClient.LinkResult> f) {
        try {
            return f.get();
        } catch (GarminAuthException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (GarminUnavailableException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
    }

    private void requireAvailable() {
        if (!cipher.available()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Garmin linking is off on this server. Set GARMIN_TOKEN_KEY to turn it on.");
        }
    }

    private User user(String id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in again."));
    }
}
