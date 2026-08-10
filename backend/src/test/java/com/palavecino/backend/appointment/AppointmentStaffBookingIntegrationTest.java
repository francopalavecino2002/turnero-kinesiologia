package com.palavecino.backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
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
 * POST /api/appointments/staff-book: PROFESSIONAL/ADMIN booking on behalf of a patient who called
 * or walked in. Must reuse the exact same availability/capacity/service-offered validation as the
 * online patient flow (bookAppointmentInternal), enforce the own-agenda restriction for
 * PROFESSIONAL callers, and dedupe guest patients by phone.
 */
@SpringBootTest(properties = "app.mail.async=false")
@AutoConfigureMockMvc
@Testcontainers
@Transactional
@Import(FakeEmailConfig.class)
class AppointmentStaffBookingIntegrationTest {

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

    private User registeredPatientUser;
    private Patient registeredPatient;
    private Professional professional;
    private Service generalService;
    private Service emsellaServiceNotOffered;
    private LocalDate bookingDate;

    @BeforeEach
    void setUp() {
        emailSender.clear();

        generalService = serviceRepository.save(new Service("General", 60, true));
        emsellaServiceNotOffered = serviceRepository.save(new Service("EMSELLA", 30, true));

        registeredPatientUser = userRepository.save(new User(unique("patient") + "@example.com", "hash", Role.PATIENT, true));
        registeredPatient = patientRepository.save(new Patient("Maria", "Lopez", "111111", registeredPatientUser));

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

    private String professionalToken() {
        return jwtService.generateToken(professional.getUser());
    }

    private String jsonWithPatientId(Long professionalId, Long serviceId, LocalDateTime dateTime, Long patientId) {
        return """
                {"professionalId": %d, "serviceId": %d, "dateTime": "%s", "patientId": %d}
                """.formatted(professionalId, serviceId, dateTime, patientId);
    }

    private String jsonWithGuest(Long professionalId, Long serviceId, LocalDateTime dateTime,
                                  String guestName, String guestPhone, String guestEmail) {
        String emailField = guestEmail == null ? "null" : "\"" + guestEmail + "\"";
        return """
                {"professionalId": %d, "serviceId": %d, "dateTime": "%s",
                 "guestPatient": {"name": "%s", "phone": "%s", "email": %s}}
                """.formatted(professionalId, serviceId, dateTime, guestName, guestPhone, emailField);
    }

    @Test
    void staffBooksForExistingRegisteredPatient() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = jsonWithPatientId(professional.getId(), generalService.getId(), dateTime, registeredPatient.getId());

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + professionalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("BOOKED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.patientFirstName").value("Maria"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(responseJson).get("id").asLong();
        Appointment persisted = appointmentRepository.findById(id).orElseThrow();
        assertThat(persisted.getPatient().getId()).isEqualTo(registeredPatient.getId());
    }

    @Test
    void staffBookingCreatesNewGuestPatientWhenNoPhoneMatch() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = jsonWithGuest(professional.getId(), generalService.getId(), dateTime,
                "Jorge Diaz", "999888777", "jorge@example.com");

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + professionalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.patientFirstName").value("Jorge"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(responseJson).get("id").asLong();
        Appointment persisted = appointmentRepository.findById(id).orElseThrow();
        Patient guest = persisted.getPatient();
        assertThat(guest.isGuest()).isTrue();
        assertThat(guest.getGuestPhone()).isEqualTo("999888777");
        assertThat(guest.getEmail()).isEqualTo("jorge@example.com");
    }

    @Test
    void staffBookingReusesExistingGuestByPhoneInsteadOfDuplicating() throws Exception {
        Patient existingGuest = patientRepository.save(Patient.guest("Jorge Diaz", "999888777", "jorge@example.com"));

        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = jsonWithGuest(professional.getId(), generalService.getId(), dateTime,
                "Jorge Diaz", "999888777", "jorge@example.com");

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + professionalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(responseJson).get("id").asLong();
        Appointment persisted = appointmentRepository.findById(id).orElseThrow();
        assertThat(persisted.getPatient().getId()).isEqualTo(existingGuest.getId());
        assertThat(patientRepository.count()).isEqualTo(2); // registeredPatient (setUp) + existingGuest, no new row
    }

