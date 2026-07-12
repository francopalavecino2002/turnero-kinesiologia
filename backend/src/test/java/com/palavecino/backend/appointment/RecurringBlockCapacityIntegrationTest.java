package com.palavecino.backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.ObjectMapper;
import com.palavecino.backend.appointment.dto.AvailableSlotResponse;
import com.palavecino.backend.appointment.dto.CreateAppointmentRequest;
import com.palavecino.backend.availability.Availability;
import com.palavecino.backend.availability.AvailabilityRepository;
import com.palavecino.backend.availability.DayOfWeek;
import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.patient.PatientRepository;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.recurringblock.RecurringBlock;
import com.palavecino.backend.recurringblock.RecurringBlockRepository;
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
class RecurringBlockCapacityIntegrationTest {

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
    private RecurringBlockRepository recurringBlockRepository;

    private Patient patient;
    private Professional professional1;
    private Professional professional2;
    private Service generalService;
    private Service emsellaService;
    private LocalDate mondayDate;
    private LocalDate saturdayDate;

    @BeforeEach
    void setUp() {
        generalService = serviceRepository.save(new Service("Deporte y Traumatología", 60, true));
        emsellaService = serviceRepository.save(new Service("EMSELLA", 30, true));

        User patientUser = userRepository.save(new User(unique("patient") + "@example.com", "hash", Role.PATIENT, true));
        patient = patientRepository.save(new Patient("Maria", "Lopez", "111111", patientUser));

        User user1 = userRepository.save(new User(unique("pro1") + "@example.com", "hash", Role.PROFESSIONAL, true));
        professional1 = new Professional("Marcela", "Altamirano", user1);
        professional1.setServices(new HashSet<>(List.of(generalService, emsellaService)));
        professional1 = professionalRepository.save(professional1);

        User user2 = userRepository.save(new User(unique("pro2") + "@example.com", "hash", Role.PROFESSIONAL, true));
        professional2 = new Professional("Franco", "Lastra", user2);
        professional2.setServices(new HashSet<>(List.of(generalService, emsellaService)));
        professional2 = professionalRepository.save(professional2);

        mondayDate = nextDateForDayOfWeek(java.time.DayOfWeek.MONDAY, 3);
        saturdayDate = nextDateForDayOfWeek(java.time.DayOfWeek.SATURDAY, 3);

        availabilityRepository.save(new Availability(professional1, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(20, 0)));
        availabilityRepository.save(new Availability(professional1, DayOfWeek.SATURDAY,
                LocalTime.of(9, 0), LocalTime.of(13, 0)));
        availabilityRepository.save(new Availability(professional2, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(20, 0)));

        recurringBlockRepository.save(new RecurringBlock(DayOfWeek.MONDAY,
                LocalTime.of(16, 0), LocalTime.of(19, 30), emsellaService, null, true,
                "EMSELLA - box reservado"));
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

    private CreateAppointmentRequest request(Long patientId, Long professionalId, Long serviceId, LocalDateTime dateTime) {
        return new CreateAppointmentRequest(patientId, professionalId, serviceId, dateTime);
    }

    private void book(CreateAppointmentRequest body) throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isCreated());
    }

    @Test
    void generalAppointmentAllowedWhenOnlyEmsellaBlockReducesCapacity() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(mondayDate, LocalTime.of(17, 0));
        CreateAppointmentRequest body = request(patient.getId(), professional1.getId(), generalService.getId(), dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("BOOKED"));
    }

    @Test
    void secondOverlappingGeneralAppointmentRejectedWhenEffectiveCapacityExhausted() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(mondayDate, LocalTime.of(17, 0));
        book(request(patient.getId(), professional1.getId(), generalService.getId(), dateTime));

        CreateAppointmentRequest secondBody = request(patient.getId(), professional2.getId(),
                generalService.getId(), dateTime);

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondBody)))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andReturn().getResponse().getContentAsString();

        String message = objectMapper.readTree(responseJson).get("message").asString();
        assertThat(message).containsIgnoringCase("capacity");
    }

    @Test
    void emsellaAppointmentDoesNotConsumeGeneralCapacity() throws Exception {
        LocalDateTime emsellaStart = LocalDateTime.of(mondayDate, LocalTime.of(17, 0));
        book(request(patient.getId(), professional1.getId(), emsellaService.getId(), emsellaStart));

        LocalDateTime generalStart = LocalDateTime.of(mondayDate, LocalTime.of(17, 0));
        CreateAppointmentRequest generalBody = request(patient.getId(), professional2.getId(),
                generalService.getId(), generalStart);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(generalBody)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("BOOKED"));

        long generalCount = appointmentRepository.countOverlappingActiveExcludingServices(
                generalStart, generalStart.plusMinutes(60), List.of(emsellaService.getId()));
        assertThat(generalCount).isEqualTo(1);
    }

    @Test
    void secondOverlappingEmsellaAppointmentRejectedBecauseOnlyOneBox() throws Exception {
        LocalDateTime dateTime = LocalDateTime.of(mondayDate, LocalTime.of(17, 0));
        book(request(patient.getId(), professional1.getId(), emsellaService.getId(), dateTime));

        CreateAppointmentRequest secondBody = request(patient.getId(), professional2.getId(),
                emsellaService.getId(), dateTime);

        String responseJson = mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondBody)))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andReturn().getResponse().getContentAsString();

        String message = objectMapper.readTree(responseJson).get("message").asString();
        assertThat(message).containsIgnoringCase("reserved");
    }

    @Test
    void rpgBlockReducesGeneralCapacityButLeavesOneBoxForMarcela() throws Exception {
        User alejandraUser = userRepository.save(new User(unique("alejandra") + "@example.com", "hash",
                Role.PROFESSIONAL, true));
        Professional alejandra = new Professional("Alejandra", "González", alejandraUser);
        alejandra = professionalRepository.save(alejandra);
        recurringBlockRepository.save(new RecurringBlock(DayOfWeek.SATURDAY,
                LocalTime.of(11, 0), LocalTime.of(15, 0), null, alejandra, true,
                "RPG - Alejandra González"));

        LocalDateTime dateTime = LocalDateTime.of(saturdayDate, LocalTime.of(11, 30));
        CreateAppointmentRequest body = request(patient.getId(), professional1.getId(), generalService.getId(), dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("BOOKED"));
    }

    @Test
    void availableSlotsHidesSlotUnavailableDueToReducedCapacity(
            @Autowired AppointmentService appointmentService) throws Exception {
        LocalDateTime otherProfessionalBooking = LocalDateTime.of(mondayDate, LocalTime.of(17, 0));
        book(request(patient.getId(), professional2.getId(), generalService.getId(), otherProfessionalBooking));

        List<AvailableSlotResponse> slots = appointmentService.findAvailableSlots(
                professional1.getId(), generalService.getId(), mondayDate);

        assertThat(slots).extracting(AvailableSlotResponse::startTime)
                .doesNotContain(LocalDateTime.of(mondayDate, LocalTime.of(17, 0)));
    }

    @Test
    void inactiveBlockDoesNotReduceCapacity() throws Exception {
        LocalDate tuesdayDate = nextDateForDayOfWeek(java.time.DayOfWeek.TUESDAY, 3);
        availabilityRepository.save(new Availability(professional1, DayOfWeek.TUESDAY,
                LocalTime.of(9, 0), LocalTime.of(20, 0)));
        availabilityRepository.save(new Availability(professional2, DayOfWeek.TUESDAY,
                LocalTime.of(9, 0), LocalTime.of(20, 0)));
        recurringBlockRepository.save(new RecurringBlock(DayOfWeek.TUESDAY,
                LocalTime.of(16, 0), LocalTime.of(19, 30), emsellaService, null, false,
                "EMSELLA - inactive"));

        LocalDateTime dateTime = LocalDateTime.of(tuesdayDate, LocalTime.of(17, 0));
        book(request(patient.getId(), professional1.getId(), generalService.getId(), dateTime));

        CreateAppointmentRequest secondBody = request(patient.getId(), professional2.getId(),
                generalService.getId(), dateTime);

        mockMvc.perform(MockMvcRequestBuilders.post("/api/appointments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondBody)))
                .andExpect(MockMvcResultMatchers.status().isCreated())
                .andExpect(MockMvcResultMatchers.jsonPath("$.status").value("BOOKED"));
    }
}
