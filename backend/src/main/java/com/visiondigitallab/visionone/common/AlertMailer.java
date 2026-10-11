package com.visiondigitallab.visionone.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sends operational email to whoever runs VisionOne. Never to a patient, and never to a client.
 *
 * <p><b>What this is not for.</b> Healthie already emails patients about their own appointments,
 * and Keycloak sends password resets through its own SMTP configuration. A second sender for
 * either would mean a patient receiving two notices for one booking, with VisionOne as the less
 * well-informed of the two. This is for one thing: telling a human that something which runs
 * unattended has stopped working.
 *
 * <p><b>Optional by construction.</b> {@code JavaMailSender} only exists when {@code spring.mail.host}
 * is configured, so it is injected through an {@link ObjectProvider} and absence is a supported
 * state rather than a startup failure. Unconfigured, this logs at warn and returns - a development
 * machine should not need an SMTP server, and a missing one must never stop a sync.
 *
 * <p><b>It cannot throw.</b> Every caller is on a scheduler, reporting that something else already
 * went wrong. An alert that fails by raising an exception would replace a recoverable upstream
 * problem with an unrecoverable local one, and would do it inside the error path where it is least
 * likely to be noticed.
 *
 * <p><b>Nothing here may carry patient data.</b> Alert bodies are assembled from counts, statuses
 * and adapter messages, and adapter messages have their credentials stripped before they are
 * raised. That is a property of the callers rather than something this class can enforce, so it is
 * stated where the sending happens: do not pass anything into {@code subject} or {@code body} that
 * came out of a patient record.
 */
@Component
public class AlertMailer {

    private static final Logger log = LoggerFactory.getLogger(AlertMailer.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String to;
    private final String from;

    public AlertMailer(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${visionone.alerts.to:}") String to,
            @Value("${visionone.alerts.from:alerts@visiondigitallab.com}") String from) {
        this.mailSender = mailSender;
        this.to = to == null ? "" : to.trim();
        this.from = from;
    }

    /** Whether an alert would actually reach anyone, so a caller can avoid bookkeeping that cannot pay off. */
    public boolean enabled() {
        return !to.isEmpty() && mailSender.getIfAvailable() != null;
    }

    /**
     * @return true if the message was handed to the mail server, false if it was only logged
     */
    public boolean send(String subject, String body) {
        // Logged either way and at warn, because the thing being reported is a fault whether or
        // not email is configured. On a machine with no SMTP this log line is the alert.
        log.warn("ALERT: {} - {}", subject, body.replace('\n', ' '));

        JavaMailSender sender = mailSender.getIfAvailable();
        if (to.isEmpty() || sender == null) {
            return false;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to.split("\\s*,\\s*"));
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            return true;
        } catch (RuntimeException e) {
            // Deliberately swallowed. See the class comment: this runs inside somebody else's
            // error path, and an SMTP outage must not escalate into a failed sync.
            log.warn("Could not send alert email: {}", e.getMessage());
            return false;
        }
    }
}
