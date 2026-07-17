package com.palavecino.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.palavecino.backend.user.Role;
import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private Environment environment;

    private AdminBootstrapRunner buildRunner(String email, String password, boolean devProfile) {
        when(environment.getActiveProfiles()).thenReturn(devProfile ? new String[]{"dev"} : new String[]{});
        return new AdminBootstrapRunner(userRepository, passwordEncoder, environment, email, password);
    }

    @Test
    void skipsWhenAdminAlreadyExists() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
                userRepository, passwordEncoder, environment, "admin@prod.com", "Secret123!");
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        runner.run(null);

        verify(userRepository, never()).findByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void devPromotesExistingSeededProfessionalByEmail() {
        AdminBootstrapRunner runner = buildRunner("", "", true);

        User seededPro = new User("marcela.altamirano@equi.dev", "encoded:changeme123",
                Role.PROFESSIONAL, true);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.findByEmail("marcela.altamirano@equi.dev"))
                .thenReturn(Optional.of(seededPro));

        runner.run(null);

        assertThat(seededPro.getRole()).isEqualTo(Role.ADMIN);
        assertThat(seededPro.isMustChangePassword()).isTrue();
        assertThat(seededPro.getPassword()).isEqualTo("encoded:changeme123");
        verify(userRepository).save(seededPro);
    }

    @Test
    void devCreatesAdminWhenNoSeededUserExists() {
        when(passwordEncoder.encode("ChangeMe123!")).thenReturn("encoded:ChangeMe123!");
        AdminBootstrapRunner runner = buildRunner("", "", true);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.findByEmail("marcela.altamirano@equi.dev"))
                .thenReturn(Optional.empty());

        runner.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("marcela.altamirano@equi.dev");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.isMustChangePassword()).isTrue();
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void prodCreatesAdminFromEnvVars() {
        when(passwordEncoder.encode("StrongPass1!")).thenReturn("encoded:StrongPass1!");
        AdminBootstrapRunner runner = buildRunner("admin@clinic.com", "StrongPass1!", false);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.findByEmail("admin@clinic.com")).thenReturn(Optional.empty());

        runner.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("admin@clinic.com");
        assertThat(saved.getPassword()).isEqualTo("encoded:StrongPass1!");
        assertThat(saved.getRole()).isEqualTo(Role.ADMIN);
        assertThat(saved.isMustChangePassword()).isTrue();
    }

    @Test
    void prodPromotesExistingUserByEmail() {
        AdminBootstrapRunner runner = buildRunner("admin@clinic.com", "StrongPass1!", false);

        User existing = new User("admin@clinic.com", "old:pass", Role.PATIENT, true);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.findByEmail("admin@clinic.com")).thenReturn(Optional.of(existing));

        runner.run(null);

        assertThat(existing.getRole()).isEqualTo(Role.ADMIN);
        assertThat(existing.isMustChangePassword()).isTrue();
        assertThat(existing.getPassword()).isEqualTo("old:pass");
        verify(userRepository).save(existing);
    }

    @Test
    void prodFailsHardWhenEnvVarsMissing() {
        AdminBootstrapRunner runner = buildRunner("", "", false);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> runner.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_EMAIL")
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    @Test
    void devWithEnvVarsUsesConfiguredEmail() {
        AdminBootstrapRunner runner = buildRunner("custom-admin@dev.com", "CustomPass1!", true);

        User existing = new User("custom-admin@dev.com", "old", Role.PATIENT, true);
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.findByEmail("custom-admin@dev.com"))
                .thenReturn(Optional.of(existing));

        runner.run(null);

        assertThat(existing.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository).save(existing);
    }
}
