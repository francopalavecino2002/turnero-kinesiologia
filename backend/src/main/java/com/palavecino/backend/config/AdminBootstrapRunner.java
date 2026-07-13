package com.palavecino.backend.config;

import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures exactly one ADMIN user exists on startup (Marcela Altamirano, the clinic owner). This
 * is security-critical, so credentials are never hardcoded:
 * - ADMIN_EMAIL / ADMIN_PASSWORD env vars are the only source of truth.
 * - The "dev" profile only falls back to safe local-dev defaults so `./mvnw spring-boot:run`
 *   works out of the box.
 * - Any other profile (including production) with those env vars missing fails application
 *   startup outright, rather than risk a known default admin password reaching production.
 * Idempotent: skips entirely if an ADMIN already exists. If DevDataSeeder already created her
 * Professional record (dev profile), her existing user account is upgraded to ADMIN in place so
 * she's both a professional and the admin under one login - otherwise a standalone admin user is
 * created.
 */
@Component
@Order(2)
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final String DEV_PROFILE = "dev";
    private static final String DEFAULT_DEV_EMAIL = "admin@equi.dev";
    private static final String DEFAULT_DEV_PASSWORD = "ChangeMe123!";
    private static final String ADMIN_FIRST_NAME = "Marcela";
    private static final String ADMIN_LAST_NAME = "Altamirano";

    private final UserRepository userRepository;
    private final ProfessionalRepository professionalRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final String configuredEmail;
    private final String configuredPassword;

    public AdminBootstrapRunner(UserRepository userRepository,
                                 ProfessionalRepository professionalRepository,
                                 PasswordEncoder passwordEncoder,
                                 Environment environment,
                                 @Value("${admin.email:}") String configuredEmail,
                                 @Value("${admin.password:}") String configuredPassword) {
        this.userRepository = userRepository;
        this.professionalRepository = professionalRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.configuredEmail = configuredEmail;
        this.configuredPassword = configuredPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            return;
        }

        boolean devProfile = Arrays.asList(environment.getActiveProfiles()).contains(DEV_PROFILE);
        boolean missingCredentials = configuredEmail.isBlank() || configuredPassword.isBlank();

        if (missingCredentials && !devProfile) {
            throw new IllegalStateException(
                    "ADMIN_EMAIL and ADMIN_PASSWORD environment variables must be set to bootstrap the admin "
                            + "account outside the 'dev' profile. Refusing to start rather than fall back to a "
                            + "default admin password in production.");
        }

        String email = missingCredentials ? DEFAULT_DEV_EMAIL : configuredEmail;
        String password = missingCredentials ? DEFAULT_DEV_PASSWORD : configuredPassword;

        Optional<Professional> existingProfessional =
                professionalRepository.findByFirstNameAndLastName(ADMIN_FIRST_NAME, ADMIN_LAST_NAME);

        if (existingProfessional.isPresent()) {
            User user = existingProfessional.get().getUser();
            user.setRole(Role.ADMIN);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(password));
            user.setMustChangePassword(true);
            userRepository.save(user);
        } else {
            User admin = new User(email, passwordEncoder.encode(password), Role.ADMIN, true, true);
            userRepository.save(admin);
        }
    }
}
