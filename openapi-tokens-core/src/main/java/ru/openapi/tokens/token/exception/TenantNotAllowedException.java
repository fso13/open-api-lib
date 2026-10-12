package ru.openapi.tokens.token.exception;

/**
 * Raised when a client tries to create a token for a tenant it is not allowed to use.
 */
public class TenantNotAllowedException extends RuntimeException {

    private final String requestedTenantId;
    private final String ownerTenantId;

    public TenantNotAllowedException(String requestedTenantId, String ownerTenantId) {
        super("Tenant override is not allowed: requested '%s', owner tenant '%s'"
                .formatted(requestedTenantId, ownerTenantId));
        this.requestedTenantId = requestedTenantId;
        this.ownerTenantId = ownerTenantId;
    }

    public String getRequestedTenantId() {
        return requestedTenantId;
    }

    public String getOwnerTenantId() {
        return ownerTenantId;
    }
}
