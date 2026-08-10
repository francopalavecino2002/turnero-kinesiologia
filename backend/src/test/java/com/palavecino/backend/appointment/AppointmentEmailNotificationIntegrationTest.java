package com.palavecino.backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.palavecino.backend.availability.Availability;
import com.palavecino.backend.availability.AvailabilityRepository;
import com.palavecino.backend.availability.DayOfWeek;
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
 * Booking and cancellation must trigger a confirmation/cancellation email to the patient with the
 * right appointment data. {@code app.mail.async=false} swaps in a synchronous executor so the fake
 * sender is populated before the HTTP call returns (see AsyncConfig / FakeEmailConfig).
 */
@SpringBootTest(properties = "app.mail.async=false")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Import(FakeEmailConfig.class)
class AppointmentEmailNotificationIntegrationTest {

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
    private AvailabilityRepository availabilityRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private FakeEmailSender emailSender;

    private User patientUser;
    private Patient patient;
    private Professional professional;
    private Service generalService;
    private LocalDate bookingDate;

    @BeforeEach
    void setUp() {
        emailSender.clear();

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

    private Appointment createAppointment(AppointmentStatus status, LocalDateTime dateTime) {
        return appointmentRepository.save(new Appointment(patient, professional, generalService,
                dateTime, status, generalService.getDurationMinutes()));
    }

    @Test
    void bookingSendsConfirmationEmailsToPatientAndProfessional() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = """
                {"professionalId": %d, "serviceId": %d, "dateTime": "%s"}
                """.formatted(professional.getId(), generalService.getId(), dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + patientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        assertThat(emailSender.count()).isEqualTo(2);

        FakeEmailSender.CapturedEmail toPatient = emailSender.all().get(0);
        assertThat(toPatient.to()).isEqualTo(patientUser.getEmail());
        assertThat(toPatient.subject()).contains("Turno reservado");
        assertThat(toPatient.htmlBody())
                .contains("Ana Gomez")
                .contains("General")
                .contains("Amancay 450")
                .contains("60 minutos")
                .contains("09:00");

        FakeEmailSender.CapturedEmail toProfessional = emailSender.all().get(1);
        assertThat(toProfessional.to()).isEqualTo(professional.getUser().getEmail());
        assertThat(toProfessional.subject()).contains("Te reservaron un turno");
        assertThat(toProfessional.htmlBody())
                .contains("Maria Lopez")
                .contains("General")
                .contains("60 minutos")
                .contains("09:00");
    }

    @Test
    void bookingByPatientWithNotificationsDisabledStillNotifiesProfessional() throws Exception {
        User optedOutUser = userRepository.save(new User(unique("optout") + "@example.com", "hash", Role.PATIENT, true));
        patientRepository.save(new Patient("Rosa", "Mendez", "222222", optedOutUser, false));

        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = """
                {"professionalId": %d, "serviceId": %d, "dateTime": "%s"}
                """.formatted(professional.getId(), generalService.getId(), dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + jwtService.generateToken(optedOutUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        // The patient opted out, but the professional is always notified of new bookings.
        assertThat(emailSender.count()).isEqualTo(1);
        FakeEmailSender.CapturedEmail mail = emailSender.all().get(0);
        assertThat(mail.to()).isEqualTo(professional.getUser().getEmail());
        assertThat(mail.subject()).contains("Te reservaron un turno");
        assertThat(mail.htmlBody()).contains("Rosa Mendez");
    }

    @Test
    void bookingCancelledByPatientSendsCancellationEmailsToPatientAndProfessional() throws Exception {
        LocalDateTime dateTime = LocalDateTime.now().plusHours(48);
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + patientToken()))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(emailSender.count()).isEqualTo(2);

        FakeEmailSender.CapturedEmail toPatient = emailSender.all().get(0);
        assertThat(toPatient.to()).isEqualTo(patientUser.getEmail());
        assertThat(toPatient.subject()).contains("Turno cancelado");
        assertThat(toPatient.htmlBody())
                .contains("Ana Gomez")
                .contains("General")
                .contains("Amancay 450");

        FakeEmailSender.CapturedEmail toProfessional = emailSender.all().get(1);
        assertThat(toProfessional.to()).isEqualTo(professional.getUser().getEmail());
        assertThat(toProfessional.subject()).contains("Turno cancelado");
        assertThat(toProfessional.htmlBody())
                .contains("Maria Lopez")
                .contains("General");
    }

    @Test
    void bookingCancelledByProfessionalSendsCancellationEmailToPatientOnly() throws Exception {
        // Cancellation is role-agnostic: whoever cancels (patient, professional or admin), the
        // email goes to the patient. The professional is NOT notified when they cancelled the
        // appointment themselves from their own agenda (that would be informing them of an action
        // they just performed). The 24h notice rule only applies to patient-initiated
        // cancellations, so a professional can cancel with less notice.
        LocalDateTime dateTime = LocalDateTime.now().plusHours(1);
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + jwtService.generateToken(professional.getUser())))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(emailSender.count()).isEqualTo(1);
        FakeEmailSender.CapturedEmail mail = emailSender.all().get(0);
        assertThat(mail.to()).isEqualTo(patientUser.getEmail());
        assertThat(mail.subject()).contains("Turno cancelado");
        assertThat(mail.htmlBody())
                .contains("Ana Gomez")
                .contains("General")
                .contains("Amancay 450");
    }

    @Test
    void bookingCancelledByAdminNotifiesPatientAndProfessional() throws Exception {
        User adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));
        LocalDateTime dateTime = LocalDateTime.now().plusHours(1);
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + jwtService.generateToken(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(emailSender.count()).isEqualTo(2);

        FakeEmailSender.CapturedEmail toPatient = emailSender.all().get(0);
        assertThat(toPatient.to()).isEqualTo(patientUser.getEmail());
        assertThat(toPatient.subject()).contains("Turno cancelado");

        FakeEmailSender.CapturedEmail toProfessional = emailSender.all().get(1);
        assertThat(toProfessional.to()).isEqualTo(professional.getUser().getEmail());
        assertThat(toProfessional.subject()).contains("Turno cancelado");
        assertThat(toProfessional.htmlBody())
                .contains("Maria Lopez")
                .contains("General");
    }

    @Test
    void confirmingAppointmentSendsConfirmationEmailToPatient() throws Exception {
        LocalDateTime dateTime = LocalDateTime.now().plusHours(48);
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/confirm")
                        .header("Authorization", "Bearer " + jwtService.generateToken(professional.getUser())))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(emailSender.count()).isEqualTo(1);
        FakeEmailSender.CapturedEmail mail = emailSender.all().get(0);
        assertThat(mail.to()).isEqualTo(patientUser.getEmail());
        assertThat(mail.subject()).contains("Turno confirmado");
        assertThat(mail.htmlBody())
                .contains("Ana Gomez")
                .contains("General")
                .contains("Amancay 450");
    }

    @Test
    void confirmingAppointmentRespectsNotificationsDisabled() throws Exception {
        User optedOutUser = userRepository.save(new User(unique("optout") + "@example.com", "hash", Role.PATIENT, true));
        Patient optedOutPatient = patientRepository.save(new Patient("Rosa", "Mendez", "222222", optedOutUser, false));
        LocalDateTime dateTime = LocalDateTime.now().plusHours(48);
        Appointment appointment = appointmentRepository.save(new Appointment(optedOutPatient, professional, generalService,
                dateTime, AppointmentStatus.BOOKED, generalService.getDurationMinutes()));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/confirm")
                        .header("Authorization", "Bearer " + jwtService.generateToken(professional.getUser())))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(emailSender.count()).isEqualTo(0);
    }

    @Test
    void markingNoShowSendsEmailToPatient() throws Exception {
        LocalDateTime dateTime = LocalDateTime.now().plusHours(48);
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/no-show")
                        .header("Authorization", "Bearer " + jwtService.generateToken(professional.getUser())))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(emailSender.count()).isEqualTo(1);
        FakeEmailSender.CapturedEmail mail = emailSender.all().get(0);
        assertThat(mail.to()).isEqualTo(patientUser.getEmail());
        assertThat(mail.subject()).contains("Turno no asistido");
        assertThat(mail.htmlBody()).contains("Ana Gomez");
    }

    @Test
    void markingNoShowRespectsNotificationsDisabled() throws Exception {
        User optedOutUser = userRepository.save(new User(unique("optout") + "@example.com", "hash", Role.PATIENT, true));
        Patient optedOutPatient = patientRepository.save(new Patient("Rosa", "Mendez", "222222", optedOutUser, false));
        LocalDateTime dateTime = LocalDateTime.now().plusHours(48);
        Appointment appointment = appointmentRepository.save(new Appointment(optedOutPatient, professional, generalService,
                dateTime, AppointmentStatus.BOOKED, generalService.getDurationMinutes()));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/no-show")
                        .header("Authorization", "Bearer " + jwtService.generateToken(professional.getUser())))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(emailSender.count()).isEqualTo(0);
    }

    @Test
    void completingAppointmentByAdminNotifiesProfessional() throws Exception {
        User adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));
        LocalDateTime dateTime = LocalDateTime.now().plusHours(48);
        Appointment appointment = createAppointment(AppointmentStatus.CONFIRMED, dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/complete")
                        .header("Authorization", "Bearer " + jwtService.generateToken(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(emailSender.count()).isEqualTo(1);
        FakeEmailSender.CapturedEmail mail = emailSender.all().get(0);
        assertThat(mail.to()).isEqualTo(professional.getUser().getEmail());
        assertThat(mail.subject()).contains("Turno completado");
        assertThat(mail.htmlBody()).contains("Maria Lopez");
    }

    @Test
    void completingAppointmentByTheProfessionalItselfDoesNotSelfNotify() throws Exception {
        LocalDateTime dateTime = LocalDateTime.now().plusHours(48);
        Appointment appointment = createAppointment(AppointmentStatus.CONFIRMED, dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/complete")
                        .header("Authorization", "Bearer " + jwtService.generateToken(professional.getUser())))
                .andExpect(MockMvcResultMatchers.status().isOk());

        assertThat(emailSender.count()).isEqualTo(0);
    }
}
