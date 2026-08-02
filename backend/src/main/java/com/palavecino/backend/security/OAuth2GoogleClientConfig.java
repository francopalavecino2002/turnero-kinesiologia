package com.palavecino.backend.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;

/**
 * Builds the Google {@link ClientRegistration} programmatically so the feature can be switched
 * on/off with an env var instead of forcing {@code GOOGLE_CLIENT_ID}/{@code GOOGLE_CLIENT_SECRET}
 * to exist in every environment (tests, local dev without Google). Credentials are never
 * hardcoded: they come from environment variables, and startup fails fast with a clear message
 * if the feature is enabled but the credentials are missing.
 *
 * <p>The standard Spring Security OAuth2 login endpoints ({@code /oauth2/authorization/google}
 * and {@code /login/oauth2/code/google}) are provided by {@code oauth2Login()} on the security
 * filter chain, which is wired conditionally in {@link SecurityConfig}.
 */
@Configuration
public class OAuth2GoogleClientConfig {

    @Bean
    @ConditionalOnProperty(name = "app.oauth2.google.enabled", havingValue = "true")
    public ClientRegistrationRepository googleClientRegistrationRepository(
            @Value("${GOOGLE_CLIENT_ID:}") String clientId,
            @Value("${GOOGLE_CLIENT_SECRET:}") String clientSecret) {
        if (clientId.isBlank() || clientSecret.isBlank()) {
            throw new IllegalStateException(
                    "GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET must be set when GOOGLE_OAUTH_ENABLED=true. "
                            + "Refusing to start rather than boot a broken OAuth2 client.");
        }

        ClientRegistration google = ClientRegistration.withRegistrationId("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/google")
                .scope(OidcScopes.OPENID, OidcScopes.EMAIL, OidcScopes.PROFILE)
                // Explicit Google endpoints (instead of issuerUri) so the app never performs a
                // lazy network fetch of the provider metadata on the first login.
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .userInfoUri("https://www.googleapis.com/oauth2/v3/userinfo")
                .userNameAttributeName("sub")
                .build();

        return new InMemoryClientRegistrationRepository(google);
    }
}
