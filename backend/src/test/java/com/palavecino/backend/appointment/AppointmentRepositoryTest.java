package com.palavecino.backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.service.Service;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class AppointmentRepositoryTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.springframework.beans.factory.annotation.Autowired
    private AppointmentRepository appointmentRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Professional professional1;
    private Professional professional2;
    private Patient patient1;
    private Patient patient2;
    private Service generalService;
    private Service emsellaService;

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 7, 15, 0, 0);

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
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    private Appointment appointmentAt(Professional professional, Patient patient, Service service,
                                       LocalDateTime dateTime, AppointmentStatus status) {
        Appointment appointment = new Appointment(patient, professional, service, dateTime, status, service.getDurationMinutes());
        entityManager.persist(appointment);
        return appointment;
    }

    @Test
    void partialOverlapIsDetected() {
        appointmentAt(professional1, patient1, generalService, DAY.plusHours(9), AppointmentStatus.BOOKED);
        entityManager.flush();

        var byProfessional = appointmentRepository.findOverlappingByProfessional(
                professional1, DAY.plusHours(9).plusMinutes(30), DAY.plusHours(10).plusMinutes(30));
        var all = appointmentRepository.findOverlappingAll(
                DAY.plusHours(9).plusMinutes(30), DAY.plusHours(10).plusMinutes(30));

        assertThat(byProfessional).hasSize(1);
        assertThat(all).hasSize(1);
    }

    @Test
    void adjacentAppointmentIsNotOverlapping() {
        appointmentAt(professional1, patient1, generalService, DAY.plusHours(9), AppointmentStatus.BOOKED);
        entityManager.flush();

        var afterRange = appointmentRepository.findOverlappingByProfessional(
                professional1, DAY.plusHours(10), DAY.plusHours(11));
        assertThat(afterRange).isEmpty();
    }

    @Test
    void adjacentAppointmentIsNotOverlapping_reverse() {
        appointmentAt(professional1, patient1, generalService, DAY.plusHours(8), AppointmentStatus.BOOKED);
        entityManager.flush();

        var beforeRange = appointmentRepository.findOverlappingByProfessional(
                professional1, DAY.plusHours(9), DAY.plusHours(10));
        assertThat(beforeRange).isEmpty();
    }

    @Test
    void cancelledAppointmentIsExcludedFromOverlapResults() {
        appointmentAt(professional1, patient1, generalService, DAY.plusHours(9), AppointmentStatus.CANCELLED);
        entityManager.flush();

        var byProfessional = appointmentRepository.findOverlappingByProfessional(
                professional1, DAY.plusHours(9).plusMinutes(30), DAY.plusHours(10).plusMinutes(30));
        var all = appointmentRepository.findOverlappingAll(
                DAY.plusHours(9).plusMinutes(30), DAY.plusHours(10).plusMinutes(30));

        assertThat(byProfessional).isEmpty();
        assertThat(all).isEmpty();
    }

    @Test
    void durationComesFromService_shortServiceDoesNotOverlapLaterRange() {
        appointmentAt(professional1, patient1, emsellaService, DAY.plusHours(9), AppointmentStatus.BOOKED);
        entityManager.flush();

        var result = appointmentRepository.findOverlappingByProfessional(
                professional1, DAY.plusHours(9).plusMinutes(30), DAY.plusHours(10));
        assertThat(result).isEmpty();
    }

    @Test
    void durationComesFromService_longServiceDoesOverlapSameRange() {
        appointmentAt(professional1, patient1, generalService, DAY.plusHours(9), AppointmentStatus.BOOKED);
        entityManager.flush();

        var result = appointmentRepository.findOverlappingByProfessional(
                professional1, DAY.plusHours(9).plusMinutes(30), DAY.plusHours(10));
        assertThat(result).hasSize(1);
    }

    @Test
    void countOverlappingActiveCountsTwoActiveAppointmentsAndIgnoresCancelled() {
        appointmentAt(professional1, patient1, generalService, DAY.plusHours(9), AppointmentStatus.BOOKED);
        appointmentAt(professional2, patient2, generalService, DAY.plusHours(9).plusMinutes(15), AppointmentStatus.CONFIRMED);
        appointmentAt(professional1, patient2, generalService, DAY.plusHours(9).plusMinutes(10), AppointmentStatus.CANCELLED);
        entityManager.flush();

        long count = appointmentRepository.countOverlappingActive(
                DAY.plusHours(9).plusMinutes(30), DAY.plusHours(10).plusMinutes(30));

        assertThat(count).isEqualTo(2);
    }

    @Test
    void appointmentFullyContainedInRangeIsOverlapping() {
        appointmentAt(professional1, patient1, emsellaService, DAY.plusHours(9).plusMinutes(15), AppointmentStatus.BOOKED);
        entityManager.flush();

        var result = appointmentRepository.findOverlappingByProfessional(
                professional1, DAY.plusHours(9), DAY.plusHours(10));
        assertThat(result).hasSize(1);
    }

    @Test
    void appointmentsFarApartAreNotOverlapping() {
        appointmentAt(professional1, patient1, generalService, DAY.plusHours(9), AppointmentStatus.BOOKED);
        entityManager.flush();

        var result = appointmentRepository.findOverlappingByProfessional(
                professional1, DAY.plusHours(14), DAY.plusHours(15));
        assertThat(result).isEmpty();
    }
}
