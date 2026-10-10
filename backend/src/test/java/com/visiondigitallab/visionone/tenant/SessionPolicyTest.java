package com.visiondigitallab.visionone.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.visiondigitallab.visionone.tenant.web.SessionController.SessionPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The inactivity policy the API hands the browser.
 *
 * It moved here from a Vite build argument so it could be changed with a restart instead of a
 * rebuild. That makes it easier to change and therefore easier to get wrong, which is the whole
 * reason these exist: the configuration is now a line in a .env file that nobody rebuilds or
 * reviews, and a bad value reaches clients on the next restart.
 */
class SessionPolicyTest {

    @Test
    @DisplayName("passes through a usable configuration")
    void usableConfiguration() {
        SessionPolicy policy = SessionPolicy.resolve(20, 90);

        assertThat(policy.idleTimeoutMinutes()).isEqualTo(20);
        assertThat(policy.warningSeconds()).isEqualTo(90);
    }

    @Test
    @DisplayName("a timeout of zero is corrected, not served")
    void zeroTimeoutIsCorrected() {
        // The one that matters. Zero means a deadline of now, so every client is signed out the
        // instant they arrive, over and over, with nothing on screen explaining why.
        assertThat(SessionPolicy.resolve(0, 60).idleTimeoutMinutes()).isEqualTo(15);
        assertThat(SessionPolicy.resolve(-5, 60).idleTimeoutMinutes()).isEqualTo(15);
    }

    @Test
    @DisplayName("a missing or negative warning falls back rather than disappearing")
    void warningFallsBack() {
        assertThat(SessionPolicy.resolve(15, 0).warningSeconds()).isEqualTo(60);
        assertThat(SessionPolicy.resolve(15, -1).warningSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("the warning can never cover more than half the window")
    void warningIsCappedAtHalfTheWindow() {
        // Otherwise it is on screen from the moment the page loads, telling someone they are about
        // to be signed out before they have done anything.
        SessionPolicy policy = SessionPolicy.resolve(2, 600);

        assertThat(policy.idleTimeoutMinutes()).isEqualTo(2);
        assertThat(policy.warningSeconds()).isEqualTo(60);
    }

    @Test
    @DisplayName("both wrong at once still yields a working policy")
    void bothWrong() {
        assertThat(SessionPolicy.resolve(0, 0)).isEqualTo(new SessionPolicy(15, 60));
    }
}
