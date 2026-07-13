package com.palavecino.backend.config;

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
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads demo data for local development so the API can be exercised manually
 * without a real admin panel. Only active on the "dev" profile; never runs
 * against the test or production databases. In production, professionals and
 * services are created by an admin through the app, not seeded here.
 */
@Component
@Profile("dev")
@Order(1)
public class DevDataSeeder implements CommandLineRunner {

    private static final String PLACEHOLDER_PASSWORD = "changeme123";

    private final ServiceRepository serviceRepository;
    private final ProfessionalRepository professionalRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AvailabilityRepository availabilityRepository;
    private final RecurringBlockRepository recurringBlockRepository;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(ServiceRepository serviceRepository,
                          ProfessionalRepository professionalRepository,
                          PatientRepository patientRepository,
                          UserRepository userRepository,
                          AvailabilityRepository availabilityRepository,
                          RecurringBlockRepository recurringBlockRepository,
                          PasswordEncoder passwordEncoder) {
        this.serviceRepository = serviceRepository;
        this.professionalRepository = professionalRepository;
        this.patientRepository = patientRepository;
        this.userRepository = userRepository;
        this.availabilityRepository = availabilityRepository;
        this.recurringBlockRepository = recurringBlockRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (serviceRepository.count() > 0 || patientRepository.count() > 0) {
            return;
        }

        Map<String, Service> services = seedServices();
        Professional alejandra = seedProfessional("Alejandra", "González", "alejandra.gonzalez@equi.dev",
                services.get("Reeducación Postural (RPG)"),
                List.of(
                        new AvailabilityWindow(DayOfWeek.SATURDAY, LocalTime.of(11, 0), LocalTime.of(15, 0))
                ));
        seedProfessional("Marcela", "Altamirano", "marcela.altamirano@equi.dev",
                services.get("Deporte y Traumatología"),
                List.of(
                        new AvailabilityWindow(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)),
                        new AvailabilityWindow(DayOfWeek.TUESDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)),
                        new AvailabilityWindow(DayOfWeek.WEDNESDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)),
                        new AvailabilityWindow(DayOfWeek.THURSDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)),
                        new AvailabilityWindow(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(18, 0)),
                        new AvailabilityWindow(DayOfWeek.SATURDAY, LocalTime.of(9, 0), LocalTime.of(12, 0))
                ));
        seedProfessional("Franco", "Lastra", "franco.lastra@equi.dev",
                services.get("Deporte y Traumatología"),
                List.of(
                        new AvailabilityWindow(DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(20, 0)),
                        new AvailabilityWindow(DayOfWeek.TUESDAY, LocalTime.of(14, 0), LocalTime.of(20, 0)),
                        new AvailabilityWindow(DayOfWeek.WEDNESDAY, LocalTime.of(14, 0), LocalTime.of(20, 0)),
                        new AvailabilityWindow(DayOfWeek.THURSDAY, LocalTime.of(14, 0), LocalTime.of(20, 0)),
                        new AvailabilityWindow(DayOfWeek.FRIDAY, LocalTime.of(14, 0), LocalTime.of(20, 0))
                ));
        seedProfessional("Delia", "Furlán", "delia.furlan@equi.dev",
                services.get("Drenaje Linfático y Reflexología"),
                List.of(
                        new AvailabilityWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(20, 0)),
                        new AvailabilityWindow(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(20, 0)),
                        new AvailabilityWindow(DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(20, 0)),
                        new AvailabilityWindow(DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(20, 0)),
                        new AvailabilityWindow(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(20, 0))
                ));
        seedProfessional("Carolina", "Seona", "carolina.seona@equi.dev",
                services.get("Rehabilitación Piso Pélvico"),
                List.of(
                        new AvailabilityWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(20, 0)),
                        new AvailabilityWindow(DayOfWeek.TUESDAY, LocalTime.of(8, 0), LocalTime.of(20, 0)),
                        new AvailabilityWindow(DayOfWeek.WEDNESDAY, LocalTime.of(8, 0), LocalTime.of(20, 0)),
                        new AvailabilityWindow(DayOfWeek.THURSDAY, LocalTime.of(8, 0), LocalTime.of(20, 0)),
                        new AvailabilityWindow(DayOfWeek.FRIDAY, LocalTime.of(8, 0), LocalTime.of(20, 0))
                ));

        seedPatient("Juan", "Pérez", "3511111111", "juan.perez@example.com");
        seedPatient("María", "Gómez", "3512222222", "maria.gomez@example.com");

        seedRecurringBlocks(services.get("EMSELLA"), alejandra);
    }

    private Map<String, Service> seedServices() {
        Service deporteTraumatologia = serviceRepository.save(new Service("Deporte y Traumatología", 60, true));
        Service rpg = serviceRepository.save(new Service("Reeducación Postural (RPG)", 60, true));
        Service drenaje = serviceRepository.save(new Service("Drenaje Linfático y Reflexología", 60, true));
        Service pisoPelvico = serviceRepository.save(new Service("Rehabilitación Piso Pélvico", 60, true));
        Service emsella = serviceRepository.save(new Service("EMSELLA", 30, true));
        serviceRepository.save(new Service("Alquiler de Magnetoterapia", 60, true));

        return Map.of(
                "Deporte y Traumatología", deporteTraumatologia,
                "Reeducación Postural (RPG)", rpg,
                "Drenaje Linfático y Reflexología", drenaje,
                "Rehabilitación Piso Pélvico", pisoPelvico,
                "EMSELLA", emsella
        );
    }

    private Professional seedProfessional(String firstName, String lastName, String email, Service service,
                                           List<AvailabilityWindow> windows) {
        User user = userRepository.save(new User(email, passwordEncoder.encode(PLACEHOLDER_PASSWORD), Role.PROFESSIONAL, true));
        Professional professional = new Professional(firstName, lastName, user);
        professional.getServices().add(service);
        professional = professionalRepository.save(professional);

        for (AvailabilityWindow window : windows) {
            availabilityRepository.save(new Availability(professional, window.dayOfWeek(), window.start(), window.end()));
        }

        return professional;
    }

    private void seedRecurringBlocks(Service emsella, Professional alejandra) {
        recurringBlockRepository.save(new RecurringBlock(DayOfWeek.MONDAY, LocalTime.of(16, 0), LocalTime.of(19, 30),
                emsella, null, true, "EMSELLA - box reservado para el equipo"));
        recurringBlockRepository.save(new RecurringBlock(DayOfWeek.THURSDAY, LocalTime.of(16, 0), LocalTime.of(19, 30),
                emsella, null, true, "EMSELLA - box reservado para el equipo"));
        recurringBlockRepository.save(new RecurringBlock(DayOfWeek.SATURDAY, LocalTime.of(11, 0), LocalTime.of(15, 0),
                null, alejandra, true, "RPG - Alejandra González (bloqueo semanal de box)"));
    }

    private void seedPatient(String firstName, String lastName, String phone, String email) {
        User user = userRepository.save(new User(email, passwordEncoder.encode(PLACEHOLDER_PASSWORD), Role.PATIENT, true));
        patientRepository.save(new Patient(firstName, lastName, phone, user));
    }

    private record AvailabilityWindow(DayOfWeek dayOfWeek, LocalTime start, LocalTime end) {
    }
}
