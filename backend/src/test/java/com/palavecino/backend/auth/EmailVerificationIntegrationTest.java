package com.palavecino.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.palavecino.backend.auth.dto.LoginRequest;
import com.palavecino.backend.auth.dto.RegisterRequest;
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
import java.util.List;
import java.util.Optional;
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
class EmailVerificationIntegrationTest {

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
    private JwtService jwtService;

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

    private RegisterRequest validRegisterRequest(String email) {
        return new RegisterRequest(email, "password123", "Ana", "Perez", "3511234567", true);
    }

    private String register(String email) throws Exception {
        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest(email))))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(responseJson).get("id").asText();
    }

    private void verify(String rawToken) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/verify-email")
                        .param("token", rawToken))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    private static String knownRawToken() {
        return "a".repeat(64);
    }

    // ---- register ----

    @Test
    void registerCreatesUnverifiedUserAndIssuesVerificationToken() throws Exception {
        String email = unique("reg") + "@example.com";
        String userId = register(email);

        User persisted = userRepository.findById(Long.parseLong(userId)).orElseThrow();
        assertThat(persisted.isEmailVerified()).isFalse();
        assertThat(persisted.getRole()).isEqualTo(Role.PATIENT);

        Optional<UserToken> token = userTokenRepository.findAll().stream()
                .filter(t -> t.getUser().getId().equals(persisted.getId()))
                .filter(t -> t.getType() == TokenType.EMAIL_VERIFICATION)
                .filter(t -> t.getUsedAt() == null)
                .findFirst();
        assertThat(token).isPresent();
        assertThat(token.get().getExpiresAt()).isAfter(LocalDateTime.now());

        assertThat(emailSender.count()).isEqualTo(1);
        FakeEmailSender.CapturedEmail mail = emailSender.all().get(0);
        assertThat(mail.to()).isEqualTo(email);
        assertThat(mail.subject()).contains("Confirmá tu email");
        assertThat(mail.htmlBody()).contains("/verificar-email?token=");
    }

    // ---- login gating ----

    @Test
    void loginIsRejectedWhileUnverifiedAndAcceptedAfterVerification() throws Exception {
        String email = unique("login") + "@example.com";
        register(email);

        String rejectedJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String message = objectMapper.readTree(rejectedJson).get("message").asString();
        String code = objectMapper.readTree(rejectedJson).get("code").asString();
        assertThat(code).isEqualTo("EMAIL_NOT_VERIFIED");
        assertThat(message).isNotEqualTo("Invalid email or password");

        String rawToken = FakeEmailSender.extractVerificationToken(emailSender.all().get(0).htmlBody());
        verify(rawToken);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.token").isNotEmpty());
    }

    @Test
    void loginWithWrongPasswordStillReturnsGenericMessageForUnverifiedAccount() throws Exception {
        String email = unique("wrongpass") + "@example.com";
        register(email);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "wrongpassword"))))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("Invalid email or password"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").doesNotExist());
    }

    // ---- verify-email ----

    @Test
    void verifyEmailWithValidTokenMarksUserVerified() throws Exception {
        String email = unique("valid") + "@example.com";
        String userId = register(email);
        String rawToken = FakeEmailSender.extractVerificationToken(emailSender.all().get(0).htmlBody());

        verify(rawToken);

        User persisted = userRepository.findById(Long.parseLong(userId)).orElseThrow();
        assertThat(persisted.isEmailVerified()).isTrue();
    }

    @Test
    void verifyEmailWithUnknownTokenIsRejected() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/verify-email")
                        .param("token", knownRawToken()))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void verifyEmailWithExpiredTokenIsRejected() throws Exception {
        User user = userRepository.save(
                new User(unique("expired") + "@example.com", "hash", Role.PATIENT, true, false, false));
        String rawToken = "b".repeat(64);
        userTokenRepository.save(new UserToken(user, UserTokenService.hash(rawToken),
                TokenType.EMAIL_VERIFICATION, LocalDateTime.now().minusHours(2), LocalDateTime.now()));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/verify-email")
                        .param("token", rawToken))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void verifyEmailWithAlreadyUsedTokenIsRejected() throws Exception {
        String email = unique("used") + "@example.com";
        register(email);
        String rawToken = FakeEmailSender.extractVerificationToken(emailSender.all().get(0).htmlBody());
        verify(rawToken);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/verify-email")
                        .param("token", rawToken))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void verifyEmailWithPasswordResetTokenIsRejected() throws Exception {
        User user = userRepository.save(
                new User(unique("wrongtype") + "@example.com", "hash", Role.PATIENT, true));
        String rawToken = userTokenService.issue(user, TokenType.PASSWORD_RESET);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/verify-email")
                        .param("token", rawToken))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    // ---- resend ----

    @Test
    void resendVerificationInvalidatesPreviousToken() throws Exception {
        String email = unique("resend") + "@example.com";
        register(email);

        String oldToken = FakeEmailSender.extractVerificationToken(emailSender.all().get(0).htmlBody());
        emailSender.clear();

        // Past the resend cooldown so a second link is actually issued.
        clock.advance(Duration.ofMinutes(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(emailSender.count()).isEqualTo(1);
        String newToken = FakeEmailSender.extractVerificationToken(emailSender.all().get(0).htmlBody());
        assertThat(newToken).isNotEqualTo(oldToken);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/verify-email")
                        .param("token", oldToken))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        verify(newToken);

        User user = userRepository.findByEmail(email).orElseThrow();
        assertThat(user.isEmailVerified()).isTrue();
    }

    // ---- resend cooldown (rate limit) ----

    @Test
    void resendVerificationWithinCooldownReturns200WithoutSendingOrIssuingNewToken() throws Exception {
        String email = unique("cooldown") + "@example.com";
        register(email);
        emailSender.clear();

        // Registration just issued a verification token, so the resend cooldown is active: two
        // back-to-back resends must both answer 200 (anti-enumeration) but send nothing and create
        // no new token.
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk());

        // No email was ever sent by the resends, and only the single token issued at registration
        // exists: nothing new was created.
        assertThat(emailSender.count()).isZero();
        List<UserToken> tokens = userTokenRepository.findAll().stream()
                .filter(t -> t.getType() == TokenType.EMAIL_VERIFICATION)
                .filter(t -> t.getUser().getEmail().equals(email))
                .toList();
        assertThat(tokens).hasSize(1);
    }

    @Test
    void resendVerificationIsAllowedAgainOnceTheCooldownElapses() throws Exception {
        String email = unique("cooldownok") + "@example.com";
        register(email);
        emailSender.clear();

        // Within cooldown: nothing sent, no new token.
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk());
        assertThat(emailSender.count()).isZero();

        // Once the cooldown window passes, the same request really sends a fresh link.
        clock.advance(Duration.ofMinutes(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + email + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(emailSender.count()).isEqualTo(1);
        String newToken = FakeEmailSender.extractVerificationToken(emailSender.all().get(0).htmlBody());

        verify(newToken);
    }

    @Test
    void resendVerificationForUnknownEmailReturns200GenericWithoutSending() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody" + System.nanoTime() + "@example.com\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").isNotEmpty());

        assertThat(emailSender.count()).isZero();
    }

    @Test
    void resendVerificationForAlreadyVerifiedAccountReturns200GenericWithoutSending() throws Exception {
        User user = userRepository.save(
                new User(unique("verified") + "@example.com", "hash", Role.PATIENT, true));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + user.getEmail() + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(emailSender.count()).isZero();
    }

    @Test
    void resendVerificationForInactiveAccountReturns200GenericWithoutSending() throws Exception {
        User user = userRepository.save(
                new User(unique("inactive") + "@example.com", "hash", Role.PATIENT, false));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/resend-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + user.getEmail() + "\"}"))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(emailSender.count()).isZero();
    }

    // ---- admin-created professionals ----

    @Test
    void adminCreatedProfessionalIsPreVerified() throws Exception {
        User admin = userRepository.save(
                new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));

        String body = """
                {
                    "firstName": "Pro",
                    "lastName": "Fesional",
                    "email": %s,
                    "serviceIds": []
                }
                """.formatted(asJsonString(unique("pro") + "@example.com"));

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals")
                        .header("Authorization", "Bearer " + jwtService.generateToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String email = objectMapper.readTree(responseJson).get("email").asString();
        User created = userRepository.findByEmail(email).orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.PROFESSIONAL);
        assertThat(created.isEmailVerified()).isTrue();
        assertThat(created.isMustChangePassword()).isTrue();
    }

    private static String asJsonString(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
