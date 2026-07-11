package com.palavecino.backend.appointment;

import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.professional.Professional;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.professional = :professional
              AND a.status <> com.palavecino.backend.appointment.AppointmentStatus.CANCELLED
              AND a.dateTime < :rangeEnd
              AND FUNCTION('TIMESTAMPADD', 'MINUTE', a.service.durationMinutes, a.dateTime) > :rangeStart
            """)
    List<Appointment> findOverlappingByProfessional(
            @Param("professional") Professional professional,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    List<Appointment> findByPatient(Patient patient);

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.status <> com.palavecino.backend.appointment.AppointmentStatus.CANCELLED
              AND a.dateTime < :rangeEnd
              AND FUNCTION('TIMESTAMPADD', 'MINUTE', a.service.durationMinutes, a.dateTime) > :rangeStart
            """)
    List<Appointment> findOverlappingAll(
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    @Query("""
            SELECT COUNT(a) FROM Appointment a
            WHERE a.status IN (
                com.palavecino.backend.appointment.AppointmentStatus.BOOKED,
                com.palavecino.backend.appointment.AppointmentStatus.CONFIRMED
            )
              AND a.dateTime < :rangeEnd
              AND FUNCTION('TIMESTAMPADD', 'MINUTE', a.service.durationMinutes, a.dateTime) > :rangeStart
            """)
    long countOverlappingActive(
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);
}
