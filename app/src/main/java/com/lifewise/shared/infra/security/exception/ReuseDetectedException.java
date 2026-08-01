package com.lifewise.shared.infra.security.exception;

/**
 * Refresh-token reuse detected (parent token already rotated). All tokens in the family must be revoked.
 *
 * <p>Maps to HTTP 401 with {@code REFRESH_REUSE_DETECTED} per plan-shared-infra §2.1.
 */
public class ReuseDetectedException extends RuntimeException {

    private final String familyId;

    public ReuseDetectedException(String familyId) {
        super("refresh token reuse detected for family=" + familyId);
        this.familyId = familyId;
    }

    public String familyId() {
        return familyId;
    }
}