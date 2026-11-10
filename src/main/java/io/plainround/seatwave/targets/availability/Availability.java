package io.plainround.seatwave.targets.availability;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The share of a window in which a part or a whole system works, as a fraction from 0 to 1.
 *
 * <p>A fraction of 0.999 means 99.9 percent. The constructor rejects every value outside 0 to 1,
 * and NaN, so an availability outside that range cannot exist anywhere in the program. Instances
 * are immutable and safe to share between threads.
 *
 * @param fraction the share of the window that is good, from 0 to 1 inclusive
 */
public record Availability(double fraction) {

  /** A part that never works. */
  public static final Availability ALWAYS_DOWN = new Availability(0.0);

  /** A part that never fails. */
  public static final Availability ALWAYS_UP = new Availability(1.0);

  // A percent is a fraction with the decimal point moved two places: 99.9 percent is 0.999.
  private static final int PERCENT_SHIFT = 2;
  private static final BigDecimal HIGHEST_PERCENT = BigDecimal.ONE.movePointRight(PERCENT_SHIFT);
  // Six decimals show a pair of 99.95 percent parts as 99.999975 instead of rounding it to 100.
  private static final int PERCENT_DECIMALS = 6;

  /**
   * Creates an availability.
   *
   * @throws IllegalArgumentException if the fraction is below 0, above 1 or not a number
   */
  public Availability {
    // Written as a negated range test so that NaN, which fails every comparison, is rejected too.
    if (!(fraction >= 0.0 && fraction <= 1.0)) {
      throw new IllegalArgumentException(
          "an availability must be between 0 and 1, but was " + fraction);
    }
  }

  /**
   * Tells whether a percent can be turned into an availability.
   *
   * @param percent the percent to check, for example 99.9
   * @return true when the percent is from 0 to 100 inclusive
   */
  public static boolean isPercent(BigDecimal percent) {
    Objects.requireNonNull(percent, "percent");
    return percent.signum() >= 0 && percent.compareTo(HIGHEST_PERCENT) <= 0;
  }

  /**
   * Converts a percent into an availability.
   *
   * @param percent a percent from 0 to 100, for example 99.9
   * @return the same availability as a fraction, for example 0.999
   * @throws IllegalArgumentException if the percent is outside 0 to 100
   */
  public static Availability ofPercent(BigDecimal percent) {
    if (!isPercent(percent)) {
      throw new IllegalArgumentException("a percent must be between 0 and 100, but was " + percent);
    }
    // The point moves in decimal arithmetic, so 99.9 becomes exactly 0.999 before it is a double.
    return new Availability(percent.movePointLeft(PERCENT_SHIFT).doubleValue());
  }

  /**
   * Combines two parts in a chain, where a request needs both.
   *
   * @param other the next part of the chain
   * @return the share of the window in which both parts work, assuming they fail independently
   */
  public Availability bothUp(Availability other) {
    Objects.requireNonNull(other, "other");
    return new Availability(fraction * other.fraction);
  }

  /**
   * Combines two copies in parallel, where a request needs only one of them.
   *
   * @param other the other copy
   * @return the share of the window in which at least one copy works, assuming they fail
   *     independently
   */
  public Availability eitherUp(Availability other) {
    Objects.requireNonNull(other, "other");
    return new Availability(1.0 - unavailability() * other.unavailability());
  }

  /**
   * Returns the share of the window in which the part does not work.
   *
   * @return 1 minus the fraction
   */
  public double unavailability() {
    return 1.0 - fraction;
  }

  /**
   * Tells whether this availability is lower than another.
   *
   * @param other the availability to compare with
   * @return true when this fraction is strictly smaller
   */
  public boolean isLowerThan(Availability other) {
    Objects.requireNonNull(other, "other");
    return fraction < other.fraction;
  }

  /**
   * Tells whether this availability reaches a target.
   *
   * @param target the availability that must be reached
   * @return true when this fraction is at least the target's
   */
  public boolean meets(Availability target) {
    Objects.requireNonNull(target, "target");
    return fraction >= target.fraction;
  }

  /**
   * Formats the availability as a percent for people to read.
   *
   * @return for example "99.7003 %", with at most six decimals and no trailing zeros
   */
  public String asPercent() {
    // BigDecimal.valueOf uses the shortest decimal text of the double, so 0.999 prints as 99.9.
    BigDecimal percent =
        BigDecimal.valueOf(fraction)
            .movePointRight(PERCENT_SHIFT)
            .setScale(PERCENT_DECIMALS, RoundingMode.HALF_UP)
            .stripTrailingZeros();
    return percent.toPlainString() + " %";
  }
}
