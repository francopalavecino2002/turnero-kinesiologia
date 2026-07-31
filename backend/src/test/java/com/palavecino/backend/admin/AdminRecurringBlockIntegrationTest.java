package com.palavecino.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.palavecino.backend.appointment.Appointment;
import com.palavecino.backend.appointment.AppointmentRepository;
import com.palavecino.backend.appointment.AppointmentService;
import com.palavecino.backend.appointment.AppointmentStatus;
import com.palavecino.backend.appointment.dto.AvailableSlotResponse;
import com.palavecino.backend.availability.Availability;
import com.palavecino.backend.availability.AvailabilityRepository;
import com.palavecino.backend.availability.DayOfWeek;
import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.patient.PatientRepository;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.recurringblock.RecurringBlock;
import com.palavecino.backend.recurringblock.RecurringBlockRepository;
import com.palavecino.backend.security.JwtService;
import com.palavecino.backend.service.Service;
import com.palavecino.backend.service.ServiceRepository;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
class AdminRecurringBlockIntegrationTest {

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
    private RecurringBlockRepository recurringBlockRepository;

    @Autowired
    private AvailabilityRepository availabilityRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AppointmentService appointmentService;

    private User adminUser;
    private User patientUser;
    private User professionalUser;
    private Professional professional;
    private Service testService;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));
        patientUser = userRepository.save(new User(unique("patient") + "@example.com", "hash", Role.PATIENT, true));
        professionalUser = userRepository.save(new User(unique("pro") + "@example.com", "hash", Role.PROFESSIONAL, true));
        testService = serviceRepository.save(new Service("Test Service", 60, true));
        professional = new Professional("Test", "Professional", professionalUser);
        professional.setServices(new HashSet<>(List.of(testService)));
        professional = professionalRepository.save(professional);
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    private String adminToken() {
        return tokenFor(adminUser);
    }

    private static LocalDate nextDateForDayOfWeek(java.time.DayOfWeek dayOfWeek) {
        LocalDate date = LocalDate.now().plusDays(3);
        while (date.getDayOfWeek() != dayOfWeek) {
            date = date.plusDays(1);
        }
        return date;
    }

    // ---- Authorization tests ----

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/recurring-blocks"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void patientCannotAccess() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + tokenFor(patientUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void professionalCannotAccess() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + tokenFor(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    // ---- CRUD: Create ----

    @Test
    void createBlockWithAllFields() throws Exception {
        String body = """
                {
                    "dayOfWeek": "MONDAY",
                    "startTime": "10:00:00",
                    "endTime": "12:00:00",
                    "description": "Bloque semanal de box",
                    "serviceId": %d,
                    "professionalId": %d
                }
                """.formatted(testService.getId(), professional.getId());

        String json = mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.dayOfWeek").value("MONDAY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.startTime").value("10:00:00"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.endTime").value("12:00:00"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.description").value("Bloque semanal de box"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.serviceId").value(testService.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.serviceName").value("Test Service"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.professionalId").value(professional.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.professionalName").value("Test Professional"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.affectedAppointmentsCount").value(0))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(json).get("id").asLong();
        assertThat(recurringBlockRepository.findById(id)).isPresent();
    }

    @Test
    void createBlockWithoutServiceAndProfessional() throws Exception {
        String body = """
                {
                    "dayOfWeek": "TUESDAY",
                    "startTime": "14:00:00",
                    "endTime": "15:00:00",
                    "description": "Box ocupado por mantenimiento"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.serviceId").isEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.serviceName").isEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.professionalId").isEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.professionalName").isEmpty());
    }

    @Test
    void createBlockStartTimeBeforeEndTimeRequired() throws Exception {
        String body = """
                {
                    "dayOfWeek": "MONDAY",
                    "startTime": "14:00:00",
                    "endTime": "13:00:00",
                    "description": "Bad time order"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("before")));
    }

    @Test
    void createBlockStartTimeEqualToEndTimeReturns400() throws Exception {
        String body = """
                {
                    "dayOfWeek": "MONDAY",
                    "startTime": "14:00:00",
                    "endTime": "14:00:00",
                    "description": "Equal times"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void createBlockWithBlankDescriptionReturns400() throws Exception {
        String body = """
                {
                    "dayOfWeek": "MONDAY",
                    "startTime": "10:00:00",
                    "endTime": "12:00:00",
                    "description": ""
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void createBlockWithNonExistentServiceReturns404() throws Exception {
        String body = """
                {
                    "dayOfWeek": "MONDAY",
                    "startTime": "10:00:00",
                    "endTime": "12:00:00",
                    "description": "Bad service",
                    "serviceId": 99999
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void createBlockWithInactiveServiceReturns400() throws Exception {
        Service inactiveService = serviceRepository.save(new Service("Inactive", 30, false));

        String body = """
                {
                    "dayOfWeek": "MONDAY",
                    "startTime": "10:00:00",
                    "endTime": "12:00:00",
                    "description": "Inactive service",
                    "serviceId": %d
                }
                """.formatted(inactiveService.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("not active")));
    }

    @Test
    void createBlockWithNonExistentProfessionalReturns404() throws Exception {
        String body = """
                {
                    "dayOfWeek": "MONDAY",
                    "startTime": "10:00:00",
                    "endTime": "12:00:00",
                    "description": "Bad professional",
                    "professionalId": 99999
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void createBlockWithInactiveProfessionalReturns400() throws Exception {
        User inactiveProUser = userRepository.save(new User(unique("inactivepro") + "@example.com", "hash", Role.PROFESSIONAL, false));
        Professional inactiveProfessional = new Professional("Inactive", "Pro", inactiveProUser);
        inactiveProfessional = professionalRepository.save(inactiveProfessional);

        String body = """
                {
                    "dayOfWeek": "MONDAY",
                    "startTime": "10:00:00",
                    "endTime": "12:00:00",
                    "description": "Inactive professional",
                    "professionalId": %d
                }
                """.formatted(inactiveProfessional.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("not active")));
    }

    // ---- CRUD: Get by ID ----

    @Test
    void getBlockByIdReturnsFullDetail() throws Exception {
        RecurringBlock block = recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(11, 0),
                        testService, professional, true, "Get test block"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/recurring-blocks/" + block.getId())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.id").value(block.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.dayOfWeek").value("WEDNESDAY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.description").value("Get test block"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.serviceId").value(testService.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.professionalId").value(professional.getId()));
    }

    @Test
    void getBlockByIdNonExistentReturns404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/recurring-blocks/99999")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    // ---- CRUD: Update ----

    @Test
    void updateBlockModifiesFields() throws Exception {
        RecurringBlock block = recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        null, null, true, "Original description"));

        String body = """
                {
                    "dayOfWeek": "FRIDAY",
                    "startTime": "15:00:00",
                    "endTime": "17:00:00",
                    "description": "Updated description",
                    "serviceId": %d,
                    "professionalId": %d
                }
                """.formatted(testService.getId(), professional.getId());

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/recurring-blocks/" + block.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.dayOfWeek").value("FRIDAY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.startTime").value("15:00:00"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.endTime").value("17:00:00"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.description").value("Updated description"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.serviceId").value(testService.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.professionalId").value(professional.getId()));

        RecurringBlock loaded = recurringBlockRepository.findById(block.getId()).orElseThrow();
        assertThat(loaded.getDayOfWeek()).isEqualTo(DayOfWeek.FRIDAY);
        assertThat(loaded.getDescription()).isEqualTo("Updated description");
        assertThat(loaded.getService()).isNotNull();
        assertThat(loaded.getProfessional()).isNotNull();
    }

    @Test
    void updateBlockCanClearServiceAndProfessional() throws Exception {
        RecurringBlock block = recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        testService, professional, true, "With service and pro"));

        String body = """
                {
                    "dayOfWeek": "MONDAY",
                    "startTime": "10:00:00",
                    "endTime": "12:00:00",
                    "description": "Cleared"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/recurring-blocks/" + block.getId())
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.serviceId").isEmpty())
                .andExpect(MockMvcResultMatchers.jsonPath("$.professionalId").isEmpty());
    }

    @Test
    void updateBlockNonExistentReturns404() throws Exception {
        String body = """
                {
                    "dayOfWeek": "MONDAY",
                    "startTime": "10:00:00",
                    "endTime": "12:00:00",
                    "description": "Ghost"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/recurring-blocks/99999")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    // ---- Deactivate / Reactivate ----

    @Test
    void deactivateSetsActiveFalse() throws Exception {
        RecurringBlock block = recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        null, null, true, "To deactivate"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks/" + block.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(false));

        assertThat(recurringBlockRepository.findById(block.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void reactivateSetsActiveTrue() throws Exception {
        RecurringBlock block = recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        null, null, false, "To reactivate"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks/" + block.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.affectedAppointmentsCount").value(0));

        assertThat(recurringBlockRepository.findById(block.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void deactivateNonExistentReturns404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks/99999/deactivate")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    // ---- List ----

    @Test
    void listDefaultReturnsOnlyActive() throws Exception {
        recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        null, null, true, "Active block"));
        recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(15, 0),
                        null, null, false, "Inactive block"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].description").value("Active block"));
    }

    @Test
    void listWithIncludeInactiveTrueReturnsAll() throws Exception {
        recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        null, null, true, "Active block"));
        recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(15, 0),
                        null, null, false, "Inactive block"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/recurring-blocks")
                        .param("includeInactive", "true")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2));
    }

    // ---- Capacity validation ----

    @Test
    void createBlockRejectedWhenCapacityExhausted() throws Exception {
        recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        null, null, true, "Block 1"));
        recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        null, null, true, "Block 2"));

        String body = """
                {
                    "dayOfWeek": "MONDAY",
                    "startTime": "10:00:00",
                    "endTime": "12:00:00",
                    "description": "Block 3 — should be rejected"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("capacidad")));
    }

    @Test
    void createBlockAllowedWhenCapacityNotExhausted() throws Exception {
        recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        null, null, true, "Block 1"));

        String body = """
                {
                    "dayOfWeek": "MONDAY",
                    "startTime": "10:00:00",
                    "endTime": "12:00:00",
                    "description": "Block 2 — allowed"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated());
    }

    @Test
    void reactivateBlockRejectedWhenCapacityExhausted() throws Exception {
        RecurringBlock inactive = recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        null, null, false, "Inactive block"));
        recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        null, null, true, "Block 1"));
        recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        null, null, true, "Block 2"));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks/" + inactive.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("capacidad")));
    }

    @Test
    void deactivateThenReactivateFreesAndReoccupiesCapacity() throws Exception {
        recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        null, null, true, "Permanent block"));

        RecurringBlock blockToToggle = recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(12, 0),
                        null, null, true, "Toggle block"));

        String thirdBody = """
                {
                    "dayOfWeek": "MONDAY",
                    "startTime": "10:00:00",
                    "endTime": "12:00:00",
                    "description": "Third block"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(thirdBody))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks/" + blockToToggle.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.active").value(false));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(thirdBody))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks/" + blockToToggle.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    // ---- Active block affects available slots ----

    @Test
    void activeBlockReducesAvailableSlots() throws Exception {
        LocalDate testMonday = nextDateForDayOfWeek(java.time.DayOfWeek.MONDAY);

        User proUser2 = userRepository.save(new User(unique("pro2") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional pro2 = new Professional("Second", "Pro", proUser2);
        pro2.setServices(new HashSet<>(List.of(testService)));
        pro2 = professionalRepository.save(pro2);

        availabilityRepository.save(new Availability(professional, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(13, 0)));
        availabilityRepository.save(new Availability(pro2, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(13, 0)));

        Patient patient = patientRepository.save(new Patient("Pacient", "Test", "123", patientUser));

        LocalDateTime appointmentTime = LocalDateTime.of(testMonday, LocalTime.of(10, 0));
        appointmentRepository.save(new Appointment(patient, pro2, testService,
                appointmentTime, AppointmentStatus.BOOKED, testService.getDurationMinutes()));

        List<AvailableSlotResponse> slotsWithoutBlock = appointmentService.findAvailableSlots(
                professional.getId(), testService.getId(), testMonday);

        recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0),
                        null, null, true, "Block reducing capacity"));

        List<AvailableSlotResponse> slotsWithBlock = appointmentService.findAvailableSlots(
                professional.getId(), testService.getId(), testMonday);

        assertThat(slotsWithBlock.size()).isLessThan(slotsWithoutBlock.size());
    }

    @Test
    void deactivatingBlockRestoresCapacity() throws Exception {
        LocalDate testMonday = nextDateForDayOfWeek(java.time.DayOfWeek.MONDAY);

        User proUser2 = userRepository.save(new User(unique("pro2") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional pro2 = new Professional("Second", "Pro", proUser2);
        pro2.setServices(new HashSet<>(List.of(testService)));
        pro2 = professionalRepository.save(pro2);

        availabilityRepository.save(new Availability(professional, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(13, 0)));
        availabilityRepository.save(new Availability(pro2, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(13, 0)));

        Patient patient = patientRepository.save(new Patient("Pacient", "Test", "123", patientUser));

        LocalDateTime appointmentTime = LocalDateTime.of(testMonday, LocalTime.of(10, 0));
        appointmentRepository.save(new Appointment(patient, pro2, testService,
                appointmentTime, AppointmentStatus.BOOKED, testService.getDurationMinutes()));

        RecurringBlock block = recurringBlockRepository.save(
                new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(11, 0),
                        null, null, true, "Block reducing capacity"));

        List<AvailableSlotResponse> slotsWithBlock = appointmentService.findAvailableSlots(
                professional.getId(), testService.getId(), testMonday);

        block.setActive(false);
        recurringBlockRepository.save(block);

        List<AvailableSlotResponse> slotsAfterDeactivate = appointmentService.findAvailableSlots(
                professional.getId(), testService.getId(), testMonday);

        assertThat(slotsAfterDeactivate.size()).isGreaterThan(slotsWithBlock.size());
    }

    // ---- affectedAppointmentsCount ----

    @Test
    void createBlockReportsAffectedAppointments() throws Exception {
        LocalDate testMonday = nextDateForDayOfWeek(java.time.DayOfWeek.MONDAY);

        User patientUser2 = userRepository.save(new User(unique("pat2") + "@example.com", "hash", Role.PATIENT, true));
        Patient patient = patientRepository.save(new Patient("Pacient", "Test", "123", patientUser2));

        availabilityRepository.save(new Availability(professional, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(20, 0)));

        LocalDateTime appointmentTime = LocalDateTime.of(testMonday, LocalTime.of(10, 0));
        com.palavecino.backend.appointment.Appointment appointment =
                new com.palavecino.backend.appointment.Appointment(
                        patient, professional, testService, appointmentTime,
                        com.palavecino.backend.appointment.AppointmentStatus.BOOKED,
                        testService.getDurationMinutes());
        appointmentRepository.save(appointment);

        String body = """
                {
                    "dayOfWeek": "MONDAY",
                    "startTime": "10:00:00",
                    "endTime": "12:00:00",
                    "description": "Block overlapping existing appointment"
                }
                """;

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/recurring-blocks")
                        .header("Authorization", "Bearer " + adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.affectedAppointmentsCount")
                        .value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }
}
