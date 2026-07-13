package com.palavecino.backend.auth.dto;

import com.palavecino.backend.user.Role;

public record UserInfoResponse(
        Long id,
        String email,
        Role role,
        String firstName,
        String lastName,
        boolean mustChangePassword) {
}
