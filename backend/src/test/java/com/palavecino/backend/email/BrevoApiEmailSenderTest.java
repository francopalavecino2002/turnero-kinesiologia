package com.palavecino.backend.email;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

/**
 * Verifies the request shape sent to Brevo and the fail-safe contract: a 4xx/5xx response or a
 * network/timeout failure is logged and never propagated to the caller.
 */
class BrevoApiEmailSenderTest {

    private MockRestServiceServer server;
    private BrevoApiEmailSender sender;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.brevo.com");
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new BrevoApiEmailSender(builder.build(), "test-api-key", "no-reply@equi.dev", "eQi");

        logAppender = new ListAppender<>();
        logAppender.start();
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BrevoApiEmailSender.class))
                .addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BrevoApiEmailSender.class))
                .detachAppender(logAppender);
    }

    @Test
    void sendsRequestAndLogsSuccessOnBrevoOk() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("api-key", "test-api-key"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header("Accept", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("{\"messageId\":\"abc123\"}", MediaType.APPLICATION_JSON));

        sender.sendEmail("appointment-booked-professional", "pro@example.com", "Te reservaron un turno", "<p>hola</p>");

        server.verify();
        assertLogContains("sent");
    }

    @Test
    void logsFailureWithoutThrowingWhenBrevoReturnsError() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"message\":\"invalid api key\"}"));

        sender.sendEmail("appointment-booked-professional", "pro@example.com", "Te reservaron un turno", "<p>hola</p>");

        server.verify();
        assertLogContains("failed to send");
    }

    @Test
    void logsFailureWithoutThrowingOnNetworkTimeout() {
        server.expect(requestTo("https://api.brevo.com/v3/smtp/email"))
                .andRespond(request -> {
                    throw new IOException("simulated connect timeout");
                });

        sender.sendEmail("appointment-booked-professional", "pro@example.com", "Te reservaron un turno", "<p>hola</p>");

        assertLogContains("failed to send");
    }

    @Test
    void skipsSendWhenApiKeyIsBlank() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.brevo.com");
        BrevoApiEmailSender senderWithoutKey = new BrevoApiEmailSender(builder.build(), "", "no-reply@equi.dev", "eQi");

        senderWithoutKey.sendEmail("appointment-booked-professional", "pro@example.com", "subject", "<p>hola</p>");

        assertLogContains("BREVO_API_KEY is not configured");
    }

    private void assertLogContains(String fragment) {
        boolean found = logAppender.list.stream()
                .anyMatch(event -> event.getFormattedMessage().contains(fragment));
        org.assertj.core.api.Assertions.assertThat(found)
                .withFailMessage("Expected a log message containing '%s' but got: %s", fragment,
                        logAppender.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
                .isTrue();
    }
}
