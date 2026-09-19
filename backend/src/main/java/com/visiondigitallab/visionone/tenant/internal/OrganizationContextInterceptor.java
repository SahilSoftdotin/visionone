package com.visiondigitallab.visionone.tenant.internal;

import com.visiondigitallab.visionone.auth.CurrentUser;
import com.visiondigitallab.visionone.common.AccessDeniedInOrganizationException;
import com.visiondigitallab.visionone.common.NotFoundException;
import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import com.visiondigitallab.visionone.tenant.api.OrganizationContextHolder;
import com.visiondigitallab.visionone.tenant.domain.Membership;
import com.visiondigitallab.visionone.tenant.domain.MembershipStatus;
import com.visiondigitallab.visionone.tenant.domain.Organization;
import com.visiondigitallab.visionone.tenant.repository.MembershipRepository;
import com.visiondigitallab.visionone.tenant.repository.OrganizationRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Layer 2 of tenant safety.
 *
 * <p>Resolves {@code {orgId}} from the path and refuses the request unless the caller holds an
 * active membership in it. The organization comes from the URL rather than from a token claim so
 * that the check is explicit and testable, and so a token carrying several organizations cannot
 * silently pick one.
 */
@Component
public class OrganizationContextInterceptor implements HandlerInterceptor {

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;
    private final CurrentUser currentUser;

    public OrganizationContextInterceptor(
            OrganizationRepository organizations, MembershipRepository memberships, CurrentUser currentUser) {
        this.organizations = organizations;
        this.memberships = memberships;
        this.currentUser = currentUser;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UUID organizationId = organizationIdFrom(request);
        if (organizationId == null) {
            return true;
        }

        Organization organization = organizations
                .findById(organizationId)
                .orElseThrow(() -> new AccessDeniedInOrganizationException(organizationId));

        Membership membership = memberships
                .findByOrganizationIdAndKeycloakUserIdAndStatus(
                        organizationId, currentUser.subject(), MembershipStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedInOrganizationException(organizationId));

        OrganizationContextHolder.set(new OrganizationContext(
                organization.getId(),
                organization.getName(),
                organization.zoneId(),
                organization.getCurrency(),
                membership.getRole()));
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        OrganizationContextHolder.clear();
    }

    @SuppressWarnings("unchecked")
    private UUID organizationIdFrom(HttpServletRequest request) {
        Object attribute = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attribute instanceof Map<?, ?> variables)) {
            return null;
        }
        Object raw = ((Map<String, String>) variables).get("orgId");
        if (raw == null) {
            return null;
        }
        try {
            return UUID.fromString(String.valueOf(raw));
        } catch (IllegalArgumentException ex) {
            throw new NotFoundException("Organization", raw);
        }
    }
}
