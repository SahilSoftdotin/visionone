package com.visiondigitallab.visionone.common;

import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/**
 * The {@code ?month=YYYY-MM} query parameter, resolved once.
 *
 * <p>Every month-scoped screen takes this parameter and every one of them defaults it the same way:
 * to the current month <em>in the organization's timezone</em>. That default is the part worth
 * having in one place - a server in UTC and a practice in New York disagree about which month it is
 * for several hours at every month boundary, and a copy of this logic that forgot the zone would be
 * wrong only occasionally, which is the hardest kind of wrong to notice.
 *
 * <p>The zone is a parameter rather than read from the request context because {@code common} is
 * the one package every module depends on, so it must depend on none of them. The build enforces
 * that: reaching into {@code tenant} from here is a module cycle.
 */
public final class RequestedMonth {

    private RequestedMonth() {}

    public static YearMonth parse(String raw, ZoneId organizationZone) {
        if (raw == null || raw.isBlank()) {
            return YearMonth.now(organizationZone);
        }
        try {
            return YearMonth.parse(raw);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("month must be in YYYY-MM format, received: " + raw);
        }
    }
}
