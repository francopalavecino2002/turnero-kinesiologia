package com.palavecino.backend.professional;

import com.palavecino.backend.security.JwtService;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * GET /api/professionals/me: lets a logged-in professional resolve their own professionalId
 * client-side (the JWT itself only carries email/role), used by the manual-booking dialog to
 * implicitly book on their own agenda.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class ProfessionalMeIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private JwtService jwtService;

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    @Test
    void returnsOwnProfessionalRecord() throws Exception {
        User professionalUser = userRepository.save(new User(unique("pro") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional professional = professionalRepository.save(new Professional("Ana", "Gomez", professionalUser));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/professionals/me")
                        .header("Authorization", "Bearer " + jwtService.generateToken(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(professional.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.firstName").value("Ana"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.services").isArray());
    }

    @Test
    void returns403ForPatientCaller() throws Exception {
        User patientUser = userRepository.save(new User(unique("patient") + "@example.com", "hash", Role.PATIENT, true));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/professionals/me")
                        .header("Authorization", "Bearer " + jwtService.generateToken(patientUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void returns404ForAdminWithoutLinkedProfessional() throws Exception {
        User adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/professionals/me")
                        .header("Authorization", "Bearer " + jwtService.generateToken(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void returnsOwnProfessionalRecordForAdminWithLinkedProfessional() throws Exception {
        User adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));
        Professional professional = professionalRepository.save(new Professional("Beto", "Diaz", adminUser));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/professionals/me")
                        .header("Authorization", "Bearer " + jwtService.generateToken(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(professional.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.firstName").value("Beto"));
    }

    @Test
    void returns401WhenNoTokenProvided() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/professionals/me"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }
}
