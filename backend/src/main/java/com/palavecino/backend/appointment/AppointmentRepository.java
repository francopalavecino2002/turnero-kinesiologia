package com.palavecino.backend.appointment;

import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.service.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.professional = :professional
              AND a.status <> com.palavecino.backend.appointment.AppointmentStatus.CANCELLED
              AND a.dateTime < :rangeEnd
              AND timestampadd(MINUTE, a.durationMinutes, a.dateTime) > :rangeStart
            """)
    List<Appointment> findOverlappingByProfessional(
            @Param("professional") Professional professional,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    List<Appointment> findByPatient(Patient patient);

    @Query("""
            SELECT a FROM Appointment a
            JOIN FETCH a.patient
            JOIN FETCH a.professional
            JOIN FETCH a.service
            WHERE a.id = :id
            """)
    Optional<Appointment> findByIdWithDetails(@Param("id") Long id);

    @Query("""
            SELECT a FROM Appointment a
            JOIN FETCH a.patient
            JOIN FETCH a.professional
            JOIN FETCH a.service
            WHERE a.professional = :professional
              AND a.dateTime >= :dayStart AND a.dateTime < :dayEnd
            ORDER BY a.dateTime
            """)
    List<Appointment> findByProfessionalAndDate(
            @Param("professional") Professional professional,
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd);

    @Query("""
            SELECT a FROM Appointment a
            JOIN FETCH a.patient
            JOIN FETCH a.professional
            JOIN FETCH a.service
            WHERE a.patient = :patient
            ORDER BY a.dateTime
            """)
    List<Appointment> findByPatientWithDetails(@Param("patient") Patient patient);

    @Query("""
            SELECT a FROM Appointment a
            JOIN FETCH a.patient
            JOIN FETCH a.professional
            JOIN FETCH a.service
            WHERE a.dateTime >= :dayStart AND a.dateTime < :dayEnd
            ORDER BY a.dateTime
            """)
    List<Appointment> findAllByDate(
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd") LocalDateTime dayEnd);

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.status <> com.palavecino.backend.appointment.AppointmentStatus.CANCELLED
              AND a.dateTime < :rangeEnd
              AND timestampadd(MINUTE, a.durationMinutes, a.dateTime) > :rangeStart
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
              AND timestampadd(MINUTE, a.durationMinutes, a.dateTime) > :rangeStart
            """)
    long countOverlappingActive(
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    @Query("""
            SELECT a FROM Appointment a
            WHERE a.service = :service
              AND a.status IN (
                  com.palavecino.backend.appointment.AppointmentStatus.BOOKED,
                  com.palavecino.backend.appointment.AppointmentStatus.CONFIRMED
              )
              AND a.dateTime < :rangeEnd
              AND timestampadd(MINUTE, a.durationMinutes, a.dateTime) > :rangeStart
            """)
    List<Appointment> findOverlappingActiveByService(
            @Param("service") Service service,
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    @Query("""
            SELECT COUNT(a) FROM Appointment a
            WHERE a.service.id NOT IN :excludedServiceIds
              AND a.status IN (
                  com.palavecino.backend.appointment.AppointmentStatus.BOOKED,
                  com.palavecino.backend.appointment.AppointmentStatus.CONFIRMED
              )
              AND a.dateTime < :rangeEnd
              AND timestampadd(MINUTE, a.durationMinutes, a.dateTime) > :rangeStart
            """)
    long countOverlappingActiveExcludingServices(
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd,
            @Param("excludedServiceIds") List<Long> excludedServiceIds);

    @Query(value = """
            SELECT EXTRACT(DAY FROM a.date_time)::int AS day_of_month,
                   COUNT(*)                           AS cnt
            FROM appointment a
            WHERE a.date_time >= :rangeStart
              AND a.date_time < :rangeEnd
              AND a.status <> 'CANCELLED'
            GROUP BY EXTRACT(DAY FROM a.date_time)
            ORDER BY day_of_month
            """, nativeQuery = true)
    List<Object[]> countNonCancelledPerDay(
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd);

    @Query(value = """
            SELECT EXTRACT(DAY FROM a.date_time)::int AS day_of_month,
                   COUNT(*)                           AS cnt
            FROM appointment a
            WHERE a.date_time >= :rangeStart
              AND a.date_time < :rangeEnd
              AND a.status <> 'CANCELLED'
              AND a.professional_id = :professionalId
            GROUP BY EXTRACT(DAY FROM a.date_time)
            ORDER BY day_of_month
            """, nativeQuery = true)
    List<Object[]> countNonCancelledPerDayByProfessional(
            @Param("rangeStart") LocalDateTime rangeStart,
            @Param("rangeEnd") LocalDateTime rangeEnd,
            @Param("professionalId") Long professionalId);
}
