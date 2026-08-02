package com.palavecino.backend.security;

import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.patient.PatientRepository;
import com.palavecino.backend.user.AuthProvider;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads the Google user into the app: after Google's OIDC user info has been resolved, looks up
 * the account by the email Google returned and either creates it or authenticates it. The email
 * is only trusted once Google confirms it is verified (email_verified claim).
 *
 * <p>Account resolution (cases 4a-4d from the design):
 * <ul>
 *   <li><b>New email</b> → creates a {@code GOOGLE} account with {@code role = PATIENT}, a
 *       verified, active Patient (mirrors public self-registration minus the password), no
 *       password, and no forced password change.</li>
 *   <li><b>Existing LOCAL account</b> → authenticates as-is: the password and the provider are
 *       left untouched. Rationale: Google has independently verified that the person controls
 *       this email (same trust level as a password-reset link), so letting them in is safe; but
 *       silently rewriting the account to {@code GOOGLE} would be an irreversible surprise
 *       (their original password stops being usable) and gains nothing, since the account stays
 *       fully usable either way. Keeping it {@code LOCAL} preserves the existing password flow.</li>
 *   <li><b>Existing GOOGLE account</b> → authenticates as-is.</li>
 * </ul>
 */
@Service
public class GoogleOAuth2UserService extends OidcUserService {

    private static final String ERROR_INVALID_REQUEST = "invalid_request";
    private static final String ERROR_EMAIL_NOT_VERIFIED = "email_not_verified";
    private static final String ERROR_ACCOUNT_INACTIVE = "account_inactive";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;

    public GoogleOAuth2UserService(UserRepository userRepository, PatientRepository patientRepository) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        authenticateOrCreate(oidcUser.getClaimAsString("email"),
                Boolean.TRUE.equals(oidcUser.getClaimAsBoolean("email_verified")),
                oidcUser.getClaimAsString("given_name"),
                oidcUser.getClaimAsString("family_name"));
        return oidcUser;
    }

    /**
     * Package-private so the business logic can be unit-tested in isolation without mocking the
     * OIDC exchange with Google.
     */
    void authenticateOrCreate(String email, boolean emailVerified, String givenName, String familyName) {
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(ERROR_INVALID_REQUEST, "Google no devolvió un email para esta cuenta.", null));
        }
        if (!emailVerified) {
            throw new OAuth2AuthenticationException(new OAuth2Error(ERROR_EMAIL_NOT_VERIFIED,
                    "Google reportó que este email no está verificado. No se puede usar esa identidad.", null));
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            user = new User(email, null, Role.PATIENT, true, false, true);
            user.setAuthProvider(AuthProvider.GOOGLE);
            userRepository.save(user);
            patientRepository.save(new Patient(
                    givenName == null ? "" : givenName,
                    familyName == null ? "" : familyName,
                    // Google does not expose a phone number; keep the existing NOT NULL contract
                    // with an empty placeholder until a "complete your profile" flow exists.
                    "",
                    user,
                    true));
            return;
        }

        if (!user.isActive()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(ERROR_ACCOUNT_INACTIVE, "Esta cuenta está desactivada.", null));
        }
        // Existing LOCAL or GOOGLE account: authenticate as-is, never touch password/provider.
    }
}
