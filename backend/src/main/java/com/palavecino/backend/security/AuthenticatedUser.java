package com.palavecino.backend.security;

import com.palavecino.backend.user.Role;

/**
 * The authenticated caller's identity, resolved once per request from the JWT/SecurityContext.
 * patientId / professionalId are null unless the user's role has a corresponding row - by
 * construction a PATIENT always has a patientId and a PROFESSIONAL always has a professionalId.
 */
public record AuthenticatedUser(Long userId, String email, Role role, Long patientId, Long professionalId) {

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isPatient() {
        return role == Role.PATIENT;
    }

    public boolean isProfessional() {
        return role == Role.PROFESSIONAL;
    }
}
