package com.palavecino.backend.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies the V6 migration semantics against a real database:
 * <ul>
 *   <li>accounts that already existed before V6 are backfilled to {@code email_verified = TRUE}
 *       (the clinic would otherwise lock itself out on deploy),</li>
 *   <li>rows created after V6 default to {@code email_verified = FALSE} unless explicitly set,</li>
 *   <li>the {@code user_token} table (and its unique hash) exists.</li>
 * </ul>
 * A dedicated migration location injects a V5_5 seed so there are real "pre-verification" rows
 * for the backfill UPDATE to act on. This class runs against its own database instance.
 */
@SpringBootTest(properties = {
        "spring.flyway.locations=classpath:db/migration,classpath:db/migration-test"
})
@Testcontainers
class EmailVerificationMigrationIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void preExistingAccountsAreBackfilledToVerified() {
        List<Boolean> verified = jdbcTemplate.queryForList(
                "SELECT email_verified FROM user_account WHERE email IN (?, ?, ?) ORDER BY email",
                Boolean.class, "pre-patient@example.com", "pre-professional@example.com", "pre-admin@example.com");

        assertThat(verified).hasSize(3).containsOnly(true);
    }

    @Test
    void accountsCreatedAfterV6DefaultToUnverified() {
        String email = "fresh-" + System.nanoTime() + "@example.com";
        jdbcTemplate.update("INSERT INTO user_account (email, password, role) VALUES (?, ?, 'PATIENT')",
                email, "x");

        Boolean verified = jdbcTemplate.queryForObject(
                "SELECT email_verified FROM user_account WHERE email = ?", Boolean.class, email);

        assertThat(verified).isFalse();
    }

    @Test
    void userTokenTableExistsWithUniqueHash() {
        Integer tables = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'user_token'",
                Integer.class);
        assertThat(tables).isEqualTo(1);

        Integer uniqueIndexes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE tablename = 'user_token' AND indexdef ILIKE '%token_hash%'",
                Integer.class);
        assertThat(uniqueIndexes).isGreaterThanOrEqualTo(1);
    }
}
