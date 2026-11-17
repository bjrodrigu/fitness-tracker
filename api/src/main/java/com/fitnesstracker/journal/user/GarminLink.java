package com.fitnesstracker.journal.user;

import java.time.Instant;

/**
 * A user's connection to Garmin Connect. Only an encrypted OAuth token is kept, never the
 * Garmin password.
 */
public record GarminLink(
        String encryptedToken,
        Instant linkedAt,
        Instant lastSyncAt,
        /** Last sync that worked; the next one starts a few days before it. */
        Instant lastOkAt,
        SyncStatus lastSyncStatus,
        String lastSyncMessage,
        Integer lastSyncNew) {

    public enum SyncStatus {
        OK,
        /** Garmin rejected the saved token. The user has to link again. */
        NEEDS_RELINK,
        /** Garmin or the sync service was down; the next scheduled run tries again. */
        FAILED
    }

    public GarminLink withToken(String token) {
        return new GarminLink(token, linkedAt, lastSyncAt, lastOkAt, lastSyncStatus, lastSyncMessage, lastSyncNew);
    }

    public GarminLink withResult(Instant at, SyncStatus status, String message, Integer created) {
        return new GarminLink(encryptedToken, linkedAt, at, status == SyncStatus.OK ? at : lastOkAt, status, message,
                created);
    }
}
