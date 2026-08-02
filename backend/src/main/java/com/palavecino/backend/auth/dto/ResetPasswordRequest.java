package com.palavecino.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank String token,
        // Same strength rule as register/change-password (@Size(min = 8)): reusing it here keeps
        // the password policy consistent across the whole system instead of inventing a new one.
        @NotBlank @Size(min = 8) String newPassword) {
}
