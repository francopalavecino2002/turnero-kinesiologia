package com.palavecino.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.palavecino.backend.auth.dto.LoginRequest;
import com.palavecino.backend.email.FakeEmailConfig;
import com.palavecino.backend.email.FakeEmailSender;
import com.palavecino.backend.security.JwtService;
import com.palavecino.backend.support.AdvanceableClock;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import com.palavecino.backend.usertoken.TokenType;
import com.palavecino.backend.usertoken.UserToken;
import com.palavecino.backend.usertoken.UserTokenRepository;
import com.palavecino.backend.usertoken.UserTokenService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = "app.mail.async=false")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Import(FakeEmailConfig.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class PasswordResetIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return new AdvanceableClock(Instant.now(), ZoneId.of("America/Argentina/Buenos_Aires"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserTokenRepository userTokenRepository;

    @Autowired
    private UserTokenService userTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private FakeEmailSender emailSender;

    @Autowired
    private AdvanceableClock clock;

    @BeforeEach
    void setUp() {
        clock.resetToNow();
        emailSender.clear();
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    private void forgotPassword(String email) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").isNotEmpty());
    }

    private void resetPassword(String token, String newPassword) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\", \"newPassword\": \"" + newPassword + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    private User saveVerifiedUser(String email) {
        return userRepository.save(new User(email, passwordEncoder.encode("password123"), Role.PATIENT, true));
    }

    // ---- forgot-password ----

    @Test
    void forgotPasswordForUnknownEmailReturns200GenericWithoutSending() throws Exception {
        forgotPassword("nobody" + System.nanoTime() + "@example.com");

        assertThat(emailSender.count()).isZero();
    }

    @Test
    void forgotPasswordForUnverifiedAccountReturns200GenericWithoutSending() throws Exception {
        userRepository.save(new User(unique("unverified") + "@example.com",
                passwordEncoder.encode("password123"), Role.PATIENT, true, false, false));

        forgotPassword("unverified@example.com");

        assertThat(emailSender.count()).isZero();
    }

    @Test
    void forgotPasswordForInactiveAccountReturns200GenericWithoutSending() throws Exception {
        userRepository.save(new User(unique("inactive") + "@example.com",
                passwordEncoder.encode("password123"), Role.PATIENT, false));

        // email unknown to the test, reuse the unique one
        forgotPassword(userRepository.findAll().stream()
                .filter(u -> u.getEmail().startsWith("inactive"))
                .map(User::getEmail)
                .findFirst().orElseThrow());

        assertThat(emailSender.count()).isZero();
    }

    @Test
    void forgotPasswordForVerifiedAccountSendsResetLink() throws Exception {
        User user = saveVerifiedUser(unique("reset") + "@example.com");

        forgotPassword(user.getEmail());

        assertThat(emailSender.count()).isEqualTo(1);
        FakeEmailSender.CapturedEmail mail = emailSender.all().get(0);
        assertThat(mail.to()).isEqualTo(user.getEmail());
        assertThat(mail.subject()).contains("Restablecé tu contraseña");
        assertThat(mail.htmlBody()).contains("/restablecer-password?token=");

        String rawToken = FakeEmailSender.extractPasswordResetToken(mail.htmlBody());
        UserToken persisted = userTokenRepository.findByTokenHash(UserTokenService.hash(rawToken)).orElseThrow();
        assertThat(persisted.getType()).isEqualTo(TokenType.PASSWORD_RESET);
        assertThat(persisted.getUsedAt()).isNull();
        assertThat(persisted.getExpiresAt()).isAfter(LocalDateTime.now());
    }

    @Test
    void secondForgotPasswordInvalidatesPreviousToken() throws Exception {
        User user = saveVerifiedUser(unique("twice") + "@example.com");

        forgotPassword(user.getEmail());
        String firstToken = FakeEmailSender.extractPasswordResetToken(emailSender.all().get(0).htmlBody());
        emailSender.clear();

        // Past the resend cooldown so a second link is actually issued.
        clock.advance(Duration.ofMinutes(2));

        forgotPassword(user.getEmail());
        String secondToken = FakeEmailSender.extractPasswordResetToken(emailSender.all().get(0).htmlBody());
        assertThat(secondToken).isNotEqualTo(firstToken);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + firstToken + "\", \"newPassword\": \"firstpass\"}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    // ---- forgot-password cooldown (rate limit) ----

    @Test
    void forgotPasswordWithinCooldownReturns200WithoutSending() throws Exception {
        User user = saveVerifiedUser(unique("cooldown") + "@example.com");

        forgotPassword(user.getEmail());
        assertThat(emailSender.count()).isEqualTo(1);
        emailSender.clear();

        // Second request for the same user inside the 60s window: 200 but no mail.
        forgotPassword(user.getEmail());

        assertThat(emailSender.count()).isZero();
    }

    @Test
    void forgotPasswordIsAllowedAgainOnceTheCooldownElapses() throws Exception {
        User user = saveVerifiedUser(unique("cooldownok") + "@example.com");

        forgotPassword(user.getEmail());
        assertThat(emailSender.count()).isEqualTo(1);
        emailSender.clear();

        clock.advance(Duration.ofMinutes(2));

        forgotPassword(user.getEmail());

        assertThat(emailSender.count()).isEqualTo(1);
    }

    // ---- reset-password ----

    @Test
    void resetPasswordWithValidTokenUpdatesPasswordAndClearsMustChangePassword() throws Exception {
        User user = userRepository.save(new User(unique("resetok") + "@example.com",
                passwordEncoder.encode("password123"), Role.PROFESSIONAL, true, true));
        String token = userTokenService.issue(user, TokenType.PASSWORD_RESET);

        resetPassword(token, "nuevaClave123");

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("nuevaClave123", persisted.getPassword())).isTrue();
        assertThat(persisted.isMustChangePassword()).isFalse();

        UserToken consumed = userTokenRepository.findByTokenHash(UserTokenService.hash(token)).orElseThrow();
        assertThat(consumed.getUsedAt()).isNotNull();

        // old password no longer works, new one does
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(user.getEmail(), "password123"))))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(user.getEmail(), "nuevaClave123"))))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void resetPasswordWithUnknownTokenIsRejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + "c".repeat(64) + "\", \"newPassword\": \"nuevaClave123\"}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void resetPasswordWithAlreadyUsedTokenIsRejected() throws Exception {
        User user = saveVerifiedUser(unique("reuse") + "@example.com");
        String token = userTokenService.issue(user, TokenType.PASSWORD_RESET);

        resetPassword(token, "primera123");
        emailSender.clear();

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\", \"newPassword\": \"segunda123\"}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        User persisted = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches("segunda123", persisted.getPassword())).isFalse();
    }

    @Test
    void resetPasswordWithExpiredTokenIsRejected() throws Exception {
        User user = saveVerifiedUser(unique("expired") + "@example.com");
        String rawToken = "d".repeat(64);
        userTokenRepository.save(new UserToken(user, UserTokenService.hash(rawToken),
                TokenType.PASSWORD_RESET, LocalDateTime.now().minusMinutes(30), LocalDateTime.now()));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + rawToken + "\", \"newPassword\": \"nuevaClave123\"}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void resetPasswordWithEmailVerificationTokenIsRejected() throws Exception {
        User user = saveVerifiedUser(unique("wrongtype") + "@example.com");
        String token = userTokenService.issue(user, TokenType.EMAIL_VERIFICATION);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\", \"newPassword\": \"nuevaClave123\"}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void resetPasswordWithShortNewPasswordReturns400() throws Exception {
        User user = saveVerifiedUser(unique("short") + "@example.com");
        String token = userTokenService.issue(user, TokenType.PASSWORD_RESET);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\": \"" + token + "\", \"newPassword\": \"short\"}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void missingTokenReturns400() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\": \"nuevaClave123\"}"))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }
}
