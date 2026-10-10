package com.visiondigitallab.visionone.tenant.web;

import com.visiondigitallab.visionone.auth.CurrentUser;
import com.visiondigitallab.visionone.auth.Role;
import com.visiondigitallab.visionone.tenant.api.OrganizationDirectory;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the frontend needs immediately after login: who the caller is, which organizations they can
 * open, and how long a client may sit idle. Not tenant-scoped, so it sits outside the
 * {@code /orgs/{orgId}} tree.
 */
@RestController
@RequestMapping("/api/v1")
public class SessionController {

    private static final Logger log = LoggerFactory.getLogger(SessionController.class);

    private final CurrentUser currentUser;
    private final OrganizationDirectory directory;
    private final SessionPolicy sessionPolicy;

    public SessionController(
            CurrentUser currentUser,
            OrganizationDirectory directory,
            @Value("${visionone.session.idle-timeout-minutes:15}") int idleTimeoutMinutes,
            @Value("${visionone.session.warning-seconds:60}") int warningSeconds) {
        this.currentUser = currentUser;
        this.directory = directory;
        // Resolved once at startup rather than per request: it cannot change without a restart,
        // and /me is on the critical path of every page load.
        this.sessionPolicy = SessionPolicy.resolve(idleTimeoutMinutes, warningSeconds);
        if (sessionPolicy.idleTimeoutMinutes() != idleTimeoutMinutes
                || sessionPolicy.warningSeconds() != warningSeconds) {
            log.warn(
                    "Session policy {}m/{}s is not usable and was adjusted to {}m/{}s. A timeout of"
                            + " zero or less would sign every client out on arrival.",
                    idleTimeoutMinutes,
                    warningSeconds,
                    sessionPolicy.idleTimeoutMinutes(),
                    sessionPolicy.warningSeconds());
        }
    }

    @GetMapping("/me")
    public SessionResponse me() {
        String subject = currentUser.subject();
        List<OrganizationMembership> orgs = directory.forSubject(subject).stream()
                .map(SessionController::toOrganizationMembership)
                .toList();
        return new SessionResponse(subject, currentUser.displayName(), orgs, sessionPolicy);
    }

    /** Unauthenticated: lets the SPA discover the identity provider before it has a token. */
    @GetMapping("/meta")
    public MetaResponse meta() {
        return new MetaResponse("VisionOne", "v1", "phase-1");
    }

    private static OrganizationMembership toOrganizationMembership(
            OrganizationDirectory.OrganizationMembershipSummary membership) {
        OrganizationDirectory.OrganizationSummary summary = membership.organization();
        return new OrganizationMembership(
                summary.id(), summary.name(), summary.slug(), summary.timezone(),
                summary.currency(), membership.role());
    }

    public record SessionResponse(
            String subject,
            String displayName,
            List<OrganizationMembership> organizations,
            SessionPolicy sessionPolicy) {}

    /**
     * How long a client may sit idle before the browser signs them out, and how much warning it
     * gives first.
     *
     * Sent to every caller rather than only to clients. The role is per organization - the same
     * person could be a client in one and Vision in another - so the server cannot say which rule
     * applies without being told which organization is open. The SPA knows, and applies this only
     * where the role is CLIENT_OWNER.
     *
     * It lives here rather than in the bundle so it can be changed with a restart instead of a
     * rebuild. It is advice to the browser and nothing more: this is a convenience and an honesty
     * measure for an unattended screen, not an access control. The session itself is bounded by
     * Keycloak's ssoSessionMaxLifespan, and the API authenticates every request on its own.
     */
    public record SessionPolicy(int idleTimeoutMinutes, int warningSeconds) {

        private static final int DEFAULT_IDLE_MINUTES = 15;
        private static final int DEFAULT_WARNING_SECONDS = 60;

        /**
         * Refuses a configuration that cannot work rather than passing it on.
         *
         * Zero is the one that matters: it means a deadline of now, so every client is signed out
         * the instant they arrive, over and over, with no obvious cause. A warning longer than the
         * timeout is on screen before anyone has done anything, so it is capped at half the
         * window. Bad values are corrected and logged rather than failing startup - the API has no
         * business refusing to serve a dashboard over a timeout figure.
         */
        public static SessionPolicy resolve(int idleTimeoutMinutes, int warningSeconds) {
            int idle = idleTimeoutMinutes > 0 ? idleTimeoutMinutes : DEFAULT_IDLE_MINUTES;
            int warning = warningSeconds > 0 ? warningSeconds : DEFAULT_WARNING_SECONDS;
            return new SessionPolicy(idle, Math.min(warning, idle * 60 / 2));
        }
    }

    public record OrganizationMembership(
            UUID id, String name, String slug, String timezone, String currency, Role role) {}

    public record MetaResponse(String product, String apiVersion, String phase) {}
}
