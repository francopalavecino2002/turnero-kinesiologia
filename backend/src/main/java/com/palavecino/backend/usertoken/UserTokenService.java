package com.palavecino.backend.usertoken;

import com.palavecino.backend.exception.BusinessRuleViolationException;
import com.palavecino.backend.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and consumes {@link UserToken}s. Tokens are 256-bit random values; only their SHA-256
 * hash reaches the database. All checks (existence, type, expiry, single use) happen here so the
 * callers cannot get them wrong.
 */
@Service
public class UserTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserTokenRepository userTokenRepository;
    private final Clock clock;
    private final Duration emailVerificationTtl;
    private final Duration passwordResetTtl;

    public UserTokenService(UserTokenRepository userTokenRepository,
                            Clock clock,
                            @Value("${app.tokens.email-verification-ttl}") Duration emailVerificationTtl,
                            @Value("${app.tokens.password-reset-ttl}") Duration passwordResetTtl) {
        this.userTokenRepository = userTokenRepository;
        this.clock = clock;
        this.emailVerificationTtl = emailVerificationTtl;
        this.passwordResetTtl = passwordResetTtl;
    }

    /**
     * Issues a fresh token of the given type, invalidating any outstanding (unused) ones of the
     * same type for the same user first, so only the most recent link works.
     *
     * @return the plain token, to be placed in the emailed link; only its hash is persisted.
     */
    @Transactional
    public String issue(User user, TokenType type) {
        userTokenRepository.deleteByUserAndTypeAndUsedAtIsNull(user, type);

        LocalDateTime now = LocalDateTime.now(clock);
        String rawToken = generateRawToken();
        UserToken userToken = new UserToken(user, hash(rawToken), type, now.plus(ttlFor(type)), now);
        userTokenRepository.save(userToken);
        return rawToken;
    }

    /**
     * Validates a token of the expected type, marks it used and returns it. Throws for unknown,
     * wrong-type, expired or already-used tokens.
     */
    @Transactional
    public UserToken consume(String rawToken, TokenType expectedType) {
        LocalDateTime now = LocalDateTime.now(clock);
        UserToken userToken = userTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "El link es inválido, venció o ya fue usado."));

        if (userToken.getType() != expectedType) {
            throw new BusinessRuleViolationException("El link es inválido, venció o ya fue usado.");
        }
        if (userToken.getUsedAt() != null) {
            throw new BusinessRuleViolationException("El link ya fue usado. Pedí uno nuevo.");
        }
        if (userToken.getExpiresAt().isBefore(now)) {
            throw new BusinessRuleViolationException("El link venció. Pedí uno nuevo.");
        }

        userToken.setUsedAt(now);
        userTokenRepository.save(userToken);
        return userToken;
    }

    /**
     * Rate limit for resending links: {@code true} when a token of the same type was already
     * issued to this user less than {@code window} ago, meaning another email of that kind should
     * not be sent yet. Anchored on {@code createdAt} (already persisted) so no extra state is
     * needed.
     */
    @Transactional(readOnly = true)
    public boolean wasIssuedWithin(User user, TokenType type, Duration window) {
        return userTokenRepository.findFirstByUserAndTypeOrderByCreatedAtDesc(user, type)
                .map(token -> Duration.between(token.getCreatedAt(), LocalDateTime.now(clock)).compareTo(window) < 0)
                .orElse(false);
    }

    public static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] digestBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digestBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available on this JVM", e);
        }
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private Duration ttlFor(TokenType type) {
        return type == TokenType.EMAIL_VERIFICATION ? emailVerificationTtl : passwordResetTtl;
    }
}
