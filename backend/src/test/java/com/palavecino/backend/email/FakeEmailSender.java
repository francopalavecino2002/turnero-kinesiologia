package com.palavecino.backend.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory {@link EmailSender} used by the test suite: it records what would have been sent and
 * never touches SMTP. Registered as a {@code @Primary} bean via {@link FakeEmailConfig} so the
 * real {@link SmtpEmailSender} is never invoked during tests.
 */
public class FakeEmailSender implements EmailSender {

    private final List<CapturedEmail> emails = new CopyOnWriteArrayList<>();

    @Override
    public void sendEmail(String type, String to, String subject, String htmlBody) {
        emails.add(new CapturedEmail(type, to, subject, htmlBody));
    }

    public List<CapturedEmail> all() {
        return List.copyOf(emails);
    }

    public int count() {
        return emails.size();
    }

    public void clear() {
        emails.clear();
    }

    /** Extracts the raw token from an emailed verification link ({@code .../verificar-email?token=}). */
    public static String extractVerificationToken(String htmlBody) {
        return extractToken(htmlBody, "/verificar-email");
    }

    /** Extracts the raw token from an emailed reset link ({@code .../restablecer-password?token=}). */
    public static String extractPasswordResetToken(String htmlBody) {
        return extractToken(htmlBody, "/restablecer-password");
    }

    private static String extractToken(String htmlBody, String path) {
        String marker = path + "?token=";
        int start = htmlBody.indexOf(marker);
        assertThat(start).as("link with marker '%s' should be present", marker).isGreaterThanOrEqualTo(0);
        return htmlBody.substring(start + marker.length(), start + marker.length() + 64);
    }

    public record CapturedEmail(String type, String to, String subject, String htmlBody) {
    }
}
