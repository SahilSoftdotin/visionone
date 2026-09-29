package com.visiondigitallab.visionone.appointment.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The Calendar screen: the month's appointments, grouped by the practice's own day.
 *
 * <p>Grouping happens on the server because "which day is this appointment on" is a question about
 * the practice's timezone, and a browser in another zone would answer it differently.
 */
public record CalendarResponse(
        LocalDate periodMonth,
        String dataSource,
        Summary summary,
        List<Day> days,
        /**
         * Whether the caller may correct marketing attribution: true for Vision, false for the
         * practice. Nothing else on this screen is editable by anyone. Time, duration, status and
         * the label mirror the scheduling system, which owns them, and there is deliberately no
         * endpoint that writes them.
         */
        boolean editable,
        /** The channels attribution may be set to. The same list the rows are named from. */
        List<ChannelOption> channels) {

    /** One option in the attribution picker. */
    public record ChannelOption(UUID id, String name) {}

    public record Summary(long booked, long attended, long cancelled, long noShow, long rescheduled) {}

    public record Day(LocalDate date, List<AppointmentRow> appointments) {}

    public record AppointmentRow(
            UUID id,
            String time,
            String label,
            String serviceCategory,
            /** The channel's display name, or null when unknown. For reading. */
            String source,
            /** The same channel's id, or null. For the attribution picker to preselect. */
            UUID channelSourceId,
            String status,
            int durationMinutes) {}
}
