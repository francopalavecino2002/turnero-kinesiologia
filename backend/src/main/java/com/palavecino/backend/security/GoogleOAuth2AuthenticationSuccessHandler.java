package com.palavecino.backend.security;

import com.palavecino.backend.user.User;
import com.palavecino.backend.user.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Runs after a successful Google OAuth2 login: mints a system JWT (same format and signing key
 * as the normal email/password login in {@code AuthService}) and sends the browser back to the
 * Angular frontend at {@code /oauth2/callback?token=...}. The frontend stores the token exactly
 * like a normal login and resolves the rest of the profile from {@code /api/auth/me}.
 */
@Component
public class GoogleOAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private static final String FRONTEND_CALLBACK_PATH = "/oauth2/callback";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final String frontendBaseUrl;

    public GoogleOAuth2AuthenticationSuccessHandler(JwtService jwtService,
                                                    UserRepository userRepository,
                                                    @Value("${app.base-url}") String frontendBaseUrl) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        String email = resolveEmail(authentication);
        if (email == null) {
            response.sendRedirect(response.encodeRedirectURL(
                    frontendBaseUrl + FRONTEND_CALLBACK_PATH + "?error=login_failed"));
            return;
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !user.isActive()) {
            response.sendRedirect(response.encodeRedirectURL(
                    frontendBaseUrl + FRONTEND_CALLBACK_PATH + "?error=login_failed"));
            return;
        }

        String token = jwtService.generateToken(user);
        response.sendRedirect(response.encodeRedirectURL(
                frontendBaseUrl + FRONTEND_CALLBACK_PATH + "?token=" + token));
    }

    private String resolveEmail(Authentication authentication) {
        if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
            return oidcUser.getClaimAsString("email");
        }
        return authentication.getName();
    }
}