    @Test
    void professionalCannotBookOnAnotherProfessionalsAgenda() throws Exception {
        User otherProUser = userRepository.save(new User(unique("pro2") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional otherProfessional = new Professional("Luis", "Diaz", otherProUser);
        otherProfessional.setServices(new HashSet<>(List.of(generalService)));
        otherProfessional = professionalRepository.save(otherProfessional);
        availabilityRepository.save(new Availability(otherProfessional, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0)));

        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = jsonWithPatientId(otherProfessional.getId(), generalService.getId(), dateTime, registeredPatient.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + professionalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void adminCanBookForAnyProfessional() throws Exception {
        User adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = jsonWithPatientId(professional.getId(), generalService.getId(), dateTime, registeredPatient.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + jwtService.generateToken(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated());
    }

    @Test
    void returns400WhenBothPatientIdAndGuestPatientProvided() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = """
                {"professionalId": %d, "serviceId": %d, "dateTime": "%s", "patientId": %d,
                 "guestPatient": {"name": "Jorge Diaz", "phone": "999888777"}}
                """.formatted(professional.getId(), generalService.getId(), dateTime, registeredPatient.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + professionalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void returns400WhenNeitherPatientIdNorGuestPatientProvided() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = """
                {"professionalId": %d, "serviceId": %d, "dateTime": "%s"}
                """.formatted(professional.getId(), generalService.getId(), dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + professionalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void returns400WhenProfessionalDoesNotOfferService() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = jsonWithPatientId(professional.getId(), emsellaServiceNotOffered.getId(), dateTime, registeredPatient.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + professionalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void returns400WhenRequestedTimeIsOutsideAvailability() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(14, 0));
        String body = jsonWithPatientId(professional.getId(), generalService.getId(), dateTime, registeredPatient.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + professionalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void returns409WhenProfessionalHasOverlappingActiveAppointment() throws Exception {
        LocalDateTime existingStart = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        appointmentRepository.save(new Appointment(registeredPatient, professional, generalService, existingStart,
                AppointmentStatus.BOOKED, generalService.getDurationMinutes()));

        LocalDateTime overlappingStart = LocalDateTime.of(bookingDate, LocalTime.of(9, 30));
        String body = jsonWithPatientId(professional.getId(), generalService.getId(), overlappingStart, registeredPatient.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + professionalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Test
    void returns404WhenPatientIdDoesNotExist() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = jsonWithPatientId(professional.getId(), generalService.getId(), dateTime, 999_999L);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + professionalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void professionalBookingOnOwnAgendaDoesNotSelfNotify() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = jsonWithPatientId(professional.getId(), generalService.getId(), dateTime, registeredPatient.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + professionalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        // Only the patient's confirmation email goes out; the professional booked their own
        // agenda so they are not notified of it.
        assertThat(emailSender.count()).isEqualTo(1);
        assertThat(emailSender.all().get(0).to()).isEqualTo(registeredPatientUser.getEmail());
    }

    @Test
    void adminBookingForAnotherProfessionalNotifiesThatProfessional() throws Exception {
        User adminUser = userRepository.save(new User(unique("admin") + "@example.com", "hash", Role.ADMIN, true));
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = jsonWithPatientId(professional.getId(), generalService.getId(), dateTime, registeredPatient.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + jwtService.generateToken(adminUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        assertThat(emailSender.count()).isEqualTo(2);
        FakeEmailSender.CapturedEmail toPatient = emailSender.all().get(0);
        assertThat(toPatient.to()).isEqualTo(registeredPatientUser.getEmail());

        FakeEmailSender.CapturedEmail toProfessional = emailSender.all().get(1);
        assertThat(toProfessional.to()).isEqualTo(professional.getUser().getEmail());
        assertThat(toProfessional.subject()).contains("Te reservaron un turno");
    }

    @Test
    void guestPatientWithEmailReceivesConfirmation() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = jsonWithGuest(professional.getId(), generalService.getId(), dateTime,
                "Jorge Diaz", "999888777", "jorge@example.com");

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + professionalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        // Professional booked their own agenda (no self-notify), so only the guest's email goes out.
        assertThat(emailSender.count()).isEqualTo(1);
        assertThat(emailSender.all().get(0).to()).isEqualTo("jorge@example.com");
    }

    @Test
    void guestPatientWithoutEmailDoesNotErrorAndSendsNoPatientEmail() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = jsonWithGuest(professional.getId(), generalService.getId(), dateTime,
                "Jorge Diaz", "999888777", null);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + professionalToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isCreated());

        assertThat(emailSender.count()).isEqualTo(0);
    }

    @Test
    void returns401WhenNoTokenProvided() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = jsonWithPatientId(professional.getId(), generalService.getId(), dateTime, registeredPatient.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void returns403WhenCallerIsAPatient() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        String body = jsonWithPatientId(professional.getId(), generalService.getId(), dateTime, registeredPatient.getId());

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments/staff-book")
                        .header("Authorization", "Bearer " + jwtService.generateToken(registeredPatientUser))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }
}
