package com.palavecino.backend.auth;

import com.palavecino.backend.auth.dto.AuthResponse;
import com.palavecino.backend.auth.dto.ChangePasswordRequest;
import com.palavecino.backend.auth.dto.LoginRequest;
import com.palavecino.backend.auth.dto.MessageResponse;
import com.palavecino.backend.auth.dto.RegisterRequest;
import com.palavecino.backend.auth.dto.RegisterResponse;
import com.palavecino.backend.auth.dto.UserInfoResponse;
import com.palavecino.backend.email.EmailService;
import com.palavecino.backend.email.EmailMask;
import com.palavecino.backend.exception.ConflictException;
import com.palavecino.backend.exception.UnauthorizedException;
import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.patient.PatientRepository;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.security.JwtService;
import com.palavecino.backend.user.AuthProvider;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import com.palavecino.backend.usertoken.TokenType;
import com.palavecino.backend.usertoken.UserToken;
import com.palavecino.backend.usertoken.UserTokenService;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

    // A GOOGLE account has no system password, so a password login can never succeed for it.
    // The message is explicit on purpose: the account exists but authenticates through Google,
    // and telling the user exactly why (instead of a generic "invalid credentials") is the only
    // way out of the confusion. The check runs after the generic inactive-account check and is
    // reached only for accounts that actually exist.
    private static final String GOOGLE_ACCOUNT_MESSAGE =
            "Esta cuenta usa Google para iniciar sesión. Tocá \"Continuar con Google\".";
    private static final String GOOGLE_ACCOUNT_CODE = "GOOGLE_ACCOUNT";

    // Deliberately distinguishes an unverified account from bad credentials. Normally login is
    // anti-enumeration (one generic message) to avoid confirming which emails exist; here the
    // trade-off is inverted: an unverified user is STUCK (they cannot log in, the only way out is
    // the emailed link), so telling them exactly why is worth the small cost of confirming the
    // account exists. The code lets the frontend offer a resend-verification action without
    // parsing human text. The check itself happens only AFTER the password matches, so someone
    // without the password still learns nothing.
    private static final String EMAIL_NOT_VERIFIED_MESSAGE =
            "Tu email todavía no fue verificado. Revisá tu casilla (y la carpeta de spam) o pedí un link nuevo.";
    private static final String EMAIL_NOT_VERIFIED_CODE = "EMAIL_NOT_VERIFIED";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final ProfessionalRepository professionalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserTokenService userTokenService;
    private final EmailService emailService;
    private final Duration resendCooldown;

    public AuthService(UserRepository userRepository,
                        PatientRepository patientRepository,
                        ProfessionalRepository professionalRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        UserTokenService userTokenService,
                        EmailService emailService,
                        @Value("${app.tokens.resend-cooldown}") Duration resendCooldown) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.professionalRepository = professionalRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userTokenService = userTokenService;
        this.emailService = emailService;
        this.resendCooldown = resendCooldown;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email " + request.email() + " is already registered");
        }

        // Role is always forced to PATIENT here, regardless of anything the client might have
        // sent - public registration can only ever create the lowest-privilege role.
        // Public self-registration starts UNVERIFIED: the emailed link is the proof that the
        // person owns the address. (Users created by the admin/system are pre-verified instead.)
        User user = new User(request.email(), passwordEncoder.encode(request.password()), Role.PATIENT, true,
                false, false);
        user = userRepository.save(user);

        Patient patient = new Patient(request.firstName(), request.lastName(), request.phone(), user,
                request.notificationsEnabledOrDefault());
        patient = patientRepository.save(patient);

        // Email send is async and failure-tolerant (see EmailService), so it can never block or
        // fail the registration even if the SMTP provider is down.
        String verificationToken = userTokenService.issue(user, TokenType.EMAIL_VERIFICATION);
        emailService.sendVerificationEmail(user.getEmail(), patient.getFirstName(), verificationToken);

        return new RegisterResponse(user.getId(), user.getEmail(), user.getRole(),
                patient.getFirstName(), patient.getLastName(), patient.getPhone(), patient.isNotificationsEnabled());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));

        if (!user.isActive()) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            throw new UnauthorizedException(GOOGLE_ACCOUNT_MESSAGE, GOOGLE_ACCOUNT_CODE);
        }

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE);
        }

        if (!user.isEmailVerified()) {
            throw new UnauthorizedException(EMAIL_NOT_VERIFIED_MESSAGE, EMAIL_NOT_VERIFIED_CODE);
        }

        String token = jwtService.generateToken(user);
        NameInfo name = resolveName(user);

        return new AuthResponse(token, user.getId(), user.getEmail(), user.getRole(),
                name.firstName(), name.lastName(), user.isMustChangePassword());
    }

    @Transactional(readOnly = true)
    public UserInfoResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));

        NameInfo name = resolveName(user);
        return new UserInfoResponse(user.getId(), user.getEmail(), user.getRole(),
                name.firstName(), name.lastName(), user.isMustChangePassword());
    }

    @Transactional
    public MessageResponse verifyEmail(String rawToken) {
        UserToken userToken = userTokenService.consume(rawToken, TokenType.EMAIL_VERIFICATION);
        User user = userToken.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);
        return new MessageResponse("Tu email fue verificado. Ya podés ingresar a tu cuenta.");
    }

    @Transactional
    public MessageResponse resendVerification(String email) {
        // Anti-enumeration: the endpoint always answers 200 with the same generic message whether
        // the account exists, is verified, is inactive, or is still within the resend cooldown -
        // the response never reveals that, and it never leaks whether an email was actually sent.
        // The cooldown (rate limit) is what keeps an attacker from burning the provider's daily
        // quota by spamming this public endpoint for a real address: internally we skip both the
        // token issuance and the send, but from outside the response looks identical.
        userRepository.findByEmail(email).ifPresent(user -> {
            if (user.isActive() && !user.isEmailVerified()) {
                if (userTokenService.wasIssuedWithin(user, TokenType.EMAIL_VERIFICATION, resendCooldown)) {
                    // The response stays 200 (anti-enumeration), but the cooldown is the reason no
                    // email goes out — log it so "my verification mail didn't arrive" is explainable.
                    log.warn("[mail:verification] resend requested for {} within cooldown {}; suppressing send",
                            EmailMask.mask(email), resendCooldown);
                } else {
                    String token = userTokenService.issue(user, TokenType.EMAIL_VERIFICATION);
                    emailService.sendVerificationEmail(user.getEmail(), resolveName(user).firstName(), token);
                }
            }
        });
        return new MessageResponse(
                "Si ese email está registrado, te enviamos un link de verificación. Revisá también la carpeta de spam.");
    }

    @Transactional
    public MessageResponse forgotPassword(String email) {
        // Anti-enumeration: always answers 200 with the same generic message. The resend cooldown
        // applies here too: one link per user per window, even under automated hammering.
        // GOOGLE accounts are skipped entirely: they have no password to reset, and issuing a link
        // would only produce a "set a password that still won't log in" dead end.
        userRepository.findByEmail(email).ifPresent(user -> {
            // Only active, verified LOCAL accounts get a reset link: sending one to a stray
            // unverified account would be noise, and the verified account is what we're protecting.
            if (user.isActive() && user.isEmailVerified() && user.getAuthProvider() == AuthProvider.LOCAL) {
                if (userTokenService.wasIssuedWithin(user, TokenType.PASSWORD_RESET, resendCooldown)) {
                    log.warn("[mail:password-reset] forgot-password for {} within cooldown {}; suppressing send",
                            EmailMask.mask(email), resendCooldown);
                } else {
                    String token = userTokenService.issue(user, TokenType.PASSWORD_RESET);
                    emailService.sendPasswordResetEmail(user.getEmail(), resolveName(user).firstName(), token);
                }
            }
        });
        return new MessageResponse(
                "Si ese email está registrado, te enviamos un link para restablecer tu contraseña. "
                        + "Revisá también la carpeta de spam.");
    }

    @Transactional
    public MessageResponse resetPassword(String rawToken, String newPassword) {
        UserToken userToken = userTokenService.consume(rawToken, TokenType.PASSWORD_RESET);
        User user = userToken.getUser();
        // Defensive: reset links are never issued to GOOGLE accounts (see forgotPassword), but a
        // token issued before this rule (or a manually created one) must not silently plant a
        // password on an account that authenticates via Google.
        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            throw new UnauthorizedException("Esta cuenta usa Google para iniciar sesión y no tiene contraseña.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        // A professional/admin on a temporary password (mustChangePassword=true) just set a real
        // password themselves — clear the forced-change flag, same rule as changePassword().
        user.setMustChangePassword(false);
        userRepository.save(user);
        return new MessageResponse("Tu contraseña fue actualizada. Ya podés ingresar.");
    }

    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));

        if (user.getAuthProvider() == AuthProvider.GOOGLE) {
            throw new UnauthorizedException("Esta cuenta usa Google para iniciar sesión y no tiene contraseña.");
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new UnauthorizedException("Current password is incorrect");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }

    private NameInfo resolveName(User user) {
        return patientRepository.findByUser(user)
                .map(patient -> new NameInfo(patient.getFirstName(), patient.getLastName()))
                .or(() -> professionalRepository.findByUser(user)
                        .map(professional -> new NameInfo(professional.getFirstName(), professional.getLastName())))
                .orElse(new NameInfo("", ""));
    }

    private record NameInfo(String firstName, String lastName) {
    }
}
