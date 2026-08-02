package com.palavecino.backend.email;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Spring Mail (JavaMail) implementation of {@link EmailSender}. Provider-agnostic: it talks to
 * whatever SMTP server is configured via the MAIL_* environment variables. With no SMTP host
 * configured (local dev, tests) it logs a warning and skips the send instead of failing, so a
 * missing mail config can never break the surrounding flow.
 */
@Component
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);
    private static final String DEFAULT_FROM_NAME = "eQi – Especialidades Kinésicas";

    private final JavaMailSender mailSender;
    private final String host;
    private final String fromAddress;
    private final String fromName;

    public SmtpEmailSender(JavaMailSender mailSender,
                           @Value("${spring.mail.host:}") String host,
                           @Value("${app.mail.from-address:}") String fromAddress,
                           @Value("${app.mail.from-name:}") String fromName) {
        this.mailSender = mailSender;
        this.host = host;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
    }

    @Override
    public void sendEmail(String to, String subject, String htmlBody) {
        if (host.isBlank()) {
            log.warn("MAIL_HOST is not configured; skipping email '{}' to {}", subject, to);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            String sender = resolveSender();
            if (sender == null) {
                log.warn("Neither MAIL_FROM_ADDRESS nor MAIL_USERNAME is configured; "
                        + "skipping email '{}' to {}", subject, to);
                return;
            }

            helper.setFrom(sender, fromName.isBlank() ? DEFAULT_FROM_NAME : fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
        } catch (Exception e) {
            // Rethrown as a runtime exception; EmailService catches it on the async thread and
            // logs it, so a failed send never propagates into the request flow.
            throw new IllegalStateException("Failed to send email '" + subject + "' to " + to, e);
        }
    }

    private String resolveSender() {
        if (!fromAddress.isBlank()) {
            return fromAddress;
        }
        if (mailSender instanceof JavaMailSenderImpl impl && !impl.getUsername().isBlank()) {
            return impl.getUsername();
        }
        return null;
    }
}
