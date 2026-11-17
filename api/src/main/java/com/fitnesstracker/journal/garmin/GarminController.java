package com.fitnesstracker.journal.garmin;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fitnesstracker.journal.auth.CurrentUser;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/garmin")
public class GarminController {

    /** Garmin's pending-code id lives in the login session, not in the browser. */
    static final String MFA_ATTR = "garminMfaId";

    private final GarminService garmin;

    public GarminController(GarminService garmin) {
        this.garmin = garmin;
    }

    public record LinkRequest(@NotBlank @Size(max = 200) String email, @NotBlank @Size(max = 200) String password) {
    }

    public record CodeRequest(@NotBlank @Size(max = 12) String code) {
    }

    public record LinkResponse(boolean needsCode, GarminService.Status status) {
    }

    @GetMapping
    public GarminService.Status status() {
        return garmin.status(CurrentUser.id());
    }

    /** The Garmin password is passed straight to Garmin and not stored or logged. */
    @PostMapping("/link")
    public LinkResponse link(@Valid @RequestBody LinkRequest body, HttpSession session) {
        var out = garmin.link(CurrentUser.id(), body.email(), body.password());
        return respond(out, session);
    }

    @PostMapping("/link/code")
    public LinkResponse code(@Valid @RequestBody CodeRequest body, HttpSession session) {
        Object mfaId = session.getAttribute(MFA_ATTR);
        if (!(mfaId instanceof String id)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start again: enter your Garmin email and password.");
        }
        session.removeAttribute(MFA_ATTR);
        return respond(garmin.finishLink(CurrentUser.id(), id, body.code().trim()), session);
    }

    @PostMapping("/sync")
    public GarminService.Status sync() {
        return garmin.syncNow(CurrentUser.id());
    }

    @DeleteMapping("/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink() {
        garmin.unlink(CurrentUser.id());
    }

    private LinkResponse respond(GarminService.LinkOutcome out, HttpSession session) {
        if (out.needsCode()) {
            session.setAttribute(MFA_ATTR, out.mfaId());
        }
        return new LinkResponse(out.needsCode(), out.status());
    }
}
