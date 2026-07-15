package com.palavecino.backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;

import tools.jackson.databind.ObjectMapper;
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
import java.util.Optional;
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
class AppointmentBookingIntegrationTest {

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

    private User patientUser;
    private Patient patient;
    private Professional professional;
    private Service generalService;
    private Service emsellaServiceNotOffered;
    private LocalDate bookingDate;

    @BeforeEach
    void setUp() {
        generalService = serviceRepository.save(new Service("General", 60, true));
        emsellaServiceNotOffered = serviceRepository.save(new Service("EMSELLA", 30, true));

        patientUser = userRepository.save(new User(unique("patient") + "@example.com", "hash", Role.PATIENT, true));
        patient = patientRepository.save(new Patient("Maria", "Lopez", "111111", patientUser));

        User professionalUser = userRepository.save(new User(unique("pro") + "@example.com", "hash", Role.PROFESSIONAL, true));
        professional = new Professional("Ana", "Gomez", professionalUser);
        professional.setServices(new HashSet<>(java.util.List.of(generalService)));
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

    private CreateAppointmentRequest request(Long professionalId, Long serviceId, LocalDateTime dateTime) {
        return new CreateAppointmentRequest(professionalId, serviceId, dateTime);
    }

    @Test
    void successfulBookingReturns201AndPersistsAsBooked() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        CreateAppointmentRequest body = request(professional.getId(), generalService.getId(), dateTime);

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + patientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.header().string("Location", matchesPattern("/api/appointments/\\d+")))
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("BOOKED"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.patientFirstName").value("Maria"))
                .andReturn().getResponse().getContentAsString();

        Long id = objectMapper.readTree(responseJson).get("id").asLong();
        Optional<Appointment> persisted = appointmentRepository.findById(id);
        assertThat(persisted).isPresent();
        assertThat(persisted.get().getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(persisted.get().getDateTime()).isEqualTo(dateTime);
        assertThat(persisted.get().getPatient().getId()).isEqualTo(patient.getId());
    }

    @Test
    void locationHeaderPointsToResourceThatCanBeFetched() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        CreateAppointmentRequest body = request(professional.getId(), generalService.getId(), dateTime);

        String location = mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + patientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(MockMvcRequestBuilders.get(location)
                        .header("Authorization", "Bearer " + patientToken()))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("BOOKED"));
    }

    @Test
    void returns404WhenProfessionalDoesNotExist() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        CreateAppointmentRequest body = request(999_999L, generalService.getId(), dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + patientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void returns404WhenServiceDoesNotExist() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        CreateAppointmentRequest body = request(professional.getId(), 999_999L, dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + patientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void returns400WhenProfessionalDoesNotOfferService() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        CreateAppointmentRequest body = request(professional.getId(), emsellaServiceNotOffered.getId(), dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + patientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void returns400WhenRequestedTimeIsOutsideAvailability() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(14, 0));
        CreateAppointmentRequest body = request(professional.getId(), generalService.getId(), dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + patientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void returns400WhenSlotDoesNotFitWithinAvailability() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(11, 30));
        CreateAppointmentRequest body = request(professional.getId(), generalService.getId(), dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + patientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    void returns409WhenProfessionalHasOverlappingActiveAppointment() throws Exception {
        LocalDateTime existingStart = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        appointmentRepository.save(new Appointment(patient, professional, generalService, existingStart,
                AppointmentStatus.BOOKED));

        LocalDateTime overlappingStart = LocalDateTime.of(bookingDate, LocalTime.of(9, 30));
        CreateAppointmentRequest body = request(professional.getId(), generalService.getId(), overlappingStart);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + patientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Test
    void returns409WhenGlobalCapacityIsFull() throws Exception {
        User user2 = userRepository.save(new User(unique("pro2") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional professional2 = new Professional("Luis", "Diaz", user2);
        professional2.setServices(new HashSet<>(java.util.List.of(generalService)));
        professional2 = professionalRepository.save(professional2);

        User user3 = userRepository.save(new User(unique("pro3") + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional professional3 = new Professional("Carla", "Ruiz", user3);
        professional3.setServices(new HashSet<>(java.util.List.of(generalService)));
        professional3 = professionalRepository.save(professional3);
        availabilityRepository.save(new Availability(professional3, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0)));

        LocalDateTime slotStart = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        appointmentRepository.save(new Appointment(patient, professional, generalService, slotStart,
                AppointmentStatus.BOOKED));
        appointmentRepository.save(new Appointment(patient, professional2, generalService, slotStart,
                AppointmentStatus.CONFIRMED));

        CreateAppointmentRequest body = request(professional3.getId(), generalService.getId(), slotStart);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + patientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isConflict());
    }

    @Test
    void cancelledAppointmentDoesNotBlockNewBookingAtSameTime() throws Exception {
        LocalDateTime slotStart = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        appointmentRepository.save(new Appointment(patient, professional, generalService, slotStart,
                AppointmentStatus.CANCELLED));

        CreateAppointmentRequest body = request(professional.getId(), generalService.getId(), slotStart);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + patientToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("BOOKED"));
    }

    @Test
    void returns401WhenNoTokenProvided() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        CreateAppointmentRequest body = request(professional.getId(), generalService.getId(), dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void returns403WhenCallerIsNotAPatient() throws Exception {
        String professionalToken = jwtService.generateToken(professional.getUser());
        LocalDateTime dateTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));
        CreateAppointmentRequest body = request(professional.getId(), generalService.getId(), dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .header("Authorization", "Bearer " + professionalToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }
}
