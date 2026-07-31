package com.palavecino.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.palavecino.backend.appointment.AppointmentService;
import com.palavecino.backend.appointment.dto.AvailableSlotResponse;
import com.palavecino.backend.availability.Availability;
import com.palavecino.backend.availability.AvailabilityRepository;
import com.palavecino.backend.availability.DayOfWeek;
import com.palavecino.backend.availability.dto.CreateAvailabilityRequest;
import com.palavecino.backend.availability.dto.UpdateAvailabilityRequest;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.security.JwtService;
import com.palavecino.backend.service.Service;
import com.palavecino.backend.service.ServiceRepository;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
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
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class AdminAvailabilityIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final LocalDate TEST_DATE = LocalDate.of(2026, 7, 15);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-07-10T15:00:00Z"), ZoneId.of("America/Argentina/Buenos_Aires"));
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private AvailabilityRepository availabilityRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AppointmentService appointmentService;

    private User adminUser;
    private User patientUser;
    private User professionalUser;
    private Professional professional;
    private Service generalService;
    private Service emsellaService;

    @BeforeEach
    void setUp() {
        adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));
        patientUser = userRepository.save(new User(unique("patient") + "@example.com", "hash", Role.PATIENT, true));
        professionalUser = userRepository.save(new User(unique("pro") + "@example.com", "hash", Role.PROFESSIONAL, true));

        generalService = serviceRepository.save(new Service("General", 60, true));
        emsellaService = serviceRepository.save(new Service("EMSELLA", 30, true));

        professional = new Professional("Marcela", "Lopez", professionalUser);
        professional.setServices(new HashSet<>(Set.of(generalService, emsellaService)));
        professional = professionalRepository.save(professional);
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    // ---- Auth tests ----

    @Test
    void adminAvailabilityRequiresAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/availability")
                        .param("professionalId", "1"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void patientCannotAccessAdminAvailability() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/availability")
                        .param("professionalId", "1")
                        .header("Authorization", "Bearer " + tokenFor(patientUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void professionalCannotAccessAdminAvailability() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/availability")
                        .param("professionalId", "1")
                        .header("Authorization", "Bearer " + tokenFor(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    // ---- CRUD: List ----

    @Test
    void listReturnsAvailabilitiesForProfessional() throws Exception {
        availabilityRepository.save(new Availability(professional, DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0)));
        availabilityRepository.save(new Availability(professional, DayOfWeek.WEDNESDAY,
                LocalTime.of(14, 0), LocalTime.of(17, 0), emsellaService));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/availability")
                        .param("professionalId", String.valueOf(professional.getId()))
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2));
    }

    @Test
    void listWithNonexistentProfessionalReturns404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/availability")
                        .param("professionalId", "999999")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    // ---- CRUD: Create ----

    @Test
    void createGeneralAvailabilityReturns201() throws Exception {
        CreateAvailabilityRequest request = new CreateAvailabilityRequest(
                professional.getId(), DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(16, 0), null);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/availability")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.professionalId").value(professional.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.dayOfWeek").value("MONDAY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.startTime").value("08:00:00"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.endTime").value("16:00:00"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.serviceId").isEmpty());
    }

    @Test
    void createServiceSpecificAvailabilityReturns201() throws Exception {
        CreateAvailabilityRequest request = new CreateAvailabilityRequest(
                professional.getId(), DayOfWeek.MONDAY, LocalTime.of(16, 0), LocalTime.of(19, 30), emsellaService.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/availability")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.serviceId").value(emsellaService.getId()))
                .andExpect(MockMvcResultMatchers.jsonPath("$.serviceName").value("EMSELLA"));
    }

    @Test
    void createAvailabilityStartTimeMustBeBeforeEndTime() throws Exception {
        CreateAvailabilityRequest request = new CreateAvailabilityRequest(
                professional.getId(), DayOfWeek.MONDAY, LocalTime.of(16, 0), LocalTime.of(15, 0), null);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/availability")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void createAvailabilityWithSameStartEndTimeReturnsBadRequest() throws Exception {
        CreateAvailabilityRequest request = new CreateAvailabilityRequest(
                professional.getId(), DayOfWeek.MONDAY, LocalTime.of(10, 0), LocalTime.of(10, 0), null);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/availability")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void createAvailabilityNonexistentServiceReturns404() throws Exception {
        CreateAvailabilityRequest request = new CreateAvailabilityRequest(
                professional.getId(), DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(16, 0), 999999L);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/availability")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void createAvailabilityInactiveServiceReturnsBadRequest() throws Exception {
        Service inactiveService = serviceRepository.save(new Service("Inactive", 60, false));

        CreateAvailabilityRequest request = new CreateAvailabilityRequest(
                professional.getId(), DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(16, 0), inactiveService.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/availability")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void createAvailabilityServiceNotOfferedByProfessionalReturnsBadRequest() throws Exception {
        User otherProUser = userRepository.save(new User(unique("other") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional otherPro = new Professional("Other", "Pro", otherProUser);
        otherPro.setServices(new HashSet<>());
        otherPro = professionalRepository.save(otherPro);

        CreateAvailabilityRequest request = new CreateAvailabilityRequest(
                otherPro.getId(), DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(16, 0), emsellaService.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/admin/availability")
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    // ---- CRUD: Update ----

    @Test
    void updateAvailabilityReturns200() throws Exception {
        Availability availability = availabilityRepository.save(
                new Availability(professional, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(16, 0)));

        UpdateAvailabilityRequest request = new UpdateAvailabilityRequest(
                DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(13, 0), emsellaService.getId());

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/availability/" + availability.getId())
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.dayOfWeek").value("TUESDAY"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.startTime").value("09:00:00"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.endTime").value("13:00:00"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.serviceId").value(emsellaService.getId()));
    }

    @Test
    void updateAvailabilityClearServiceReturnsGeneral() throws Exception {
        Availability availability = availabilityRepository.save(
                new Availability(professional, DayOfWeek.MONDAY, LocalTime.of(16, 0), LocalTime.of(19, 30), emsellaService));

        UpdateAvailabilityRequest request = new UpdateAvailabilityRequest(
                DayOfWeek.MONDAY, LocalTime.of(16, 0), LocalTime.of(20, 0), null);

        mockMvc.perform(MockMvcRequestBuilders.put("/api/admin/availability/" + availability.getId())
                        .header("Authorization", "Bearer " + tokenFor(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.serviceId").isEmpty());
    }

    // ---- CRUD: Delete ----

    @Test
    void deleteAvailabilityReturns204() throws Exception {
        Availability availability = availabilityRepository.save(
                new Availability(professional, DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(16, 0)));

        mockMvc.perform(MockMvcRequestBuilders.delete("/api/admin/availability/" + availability.getId())
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isNoContent());

        assertThat(availabilityRepository.findById(availability.getId())).isEmpty();
    }

    @Test
    void deleteNonexistentAvailabilityReturns404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/api/admin/availability/999999")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    // ---- Slot resolution: specific > general ----

    @Test
    void specificAvailabilityTakesPriorityOverGeneral() {
        availabilityRepository.save(new Availability(professional, DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(20, 0)));
        availabilityRepository.save(new Availability(professional, DayOfWeek.WEDNESDAY,
                LocalTime.of(16, 0), LocalTime.of(19, 30), emsellaService));

        List<AvailableSlotResponse> generalSlots = appointmentService.findAvailableSlots(
                professional.getId(), generalService.getId(), TEST_DATE);
        List<AvailableSlotResponse> emsellaSlots = appointmentService.findAvailableSlots(
                professional.getId(), emsellaService.getId(), TEST_DATE);

        assertThat(generalSlots).hasSize(12);
        assertThat(generalSlots.get(0).startTime().toLocalTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(generalSlots.get(11).startTime().toLocalTime()).isEqualTo(LocalTime.of(19, 0));

        assertThat(emsellaSlots).hasSize(7);
        assertThat(emsellaSlots.get(0).startTime().toLocalTime()).isEqualTo(LocalTime.of(16, 0));
        assertThat(emsellaSlots.get(6).startTime().toLocalTime()).isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    void splitSpecificAvailabilitiesCombineCorrectly() {
        availabilityRepository.save(new Availability(professional, DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(20, 0)));
        availabilityRepository.save(new Availability(professional, DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0), generalService));
        availabilityRepository.save(new Availability(professional, DayOfWeek.WEDNESDAY,
                LocalTime.of(16, 0), LocalTime.of(19, 0), generalService));

        List<AvailableSlotResponse> slots = appointmentService.findAvailableSlots(
                professional.getId(), generalService.getId(), TEST_DATE);

        assertThat(slots).hasSize(6);
        assertThat(slots).extracting(s -> s.startTime().toLocalTime())
                .containsExactly(
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0),
                        LocalTime.of(11, 0),
                        LocalTime.of(16, 0),
                        LocalTime.of(17, 0),
                        LocalTime.of(18, 0));
    }

    @Test
    void fallbackToGeneralWhenNoSpecificAvailability() {
        availabilityRepository.save(new Availability(professional, DayOfWeek.WEDNESDAY,
                LocalTime.of(8, 0), LocalTime.of(12, 0)));

        List<AvailableSlotResponse> slots = appointmentService.findAvailableSlots(
                professional.getId(), generalService.getId(), TEST_DATE);

        assertThat(slots).hasSize(4);
        assertThat(slots.get(0).startTime().toLocalTime()).isEqualTo(LocalTime.of(8, 0));
    }
}
