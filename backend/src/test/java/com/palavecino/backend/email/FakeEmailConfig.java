package com.palavecino.backend.email;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Swaps the real SMTP sender for a {@link FakeEmailSender} in test contexts. Combined with
 * {@code app.mail.async=false} (synchronous executor), tests can assert on sent emails
 * deterministically without ever contacting a real mail server.
 */
@TestConfiguration
public class FakeEmailConfig {

    @Bean
    @Primary
    public FakeEmailSender emailSender() {
        return new FakeEmailSender();
    }
}
