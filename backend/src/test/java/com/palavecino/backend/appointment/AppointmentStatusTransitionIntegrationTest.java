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
import java.time.LocalDateTime;
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
class AppointmentStatusTransitionIntegrationTest {

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

    private Service service;
    private User patientUser;
    private Patient patient;
    private User professionalUser;
    private Professional professional;
    private User adminUser;
    private User otherProfessionalUser;

    @BeforeEach
    void setUp() {
        service = serviceRepository.save(new Service("General", 60, true));

        patientUser = userRepository.save(new User(unique("patient") + "@example.com", "hash", Role.PATIENT, true));
        patient = patientRepository.save(new Patient("Ana", "Perez", "111111", patientUser));

        professionalUser = userRepository.save(new User(unique("pro") + "@example.com", "hash", Role.PROFESSIONAL, true));
        professional = new Professional("Marcela", "Altamirano", professionalUser);
        professional.setServices(new HashSet<>(java.util.List.of(service)));
        professional = professionalRepository.save(professional);

        User otherProUser = userRepository.save(new User(unique("other-pro") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional other = new Professional("Franco", "Lastra", otherProUser);
        professionalRepository.save(other);
        otherProfessionalUser = otherProUser;

        adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));
    }

    private static String unique(String prefix) {
        return prefix + System.nanoTime();
    }

    private String tokenFor(User user) {
        return jwtService.generateToken(user);
    }

    private Appointment createAppointment(AppointmentStatus status, LocalDateTime dateTime) {
        return appointmentRepository.save(new Appointment(patient, professional, service, dateTime, status));
    }

    // ---- valid transitions ----

    @Test
    void confirmBookedAppointmentSucceeds() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusDays(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/confirm")
                        .header("Authorization", "Bearer " + tokenFor(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void completeBookedAppointmentSucceeds() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusDays(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/complete")
                        .header("Authorization", "Bearer " + tokenFor(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void noShowBookedAppointmentSucceeds() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusDays(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/no-show")
                        .header("Authorization", "Bearer " + tokenFor(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void confirmedAppointmentCanBeCompletedByAdmin() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.CONFIRMED, LocalDateTime.now().plusDays(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/complete")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void confirmedAppointmentCanBeCancelled() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.CONFIRMED, LocalDateTime.now().plusDays(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenFor(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void confirmedAppointmentCanBeMarkedNoShow() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.CONFIRMED, LocalDateTime.now().plusDays(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/no-show")
                        .header("Authorization", "Bearer " + tokenFor(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("NO_SHOW"));
    }

    // ---- invalid transitions ----

    @Test
    void cancelledAppointmentCannotBeConfirmed() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.CANCELLED, LocalDateTime.now().plusDays(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/confirm")
                        .header("Authorization", "Bearer " + tokenFor(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Test
    void completedAppointmentCannotBeCancelled() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.COMPLETED, LocalDateTime.now().plusDays(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenFor(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Test
    void noShowAppointmentCannotBeCompleted() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.NO_SHOW, LocalDateTime.now().plusDays(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/complete")
                        .header("Authorization", "Bearer " + tokenFor(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isConflict());
    }

    // ---- cancellation notice period ----

    @Test
    void patientCancellingLessThan24HoursBeforeIsRejected() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusHours(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenFor(patientUser)))
                .andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Test
    void patientCancellingMoreThan24HoursBeforeSucceeds() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusHours(48));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenFor(patientUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void professionalCancelling1HourBeforeSucceeds() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusHours(1));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenFor(professionalUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void adminCancellingLessThan24HoursBeforeSucceeds() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusHours(1));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenFor(adminUser)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("CANCELLED"));
    }

    // ---- role restrictions on transitions ----

    @Test
    void patientCannotConfirmAppointment() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusDays(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/confirm")
                        .header("Authorization", "Bearer " + tokenFor(patientUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void patientCannotCompleteAppointment() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusDays(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/complete")
                        .header("Authorization", "Bearer " + tokenFor(patientUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void patientCannotMarkAppointmentNoShow() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusDays(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/no-show")
                        .header("Authorization", "Bearer " + tokenFor(patientUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void professionalCannotConfirmAnotherProfessionalsAppointment() throws Exception {
        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusDays(2));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/confirm")
                        .header("Authorization", "Bearer " + tokenFor(otherProfessionalUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void patientCannotCancelAnotherPatientsAppointment() throws Exception {
        User otherPatientUser = userRepository.save(new User(unique("other-pat") + "@example.com", "hash", Role.PATIENT, true));
        patientRepository.save(new Patient("Otro", "Paciente", "333333", otherPatientUser));

        Appointment appointment = createAppointment(AppointmentStatus.BOOKED, LocalDateTime.now().plusHours(48));

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/" + appointment.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenFor(otherPatientUser)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }
}
