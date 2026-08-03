package com.palavecino.backend.security;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Authentication (who are you) is enforced here at the filter-chain level: deny-by-default via
 * anyRequest().authenticated(), with a short explicit allow-list of genuinely public endpoints.
 * Authorization (what are you allowed to do) is enforced with @PreAuthorize on controller methods
 * for role checks, and with explicit ownership checks inside the service layer for per-resource
 * IDOR protection (a role check alone can't know whether appointment #42 belongs to the caller).
 *
 * CSRF is disabled because CSRF protection defends session-cookie-based auth, where a browser
 * automatically attaches credentials to any request. This API is stateless and JWT-based: the
 * token must be attached explicitly by the client on every request, so there's no ambient
 * credential for a forged cross-site request to ride on.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                           JsonAuthenticationEntryPoint authenticationEntryPoint,
                           JsonAccessDeniedHandler accessDeniedHandler,
                           CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository,
                                                   GoogleOAuth2UserService googleOAuth2UserService,
                                                   GoogleOAuth2AuthenticationSuccessHandler googleOAuth2SuccessHandler,
                                                   OAuth2AuthenticationFailureHandler oauth2FailureHandler) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/api/auth/register", "/api/auth/login",
                                "/api/auth/verify-email", "/api/auth/resend-verification",
                                "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .requestMatchers("/oauth2/authorization/google", "/login/oauth2/code/google").permitAll()
                        .requestMatchers("/api/appointments/available-slots").permitAll()
                        // Public catalog + health endpoints are allowed for GET and HEAD alike: a HEAD is
                        // semantically a GET without a response body, so whatever is public to GET must not
                        // 401 on HEAD (uptime monitors use HEAD to stay light).
                        .requestMatchers(HttpMethod.GET, "/api/services/**", "/api/professionals/**").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/services/**", "/api/professionals/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers(HttpMethod.HEAD, "/api/health").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        // Google OAuth2 login is only wired when GOOGLE_OAUTH_ENABLED=true (and the credential
        // beans exist). When it is off, /oauth2/** stays unauthenticated and unreachable, which
        // keeps tests and credential-less local dev booting fine.
        ClientRegistrationRepository repository = clientRegistrationRepository.getIfAvailable();
        if (repository != null) {
            http.oauth2Login(oauth2 -> oauth2
                    .clientRegistrationRepository(repository)
                    .userInfoEndpoint(userInfo -> userInfo.oidcUserService(googleOAuth2UserService))
                    .successHandler(googleOAuth2SuccessHandler)
                    .failureHandler(oauth2FailureHandler));
        }

        return http.build();
    }
}
