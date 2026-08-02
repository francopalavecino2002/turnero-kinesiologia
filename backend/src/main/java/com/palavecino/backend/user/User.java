package com.palavecino.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_account")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    // Nullable on purpose: accounts created via Google OAuth have no system password.
    @Column(nullable = true)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Origin of the account: LOCAL (email+password) or GOOGLE (OAuth). All pre-existing accounts
    // are LOCAL (backfilled by the V7 migration).
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    protected User() {
    }

    public User(String email, String password, Role role, boolean active) {
        this(email, password, role, active, false, true);
    }

    public User(String email, String password, Role role, boolean active, boolean mustChangePassword) {
        this(email, password, role, active, mustChangePassword, true);
    }

    /**
     * Users created by the system (admin bootstrap, admin-created professionals, dev seeds, tests)
     * default to {@code emailVerified = true}: they were onboarded by a trusted party, so nothing
     * to verify. Only public self-registration in AuthService passes {@code emailVerified = false}.
     */
    public User(String email, String password, Role role, boolean active, boolean mustChangePassword,
                boolean emailVerified) {
        this.email = email;
        this.password = password;
        this.role = role;
        this.active = active;
        this.mustChangePassword = mustChangePassword;
        this.emailVerified = emailVerified;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public AuthProvider getAuthProvider() {
        return authProvider;
    }

    public void setAuthProvider(AuthProvider authProvider) {
        this.authProvider = authProvider;
    }
}
