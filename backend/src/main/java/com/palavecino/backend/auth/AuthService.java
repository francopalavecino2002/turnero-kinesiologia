package com.palavecino.backend.auth;

import com.palavecino.backend.auth.dto.AuthResponse;
import com.palavecino.backend.auth.dto.ChangePasswordRequest;
import com.palavecino.backend.auth.dto.LoginRequest;
import com.palavecino.backend.auth.dto.RegisterRequest;
import com.palavecino.backend.auth.dto.RegisterResponse;
import com.palavecino.backend.auth.dto.UserInfoResponse;
import com.palavecino.backend.exception.ConflictException;
import com.palavecino.backend.exception.UnauthorizedException;
import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.patient.PatientRepository;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.security.JwtService;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid email or password";

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final ProfessionalRepository professionalRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                        PatientRepository patientRepository,
                        ProfessionalRepository professionalRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.professionalRepository = professionalRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("Email " + request.email() + " is already registered");
        }

        // Role is always forced to PATIENT here, regardless of anything the client might have
        // sent - public registration can only ever create the lowest-privilege role.
        User user = new User(request.email(), passwordEncoder.encode(request.password()), Role.PATIENT, true);
        user = userRepository.save(user);

        Patient patient = new Patient(request.firstName(), request.lastName(), request.phone(), user, true);
        patient = patientRepository.save(patient);

        return new RegisterResponse(user.getId(), user.getEmail(), user.getRole(),
                patient.getFirstName(), patient.getLastName(), patient.getPhone(), patient.isNotificationsEnabled());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .filter(candidate -> passwordEncoder.matches(request.password(), candidate.getPassword()))
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));

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
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS_MESSAGE));

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
