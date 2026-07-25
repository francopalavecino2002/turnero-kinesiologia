package com.palavecino.backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;

import com.palavecino.backend.appointment.dto.CreateAppointmentRequest;
import com.palavecino.backend.availability.Availability;
import com.palavecino.backend.availability.AvailabilityRepository;
import com.palavecino.backend.availability.DayOfWeek;
import com.palavecino.backend.exception.ConflictException;
import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.patient.PatientRepository;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.security.AuthenticatedUser;
import com.palavecino.backend.service.Service;
import com.palavecino.backend.service.ServiceRepository;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class AppointmentConcurrencyTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

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

    private Service generalService;
    private LocalDate bookingDate;

    @BeforeEach
    void setUp() {
        generalService = serviceRepository.save(new Service("General", 60, true));
        bookingDate = nextDateForDayOfWeek(java.time.DayOfWeek.MONDAY, 3);
    }

    private static LocalDate nextDateForDayOfWeek(java.time.DayOfWeek dayOfWeek, int minDaysAhead) {
        LocalDate date = LocalDate.now().plusDays(minDaysAhead);
        while (date.getDayOfWeek() != dayOfWeek) {
            date = date.plusDays(1);
        }
        return date;
    }

    private Professional createProfessional(String name, String lastName) {
        User user = userRepository.save(new User(
                "pro-" + name.toLowerCase() + "-" + System.nanoTime() + "@example.com",
                "hash", Role.PROFESSIONAL, true));
        Professional pro = new Professional(name, lastName, user);
        pro.setServices(new HashSet<>(java.util.List.of(generalService)));
        pro = professionalRepository.save(pro);
        availabilityRepository.save(new Availability(pro, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0)));
        return pro;
    }

    private Patient createPatient(String name, String lastName) {
        User user = userRepository.save(new User(
                "patient-" + name.toLowerCase() + "-" + System.nanoTime() + "@example.com",
                "hash", Role.PATIENT, true));
        return patientRepository.save(new Patient(name, lastName, "000" + System.nanoTime(), user));
    }

    private AuthenticatedUser authUser(Patient patient) {
        return new AuthenticatedUser(patient.getUser().getId(), patient.getUser().getEmail(),
                patient.getUser().getRole(), patient.getId(), null);
    }

    @Test
    void concurrentBookingsForSameProfessionalNeverExceedOne() throws Exception {
        int threadCount = 8;
        Professional professional = createProfessional("Ana", "Gomez");
        LocalDateTime slotTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));

        List<AuthenticatedUser> users = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Patient p = createPatient("P" + i, "L" + i);
            users.add(authUser(p));
        }

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    CreateAppointmentRequest request = new CreateAppointmentRequest(
                            professional.getId(), generalService.getId(), slotTime);
                    appointmentService.bookAppointment(request, users.get(idx));
                    successCount.incrementAndGet();
                } catch (ConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get())
                .as("Only 1 booking can succeed for the same professional at the same time")
                .isEqualTo(1);
        assertThat(successCount.get() + conflictCount.get() + errorCount.get())
                .as("All threads should complete")
                .isEqualTo(threadCount);
        assertThat(errorCount.get())
                .as("No unexpected errors (deadlocks, timeouts)")
                .isZero();
    }

    @Test
    void overlappingBookingsOnEmptyTableOnlyOneSucceeds() throws Exception {
        int threadCount = 2;
        Professional professional = createProfessional("Ana", "Gomez");
        LocalDateTime slotA = LocalDateTime.of(bookingDate, LocalTime.of(10, 0));
        LocalDateTime slotB = LocalDateTime.of(bookingDate, LocalTime.of(10, 15));

        List<AuthenticatedUser> users = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            Patient p = createPatient("P" + i, "L" + i);
            users.add(authUser(p));
        }

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        executor.submit(() -> {
            try {
                startGate.await();
                CreateAppointmentRequest request = new CreateAppointmentRequest(
                        professional.getId(), generalService.getId(), slotA);
                appointmentService.bookAppointment(request, users.get(0));
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                doneGate.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startGate.await();
                CreateAppointmentRequest request = new CreateAppointmentRequest(
                        professional.getId(), generalService.getId(), slotB);
                appointmentService.bookAppointment(request, users.get(1));
                successCount.incrementAndGet();
            } catch (ConflictException e) {
                conflictCount.incrementAndGet();
            } catch (Exception e) {
                errorCount.incrementAndGet();
            } finally {
                doneGate.countDown();
            }
        });

        startGate.countDown();
        doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get())
                .as("Only 1 of two overlapping bookings on empty table should succeed (9:00 60min vs 9:15 60min)")
                .isEqualTo(1);
        assertThat(successCount.get() + conflictCount.get() + errorCount.get())
                .as("All threads should complete")
                .isEqualTo(threadCount);
        assertThat(errorCount.get())
                .as("No unexpected errors (deadlocks, timeouts)")
                .isZero();
    }

    @Test
    void concurrentBookingsAcrossDifferentProfessionalsRespectGlobalCapacity() throws Exception {
        int threadCount = 5;
        int maxConcurrent = 2;
        LocalDateTime slotTime = LocalDateTime.of(bookingDate, LocalTime.of(9, 0));

        List<Professional> professionals = new ArrayList<>();
        List<AuthenticatedUser> users = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            professionals.add(createProfessional("Pro" + i, "Last" + i));
            Patient p = createPatient("Pat" + i, "Last" + i);
            users.add(authUser(p));
        }

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    CreateAppointmentRequest request = new CreateAppointmentRequest(
                            professionals.get(idx).getId(), generalService.getId(), slotTime);
                    appointmentService.bookAppointment(request, users.get(idx));
                    successCount.incrementAndGet();
                } catch (ConflictException e) {
                    conflictCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneGate.countDown();
                }
            });
        }

        startGate.countDown();
        doneGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get())
                .as("At most %d bookings should succeed (clinic capacity)", maxConcurrent)
                .isLessThanOrEqualTo(maxConcurrent);
        assertThat(successCount.get() + conflictCount.get() + errorCount.get())
                .as("All threads should complete")
                .isEqualTo(threadCount);
        assertThat(errorCount.get())
                .as("No unexpected errors (deadlocks, timeouts)")
                .isZero();
    }
}
