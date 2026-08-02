package com.palavecino.backend.exception;

/**
 * @param code optional machine-readable discriminator for cases where the client must tell two
 *             401s apart (e.g. "EMAIL_NOT_VERIFIED" vs invalid credentials); null for plain 401s.
 */
public class UnauthorizedException extends RuntimeException {

    private final String code;

    public UnauthorizedException(String message) {
        this(message, null);
    }

    public UnauthorizedException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
