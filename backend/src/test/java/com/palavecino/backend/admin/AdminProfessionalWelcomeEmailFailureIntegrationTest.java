package com.palavecino.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.palavecino.backend.email.EmailSender;
import com.palavecino.backend.email.ThrowingEmailSender;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.security.JwtService;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A broken mail provider must never fail creating a professional: EmailService swallows the
 * welcome-email exception on the (synchronous, in-test) async thread, so the creation is not
 * rolled back and the HTTP call still succeeds.
 */
@SpringBootTest(properties = "app.mail.async=false")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Import(AdminProfessionalWelcomeEmailFailureIntegrationTest.ThrowingEmailConfig.class)
class AdminProfessionalWelcomeEmailFailureIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class ThrowingEmailConfig {
        @Bean
        @Primary
        public EmailSender throwingEmailSender() {
            return new ThrowingEmailSender();
        }
    }

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

    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    @Test
    void createProfessionalSucceedsEvenWhenWelcomeEmailFails() throws Exception {
        String email = "falla" + System.nanoTime() + "@example.com";
        String body = """
                {
                    "firstName": "Falla",
                    "lastName": "Email",
                    "email": %s,
                    "serviceIds": []
                }
                """.formatted(asJsonString(email));

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals")
                        .header("Authorization", "Bearer " + jwtService.generateToken(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(responseJson).get("id").asLong();
        assertThat(professionalRepository.findById(id)).isPresent();
    }

    private static String asJsonString(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
