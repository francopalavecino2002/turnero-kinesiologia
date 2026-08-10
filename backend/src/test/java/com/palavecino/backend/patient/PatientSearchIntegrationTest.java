package com.palavecino.backend.patient;

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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * GET /api/patients?search=: staff-only autocomplete used by the manual booking flow to find an
 * existing patient (registered or guest) by name or email.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class PatientSearchIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private JwtService jwtService;

    private User professionalUser;
    private Patient registeredPatient;
    private Patient guestPatient;

    @BeforeEach
    void setUp() {
        User patientUser = userRepository.save(new User(unique("maria") + "@example.com", "hash", Role.PATIENT, true));
        registeredPatient = patientRepository.save(new Patient("Maria", "Lopez", "111111", patientUser));

        guestPatient = patientRepository.save(Patient.guest("Jorge Diaz", "999888777", "jorge@example.com"));

        professionalUser = userRepository.save(new User(unique("pro") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional professional = new Professional("Ana", "Gomez", professionalUser);
        professionalRepository.save(professional);
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    @Test
    void searchByFirstNameFindsRegisteredPatient() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/patients")
                        .param("search", "Maria")
                        .header("Authorization", "Bearer " + jwtService.generateToken(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value(registeredPatient.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].fullName").value("Maria Lopez"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].registered").value(true));
    }

    @Test
    void searchByGuestEmailFindsGuestPatient() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/patients")
                        .param("search", "jorge@example.com")
                        .header("Authorization", "Bearer " + jwtService.generateToken(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value(guestPatient.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].registered").value(false));
    }

    @Test
    void searchWithNoMatchesReturnsEmptyList() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/patients")
                        .param("search", "nonexistent-name-zzz")
                        .header("Authorization", "Bearer " + jwtService.generateToken(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(0));
    }

    @Test
    void adminCanSearchPatients() throws Exception {
        User adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/patients")
                        .param("search", "Maria")
                        .header("Authorization", "Bearer " + jwtService.generateToken(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1));
    }

    @Test
    void patientCannotSearchPatients() throws Exception {
        User patientUser = userRepository.save(new User(unique("other") + "@example.com", "hash", Role.PATIENT, true));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/patients")
                        .param("search", "Maria")
                        .header("Authorization", "Bearer " + jwtService.generateToken(patientUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void returns401WhenNoTokenProvided() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/patients")
                        .param("search", "Maria"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }
}
