package com.palavecino.backend.email;

/**
 * Masks email addresses in log output: keeps the first two and last character of the local part
 * plus the domain, so logs stay greppable by domain while exposing as little PII as possible.
 * E.g. {@code juan.perez@example.com} becomes {@code ju***s@example.com}.
 */
public final class EmailMask {

    private EmailMask() {
    }

    public static String mask(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return email;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local + "***" + domain;
        }
        return local.substring(0, 2) + "***" + local.substring(local.length() - 1) + domain;
    }
}
