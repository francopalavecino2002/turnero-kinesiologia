package com.palavecino.backend.appointment;

import com.palavecino.backend.availability.Availability;
import com.palavecino.backend.availability.AvailabilityRepository;
import com.palavecino.backend.availability.DayOfWeek;
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
class ClinicAgendaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private tools.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private AvailabilityRepository availabilityRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private JwtService jwtService;

    private Service service;

    private User patientAUser;
    private Patient patientA;
    private User patientBUser;
    private Patient patientB;

    private User professionalAUser;
    private Professional professionalA;
    private User professionalBUser;
    private Professional professionalB;

    private User adminUser;

    private Appointment appointmentA;
    private Appointment appointmentB;

    private LocalDate bookingDate;

    @BeforeEach
    void setUp() {
        service = serviceRepository.save(new Service("General", 60, true));

        patientAUser = userRepository.save(new User(unique("patientA") + "@example.com", "hash", Role.PATIENT, true));
        patientA = patientRepository.save(new Patient("Ana", "Perez", "111111", patientAUser));

        patientBUser = userRepository.save(new User(unique("patientB") + "@example.com", "hash", Role.PATIENT, true));
        patientB = patientRepository.save(new Patient("Beto", "Gomez", "222222", patientBUser));

        professionalAUser = userRepository.save(new User(unique("proA") + "@example.com", "hash", Role.PROFESSIONAL, true));
        professionalA = new Professional("Marcela", "Altamirano", professionalAUser);
        professionalA.setServices(new HashSet<>(java.util.List.of(service)));
        professionalA = professionalRepository.save(professionalA);

        professionalBUser = userRepository.save(new User(unique("proB") + "@example.com", "hash", Role.PROFESSIONAL, true));
        professionalB = new Professional("Franco", "Lastra", professionalBUser);
        professionalB.setServices(new HashSet<>(java.util.List.of(service)));
        professionalB = professionalRepository.save(professionalB);

        adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));

        bookingDate = nextDateForDayOfWeek(java.time.DayOfWeek.MONDAY, 3);
        availabilityRepository.save(new Availability(professionalA, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0)));
        availabilityRepository.save(new Availability(professionalB, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0)));

        appointmentA = appointmentRepository.save(new Appointment(patientA, professionalA, service,
                LocalDateTime.of(bookingDate, LocalTime.of(9, 0)), AppointmentStatus.BOOKED));
        appointmentB = appointmentRepository.save(new Appointment(patientB, professionalB, service,
                LocalDateTime.of(bookingDate, LocalTime.of(10, 0)), AppointmentStatus.CONFIRMED));
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

    // ---- access control ----

    @Test
    void patientGets403OnClinicAgenda() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/agenda")
                        .param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + tokenFor(patientAUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void unauthenticatedGets401OnClinicAgenda() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/agenda")
                        .param("date", bookingDate.toString()))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    // ---- professional sees all appointments but with privacy enforcement ----

    @Test
    void professionalSeesFullDetailForOwnAppointments() throws Exception {
        String responseJson = mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/agenda")
                        .param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + tokenFor(professionalAUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode root = objectMapper.readTree(responseJson);

        tools.jackson.databind.JsonNode ownEntry = null;
        for (tools.jackson.databind.JsonNode entry : root) {
            if (entry.get("professionalId").asLong() == professionalA.getId()) {
                ownEntry = entry;
                break;
            }
        }
        org.assertj.core.api.Assertions.assertThat(ownEntry).isNotNull();

        org.assertj.core.api.Assertions.assertThat(ownEntry.get("id").asLong())
                .isEqualTo(appointmentA.getId());
        org.assertj.core.api.Assertions.assertThat(ownEntry.get("patientFirstName").asText())
                .isEqualTo("Ana");
        org.assertj.core.api.Assertions.assertThat(ownEntry.get("patientLastName").asText())
                .isEqualTo("Perez");
        org.assertj.core.api.Assertions.assertThat(ownEntry.get("ownedByCurrentUser").asBoolean())
                .isTrue();
        org.assertj.core.api.Assertions.assertThat(ownEntry.get("status").asText())
                .isEqualTo("BOOKED");
        org.assertj.core.api.Assertions.assertThat(ownEntry.get("serviceName").asText())
                .isEqualTo("General");
        org.assertj.core.api.Assertions.assertThat(ownEntry.get("professionalFirstName").asText())
                .isEqualTo("Marcela");
        org.assertj.core.api.Assertions.assertThat(ownEntry.get("endTime")).isNotNull();
    }

    @Test
    void professionalSeesReducedDetailForOtherProfessionalsAppointments() throws Exception {
        String responseJson = mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/agenda")
                        .param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + tokenFor(professionalAUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode root = objectMapper.readTree(responseJson);

        tools.jackson.databind.JsonNode otherEntry = null;
        for (tools.jackson.databind.JsonNode entry : root) {
            if (entry.get("professionalId").asLong() == professionalB.getId()) {
                otherEntry = entry;
                break;
            }
        }
        org.assertj.core.api.Assertions.assertThat(otherEntry).isNotNull();

        org.assertj.core.api.Assertions.assertThat(otherEntry.get("id").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(otherEntry.get("patientFirstName").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(otherEntry.get("patientLastName").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(otherEntry.get("ownedByCurrentUser").asBoolean())
                .isFalse();

        org.assertj.core.api.Assertions.assertThat(otherEntry.get("professionalFirstName").asText())
                .isEqualTo("Franco");
        org.assertj.core.api.Assertions.assertThat(otherEntry.get("serviceName").asText())
                .isEqualTo("General");
        org.assertj.core.api.Assertions.assertThat(otherEntry.get("status").asText())
                .isEqualTo("CONFIRMED");
        org.assertj.core.api.Assertions.assertThat(otherEntry.get("endTime")).isNotNull();
    }

    @Test
    void otherProfessionalsAppointmentContainsNoPatientIdentifiers() throws Exception {
        String responseJson = mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/agenda")
                        .param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + tokenFor(professionalAUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode root = objectMapper.readTree(responseJson);

        for (tools.jackson.databind.JsonNode entry : root) {
            if (entry.get("professionalId").asLong() == professionalB.getId()) {
                org.assertj.core.api.Assertions.assertThat(entry.get("patientFirstName").isNull()).isTrue();
                org.assertj.core.api.Assertions.assertThat(entry.get("patientLastName").isNull()).isTrue();
                org.assertj.core.api.Assertions.assertThat(entry.has("patientPhone")).isFalse();
                org.assertj.core.api.Assertions.assertThat(entry.has("patientEmail")).isFalse();
                org.assertj.core.api.Assertions.assertThat(entry.has("patientId")).isFalse();
                org.assertj.core.api.Assertions.assertThat(entry.get("id").isNull()).isTrue();
            }
        }
    }

    // ---- admin sees full detail for everything ----

    @Test
    void adminSeesFullDetailForAllAppointments() throws Exception {
        String responseJson = mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/agenda")
                        .param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2))
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode root = objectMapper.readTree(responseJson);

        for (tools.jackson.databind.JsonNode entry : root) {
            org.assertj.core.api.Assertions.assertThat(entry.get("id")).isNotNull();
            org.assertj.core.api.Assertions.assertThat(entry.get("id").isNull()).isFalse();
            org.assertj.core.api.Assertions.assertThat(entry.get("patientFirstName")).isNotNull();
            org.assertj.core.api.Assertions.assertThat(entry.get("patientFirstName").asText()).isNotBlank();
            org.assertj.core.api.Assertions.assertThat(entry.get("patientLastName")).isNotNull();
            org.assertj.core.api.Assertions.assertThat(entry.get("patientLastName").asText()).isNotBlank();
            org.assertj.core.api.Assertions.assertThat(entry.get("ownedByCurrentUser").asBoolean())
                    .isTrue();
        }
    }

    // ---- professional cannot modify another professional's appointment ----

    @Test
    void professionalCannotConfirmAnotherProfessionalsAppointment() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointmentB.getId() + "/confirm")
                        .header("Authorization", "Bearer " + tokenFor(professionalAUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void professionalCannotCancelAnotherProfessionalsAppointment() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointmentB.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenFor(professionalAUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void professionalCannotCompleteAnotherProfessionalsAppointment() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointmentB.getId() + "/complete")
                        .header("Authorization", "Bearer " + tokenFor(professionalAUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void professionalCannotMarkAnotherProfessionalsAppointmentNoShow() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointmentB.getId() + "/no-show")
                        .header("Authorization", "Bearer " + tokenFor(professionalAUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }
}
