package com.palavecino.backend.security;

import com.palavecino.backend.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads the "Authorization: Bearer <token>" header, validates it, and populates the
 * SecurityContext. A missing or invalid token never throws here - it simply leaves the request
 * unauthenticated so it falls through to the authorization layer, which decides whether the
 * endpoint requires authentication.
 *
 * If the token is structurally valid but the user account has been deactivated (active=false),
 * the request is explicitly rejected with 401 so that JWTs issued before deactivation are
 * immediately invalidated.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService,
                                    ObjectMapper objectMapper) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            String token = header.substring(7);

            Optional<String> emailOpt = jwtService.extractEmail(token);
            if (emailOpt.isPresent() && jwtService.isTokenValid(token)) {
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(emailOpt.get());
                    if (!userDetails.isEnabled()) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        ErrorResponse body = ErrorResponse.of(HttpStatus.UNAUTHORIZED.value(),
                                "Cuenta desactivada. Contactá al administrador del consultorio.");
                        objectMapper.writeValue(response.getWriter(), body);
                        return;
                    }
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } catch (UsernameNotFoundException ignored) {
                    // Token references a user that no longer exists; continue unauthenticated.
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
