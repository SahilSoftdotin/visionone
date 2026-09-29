package com.visiondigitallab.visionone.appointment.internal;

import com.visiondigitallab.visionone.appointment.api.CalendarResponse;
import com.visiondigitallab.visionone.appointment.domain.AppointmentRef;
import com.visiondigitallab.visionone.appointment.domain.AppointmentState;
import com.visiondigitallab.visionone.appointment.repository.AppointmentRefRepository;
import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import com.visiondigitallab.visionone.common.NotFoundException;
import com.visiondigitallab.visionone.integration.api.SchedulingProvider;
import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the Calendar from stored appointment references.
 *
 * <p>Like the Front Desk, this never calls the scheduling provider on a request thread. Healthie
 * being slow, rate-limited or down must not make the practice's own calendar slow or empty, and the
 * provider's contract says the same.
 */
@Service
@Transactional(readOnly = true)
public class CalendarQueryService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AppointmentRefRepository appointments;
    private final SchedulingProvider schedulingProvider;
    private final JdbcClient jdbc;

    public CalendarQueryService(
            AppointmentRefRepository appointments,
            SchedulingProvider schedulingProvider,
            JdbcClient jdbc) {
        this.appointments = appointments;
        this.schedulingProvider = schedulingProvider;
        this.jdbc = jdbc;
    }

    public CalendarResponse read(OrganizationContext context, YearMonth month) {
        ZoneId zone = context.zoneId();
        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();

        List<AppointmentRef> period = appointments
                .findByOrganizationIdAndScheduledForGreaterThanEqualAndScheduledForLessThanOrderByScheduledForAsc(
                        context.organizationId(), from, to);

        Map<UUID, String> channelNames = channelNames(context.organizationId());

        // LinkedHashMap keeps the days in order, which the repository already returned them in.
        Map<LocalDate, List<CalendarResponse.AppointmentRow>> byDay = new LinkedHashMap<>();
        for (AppointmentRef appointment : period) {
            LocalDate day = appointment.getScheduledFor().atZone(zone).toLocalDate();
            byDay.computeIfAbsent(day, key -> new ArrayList<>())
                    .add(toRow(appointment, zone, channelNames));
        }

        List<CalendarResponse.Day> days = byDay.entrySet().stream()
                .map(entry -> new CalendarResponse.Day(entry.getKey(), entry.getValue()))
                .toList();

        return new CalendarResponse(
                month.atDay(1),
                describeSource(context),
                summarise(period),
                days,
                context.isVisionAdmin(),
                channelNames.entrySet().stream()
                        .map(entry -> new CalendarResponse.ChannelOption(entry.getKey(), entry.getValue()))
                        .toList());
    }

    private String describeSource(OrganizationContext context) {
        ProviderStatus status = schedulingProvider.status(context.organizationId());
        return switch (status) {
            case CONNECTED -> "LIVE";
            case ERROR -> "ERROR";
            case NEEDS_AUTHORIZATION -> "NEEDS_AUTHORIZATION";
            case NOT_CONNECTED -> "NOT_CONNECTED";
            case DEMO -> "DEMO";
        };
    }

    /**
     * Credit an appointment to a marketing channel, or to none.
     *
     * <p>The only write on this screen. Time, duration, status and label belong to the scheduling
     * system and there is no endpoint that changes them: VisionOne showing "cancelled" while
     * Healthie still holds the booking would be a fact about a patient's Tuesday, not a stale cache.
     *
     * <p>When a Healthie sync is added it must fill {@code channel_source_id} only where it is
     * null. A correction made here outranks a guess from a feed. The cost of that rule is that a
     * sync-set value is never refreshed either, which is the right way round.
     */
    @Transactional
    public CalendarResponse attribute(
            OrganizationContext context, YearMonth month, UUID appointmentId, UUID channelSourceId) {
        AppointmentRef appointment = appointments
                .findByIdAndOrganizationId(appointmentId, context.organizationId())
                .orElseThrow(() -> new NotFoundException("No such appointment"));

        // The interceptor proved the caller belongs to this organization; it cannot know the
        // channel does. Without this, one practice could credit another's channel.
        if (channelSourceId != null && !channelNames(context.organizationId()).containsKey(channelSourceId)) {
            throw new NotFoundException("No such channel for this organization");
        }

        appointment.attributeTo(channelSourceId);
        return read(context, month);
    }

    private Map<UUID, String> channelNames(UUID organizationId) {
        Map<UUID, String> names = new LinkedHashMap<>();
        jdbc.sql("select id, display_name from channel_source where organization_id = :orgId")
                .param("orgId", organizationId)
                .query((rs, rowNum) -> Map.entry(rs.getObject("id", UUID.class), rs.getString("display_name")))
                .list()
                .forEach(entry -> names.put(entry.getKey(), entry.getValue()));
        return names;
    }

    private static CalendarResponse.Summary summarise(List<AppointmentRef> period) {
        return new CalendarResponse.Summary(
                count(period, AppointmentState.BOOKED),
                count(period, AppointmentState.ATTENDED),
                count(period, AppointmentState.CANCELLED),
                count(period, AppointmentState.NO_SHOW),
                count(period, AppointmentState.RESCHEDULED));
    }

    private static long count(List<AppointmentRef> items, AppointmentState state) {
        return items.stream().filter(item -> item.getStatus() == state).count();
    }

    private static CalendarResponse.AppointmentRow toRow(
            AppointmentRef appointment, ZoneId zone, Map<UUID, String> channelNames) {
        return new CalendarResponse.AppointmentRow(
                appointment.getId(),
                appointment.getScheduledFor().atZone(zone).format(TIME),
                appointment.getDisplayLabel() == null ? "Appointment" : appointment.getDisplayLabel(),
                appointment.getServiceCategory(),
                // Null rather than "Direct" when the channel is unknown: a guess here would credit
                // a channel that earned nothing.
                appointment.getChannelSourceId() == null
                        ? null
                        : channelNames.get(appointment.getChannelSourceId()),
                appointment.getChannelSourceId(),
                appointment.getStatus().name(),
                appointment.getDurationMinutes());
    }
}
