package com.fitnesstracker.journal.auth;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitnesstracker.journal.user.Role;
import com.fitnesstracker.journal.user.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Session-cookie login for the single-page app.
 *
 * - The session cookie is HttpOnly; sessions are stored in MongoDB (Spring Session).
 * - CSRF uses the double-submit cookie: the app reads XSRF-TOKEN and sends it back as
 *   X-XSRF-TOKEN on every POST, PUT and DELETE.
 * - Reading needs any signed-in user; writing needs a real account (the demo is read-only).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokens,
            SecurityContextRepository contexts, ObjectMapper json) throws Exception {
        http
                .csrf(c -> c.csrfTokenRepository(csrfTokens)
                        // Plain (not XOR-masked) tokens, since the app sends back the cookie value as is.
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .securityContext(s -> s.securityContextRepository(contexts))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/**", "/api/docs/**", "/api/openapi/**", "/swagger-ui/**",
                                "/error")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/**").authenticated()
                        // Only calculates; nothing is saved.
                        .requestMatchers(HttpMethod.POST, "/api/nutrition/suggest").authenticated()
                        .requestMatchers("/api/**").hasRole(Role.USER.name())
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler(accessDenied(json)))
                .formLogin(f -> f.disable())
                .httpBasic(b -> b.disable())
                .logout(l -> l.disable())
                .requestCache(r -> r.disable());
        return http.build();
    }

    @Bean
    CookieCsrfTokenRepository csrfTokenRepository() {
        var repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repo.setCookieCustomizer(c -> c.sameSite("Lax"));
        return repo;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /** Looks people up by email. The demo account can't sign in with a password. */
    @Bean
    UserDetailsService userDetailsService(UserRepository users) {
        return email -> users.findByEmail(AuthService.normalizeEmail(email))
                .filter(u -> u.role() == Role.USER && u.passwordHash() != null)
                .map(AppUser::of)
                .orElseThrow(() -> new UsernameNotFoundException("No account"));
    }

    @Bean
    AuthenticationManager authenticationManager(UserDetailsService users, PasswordEncoder encoder) {
        var provider = new DaoAuthenticationProvider(encoder);
        provider.setUserDetailsService(users);
        return new ProviderManager(provider);
    }

    private static AccessDeniedHandler accessDenied(ObjectMapper json) {
        return (req, res, ex) -> {
            String detail;
            if (ex instanceof MissingCsrfTokenException || ex instanceof InvalidCsrfTokenException) {
                detail = "Your session expired. Reload the page and try again.";
            } else if (CurrentUserRole.is(Role.DEMO)) {
                detail = "The demo is read-only. Create an account to keep your own journal.";
            } else {
                detail = "You can't do that.";
            }
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, detail);
            res.setStatus(HttpStatus.FORBIDDEN.value());
            res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            json.writeValue(res.getOutputStream(), problem);
        };
    }

    /** Makes sure the XSRF-TOKEN cookie is sent, so the app has a token before its first POST. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(req, res);
        }
    }

    static final class CurrentUserRole {
        static boolean is(Role role) {
            var auth = org.springframework.security.core.context.SecurityContextHolder.getContext()
                    .getAuthentication();
            return auth != null && auth.getPrincipal() instanceof AppUser u && u.role() == role;
        }
    }
}
