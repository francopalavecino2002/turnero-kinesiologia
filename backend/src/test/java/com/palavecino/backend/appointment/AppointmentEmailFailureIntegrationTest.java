package com.palavecino.backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.palavecino.backend.availability.Availability;
import com.palavecino.backend.availability.AvailabilityRepository;
import com.palavecino.backend.availability.DayOfWeek;
import com.palavecino.backend.email.EmailSender;
import com.palavecino.backend.email.ThrowingEmailSender;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A broken mail provider must never fail a booking or a cancellation: EmailService swallows the
 * exception on the (synchronous, in-test) async thread, so the transaction is not rolled back and
 * the HTTP call still succeeds. The {@link ThrowingEmailSender} is registered as {@code @Primary},
 * replacing the real SMTP sender for the whole context.
 */
@SpringBootTest(properties = "app.mail.async=false")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Import(AppointmentEmailFailureIntegrationTest.ThrowingEmailConfig.class)
class AppointmentEmailFailureIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @TestConfiguration
    static class ThrowingEmailConfig {
        @Bean
        @Primary
        public EmailSender throwingEmailSender() {
            return new ThrowingEmailSender();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    private User patientUser;
    private Patient patient;
    private Professional professional;
    private Service generalService;
    private LocalDate bookingDate;

    @BeforeEach
    void setUp() {
        generalService = serviceRepository.save(new Service("General", 60, true));

        patientUser = userRepository.save(new User(unique("patient") + "@example.com", "hash", Role.PATIENT, true));
        patient = patientRepository.save(new Patient("Maria", "Lopez", "111111", patientUser));

        User professionalUser = userRepository.save(new User(unique("pro") + "@example.com", "hash", Role.PROFESSIONAL, true));
        professional = new Professional("Ana", "Gomez", professionalUser);
        professional.setServices(new HashSet<>(List.of(generalService)));
        professional = professionalRepository.save(professional);

        bookingDate = nextDateForDayOfWeek(java.time.DayOfWeek.MONDAY, 3);
        availabilityRepository.save(new Availability(professional, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0)));
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

    private String patientToken() {
        return jwtService.generateToken(patientUser);
    }

    @Test
    void bookingSucceedsEvenWhenEmailSenderFails() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = """
                {"professionalId": %d, "serviceId": %d, "dateTime": "%s"}
                """.formatted(professional.getId(), generalService.getId(), dateTime);

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + patientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(responseJson).get("id").asLong();
        Appointment persisted = appointmentRepository.findById(id).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
    }

    @Test
    void cancellationSucceedsEvenWhenEmailSenderFails() throws Exception {
        LocalDateTime dateTime = LocalDateTime.now().plusHours(48);
        Appointment appointment = appointmentRepository.save(new Appointment(patient, professional,
                generalService, dateTime, AppointmentStatus.BOOKED, generalService.getDurationMinutes()));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + patientToken()))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(appointmentRepository.findById(appointment.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.CANCELLED);
    }
}
