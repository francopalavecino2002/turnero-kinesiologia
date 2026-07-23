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
class AppointmentDurationSnapshotTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @org.springframework.beans.factory.annotation.Autowired
    private AppointmentRepository appointmentRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Professional professional;
    private Patient patient;
    private Service generalService;

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 7, 15, 0, 0);

    @BeforeEach
    void setUp() {
        generalService = persist(new Service("General", 60, true));

        User user1 = persist(new User("pro1@example.com", "hash", Role.PROFESSIONAL, true));
        User user2 = persist(new User("pat1@example.com", "hash", Role.PATIENT, true));

        professional = persist(new Professional("Ana", "Gomez", user1));
        patient = persist(new Patient("Maria", "Lopez", "111111", user2));
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }

    @Test
    void durationMinutesIsCopiedFromServiceWhenCreatingAppointment() {
        Appointment appointment = new Appointment(patient, professional, generalService,
                DAY.plusHours(9), AppointmentStatus.BOOKED, generalService.getDurationMinutes());
        entityManager.persist(appointment);
        entityManager.flush();
        entityManager.clear();

        Appointment loaded = appointmentRepository.findByIdWithDetails(appointment.getId()).orElseThrow();

        assertThat(loaded.getDurationMinutes()).isEqualTo(60);
    }

    @Test
    void appointmentDurationDoesNotChangeWhenServiceDurationChanges() {
        Appointment appointment = new Appointment(patient, professional, generalService,
                DAY.plusHours(9), AppointmentStatus.BOOKED, generalService.getDurationMinutes());
        entityManager.persist(appointment);
        entityManager.flush();

        generalService.setDurationMinutes(90);
        entityManager.flush();
        entityManager.clear();

        Appointment loaded = appointmentRepository.findById(appointment.getId()).orElseThrow();

        assertThat(loaded.getDurationMinutes()).isEqualTo(60);
    }

    @Test
    void overlapDetectionUsesSnapshotDurationNotServiceDuration() {
        Appointment appointment = new Appointment(patient, professional, generalService,
                DAY.plusHours(9), AppointmentStatus.BOOKED, generalService.getDurationMinutes());
        entityManager.persist(appointment);
        entityManager.flush();

        generalService.setDurationMinutes(30);
        entityManager.flush();
        entityManager.clear();

        var result = appointmentRepository.findOverlappingByProfessional(
                professional, DAY.plusHours(9).plusMinutes(30), DAY.plusHours(10));
        assertThat(result).hasSize(1);
    }
}
