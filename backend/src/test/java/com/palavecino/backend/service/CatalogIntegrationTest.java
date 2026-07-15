package com.palavecino.backend.service;

import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
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
 * Covers the read-only catalog endpoints (/api/services, /api/services/{id}/professionals,
 * /api/professionals/{id}): all public, all pass-through reads with no auth required.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class CatalogIntegrationTest {

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
    private Service otherActiveService;
    private Service inactiveService;

    private Professional professionalOfferingActiveService;
    private Professional professionalOfferingOtherService;

    @BeforeEach
    void setUp() {
        activeService = serviceRepository.save(new Service("Deporte y Traumatología", 60, true));
        otherActiveService = serviceRepository.save(new Service("Reeducación Postural (RPG)", 60, true));
        inactiveService = serviceRepository.save(new Service("Servicio Discontinuado", 45, false));

        User user1 = userRepository.save(new User(unique("pro1") + "@example.com", "hash", Role.PROFESSIONAL, true));
        professionalOfferingActiveService = new Professional("Marcela", "Altamirano", user1);
        professionalOfferingActiveService.setServices(new HashSet<>(java.util.List.of(activeService)));
        professionalOfferingActiveService = professionalRepository.save(professionalOfferingActiveService);

        User user2 = userRepository.save(new User(unique("pro2") + "@example.com", "hash", Role.PROFESSIONAL, true));
        professionalOfferingOtherService = new Professional("Alejandra", "González", user2);
        professionalOfferingOtherService.setServices(new HashSet<>(java.util.List.of(otherActiveService)));
        professionalOfferingOtherService = professionalRepository.save(professionalOfferingOtherService);
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    // ---- GET /api/services ----

    @Test
    void listServicesReturnsOnlyActiveOnesWithNoAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/services"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.id == %d)]", inactiveService.getId()).isEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.id == %d)]", activeService.getId()).exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.id == %d)]", otherActiveService.getId()).exists());
    }

    @Test
    void listServicesIncludesNameAndDuration() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/services"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.id == %d)].name", activeService.getId())
                        .value(org.hamcrest.Matchers.contains("Deporte y Traumatología")))
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.id == %d)].durationMinutes", activeService.getId())
                        .value(org.hamcrest.Matchers.contains(60)));
    }

    // ---- GET /api/services/{serviceId}/professionals ----

    @Test
    void listProfessionalsForServiceReturnsOnlyThoseOfferingIt() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/services/" + activeService.getId() + "/professionals"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value(professionalOfferingActiveService.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].firstName").value("Marcela"))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].lastName").value("Altamirano"));
    }

    @Test
    void listProfessionalsForServiceExcludesProfessionalsOfferingOtherServices() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/services/" + activeService.getId() + "/professionals"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.id == %d)]", professionalOfferingOtherService.getId())
                        .isEmpty());
    }

    @Test
    void listProfessionalsForServiceWithNoProfessionalsReturnsEmptyList() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/services/" + inactiveService.getId() + "/professionals"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(0));
    }

    @Test
    void listProfessionalsForNonExistentServiceReturns404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/services/999999/professionals"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void listProfessionalsForServiceWorksWithNoAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/services/" + activeService.getId() + "/professionals"))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    // ---- GET /api/professionals/{id} ----

    @Test
    void getProfessionalByIdReturnsBasicInfoWithNoAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/professionals/" + professionalOfferingActiveService.getId()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(professionalOfferingActiveService.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.firstName").value("Marcela"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.lastName").value("Altamirano"));
    }

    @Test
    void getProfessionalByNonExistentIdReturns404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/professionals/999999"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }
}
