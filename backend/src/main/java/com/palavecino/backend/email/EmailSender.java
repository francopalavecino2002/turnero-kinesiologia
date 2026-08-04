package com.palavecino.backend.email;

/**
 * Sends transactional emails. This interface is the single seam between the domain code and
 * the SMTP provider: production uses {@link SmtpEmailSender} (plain Spring Mail), tests use a
 * fake. Everything that needs to email a user depends only on this interface, so switching
 * providers (Brevo → anything else) is a configuration change, never a code change.
 */
public interface EmailSender {

    void sendEmail(String type, String to, String subject, String htmlBody);
}
