package com.visiondigitallab.visionone.growth.api;

import com.visiondigitallab.visionone.common.Money;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The growth module's read surface: what was planned, what was spent. */
public interface GrowthFinance {

    Optional<Investment> investmentFor(UUID organizationId, LocalDate periodMonth);

    List<ChannelSpend> spendByChannel(UUID organizationId, LocalDate periodMonth);

    record Investment(Money planned, Money actual) {

        public Money remaining() {
            return planned.minus(actual);
        }

        /** Percentage of the planned budget spent, to one decimal place. */
        public double utilizationPercent() {
            if (planned.amountMinor() == 0L) {
                return 0.0d;
            }
            return Math.round(actual.amountMinor() * 1000.0d / planned.amountMinor()) / 10.0d;
        }
    }

    record ChannelSpend(String channelCode, String displayName, Money planned, Money actual) {}
}
