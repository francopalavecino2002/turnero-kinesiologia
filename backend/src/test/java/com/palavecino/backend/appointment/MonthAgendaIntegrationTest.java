package com.palavecino.backend.appointment;

import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.patient.PatientRepository;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
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

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class MonthAgendaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private tools.jackson.databind.ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;
    @Autowired private PatientRepository patientRepository;
    @Autowired private ProfessionalRepository professionalRepository;
    @Autowired private ServiceRepository serviceRepository;
    @Autowired private AppointmentRepository appointmentRepository;
    @Autowired private JwtService jwtService;

    private Service service;
    private User patientAUser;
    private Patient patientA;
    private User professionalAUser;
    private Professional professionalA;
    private User professionalBUser;
    private Professional professionalB;
    private User adminUser;
    private int year;
    private int month;
    private LocalDate firstDay;

    @BeforeEach
    void setUp() {
        service = serviceRepository.save(new Service("General", 60, true));

        patientAUser = userRepository.save(new User(unique("patientA") + "@example.com", "hash", Role.PATIENT, true));
        patientA = patientRepository.save(new Patient("Ana", "Perez", "111111", patientAUser));

        professionalAUser = userRepository.save(new User(unique("proA") + "@example.com", "hash", Role.PROFESSIONAL, true));
        professionalA = new Professional("Marcela", "Altamirano", professionalAUser);
        professionalA.setServices(new HashSet<>(java.util.List.of(service)));
        professionalA = professionalRepository.save(professionalA);

        professionalBUser = userRepository.save(new User(unique("proB") + "@example.com", "hash", Role.PROFESSIONAL, true));
        professionalB = new Professional("Franco", "Lastra", professionalBUser);
        professionalB.setServices(new HashSet<>(java.util.List.of(service)));
        professionalB = professionalRepository.save(professionalB);

        adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));

        LocalDate targetMonth = LocalDate.now().plusMonths(1);
        year = targetMonth.getYear();
        month = targetMonth.getMonthValue();
        firstDay = LocalDate.of(year, month, 1);

        // professionalA: active on day 5, cancelled on day 15, active on day 20
        appointmentRepository.save(new Appointment(patientA, professionalA, service,
                LocalDateTime.of(firstDay.withDayOfMonth(5), LocalTime.of(9, 0)),
                AppointmentStatus.BOOKED, service.getDurationMinutes()));
        appointmentRepository.save(new Appointment(patientA, professionalA, service,
                LocalDateTime.of(firstDay.withDayOfMonth(15), LocalTime.of(9, 0)),
                AppointmentStatus.CANCELLED, service.getDurationMinutes()));
        appointmentRepository.save(new Appointment(patientA, professionalA, service,
                LocalDateTime.of(firstDay.withDayOfMonth(20), LocalTime.of(9, 0)),
                AppointmentStatus.CONFIRMED, service.getDurationMinutes()));

        // professionalB: active on day 10
        appointmentRepository.save(new Appointment(patientA, professionalB, service,
                LocalDateTime.of(firstDay.withDayOfMonth(10), LocalTime.of(10, 0)),
                AppointmentStatus.BOOKED, service.getDurationMinutes()));
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    // ---- access control ----

    @Test
    void patientGets403() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/agenda/month")
                        .param("year", String.valueOf(year))
                        .param("month", String.valueOf(month))
                        .header("Authorization", "Bearer " + tokenFor(patientAUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void unauthenticatedGets401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/agenda/month")
                        .param("year", String.valueOf(year))
                        .param("month", String.valueOf(month)))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    // ---- professional scope ----

    @Test
    void professionalSeesOnlyOwnAppointmentDays() throws Exception {
        String json = mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/agenda/month")
                        .param("year", String.valueOf(year))
                        .param("month", String.valueOf(month))
                        .header("Authorization", "Bearer " + tokenFor(professionalAUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode days = objectMapper.readTree(json).get("days");

        org.assertj.core.api.Assertions.assertThat(days.size()).isEqualTo(2);

        java.util.List<Integer> dayNumbers = new java.util.ArrayList<>();
        for (tools.jackson.databind.JsonNode day : days) {
            dayNumbers.add(day.get("day").asInt());
        }
        org.assertj.core.api.Assertions.assertThat(dayNumbers).containsExactlyInAnyOrder(5, 20);
    }

    @Test
    void professionalDoesNotSeeOtherProfessionalsDays() throws Exception {
        String json = mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/agenda/month")
                        .param("year", String.valueOf(year))
                        .param("month", String.valueOf(month))
                        .header("Authorization", "Bearer " + tokenFor(professionalAUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode days = objectMapper.readTree(json).get("days");

        java.util.List<Integer> dayNumbers = new java.util.ArrayList<>();
        for (tools.jackson.databind.JsonNode day : days) {
            dayNumbers.add(day.get("day").asInt());
        }
        org.assertj.core.api.Assertions.assertThat(dayNumbers).doesNotContain(10);
    }

    // ---- admin scope ----

    @Test
    void adminSeesAllClinicAppointmentDays() throws Exception {
        String json = mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/agenda/month")
                        .param("year", String.valueOf(year))
                        .param("month", String.valueOf(month))
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode days = objectMapper.readTree(json).get("days");

        // days 5, 10, 20 active; day 15 cancelled → excluded
        org.assertj.core.api.Assertions.assertThat(days.size()).isEqualTo(3);

        java.util.List<Integer> dayNumbers = new java.util.ArrayList<>();
        for (tools.jackson.databind.JsonNode day : days) {
            dayNumbers.add(day.get("day").asInt());
        }
        org.assertj.core.api.Assertions.assertThat(dayNumbers).containsExactlyInAnyOrder(5, 10, 20);
    }

    // ---- cancelled-only day handling ----

    @Test
    void cancelledOnlyDayDoesNotAppear() throws Exception {
        String json = mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/agenda/month")
                        .param("year", String.valueOf(year))
                        .param("month", String.valueOf(month))
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode days = objectMapper.readTree(json).get("days");

        java.util.List<Integer> dayNumbers = new java.util.ArrayList<>();
        for (tools.jackson.databind.JsonNode day : days) {
            dayNumbers.add(day.get("day").asInt());
        }
        org.assertj.core.api.Assertions.assertThat(dayNumbers).doesNotContain(15);
    }

    // ---- empty month ----

    @Test
    void emptyMonthReturnsEmptyList() throws Exception {
        int emptyYear = LocalDate.now().plusYears(5).getYear();

        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/agenda/month")
                        .param("year", String.valueOf(emptyYear))
                        .param("month", "1")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.days").isArray())
                .andExpect(MockMvcResultMatchers.jsonPath("$.days").isEmpty());
    }
}
