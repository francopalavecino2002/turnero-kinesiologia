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
 * whatever SMTP server is configured via the MAIL_* environment variables.
 *
 * <p>This class owns the full send-path logging so every attempt is visible: an INFO when the
 * send is attempted (with a masked recipient), an INFO when it succeeds, and an ERROR with the
 * full stack trace when it fails. Failures are logged and swallowed here — never propagated — so
 * a broken mail provider can never fail the flow that triggered the email. With no SMTP host
 * configured (local dev, tests) it logs a warning and skips the send instead of failing.
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
    public void sendEmail(String type, String to, String subject, String htmlBody) {
        String maskedTo = EmailMask.mask(to);
        log.info("[mail:{}] attempting to send '{}' to {}", type, subject, maskedTo);

        if (host.isBlank()) {
            log.warn("[mail:{}] MAIL_HOST is not configured; skipping '{}' to {}", type, subject, maskedTo);
            return;
        }

        String sender = resolveSender();
        if (sender == null) {
            log.warn("[mail:{}] neither MAIL_FROM_ADDRESS nor MAIL_USERNAME is configured; "
                    + "skipping '{}' to {}", type, subject, maskedTo);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(sender, fromName.isBlank() ? DEFAULT_FROM_NAME : fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("[mail:{}] sent '{}' to {}", type, subject, maskedTo);
        } catch (Exception e) {
            log.error("[mail:{}] failed to send '{}' to {}", type, subject, maskedTo, e);
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
