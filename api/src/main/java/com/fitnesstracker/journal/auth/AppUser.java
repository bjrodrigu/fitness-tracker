package com.fitnesstracker.journal.auth;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.fitnesstracker.journal.user.Role;
import com.fitnesstracker.journal.user.User;

/** The signed-in user, as kept in the login session. Holds no password once signed in. */
public record AppUser(String id, String email, String name, Role role, String password) implements UserDetails {

    static AppUser of(User u) {
        return new AppUser(u.id(), u.email(), u.name(), u.role(), u.passwordHash());
    }

    AppUser withoutPassword() {
        return new AppUser(id, email, name, role, null);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
