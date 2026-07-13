package com.palavecino.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.palavecino.backend.auth.dto.ChangePasswordRequest;
import com.palavecino.backend.auth.dto.LoginRequest;
import com.palavecino.backend.auth.dto.RegisterRequest;
import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.patient.PatientRepository;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class AuthIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String BOOTSTRAP_ADMIN_EMAIL = "test-admin@equi.dev";
    private static final String BOOTSTRAP_ADMIN_PASSWORD = "TestAdmin123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    private RegisterRequest validRegisterRequest(String email) {
        return new RegisterRequest(email, "password123", "Ana", "Perez", "3511234567");
    }

    // ---- register ----

    @Test
    void registerSucceedsAndCreatesUserAndPatient() throws Exception {
        String email = unique("ana") + "@example.com";

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest(email))))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.email").value(email))
                .andExpect(MockMvcResultMatchers.jsonPath("$.role").value("PATIENT"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.notificationsEnabled").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(responseJson).doesNotContain("password");

        Long userId = objectMapper.readTree(responseJson).get("id").asLong();
        User persistedUser = userRepository.findById(userId).orElseThrow();
        assertThat(persistedUser.getPassword()).isNotEqualTo("password123");
        assertThat(persistedUser.getRole().name()).isEqualTo("PATIENT");

        Optional<Patient> persistedPatient = patientRepository.findByUser(persistedUser);
        assertThat(persistedPatient).isPresent();
        assertThat(persistedPatient.get().isNotificationsEnabled()).isTrue();
    }

    @Test
    void registerSendingAdminRoleInBodyStillCreatesPatient() throws Exception {
        String email = unique("escalate") + "@example.com";
        String body = """
                {
                  "email": "%s",
                  "password": "password123",
                  "firstName": "Mal",
                  "lastName": "Actor",
                  "phone": "3510000000",
                  "role": "ADMIN"
                }
                """.formatted(email);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.role").value("PATIENT"));

        User persistedUser = userRepository.findByEmail(email).orElseThrow();
        assertThat(persistedUser.getRole().name()).isEqualTo("PATIENT");
    }

    @Test
    void registerWithExistingEmailReturns409() throws Exception {
        String email = unique("dup") + "@example.com";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest(email))))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest(email))))
                .andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Test
    void registerWithShortPasswordReturns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                unique("short") + "@example.com", "abc123", "Ana", "Perez", "3511234567");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void registerWithMissingFieldsReturns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                unique("missing") + "@example.com", "password123", "", "", "");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void registerWithMalformedEmailReturns400() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "not-an-email", "password123", "Ana", "Perez", "3511234567");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    // ---- login ----

    @Test
    void loginWithCorrectCredentialsReturnsValidJwt() throws Exception {
        String email = unique("login") + "@example.com";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest(email))))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.email").value(email))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(responseJson).get("token").asString();
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void loginWithWrongPasswordAndNonExistentEmailReturnSameGenericMessage() throws Exception {
        String email = unique("wrongpass") + "@example.com";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest(email))))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        String wrongPasswordJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "wrongpassword"))))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String nonExistentEmailJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("nobody" + System.nanoTime() + "@example.com", "whatever1"))))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        String wrongPasswordMessage = objectMapper.readTree(wrongPasswordJson).get("message").asString();
        String nonExistentEmailMessage = objectMapper.readTree(nonExistentEmailJson).get("message").asString();

        assertThat(wrongPasswordMessage).isEqualTo(nonExistentEmailMessage);
        assertThat(wrongPasswordMessage).isEqualTo("Invalid email or password");
    }

    // ---- /me ----

    @Test
    void meWithValidTokenReturns200() throws Exception {
        String email = unique("me") + "@example.com";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest(email))))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        String loginJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginJson).get("token").asString();

        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.email").value(email));
    }

    @Test
    void meWithNoTokenReturns401Json() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").exists());
    }

    @Test
    void meWithTamperedTokenReturns401() throws Exception {
        String email = unique("tamper") + "@example.com";
        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRegisterRequest(email))))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        String loginJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest(email, "password123"))))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginJson).get("token").asString();
        String tampered = token.substring(0, token.length() - 2) + "xx";

        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me")
                        .header("Authorization", "Bearer " + tampered))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(3600);
        String expiredToken = Jwts.builder()
                .subject(BOOTSTRAP_ADMIN_EMAIL)
                .claim("role", "ADMIN")
                .issuedAt(Date.from(past.minusSeconds(60)))
                .expiration(Date.from(past))
                .signWith(key)
                .compact();

        mockMvc.perform(MockMvcRequestBuilders.get("/api/auth/me")
                        .header("Authorization", "Bearer " + expiredToken))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    // ---- bootstrap admin ----

    @Test
    void bootstrapAdminIsCreatedAndCanLoginWithMustChangePasswordTrue() throws Exception {
        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.role").value("ADMIN"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.mustChangePassword").value(true))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(responseJson).get("token").asString()).isNotBlank();
    }

    // ---- change password ----

    @Test
    void changePasswordWorksClearsFlagAndInvalidatesOldPassword() throws Exception {
        String loginJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loginJson).get("token").asString();

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest(BOOTSTRAP_ADMIN_PASSWORD, "NewAdminPass123!"))))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(BOOTSTRAP_ADMIN_EMAIL, BOOTSTRAP_ADMIN_PASSWORD))))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        String reLoginJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest(BOOTSTRAP_ADMIN_EMAIL, "NewAdminPass123!"))))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.mustChangePassword").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(reLoginJson).get("token").asString()).isNotBlank();
    }
}
