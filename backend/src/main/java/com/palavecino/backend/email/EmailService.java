package com.palavecino.backend.email;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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

    // Clinic contact data used by appointment emails. Mirrors what the frontend shows on the
    // landing page; there is no DB entity for the clinic itself, so it lives here as a constant.
    private static final String CLINIC_ADDRESS = "Amancay 450, Neuquén";
    private static final DateTimeFormatter APPOINTMENT_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy, HH:mm 'hs'", new Locale("es", "AR"));

    private final EmailSender emailSender;
    private final TemplateEngine templateEngine;
    private final String appBaseUrl;
    private final Duration emailVerificationTtl;
    private final Duration passwordResetTtl;
    private final long minCancellationHours;

    public EmailService(EmailSender emailSender,
                        TemplateEngine templateEngine,
                        @Value("${app.base-url}") String appBaseUrl,
                        @Value("${app.tokens.email-verification-ttl}") Duration emailVerificationTtl,
                        @Value("${app.tokens.password-reset-ttl}") Duration passwordResetTtl,
                        @Value("${clinic.min-cancellation-hours}") long minCancellationHours) {
        this.emailSender = emailSender;
        this.templateEngine = templateEngine;
        this.appBaseUrl = appBaseUrl;
        this.emailVerificationTtl = emailVerificationTtl;
        this.passwordResetTtl = passwordResetTtl;
        this.minCancellationHours = minCancellationHours;
    }

    @Async("mailTaskExecutor")
    public void sendVerificationEmail(String to, String firstName, String rawToken) {
        try {
            Context context = new Context();
            context.setVariable("firstName", firstName);
            context.setVariable("link", appBaseUrl + "/verificar-email?token=" + rawToken);
            context.setVariable("expirationHours", emailVerificationTtl.toHours());
            String html = templateEngine.process("email/verification", context);
            emailSender.sendEmail("verification", to, "Confirmá tu email – eQi", html);
        } catch (Exception e) {
            // Safety net: SmtpEmailSender logs and swallows its own failures, so this only fires
            // for template/preparation errors (or a custom sender that throws).
            log.error("[mail:verification] failed to prepare/send to {}", EmailMask.mask(to), e);
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
            emailSender.sendEmail("password-reset", to, "Restablecé tu contraseña – eQi", html);
        } catch (Exception e) {
            log.error("[mail:password-reset] failed to prepare/send to {}", EmailMask.mask(to), e);
        }
    }

    @Async("mailTaskExecutor")
    public void sendAppointmentBookedEmail(String to, String patientFirstName, String professionalName,
                                           String serviceName, LocalDateTime dateTime, int durationMinutes) {
        try {
            Context context = new Context();
            context.setVariable("patientFirstName", patientFirstName);
            context.setVariable("professionalName", professionalName);
            context.setVariable("serviceName", serviceName);
            context.setVariable("appointmentDateTime", formatAppointmentDateTime(dateTime));
            context.setVariable("durationMinutes", durationMinutes);
            context.setVariable("clinicAddress", CLINIC_ADDRESS);
            context.setVariable("minCancellationHours", minCancellationHours);
            String html = templateEngine.process("email/appointment-booked", context);
            emailSender.sendEmail("appointment-booked", to, "Turno reservado – eQi", html);
        } catch (Exception e) {
            log.error("[mail:appointment-booked] failed to prepare/send to {}", EmailMask.mask(to), e);
        }
    }

    @Async("mailTaskExecutor")
    public void sendAppointmentCancelledEmail(String to, String patientFirstName, String professionalName,
                                              String serviceName, LocalDateTime dateTime, int durationMinutes) {
        try {
            Context context = new Context();
            context.setVariable("patientFirstName", patientFirstName);
            context.setVariable("professionalName", professionalName);
            context.setVariable("serviceName", serviceName);
            context.setVariable("appointmentDateTime", formatAppointmentDateTime(dateTime));
            context.setVariable("durationMinutes", durationMinutes);
            context.setVariable("clinicAddress", CLINIC_ADDRESS);
            String html = templateEngine.process("email/appointment-cancelled", context);
            emailSender.sendEmail("appointment-cancelled", to, "Turno cancelado – eQi", html);
        } catch (Exception e) {
            log.error("[mail:appointment-cancelled] failed to prepare/send to {}", EmailMask.mask(to), e);
        }
    }

    @Async("mailTaskExecutor")
    public void sendAppointmentBookedToProfessionalEmail(String to, String professionalFirstName, String patientName,
                                                         String serviceName, LocalDateTime dateTime, int durationMinutes) {
        try {
            Context context = new Context();
            context.setVariable("professionalFirstName", professionalFirstName);
            context.setVariable("patientName", patientName);
            context.setVariable("serviceName", serviceName);
            context.setVariable("appointmentDateTime", formatAppointmentDateTime(dateTime));
            context.setVariable("durationMinutes", durationMinutes);
            String html = templateEngine.process("email/appointment-booked-professional", context);
            emailSender.sendEmail("appointment-booked-professional", to, "Te reservaron un turno – eQi", html);
        } catch (Exception e) {
            log.error("[mail:appointment-booked-professional] failed to prepare/send to {}", EmailMask.mask(to), e);
        }
    }

    @Async("mailTaskExecutor")
    public void sendAppointmentCancelledToProfessionalEmail(String to, String professionalFirstName, String patientName,
                                                            String serviceName, LocalDateTime dateTime, int durationMinutes) {
        try {
            Context context = new Context();
            context.setVariable("professionalFirstName", professionalFirstName);
            context.setVariable("patientName", patientName);
            context.setVariable("serviceName", serviceName);
            context.setVariable("appointmentDateTime", formatAppointmentDateTime(dateTime));
            context.setVariable("durationMinutes", durationMinutes);
            String html = templateEngine.process("email/appointment-cancelled-professional", context);
            emailSender.sendEmail("appointment-cancelled-professional", to, "Turno cancelado – eQi", html);
        } catch (Exception e) {
            log.error("[mail:appointment-cancelled-professional] failed to prepare/send to {}", EmailMask.mask(to), e);
        }
    }

    @Async("mailTaskExecutor")
    public void sendWelcomeProfessionalEmail(String to, String professionalFirstName) {
        try {
            Context context = new Context();
            context.setVariable("professionalFirstName", professionalFirstName);
            context.setVariable("appUrl", appBaseUrl);
            String html = templateEngine.process("email/professional-welcome", context);
            emailSender.sendEmail("professional-welcome", to, "Bienvenido/a a eQi – tu cuenta ya está activa", html);
        } catch (Exception e) {
            log.error("[mail:professional-welcome] failed to prepare/send to {}", EmailMask.mask(to), e);
        }
    }

    private String formatAppointmentDateTime(LocalDateTime dateTime) {
        return APPOINTMENT_DATE_TIME_FORMATTER.format(dateTime);
    }
}
