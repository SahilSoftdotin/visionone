package com.visiondigitallab.visionone.tenant.api;

/**
 * Holds the verified {@link OrganizationContext} for the duration of one request.
 *
 * <p>A thread local rather than a request-scoped bean so that it is reachable from services that
 * are not web-aware, and cleared explicitly by the interceptor that sets it.
 */
public final class OrganizationContextHolder {

    private static final ThreadLocal<OrganizationContext> CURRENT = new ThreadLocal<>();

    private OrganizationContextHolder() {}

    public static void set(OrganizationContext context) {
        CURRENT.set(context);
    }

    public static OrganizationContext require() {
        OrganizationContext context = CURRENT.get();
        if (context == null) {
            throw new IllegalStateException(
                    "No organization context bound to this request. A tenant-scoped read must go "
                            + "through an /api/v1/orgs/{orgId} route, or pass the organization explicitly.");
        }
        return context;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
