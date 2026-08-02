package com.palavecino.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.palavecino.backend.patient.Patient;
import com.palavecino.backend.patient.PatientRepository;
import com.palavecino.backend.user.AuthProvider;
import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

@ExtendWith(MockitoExtension.class)
class GoogleOAuth2UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PatientRepository patientRepository;

    private GoogleOAuth2UserService service;

    @BeforeEach
    void setUp() {
        service = new GoogleOAuth2UserService(userRepository, patientRepository);
    }

    @Test
    void newEmailCreatesGoogleAccountAndPatient() {
        String email = "ana@gmail.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        service.authenticateOrCreate(email, true, "Ana", "Perez");

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo(email);
        assertThat(saved.getPassword()).isNull();
        assertThat(saved.getRole()).isEqualTo(Role.PATIENT);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.isEmailVerified()).isTrue();
        assertThat(saved.isMustChangePassword()).isFalse();
        assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);

        ArgumentCaptor<Patient> patientCaptor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(patientCaptor.capture());
        Patient patient = patientCaptor.getValue();
        assertThat(patient.getFirstName()).isEqualTo("Ana");
        assertThat(patient.getLastName()).isEqualTo("Perez");
        assertThat(patient.getUser()).isEqualTo(saved);
        assertThat(patient.isNotificationsEnabled()).isTrue();
    }

    @Test
    void newEmailWithoutGoogleNamesFallsBackToEmptyStrings() {
        String email = "anon@gmail.com";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        service.authenticateOrCreate(email, true, null, null);

        ArgumentCaptor<Patient> patientCaptor = ArgumentCaptor.forClass(Patient.class);
        verify(patientRepository).save(patientCaptor.capture());
        assertThat(patientCaptor.getValue().getFirstName()).isEmpty();
        assertThat(patientCaptor.getValue().getLastName()).isEmpty();
    }

    @Test
    void existingLocalAccountAuthenticatesWithoutTouchingPasswordOrProvider() {
        User local = new User("local@gmail.com", "encoded:pass", Role.PATIENT, true);
        when(userRepository.findByEmail(local.getEmail())).thenReturn(Optional.of(local));

        service.authenticateOrCreate(local.getEmail(), true, "Ana", "Perez");

        // Authenticate as-is: no writes, password and provider untouched.
        verify(userRepository, never()).save(any());
        verify(patientRepository, never()).save(any());
        assertThat(local.getPassword()).isEqualTo("encoded:pass");
        assertThat(local.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);
        assertThat(local.getRole()).isEqualTo(Role.PATIENT);
    }

    @Test
    void existingGoogleAccountAuthenticatesNormally() {
        User google = new User("google@gmail.com", null, Role.PATIENT, true);
        google.setAuthProvider(AuthProvider.GOOGLE);
        when(userRepository.findByEmail(google.getEmail())).thenReturn(Optional.of(google));

        service.authenticateOrCreate(google.getEmail(), true, "Ana", "Perez");

        verify(userRepository, never()).save(any());
        verify(patientRepository, never()).save(any());
        assertThat(google.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
    }

    @Test
    void unverifiedEmailIsRejected() {
        OAuth2AuthenticationException thrown = catchThrowableOfType(
                () -> service.authenticateOrCreate("duda@gmail.com", false, "Ana", "Perez"),
                OAuth2AuthenticationException.class);
        assertThat(thrown).isNotNull();
        assertThat(thrown.getError().getErrorCode()).isEqualTo("email_not_verified");

        verify(userRepository, never()).save(any());
    }

    @Test
    void missingEmailIsRejected() {
        OAuth2AuthenticationException nullEmail = catchThrowableOfType(
                () -> service.authenticateOrCreate(null, true, "Ana", "Perez"),
                OAuth2AuthenticationException.class);
        assertThat(nullEmail).isNotNull();
        assertThat(nullEmail.getError().getErrorCode()).isEqualTo("invalid_request");

        OAuth2AuthenticationException blankEmail = catchThrowableOfType(
                () -> service.authenticateOrCreate("  ", true, "Ana", "Perez"),
                OAuth2AuthenticationException.class);
        assertThat(blankEmail).isNotNull();
        assertThat(blankEmail.getError().getErrorCode()).isEqualTo("invalid_request");

        verify(userRepository, never()).save(any());
    }

    @Test
    void inactiveExistingAccountIsRejected() {
        User inactive = new User("baja@gmail.com", null, Role.PATIENT, false);
        inactive.setAuthProvider(AuthProvider.GOOGLE);
        when(userRepository.findByEmail(inactive.getEmail())).thenReturn(Optional.of(inactive));

        OAuth2AuthenticationException thrown = catchThrowableOfType(
                () -> service.authenticateOrCreate(inactive.getEmail(), true, "Ana", "Perez"),
                OAuth2AuthenticationException.class);
        assertThat(thrown).isNotNull();
        assertThat(thrown.getError().getErrorCode()).isEqualTo("account_inactive");

        verify(userRepository, never()).save(any());
    }
}
