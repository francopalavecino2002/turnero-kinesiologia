package com.palavecino.backend.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Sends the browser back to the frontend with a machine-readable error code whenever the Google
 * OAuth2 login fails (user cancelled at Google, unverified email, invalid state, etc.) so the
 * SPA can show a clear message instead of the raw authorization screen.
 */
@Component
public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

    private static final String FRONTEND_CALLBACK_PATH = "/oauth2/callback";
    private static final String DEFAULT_ERROR_CODE = "login_failed";

    private final String frontendBaseUrl;

    public OAuth2AuthenticationFailureHandler(@Value("${app.base-url}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String errorCode = DEFAULT_ERROR_CODE;
        if (exception instanceof OAuth2AuthenticationException oauth2Exception
                && oauth2Exception.getError() != null
                && oauth2Exception.getError().getErrorCode() != null) {
            errorCode = oauth2Exception.getError().getErrorCode();
        }

        String encoded = URLEncoder.encode(errorCode, StandardCharsets.UTF_8);
        response.sendRedirect(response.encodeRedirectURL(frontendBaseUrl + FRONTEND_CALLBACK_PATH + "?error=" + encoded));
    }
}
