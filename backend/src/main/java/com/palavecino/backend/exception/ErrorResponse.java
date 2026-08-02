package com.palavecino.backend.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

public record ErrorResponse(int status, String message, Instant timestamp,
                            @JsonInclude(JsonInclude.Include.NON_NULL) String code) {

    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(status, message, Instant.now(), null);
    }

    public static ErrorResponse ofWithCode(int status, String message, String code) {
        return new ErrorResponse(status, message, Instant.now(), code);
    }
}
