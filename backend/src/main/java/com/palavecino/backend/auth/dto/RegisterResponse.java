package com.palavecino.backend.auth.dto;

import com.palavecino.backend.user.Role;

public record RegisterResponse(
        Long id,
        String email,
        Role role,
        String firstName,
        String lastName,
        String phone,
        boolean notificationsEnabled) {
}
