package com.palavecino.backend.appointment;

import com.palavecino.backend.appointment.dto.CreateAppointmentRequest;
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
class AppointmentAuthorizationIntegrationTest {

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
                LocalDateTime.of(bookingDate, LocalTime.of(9, 0)), AppointmentStatus.BOOKED));
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

    // ---- IDOR: appointment ownership ----

    @Test
    void patientCannotReadAnotherPatientsAppointment() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/" + appointmentB.getId())
                        .header("Authorization", "Bearer " + tokenFor(patientAUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void patientCanReadOwnAppointment() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/" + appointmentA.getId())
                        .header("Authorization", "Bearer " + tokenFor(patientAUser)))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void professionalCannotReadAnotherAppointmentThatIsNotTheirs() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/" + appointmentB.getId())
                        .header("Authorization", "Bearer " + tokenFor(professionalAUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    // ---- IDOR: professional agenda by id ----

    @Test
    void professionalCannotReadAnotherProfessionalsAgenda() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments")
                        .param("professionalId", professionalB.getId().toString())
                        .param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + tokenFor(professionalAUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void professionalCanReadOwnAgendaByProfessionalId() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments")
                        .param("professionalId", professionalA.getId().toString())
                        .param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + tokenFor(professionalAUser)))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    @Test
    void adminCanReadAnyProfessionalsAgenda() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments")
                        .param("professionalId", professionalB.getId().toString())
                        .param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    // ---- booking cannot specify another patient ----

    @Test
    void bookingIsAlwaysCreatedForTheAuthenticatedPatientRegardless() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(10, 0));
        CreateAppointmentRequest body = new CreateAppointmentRequest(professionalA.getId(), service.getId(), dateTime);

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + tokenFor(patientAUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(responseJson).get("id").asLong();
        Appointment persisted = appointmentRepository.findById(id).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(persisted.getPatient().getId()).isEqualTo(patientA.getId());
    }

    // ---- full clinic agenda: admin only ----

    @Test
    void adminCanReadFullClinicAgenda() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments")
                        .param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(2));
    }

    @Test
    void patientCannotReadFullClinicAgenda() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments")
                        .param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + tokenFor(patientAUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void professionalCannotReadFullClinicAgenda() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments")
                        .param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + tokenFor(professionalAUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    // ---- authentication / authorization response shape ----

    @Test
    void unauthenticatedRequestToProtectedEndpointReturns401Json() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/my"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").exists());
    }

    @Test
    void authenticatedWrongRoleReturns403Json() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/my-agenda")
                        .param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + tokenFor(patientAUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden())
                .andExpect(MockMvcResultMatchers.content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").exists());
    }

    @Test
    void availableSlotsWorksWithNoToken() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/available-slots")
                        .param("professionalId", professionalA.getId().toString())
                        .param("serviceId", service.getId().toString())
                        .param("date", bookingDate.toString()))
                .andExpect(MockMvcResultMatchers.status().isOk());
    }

    // ---- /my and /my-agenda ----

    @Test
    void myReturnsOnlyAuthenticatedPatientsAppointments() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/my")
                        .header("Authorization", "Bearer " + tokenFor(patientAUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].patientFirstName").value("Ana"));
    }

    @Test
    void myAgendaReturnsOnlyAuthenticatedProfessionalsAppointments() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/my-agenda")
                        .param("date", bookingDate.toString())
                        .header("Authorization", "Bearer " + tokenFor(professionalAUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].professionalFirstName").value("Marcela"));
    }
}
