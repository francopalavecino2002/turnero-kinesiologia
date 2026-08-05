package com.palavecino.backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.palavecino.backend.email.FakeEmailConfig;
import com.palavecino.backend.email.FakeEmailSender;
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
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * An ADMIN with a linked professional profile must behave like a professional for its own
 * appointments: GET /my-agenda works, the clinic-agenda transitions (confirm/complete/no-show)
 * work, and cancelling its own appointment does not email itself. The /my-agenda endpoint is not
 * used by the current frontend (the clinic agenda covers it), but it must work over the API.
 */
@SpringBootTest(properties = "app.mail.async=false")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Import(FakeEmailConfig.class)
class AdminProfessionalAgendaIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private ProfessionalRepository professionalRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private FakeEmailSender emailSender;

    private Service service;
    private User adminUser;
    private Professional adminProfessional;
    private User patientUser;
    private Patient patient;

    @BeforeEach
    void setUp() {
        emailSender.clear();

        service = serviceRepository.save(new Service("General", 60, true));

        adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));
        adminProfessional = new Professional("Marcela", "Altamirano", adminUser);
        adminProfessional.setServices(new HashSet<>(List.of(service)));
        adminProfessional = professionalRepository.save(adminProfessional);

        patientUser = userRepository.save(new User(unique("patient") + "@example.com", "hash", Role.PATIENT, true));
        patient = patientRepository.save(new Patient("Maria", "Lopez", "111111", patientUser));
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    private String adminToken() {
        return jwtService.generateToken(adminUser);
    }

    private Appointment createAppointment(AppointmentStatus status, LocalDateTime dateTime) {
        return appointmentRepository.save(new Appointment(patient, adminProfessional, service,
                dateTime, status, service.getDurationMinutes()));
    }

    @Test
    void adminWithLinkedProfessionalCanAccessMyAgenda() throws Exception {
        LocalDateTime dateTime = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, dateTime);

        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/my-agenda")
                        .param("date", dateTime.toLocalDate().toString())
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(MockMvcResultMatchers.jsonPath("$[0].id").value(appointment.getId()));
    }

    @Test
    void adminWithoutLinkedProfessionalGets404OnMyAgenda() throws Exception {
        User plainAdmin = userRepository.save(new User(unique("plainadmin") + "@example.com", "hash", Role.ADMIN, true));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/appointments/my-agenda")
                        .param("date", "2026-01-05")
                        .header("Authorization", "Bearer " + jwtService.generateToken(plainAdmin)))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void adminWithLinkedProfessionalCanConfirmOwnAppointment() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusDays(1));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/confirm")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void adminWithLinkedProfessionalCanCompleteOwnAppointment() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusDays(1));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/complete")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void adminWithLinkedProfessionalCanMarkOwnAppointmentNoShow() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusDays(1));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/no-show")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void adminWithLinkedProfessionalCancellingOwnAppointmentDoesNotEmailItself() throws Exception {
        // Cancellation is not subject to the patient 24h rule when a professional/admin cancels,
        // so an appointment 1h ahead is fine here.
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusHours(1));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isOk());

        // Only the patient is notified: the ADMIN-professional cancelled their own appointment and
        // must not receive a self-cancellation email.
        assertThat(emailSender.count()).isEqualTo(1);
        FakeEmailSender.CapturedEmail mail = emailSender.all().get(0);
        assertThat(mail.to()).isEqualTo(patientUser.getEmail());
        assertThat(mail.subject()).contains("Turno cancelado");
        assertThat(emailSender.all().stream().noneMatch(m -> m.to().equals(adminUser.getEmail()))).isTrue();
    }

    @Test
    void adminWithLinkedProfessionalCanCancelAnotherProfessionalsAppointmentAndNotifiesThem() throws Exception {
        User otherProUser = userRepository.save(new User(unique("pro") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional otherProfessional = new Professional("Franco", "Lastra", otherProUser);
        otherProfessional.setServices(new HashSet<>(List.of(service)));
        otherProfessional = professionalRepository.save(otherProfessional);

        Appointment appointment = appointmentRepository.save(new Appointment(patient, otherProfessional, service,
                LocalDateTime.now().plusHours(1), AppointmentStatus.BOOKED, service.getDurationMinutes()));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + adminToken()))
                .andExpect(MockMvcResultMatchers.status().isOk());

        // Patient + the other professional are notified; the admin is not.
        assertThat(emailSender.count()).isEqualTo(2);
        assertThat(emailSender.all().stream()
                .map(FakeEmailSender.CapturedEmail::to))
                .containsExactlyInAnyOrder(patientUser.getEmail(), otherProUser.getEmail());
        assertThat(emailSender.all().stream().noneMatch(m -> m.to().equals(adminUser.getEmail()))).isTrue();
    }
}
