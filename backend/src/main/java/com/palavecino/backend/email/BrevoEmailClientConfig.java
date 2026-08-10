package com.palavecino.backend.email;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Production wiring for the {@link RestClient} used by {@link BrevoApiEmailSender}: Brevo's base
 * URL plus bounded connect/read timeouts. A send runs on a dedicated async thread, and without a
 * timeout a stalled Brevo API would block that thread indefinitely (invisible, since failures
 * never propagate) — mirrors the timeout discipline the old SMTP config used to apply.
 */
@Configuration
public class BrevoEmailClientConfig {

    private static final String BREVO_BASE_URL = "https://api.brevo.com";

    @Bean
    public RestClient brevoRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(10_000);
        requestFactory.setReadTimeout(10_000);

        return RestClient.builder()
                .baseUrl(BREVO_BASE_URL)
                .requestFactory(requestFactory)
                .build();
    }
}
