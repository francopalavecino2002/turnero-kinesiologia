package com.palavecino.backend.email;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Sends transactional emails through Brevo's HTTP REST API instead of SMTP. Render's free tier
 * blocks outbound traffic on the SMTP ports (25/465/587) since September 2025, which made
 * {@code SmtpEmailSender} (the previous, now-removed implementation) silently time out in
 * production while working fine locally. The REST API runs over HTTPS/443, which Render does not
 * block.
 *
 * <p>Same fail-safe contract as the rest of the mail system: any failure (network, timeout, or a
 * non-2xx response from Brevo) is logged and swallowed here, never propagated, so a broken mail
 * provider can never fail the flow that triggered the email.</p>
 *
 * <p>Takes an already-built {@link RestClient} (see {@link BrevoEmailClientConfig} for the
 * production wiring with base URL and timeouts) rather than a {@code RestClient.Builder}, so
 * tests can bind a {@code MockRestServiceServer} to their own builder and pass the resulting
 * client straight in.</p>
 */
@Component
public class BrevoApiEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(BrevoApiEmailSender.class);
    private static final String DEFAULT_FROM_NAME = "eQi – Especialidades Kinésicas";

    private final RestClient restClient;
    private final String apiKey;
    private final String fromAddress;
    private final String fromName;

    public BrevoApiEmailSender(@Qualifier("brevoRestClient") RestClient restClient,
                               @Value("${app.mail.brevo-api-key:}") String apiKey,
                               @Value("${app.mail.from-address:}") String fromAddress,
                               @Value("${app.mail.from-name:}") String fromName) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public void sendEmail(String type, String to, String subject, String htmlBody) {
        String maskedTo = EmailMask.mask(to);
        log.info("[mail:{}] attempting to send '{}' to {}", type, subject, maskedTo);

        if (apiKey.isBlank()) {
            log.warn("[mail:{}] BREVO_API_KEY is not configured; skipping '{}' to {}", type, subject, maskedTo);
            return;
        }

        if (fromAddress.isBlank()) {
            log.warn("[mail:{}] MAIL_FROM_ADDRESS is not configured; skipping '{}' to {}", type, subject, maskedTo);
            return;
        }

        BrevoEmailRequest request = new BrevoEmailRequest(
                new BrevoEmailRequest.Sender(fromName.isBlank() ? DEFAULT_FROM_NAME : fromName, fromAddress),
                List.of(new BrevoEmailRequest.Recipient(to)),
                subject,
                htmlBody);

        try {
            restClient.post()
                    .uri("/v3/smtp/email")
                    .header("api-key", apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
            log.info("[mail:{}] sent '{}' to {}", type, subject, maskedTo);
        } catch (RestClientException e) {
            log.error("[mail:{}] failed to send '{}' to {}", type, subject, maskedTo, e);
        }
    }

    private record BrevoEmailRequest(Sender sender, List<Recipient> to, String subject, String htmlContent) {
        private record Sender(String name, String email) {
        }

        private record Recipient(String email) {
        }
    }
}
