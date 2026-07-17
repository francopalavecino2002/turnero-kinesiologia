package com.palavecino.backend.config;

import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import java.util.Arrays;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ensures exactly one ADMIN user exists on startup. This is security-critical, so credentials
 * are never hardcoded:
 * <ul>
 *   <li>ADMIN_EMAIL / ADMIN_PASSWORD env vars are the only source of truth.</li>
 *   <li>The "dev" profile only falls back to safe local-dev defaults so
 *       {@code ./mvnw spring-boot:run} works out of the box.</li>
 *   <li>Any other profile (including production) with those env vars missing fails application
 *       startup outright, rather than risk a known default admin password reaching production.</li>
 * </ul>
 *
 * <h2>Resolution strategy (uniform for all profiles)</h2>
 * <ol>
 *   <li>Determine the target admin email: from ADMIN_EMAIL env var if set, otherwise from
 *       the dev default ({@code marcela.altamirano@equi.dev} — matches DevDataSeeder).</li>
 *   <li>If a user with that email already exists (e.g. seeded professional in dev, or
 *       pre-created user in prod), promote them to ADMIN and set mustChangePassword=true.
 *       Their existing password is preserved so they can log in with their original credentials.</li>
 *   <li>If no user with that email exists, create a new ADMIN user with the resolved
 *       email and password.</li>
 * </ol>
 *
 * <h2>Behaviour per profile</h2>
 * <ul>
 *   <li><b>dev (no env vars)</b>: DevDataSeeder creates Marcela Altamirano with email
 *       {@code marcela.altamirano@equi.dev} and role PROFESSIONAL. The runner finds her
 *       by email, promotes to ADMIN, and she logs in with her seeded credentials
 *       ({@code marcela.altamirano@equi.dev / changeme123}).</li>
 *   <li><b>dev (with env vars)</b>: If ADMIN_EMAIL is set, the runner uses that email
 *       instead. If a user with that email exists they are promoted; otherwise a new
 *       admin is created.</li>
 *   <li><b>production (env vars set)</b>: Finds or creates the admin from ADMIN_EMAIL /
 *       ADMIN_PASSWORD. DevDataSeeder never runs ({@code @Profile("dev")}).</li>
 *   <li><b>production (env vars missing)</b>: Throws IllegalStateException and refuses
 *       to start.</li>
 * </ul>
 *
 * <p>Idempotent: skips entirely if an ADMIN already exists.</p>
 */
@Component
@Order(2)
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private static final String DEV_PROFILE = "dev";
    private static final String DEFAULT_DEV_EMAIL = "marcela.altamirano@equi.dev";
    private static final String DEFAULT_DEV_PASSWORD = "ChangeMe123!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;
    private final String configuredEmail;
    private final String configuredPassword;

    public AdminBootstrapRunner(UserRepository userRepository,
                                 PasswordEncoder passwordEncoder,
                                 Environment environment,
                                 @Value("${admin.email:}") String configuredEmail,
                                 @Value("${admin.password:}") String configuredPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
        this.configuredEmail = configuredEmail;
        this.configuredPassword = configuredPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole(Role.ADMIN)) {
            log.debug("An ADMIN user already exists — skipping bootstrap.");
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

        Optional<User> existingUser = userRepository.findByEmail(email);
        if (existingUser.isPresent()) {
            User user = existingUser.get();
            user.setRole(Role.ADMIN);
            user.setMustChangePassword(true);
            userRepository.save(user);
            log.info("Promoted existing user '{}' (id={}) to ADMIN.", email, user.getId());
        } else {
            User admin = new User(email, passwordEncoder.encode(password), Role.ADMIN, true, true);
            userRepository.save(admin);
            log.info("Created new ADMIN user '{}'.", email);
        }
    }
}
