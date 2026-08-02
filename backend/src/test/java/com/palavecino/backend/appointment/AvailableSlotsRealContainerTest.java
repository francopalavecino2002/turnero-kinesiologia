package com.palavecino.backend.appointment;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises the exact reported failure inside a REAL servlet container (embedded Tomcat), not
 * MockMvc. Regression guard for the PostgreSQL "cannot execute SELECT FOR NO KEY UPDATE in a
 * read-only transaction" crash that made /api/appointments/available-slots return 401 (the real
 * 500 was masked by the /error dispatch re-entering the security chain).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AvailableSlotsRealContainerTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    private int port;

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
    private JwtService jwtService;

    @Test
    void availableSlotsWithValidPatientTokenAndQueryParamsReturns200InRealContainer() throws Exception {
        Service service = serviceRepository.save(new Service("General", 60, true));

        User patientUser = userRepository.save(
                new User("patient" + System.nanoTime() + "@example.com", "hash", Role.PATIENT, true));
        patientRepository.save(new Patient("Ana", "Perez", "111111", patientUser));

        User professionalUser = userRepository.save(
                new User("pro" + System.nanoTime() + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional professional = new Professional("Ana", "Gomez", professionalUser);
        professional.setServices(new HashSet<>(List.of(service)));
        professional = professionalRepository.save(professional);

        LocalDate bookingDate = nextDateForDayOfWeek(java.time.DayOfWeek.MONDAY, 3);
        availabilityRepository.save(new Availability(professional, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0)));

        String token = jwtService.generateToken(patientUser);
        String url = "http://localhost:" + port + "/api/appointments/available-slots?professionalId="
                + professional.getId() + "&serviceId=" + service.getId() + "&date=" + bookingDate;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("09:00");
    }
    @Test
    void availableSlotsWithoutTokenReturns200InRealContainer() throws Exception {
        Service service = serviceRepository.save(new Service("General", 60, true));

        User professionalUser = userRepository.save(
                new User("pro2" + System.nanoTime() + "@example.com", "hash", Role.PROFESSIONAL, true));
        Professional professional = new Professional("Ana", "Gomez", professionalUser);
        professional.setServices(new HashSet<>(List.of(service)));
        professional = professionalRepository.save(professional);

        LocalDate bookingDate = nextDateForDayOfWeek(java.time.DayOfWeek.MONDAY, 3);
        availabilityRepository.save(new Availability(professional, DayOfWeek.MONDAY,
                LocalTime.of(9, 0), LocalTime.of(12, 0)));

        String url = "http://localhost:" + port + "/api/appointments/available-slots?professionalId="
                + professional.getId() + "&serviceId=" + service.getId() + "&date=" + bookingDate;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("09:00");
    }

    private static LocalDate nextDateForDayOfWeek(java.time.DayOfWeek dayOfWeek, int minDaysAhead) {
        LocalDate date = LocalDate.now().plusDays(minDaysAhead);
        while (date.getDayOfWeek() != dayOfWeek) {
            date = date.plusDays(1);
        }
        return date;
    }
}
