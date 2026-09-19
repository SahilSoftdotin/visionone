package com.visiondigitallab.visionone.common;

/**
 * Money on the wire and in the domain: minor units plus an ISO currency code.
 *
 * <p>The backend never sends a pre-formatted string; the frontend formats for the
 * organization's locale. There is no floating point anywhere in the money path.
 */
public record Money(long amountMinor, String currency) {

    public static Money of(long amountMinor, String currency) {
        return new Money(amountMinor, currency);
    }

    public static Money zero(String currency) {
        return new Money(0L, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amountMinor + other.amountMinor, this.currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(this.amountMinor - other.amountMinor, this.currency);
    }

    /**
     * Cost per unit, rounded half-up to the nearest minor unit.
     *
     * @return null when the divisor is zero - a cost per lead with no leads is not a number,
     *     and the UI renders it as an em dash rather than as zero.
     */
    public Money dividedBy(long divisor) {
        if (divisor == 0L) {
            return null;
        }
        long half = divisor / 2;
        return new Money((amountMinor + half) / divisor, currency);
    }

    private void requireSameCurrency(Money other) {
        if (!this.currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Cannot combine " + this.currency + " with " + other.currency);
        }
    }
}
