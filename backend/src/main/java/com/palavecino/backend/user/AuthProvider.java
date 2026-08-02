package com.palavecino.backend.user;

/**
 * Origin of a user account. {@code LOCAL} accounts authenticate with a system password;
 * {@code GOOGLE} accounts were created through Google OAuth and have no system password
 * (password is {@code null}).
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
