package com.fitnesstracker.journal.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** The user behind the current request. */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static AppUser get() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUser u) {
            return u;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in first.");
    }

    public static String id() {
        return get().id();
    }
}
