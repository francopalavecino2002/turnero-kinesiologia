package com.palavecino.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.palavecino.backend.availability.Availability;
import com.palavecino.backend.availability.AvailabilityRepository;
import com.palavecino.backend.availability.DayOfWeek;
import com.palavecino.backend.email.FakeEmailConfig;
import com.palavecino.backend.email.FakeEmailSender;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.security.JwtService;
import com.palavecino.backend.service.Service;
import com.palavecino.backend.service.ServiceRepository;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * Self-service linking of a professional profile to an existing ADMIN account (POST
 * /api/admin/professionals/me/link): the account keeps its role, no new user is created, no
 * welcome email goes out, and the linked professional is immediately visible/bookable in the
 * public flow once it has services and availability. Also covers the deactivate guard: a
 * professional whose user is an ADMIN cannot be deactivated from the panel.
 */
@SpringBootTest(properties = "app.mail.async=false")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Import(FakeEmailConfig.class)
class AdminProfessionalLinkIntegrationTest {

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
    private AvailabilityRepository availabilityRepository;

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

    private static LocalDate nextDateForDayOfWeek(java.time.DayOfWeek dayOfWeek, int minDaysAhead) {
        LocalDate date = LocalDate.now().plusDays(minDaysAhead);
        while (date.getDayOfWeek() != dayOfWeek) {
            date = date.plusDays(1);
        }
        return date;
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    // ---- Link: happy path ----

    @Test
    void linkCreatesProfessionalLinkedToOwnUserWithoutNewAccountOrWelcomeEmail() throws Exception {
        Service service = serviceRepository.save(new Service("General", 60, true));
        long userCountBefore = userRepository.count();

        String body = """
                {
                    "firstName": "Marcela",
                    "lastName": "Altamirano",
                    "serviceIds": [%d]
                }
                """.formatted(service.getId());

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals/me/link")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.firstName").value("Marcela"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.lastName").value("Altamirano"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.email").value(adminUser.getEmail()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.services[0].id").value(service.getId()))
                .andReturn().getResponse().getContentAsString();

        Long professionalId = objectMapper.readTree(responseJson).get("id").asLong();

        Professional saved = professionalRepository.findById(professionalId).orElseThrow();
        assertThat(saved.getUser().getId()).isEqualTo(adminUser.getId());
        assertThat(saved.getUser().getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.getUser().isActive()).isTrue();

        // The profile is attached to the existing account: no new user row.
        assertThat(userRepository.count()).isEqualTo(userCountBefore);

        // The account already existed, so no welcome email is sent.
        assertThat(emailSender.count()).isZero();
    }

    @Test
    void linkWithEmptyServiceIdsSucceeds() throws Exception {
        String body = """
                {
                    "firstName": "Marcela",
                    "lastName": "Altamirano",
                    "serviceIds": []
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals/me/link")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.services").isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.services").isEmpty());
    }

    // ---- Link: validation ----

    @Test
    void linkWhenAccountAlreadyHasProfessionalReturns409() throws Exception {
        professionalRepository.save(new Professional("Already", "Linked", adminUser));

        String body = """
                {
                    "firstName": "Marcela",
                    "lastName": "Altamirano",
                    "serviceIds": []
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals/me/link")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("ya tiene un perfil profesional")));
    }

    @Test
    void linkWithInvalidServiceIdReturns400() throws Exception {
        String body = """
                {
                    "firstName": "Marcela",
                    "lastName": "Altamirano",
                    "serviceIds": [99999]
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals/me/link")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("no existen o están inactivos")));
    }

    @Test
    void linkWithBlankNameReturns400() throws Exception {
        String body = """
                {
                    "firstName": "",
                    "lastName": "Altamirano",
                    "serviceIds": []
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals/me/link")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    // ---- Link: access control ----

    @Test
    void linkRequiresAuthenticationAndAdminRole() throws Exception {
        String body = """
                {
                    "firstName": "Marcela",
                    "lastName": "Altamirano",
                    "serviceIds": []
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals/me/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());

        User patientUser = userRepository.save(new User(unique("patient") + "@example.com", "hash", Role.PATIENT, true));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals/me/link")
                        .header("Authorization", "Bearer " + tokenFor(patientUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    // ---- Deactivate guard ----

    @Test
    void deactivateProfessionalLinkedToAdminUserIsRejected() throws Exception {
        User otherAdmin = userRepository.save(new User(unique("admin2") + "@example.com", "hash", Role.ADMIN, true));
        Professional linked = professionalRepository.save(new Professional("Marcela", "Altamirano", adminUser));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals/" + linked.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + tokenFor(otherAdmin)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("No se puede dar de baja a un administrador")));

        // The admin account is untouched.
        assertThat(userRepository.findById(adminUser.getId()).orElseThrow().isActive()).isTrue();
    }

    // ---- Public booking flow ----

    @Test
    void linkedAdminProfessionalIsVisibleAndBookableInPublicFlow() throws Exception {
        Service service = serviceRepository.save(new Service("General", 60, true));

        String body = """
                {
                    "firstName": "Marcela",
                    "lastName": "Altamirano",
                    "serviceIds": [%d]
                }
                """.formatted(service.getId());

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/professionals/me/link")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long professionalId = objectMapper.readTree(responseJson).get("id").asLong();

        LocalDate bookingDate = nextDateForDayOfWeek(java.time.DayOfWeek.MONDAY, 3);
        availabilityRepository.save(new Availability(
                professionalRepository.findById(professionalId).orElseThrow(),
                DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0)));

        // 1. Public per-service listing includes the linked professional.
        mockMvc.perform(MockMvcRequestBuilders.get("/api/services/" + service.getId() + "/professionals"))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[?(@.firstName == 'Marcela')]").exists());

        // 2. Public detail endpoint resolves it.
        mockMvc.perform(MockMvcRequestBuilders.get("/api/professionals/" + professionalId))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.firstName").value("Marcela"));

        // 3. Public slot calculator returns available hours for the linked professional.
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/available-slots")
                        .param("professionalId", String.valueOf(professionalId))
                        .param("serviceId", String.valueOf(service.getId()))
                        .param("date", bookingDate.toString()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].startTime").isNotEmpty());
    }
}
