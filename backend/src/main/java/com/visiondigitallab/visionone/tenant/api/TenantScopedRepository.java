package com.visiondigitallab.visionone.tenant.api;

/**
 * Marker for a repository over a tenant-owned entity.
 *
 * <p>An ArchUnit test asserts that every query method on an implementation takes an
 * {@code organizationId} argument. That test is the actual enforcement; this interface exists so
 * the rule has something to select on.
 */
public interface TenantScopedRepository {}
