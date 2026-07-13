package com.palavecino.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Deliberately has no "role" field. Even if a client sends one in the JSON body, Jackson simply
 * ignores unknown properties - the server always forces PATIENT in AuthService.register(). This
 * is a hard security requirement: public registration must never be able to self-escalate.
 */
public record RegisterRequest(
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String phone) {
}
