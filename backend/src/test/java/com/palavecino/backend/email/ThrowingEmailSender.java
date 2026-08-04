package com.palavecino.backend.email;

/**
 * {@link EmailSender} that always throws, simulating a broken SMTP provider. Used by
 * {@code AppointmentEmailFailureIntegrationTest} to prove that a failed send never propagates
 * into the booking/cancellation flow (EmailService swallows it on the async thread).
 */
public class ThrowingEmailSender implements EmailSender {

    @Override
    public void sendEmail(String type, String to, String subject, String htmlBody) {
        throw new IllegalStateException("Simulated SMTP failure for " + to);
    }
}
