package com.palavecino.backend.email;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders the HTML templates under {@code resources/templates/email/} with Thymeleaf and hands
 * the result to the {@link EmailSender}.
 *
 * <p>Every public method is {@code @Async}: sending runs on a dedicated executor, never on the
 * HTTP request thread, and any failure (SMTP down, invalid key, template error) is logged and
 * swallowed here. A broken mail provider must not fail a registration or slow a request down —
 * this is especially important on Render's free tier where long requests are problematic.</p>
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final EmailSender emailSender;
    private final TemplateEngine templateEngine;
    private final String appBaseUrl;
    private final Duration emailVerificationTtl;
    private final Duration passwordResetTtl;

    public EmailService(EmailSender emailSender,
                        TemplateEngine templateEngine,
                        @Value("${app.base-url}") String appBaseUrl,
                        @Value("${app.tokens.email-verification-ttl}") Duration emailVerificationTtl,
                        @Value("${app.tokens.password-reset-ttl}") Duration passwordResetTtl) {
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
        this.appBaseUrl = appBaseUrl;
        this.emailVerificationTtl = emailVerificationTtl;
        this.passwordResetTtl = passwordResetTtl;
    }

    @Async("mailTaskExecutor")
    public void sendVerificationEmail(String to, String firstName, String rawToken) {
        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("link", appBaseUrl + "/verificar-email?token=" + rawToken);
            context.setVariable("expirationHours", emailVerificationTtl.toHours());
            String html = templateEngine.process("email/verification", context);
            emailSender.sendEmail(to, "Confirmá tu email – eQi", html);
        } catch (Exception e) {
            log.error("Failed to send verification email to {}", to, e);
        }
    }

    @Async("mailTaskExecutor")
    public void sendPasswordResetEmail(String to, String firstName, String rawToken) {
        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("link", appBaseUrl + "/restablecer-password?token=" + rawToken);
            context.setVariable("expirationHours", passwordResetTtl.toHours());
            String html = templateEngine.process("email/password-reset", context);
            emailSender.sendEmail(to, "Restablecé tu contraseña – eQi", html);
        } catch (Exception e) {
            log.error("Failed to send password-reset email to {}", to, e);
        }
    }
}
