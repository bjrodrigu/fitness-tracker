package com.fitnesstracker.journal.garmin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fitnesstracker.journal.user.UserRepository;

/** Syncs every linked Garmin account on a timer (app.garmin.sync-every, default every 6 hours). */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.garmin.schedule", havingValue = "true", matchIfMissing = true)
class GarminSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(GarminSyncScheduler.class);

    private final UserRepository users;
    private final GarminService garmin;
    private final TokenCipher cipher;

    GarminSyncScheduler(UserRepository users, GarminService garmin, TokenCipher cipher) {
        this.users = users;
        this.garmin = garmin;
        this.cipher = cipher;
    }

    @Scheduled(fixedDelayString = "${app.garmin.sync-every:PT6H}", initialDelayString = "${app.garmin.first-run:PT2M}")
    void syncAll() {
        if (!cipher.available()) {
            return;
        }
        var linked = users.findGarminLinked().stream()
                .filter(u -> u.garmin().lastSyncStatus() != com.fitnesstracker.journal.user.GarminLink.SyncStatus.NEEDS_RELINK)
                .toList();
        if (linked.isEmpty()) {
            return;
        }
        log.info("Scheduled Garmin sync for {} account(s).", linked.size());
        linked.forEach(garmin::sync);
    }
}
