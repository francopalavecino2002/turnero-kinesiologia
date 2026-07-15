package com.palavecino.backend.security;

import com.palavecino.backend.exception.UnauthorizedException;
import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.patient.PatientRepository;
import com.palavecino.backend.professional.Professional;
import com.palavecino.backend.professional.ProfessionalRepository;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserResolver {

    private final UserRepository userRepository;
    private final PatientRepository patientRepository;
    private final ProfessionalRepository professionalRepository;

    public AuthenticatedUserResolver(UserRepository userRepository,
                                      PatientRepository patientRepository,
                                      ProfessionalRepository professionalRepository) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.professionalRepository = professionalRepository;
    }

    public AuthenticatedUser resolve(Authentication authentication) {
        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        Long patientId = patientRepository.findByUser(user).map(Patient::getId).orElse(null);
        Long professionalId = professionalRepository.findByUser(user).map(Professional::getId).orElse(null);

        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getRole(), patientId, professionalId);
    }
}
