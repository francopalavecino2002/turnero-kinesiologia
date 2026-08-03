package com.palavecino.backend.security;

import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.service.Service;
import com.palavecino.backend.service.ServiceRepository;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import java.time.LocalDate;
import java.util.HashSet;
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
 * Uptime monitors probe endpoints with HEAD (a GET without a response body) to stay light. Every
 * route that is public to GET must therefore also be public to HEAD - otherwise the monitor gets a
 * 401 and flags the backend as down while the site works fine. Protected routes must keep returning
 * 401 on HEAD: opening them up by accident would be a regression.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class PublicRouteHeadAccessIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private UserRepository userRepository;

    private Service activeService;

    private Professional professional;

    @BeforeEach
    void setUp() {
        activeService = serviceRepository.save(new Service("Deporte y Traumatología", 60, true));
        User user = userRepository.save(new User(unique("pro") + "@example.com", "hash", Role.PROFESSIONAL, true));
        professional = new Professional("Marcela", "Altamirano", user);
        professional.setServices(new HashSet<>(java.util.List.of(activeService)));
        professional = professionalRepository.save(professional);
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    // ---- HEAD on public GET routes must be 200, not 401 ----

    @Test
    void headServicesReturns200() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.head("/api/services"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void headProfessionalsForServiceReturns200() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.head("/api/services/" + activeService.getId() + "/professionals"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void headProfessionalByIdReturns200() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.head("/api/professionals/" + professional.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void headAvailableSlotsReturns200() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.head("/api/appointments/available-slots")
                        .param("professionalId", professional.getId().toString())
                        .param("serviceId", activeService.getId().toString())
                        .param("date", LocalDate.now().plusDays(7).toString()))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    // ---- HEAD on protected routes must stay 401 ----

    @Test
    void headOnProtectedPatientRouteStillReturns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.head("/api/appointments/my"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void headOnProtectedAdminRouteStillReturns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.head("/api/admin/services"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void headOnProtectedAgendaRouteStillReturns401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.head("/api/appointments/agenda")
                        .param("date", LocalDate.now().toString()))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }
}
