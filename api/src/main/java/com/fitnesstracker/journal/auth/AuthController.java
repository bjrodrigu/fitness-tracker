package com.fitnesstracker.journal.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fitnesstracker.journal.user.Role;
import com.fitnesstracker.journal.user.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final AuthenticationManager authManager;
    private final SecurityContextRepository contexts;
    private final CookieCsrfTokenRepository csrfTokens;
    private final LoginThrottle throttle;
    private final boolean demoEnabled;

    public AuthController(AuthService auth, AuthenticationManager authManager, SecurityContextRepository contexts,
            CookieCsrfTokenRepository csrfTokens, LoginThrottle throttle,
            @Value("${app.demo-data:false}") boolean demoEnabled) {
        this.auth = auth;
        this.authManager = authManager;
        this.contexts = contexts;
        this.csrfTokens = csrfTokens;
        this.throttle = throttle;
        this.demoEnabled = demoEnabled;
    }

    public record SignupRequest(
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Size(min = 8, max = 128, message = "Use at least 8 characters.") String password,
            @NotBlank @Size(max = 60) String name) {

        public SignupRequest {
            // Phones like to add a trailing space after an autocompleted email.
            email = email == null ? null : email.trim();
        }
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record Me(String id, String email, String name, boolean demo) {
        static Me of(AppUser u) {
            return new Me(u.id(), u.email(), u.name(), u.role() == Role.DEMO);
        }
    }

    public record Config(boolean demo) {
    }

    /** What the sign-in screen should offer. */
    @GetMapping("/config")
    public Config config() {
        return new Config(demoEnabled && auth.demoUser().isPresent());
    }

    @GetMapping("/me")
    public Me me() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof AppUser u) {
            return Me.of(u);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not signed in.");
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public Me signup(@Valid @RequestBody SignupRequest body, HttpServletRequest req, HttpServletResponse res) {
        User user = auth.signup(body.email(), body.password(), body.name());
        return signIn(AppUser.of(user), req, res);
    }

    @PostMapping("/login")
    public Me login(@Valid @RequestBody LoginRequest body, HttpServletRequest req, HttpServletResponse res) {
        String key = AuthService.normalizeEmail(body.email());
        throttle.check(key);
        try {
            var result = authManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(key, body.password()));
            throttle.succeeded(key);
            return signIn((AppUser) result.getPrincipal(), req, res);
        } catch (AuthenticationException e) {
            throttle.failed(key);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "That email and password don't match.");
        }
    }

    /** Signs in to the shared, read-only sample journal. */
    @PostMapping("/demo")
    public Me demo(HttpServletRequest req, HttpServletResponse res) {
        if (!demoEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "There's no demo on this server.");
        }
        User demo = auth.demoUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "There's no demo on this server."));
        return signIn(AppUser.of(demo), req, res);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest req, HttpServletResponse res) {
        var session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        // Fresh token, so the sign-in screen can post straight away.
        csrfTokens.saveToken(csrfTokens.generateToken(req), req, res);
    }

    private Me signIn(AppUser user, HttpServletRequest req, HttpServletResponse res) {
        AppUser principal = user.withoutPassword();
        // New session id on sign-in, so an id planted before sign-in is worthless.
        req.getSession(true);
        req.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null,
                principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, req, res);
        // And a new CSRF token.
        csrfTokens.saveToken(csrfTokens.generateToken(req), req, res);
        return Me.of(principal);
    }
}
