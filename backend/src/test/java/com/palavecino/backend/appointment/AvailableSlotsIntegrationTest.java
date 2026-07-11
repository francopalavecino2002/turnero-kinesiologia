package com.palavecino.backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.palavecino.backend.appointment.dto.AvailableSlotResponse;
import com.palavecino.backend.availability.Availability;
import com.palavecino.backend.availability.AvailabilityRepository;
import com.palavecino.backend.exception.BusinessRuleViolationException;
import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.service.Service;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(AppointmentService.class)
@TestPropertySource(properties = {
        "clinic.max-concurrent-appointments=2",
        "spring.main.allow-bean-definition-overriding=true"
})
class AvailableSlotsIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final Instant FIXED_INSTANT = Instant.parse("2026-07-10T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 10);
    private static final LocalDate OTHER_DAY = LocalDate.of(2026, 7, 15);

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(FIXED_INSTANT, ZONE);
        }
    }

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private AvailabilityRepository availabilityRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Professional professional1;
    private Professional professional2;
    private Patient patient1;
    private Patient patient2;
    private Service generalService;
    private Service emsellaService;

    @BeforeEach
    void setUp() {
        generalService = persist(new Service("General", 60, true));
        emsellaService = persist(new Service("EMSELLA", 30, true));

        User user1 = persist(new User("pro1@example.com", "hash", Role.PROFESSIONAL, true));
        User user2 = persist(new User("pro2@example.com", "hash", Role.PROFESSIONAL, true));
        User user3 = persist(new User("pat1@example.com", "hash", Role.PATIENT, true));
        User user4 = persist(new User("pat2@example.com", "hash", Role.PATIENT, true));

        professional1 = persist(new Professional("Ana", "Gomez", user1));
        professional2 = persist(new Professional("Luis", "Diaz", user2));
        patient1 = persist(new Patient("Maria", "Lopez", "111111", user3));
        patient2 = persist(new Patient("Juan", "Perez", "222222", user4));

        professional1.getServices().add(generalService);
        professional1.getServices().add(emsellaService);
        professional2.getServices().add(generalService);
        entityManager.flush();
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private Appointment appointmentAt(Professional professional, Patient patient, Service service,
                                       LocalDateTime dateTime, AppointmentStatus status) {
        Appointment appointment = new Appointment(patient, professional, service, dateTime, status);
        entityManager.persist(appointment);
        return appointment;
    }

    private void addAvailability(Professional professional,
                                  com.palavecino.backend.availability.DayOfWeek dayOfWeek,
                                  LocalTime start, LocalTime end) {
        persist(new Availability(professional, dayOfWeek, start, end));
    }

    @Test
    void noAvailabilityReturnsEmptyList() {
        List<AvailableSlotResponse> slots = appointmentService.findAvailableSlots(
                professional1.getId(), generalService.getId(), OTHER_DAY);

        assertThat(slots).isEmpty();
    }

    @Test
    void fullListWhenNoBookings() {
        addAvailability(professional1, com.palavecino.backend.availability.DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        entityManager.flush();

        List<AvailableSlotResponse> slots = appointmentService.findAvailableSlots(
                professional1.getId(), generalService.getId(), OTHER_DAY);

        assertThat(slots).hasSize(3);
        assertThat(slots).extracting(AvailableSlotResponse::startTime).containsExactly(
                LocalDateTime.of(OTHER_DAY, LocalTime.of(9, 0)),
                LocalDateTime.of(OTHER_DAY, LocalTime.of(10, 0)),
                LocalDateTime.of(OTHER_DAY, LocalTime.of(11, 0)));
    }

    @Test
    void bookedSlotIsRemovedFromResults() {
        addAvailability(professional1, com.palavecino.backend.availability.DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        appointmentAt(professional1, patient1, generalService,
                LocalDateTime.of(OTHER_DAY, LocalTime.of(10, 0)), AppointmentStatus.BOOKED);
        entityManager.flush();

        List<AvailableSlotResponse> slots = appointmentService.findAvailableSlots(
                professional1.getId(), generalService.getId(), OTHER_DAY);

        assertThat(slots).hasSize(2);
        assertThat(slots).extracting(AvailableSlotResponse::startTime).containsExactly(
                LocalDateTime.of(OTHER_DAY, LocalTime.of(9, 0)),
                LocalDateTime.of(OTHER_DAY, LocalTime.of(11, 0)));
    }

    @Test
    void capacityFullSlotIsRemovedEvenIfProfessionalIsFree() {
        addAvailability(professional1, com.palavecino.backend.availability.DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        appointmentAt(professional2, patient1, generalService,
                LocalDateTime.of(OTHER_DAY, LocalTime.of(9, 45)), AppointmentStatus.BOOKED);
        appointmentAt(professional2, patient2, generalService,
                LocalDateTime.of(OTHER_DAY, LocalTime.of(10, 15)), AppointmentStatus.CONFIRMED);
        entityManager.flush();

        List<AvailableSlotResponse> slots = appointmentService.findAvailableSlots(
                professional1.getId(), generalService.getId(), OTHER_DAY);

        assertThat(slots).hasSize(2);
        assertThat(slots).extracting(AvailableSlotResponse::startTime).containsExactly(
                LocalDateTime.of(OTHER_DAY, LocalTime.of(9, 0)),
                LocalDateTime.of(OTHER_DAY, LocalTime.of(11, 0)));
    }

    @Test
    void shortServiceReturnsTwiceAsManySlots() {
        addAvailability(professional1, com.palavecino.backend.availability.DayOfWeek.WEDNESDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0));
        entityManager.flush();

        List<AvailableSlotResponse> generalSlots = appointmentService.findAvailableSlots(
                professional1.getId(), generalService.getId(), OTHER_DAY);
        List<AvailableSlotResponse> emsellaSlots = appointmentService.findAvailableSlots(
                professional1.getId(), emsellaService.getId(), OTHER_DAY);

        assertThat(generalSlots).hasSize(3);
        assertThat(emsellaSlots).hasSize(6);
    }

    @Test
    void pastSlotsAreExcludedWhenDateIsToday() {
        addAvailability(professional1, com.palavecino.backend.availability.DayOfWeek.FRIDAY,
                LocalTime.of(9, 0), LocalTime.of(13, 0));
        entityManager.flush();

        List<AvailableSlotResponse> slots = appointmentService.findAvailableSlots(
                professional1.getId(), generalService.getId(), TODAY);

        assertThat(slots).hasSize(1);
        assertThat(slots).extracting(AvailableSlotResponse::startTime).containsExactly(
                LocalDateTime.of(TODAY, LocalTime.of(12, 0)));
    }

    @Test
    void professionalDoesNotOfferServiceThrowsException() {
        assertThatThrownBy(() -> appointmentService.findAvailableSlots(
                professional2.getId(), emsellaService.getId(), OTHER_DAY))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("does not offer service EMSELLA");
    }
}
