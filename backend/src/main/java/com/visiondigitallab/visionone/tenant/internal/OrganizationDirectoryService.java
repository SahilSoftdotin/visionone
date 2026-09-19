package com.visiondigitallab.visionone.tenant.internal;

import com.visiondigitallab.visionone.common.NotFoundException;
import com.visiondigitallab.visionone.tenant.api.OrganizationDirectory;
import com.visiondigitallab.visionone.tenant.domain.Membership;
import com.visiondigitallab.visionone.tenant.domain.MembershipStatus;
import com.visiondigitallab.visionone.tenant.domain.Organization;
import com.visiondigitallab.visionone.tenant.repository.MembershipRepository;
import com.visiondigitallab.visionone.tenant.repository.OrganizationRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OrganizationDirectoryService implements OrganizationDirectory {

    private final OrganizationRepository organizations;
    private final MembershipRepository memberships;

    public OrganizationDirectoryService(
            OrganizationRepository organizations, MembershipRepository memberships) {
        this.organizations = organizations;
        this.memberships = memberships;
    }

    @Override
    @Cacheable(cacheNames = "orgConfig", key = "#organizationId")
    public OrganizationSummary require(UUID organizationId) {
        Organization organization = organizations
                .findById(organizationId)
                .orElseThrow(() -> new NotFoundException("Organization", organizationId));
        return toSummary(organization);
    }

    @Override
    @Cacheable(cacheNames = "membership", key = "#keycloakUserId")
    public List<OrganizationSummary> forSubject(String keycloakUserId) {
        List<Membership> active =
                memberships.findByKeycloakUserIdAndStatus(keycloakUserId, MembershipStatus.ACTIVE);
        return active.stream()
                .map(Membership::getOrganizationId)
                .distinct()
                .map(organizations::findById)
                .flatMap(java.util.Optional::stream)
                .map(OrganizationDirectoryService::toSummary)
                .toList();
    }

    private static OrganizationSummary toSummary(Organization organization) {
        return new OrganizationSummary(
                organization.getId(),
                organization.getName(),
                organization.getSlug(),
                organization.getTimezone(),
                organization.getCurrency());
    }
}
