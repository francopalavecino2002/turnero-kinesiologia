package com.palavecino.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.security.JwtService;
import com.palavecino.backend.service.Service;
import com.palavecino.backend.service.ServiceRepository;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
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
class AdminProfessionalIntegrationTest {

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
    private ServiceRepository serviceRepository;

    @Autowired
    private JwtService jwtService;

    private User adminUser;
    private User patientUser;
    private User professionalUser;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));
        patientUser = userRepository.save(new User(unique("patient") + "@example.com", "hash", Role.PATIENT, true));
        professionalUser = userRepository.save(new User(unique("pro") + "@example.com", "hash", Role.PROFESSIONAL, true));
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    // ---- Authorization tests ----

    @Test
    void adminProfessionalsRequiresAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/professionals"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void patientCannotAccessAdminProfessionals() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/professionals")
                        .header("Authorization", "Bearer " + tokenFor(patientUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void professionalCannotAccessAdminProfessionals() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/professionals")
                        .header("Authorization", "Bearer " + tokenFor(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void allAdminProfessionalEndpointsRequireAdmin() throws Exception {
        String body = """
                {
                    "firstName": "Test",
                    "lastName": "User",
                    "email": "test@example.com",
                    "serviceIds": []
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals")
                        .header("Authorization", "Bearer " + tokenFor(patientUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    // ---- Create ----

    @Test
    void createProfessionalCreatesUserAndProfessional() throws Exception {
        Service service = serviceRepository.save(new Service("Test Service", 60, true));

        String body = """
                {
                    "firstName": "Laura",
                    "lastName": "Garcia",
                    "email": %s,
                    "serviceIds": [%d]
                }
                """.formatted(asJsonString("laura" + System.nanoTime() + "@example.com"), service.getId());

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.firstName").value("Laura"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.lastName").value("Garcia"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.temporaryPassword").isNotEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.services[0].id").value(service.getId()))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(responseJson).get("id").asLong();
        Professional saved = professionalRepository.findById(id).orElseThrow();
        assertThat(saved.getUser().getEmail()).contains("laura");
        assertThat(saved.getUser().getRole()).isEqualTo(Role.PROFESSIONAL);
        assertThat(saved.getUser().isMustChangePassword()).isTrue();
        assertThat(saved.getUser().isActive()).isTrue();
        assertThat(saved.getUser().isEmailVerified()).isTrue();
    }

    @Test
    void createProfessionalWithDuplicateEmailReturns409() throws Exception {
        userRepository.save(new User("duplicate@example.com", "hash", Role.PATIENT, true));

        String body = """
                {
                    "firstName": "Dup",
                    "lastName": "User",
                    "email": "duplicate@example.com",
                    "serviceIds": []
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("ya está registrado")));
    }

    @Test
    void createProfessionalWithInvalidServiceIdReturns400() throws Exception {
        String body = """
                {
                    "firstName": "Bad",
                    "lastName": "Service",
                    "email": %s,
                    "serviceIds": [99999]
                }
                """.formatted(asJsonString("bad" + System.nanoTime() + "@example.com"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("no existen o están inactivos")));
    }

    @Test
    void createProfessionalWithInactiveServiceReturns400() throws Exception {
        Service inactiveService = serviceRepository.save(new Service("Inactive Svc", 60, false));

        String body = """
                {
                    "firstName": "Inactive",
                    "lastName": "Svc",
                    "email": %s,
                    "serviceIds": [%d]
                }
                """.formatted(asJsonString("inact" + System.nanoTime() + "@example.com"), inactiveService.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("no existen o están inactivos")));
    }

    @Test
    void createProfessionalWithEmptyServiceIdsSucceeds() throws Exception {
        String body = """
                {
                    "firstName": "No",
                    "lastName": "Services",
                    "email": %s,
                    "serviceIds": []
                }
                """.formatted(asJsonString("nosvc" + System.nanoTime() + "@example.com"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.services").isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.services").isEmpty());
    }

    @Test
    void createProfessionalWithNullServiceIdsSucceeds() throws Exception {
        String body = """
                {
                    "firstName": "Null",
                    "lastName": "Services",
                    "email": %s
                }
                """.formatted(asJsonString("nullsvc" + System.nanoTime() + "@example.com"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.services").isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.services").isEmpty());
    }

    @Test
    void createProfessionalWithBlankNameReturns400() throws Exception {
        String body = """
                {
                    "firstName": "",
                    "lastName": "OnlyLast",
                    "email": "blank@example.com",
                    "serviceIds": []
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    // ---- Temporary password only in creation ----

    @Test
    void temporaryPasswordOnlyInCreateResponseNotInGet() throws Exception {
        String createBody = """
                {
                    "firstName": "Temp",
                    "lastName": "Pass",
                    "email": %s,
                    "serviceIds": []
                }
                """.formatted(asJsonString("temppass" + System.nanoTime() + "@example.com"));

        String createJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.temporaryPassword").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(createJson).get("id").asLong();

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/professionals/" + id)
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.temporaryPassword").doesNotExist());
    }

    // ---- Get by ID ----

    @Test
    void getProfessionalByIdReturnsFullDetail() throws Exception {
        Service service = serviceRepository.save(new Service("Detail Svc", 45, true));
        User proUser = userRepository.save(new User(unique("detail") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional professional = new Professional("Detail", "Person", proUser);
        professional.setServices(new HashSet<>(List.of(service)));
        professional = professionalRepository.save(professional);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/professionals/" + professional.getId())
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.firstName").value("Detail"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.lastName").value("Person"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.email").value(proUser.getEmail()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.services[0].id").value(service.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.services[0].name").value("Detail Svc"));
    }

    @Test
    void getProfessionalByIdNonExistentReturns404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/professionals/999999")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    // ---- Update ----

    @Test
    void updateProfessionalReplacesServices() throws Exception {
        Service svc1 = serviceRepository.save(new Service("Svc One", 60, true));
        Service svc2 = serviceRepository.save(new Service("Svc Two", 30, true));
        Service svc3 = serviceRepository.save(new Service("Svc Three", 45, true));

        User proUser = userRepository.save(new User(unique("update") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional professional = new Professional("Original", "Name", proUser);
        professional.setServices(new HashSet<>(List.of(svc1, svc2)));
        professional = professionalRepository.save(professional);

        String body = """
                {
                    "firstName": "Updated",
                    "lastName": "Name",
                    "serviceIds": [%d, %d]
                }
                """.formatted(svc2.getId(), svc3.getId());

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/professionals/" + professional.getId())
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.firstName").value("Updated"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.lastName").value("Name"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.services.length()").value(2));

        Professional loaded = professionalRepository.findById(professional.getId()).orElseThrow();
        Set<Long> loadedServiceIds = loaded.getServices().stream()
                .map(Service::getId)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(loadedServiceIds).containsExactlyInAnyOrder(svc2.getId(), svc3.getId());
    }

    @Test
    void updateProfessionalWithNonExistentIdReturns404() throws Exception {
        String body = """
                {
                    "firstName": "Ghost",
                    "lastName": "User",
                    "serviceIds": []
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/professionals/999999")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    // ---- Deactivate ----

    @Test
    void deactivateProfessionalSetsUserInactive() throws Exception {
        User proUser = userRepository.save(new User(unique("deact") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional professional = professionalRepository.save(new Professional("Deact", "User", proUser));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals/" + professional.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(false));

        User loadedUser = userRepository.findById(proUser.getId()).orElseThrow();
        assertThat(loadedUser.isActive()).isFalse();
    }

    @Test
    void deactivateProfessionalStillExists() throws Exception {
        User proUser = userRepository.save(new User(unique("exist") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional professional = professionalRepository.save(new Professional("Exists", "Still", proUser));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals/" + professional.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(professionalRepository.findById(professional.getId())).isPresent();
    }

    @Test
    void deactivateNonExistentProfessionalReturns404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals/999999/deactivate")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    // ---- Reactivate ----

    @Test
    void reactivateProfessionalSetsUserActive() throws Exception {
        User proUser = userRepository.save(new User(unique("react") + "@example.com", "hash", Role.PROFESSIONAL, false));
        Professional professional = professionalRepository.save(new Professional("React", "User", proUser));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals/" + professional.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(true));

        User loadedUser = userRepository.findById(proUser.getId()).orElseThrow();
        assertThat(loadedUser.isActive()).isTrue();
    }

    @Test
    void reactivateAlreadyActiveProfessionalReturns200() throws Exception {
        User proUser = userRepository.save(new User(unique("already") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional professional = professionalRepository.save(new Professional("Already", "Active", proUser));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals/" + professional.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(true));
    }

    // ---- List with includeInactive ----

    @Test
    void listWithIncludeInactiveTrueReturnsAll() throws Exception {
        User activeProUser = userRepository.save(new User(unique("active") + "@example.com", "hash", Role.PROFESSIONAL, true));
        User inactiveProUser = userRepository.save(new User(unique("inactive") + "@example.com", "hash", Role.PROFESSIONAL, false));
        professionalRepository.save(new Professional("Active", "Pro", activeProUser));
        professionalRepository.save(new Professional("Inactive", "Pro", inactiveProUser));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/professionals")
                        .param("includeInactive", "true")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.firstName == 'Active')].active").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.firstName == 'Inactive')].active").value(false));
    }

    @Test
    void listDefaultReturnsOnlyActive() throws Exception {
        User activeProUser = userRepository.save(new User(unique("active2") + "@example.com", "hash", Role.PROFESSIONAL, true));
        User inactiveProUser = userRepository.save(new User(unique("inactive2") + "@example.com", "hash", Role.PROFESSIONAL, false));
        professionalRepository.save(new Professional("Active2", "Pro", activeProUser));
        professionalRepository.save(new Professional("Inactive2", "Pro", inactiveProUser));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/professionals")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.firstName == 'Active2')]").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.firstName == 'Inactive2')]").isEmpty());
    }

    private static String asJsonString(String value) {
        return "\"" + value.replace("\"", "\\\"") + "\"";
    }
}
