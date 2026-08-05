package com.palavecino.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.palavecino.backend.email.FakeEmailConfig;
import com.palavecino.backend.email.FakeEmailSender;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.security.JwtService;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Creating a professional from the admin panel must send a welcome email (design option b): it
 * announces the account is active and points to "¿Olvidaste tu contraseña?" — it must NOT contain
 * the temporary password. {@code app.mail.async=false} swaps in a synchronous executor so the fake
 * sender is populated before the HTTP call returns.
 */
@SpringBootTest(properties = "app.mail.async=false")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Import(FakeEmailConfig.class)
class AdminProfessionalWelcomeEmailIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private FakeEmailSender emailSender;

    private User adminUser;

    @BeforeEach
    void setUp() {
        emailSender.clear();
        adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    @Test
    void createProfessionalSendsWelcomeEmailWithoutTemporaryPassword() throws Exception {
        String email = "maria" + System.nanoTime() + "@example.com";
        String body = """
                {
                    "firstName": "Maria",
                    "lastName": "Perez",
                    "email": %s,
                    "serviceIds": []
                }
                """.formatted(asJsonString(email));

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals")
                        .header("Authorization", "Bearer " + jwtService.generateToken(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String temporaryPassword = objectMapper.readTree(responseJson).get("temporaryPassword").asText();

        assertThat(emailSender.count()).isEqualTo(1);
        FakeEmailSender.CapturedEmail mail = emailSender.all().get(0);
        assertThat(mail.type()).isEqualTo("professional-welcome");
        assertThat(mail.to()).isEqualTo(email);
        assertThat(mail.subject()).contains("Bienvenido");
        assertThat(mail.htmlBody())
                .contains("Maria")
                .contains("¿Olvidaste tu contraseña?")
                .doesNotContain(temporaryPassword);
    }

    private static String asJsonString(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
