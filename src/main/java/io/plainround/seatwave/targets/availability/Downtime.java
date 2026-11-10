package io.plainround.seatwave.targets.availability;

import java.time.Duration;
import java.util.Objects;

/**
 * Converts an availability into the downtime it allows in a window.
 *
 * <p>Stateless and safe to call from any thread.
 */
public final class Downtime {

  // The assumptions behind every "per month" and "per year" figure in the course.
  private static final int DAYS_IN_MONTH = 30;
  private static final int DAYS_IN_YEAR = 365;

  /** A month of 30 days, which is 43,200 minutes. */
  public static final Duration MONTH_OF_30_DAYS = Duration.ofDays(DAYS_IN_MONTH);

  /** A year of 365 days, which is 525,600 minutes. */
  public static final Duration YEAR_OF_365_DAYS = Duration.ofDays(DAYS_IN_YEAR);

  private static final double MILLIS_PER_MINUTE = Duration.ofMinutes(1).toMillis();

  private Downtime() {}

  /**
   * Computes the downtime budget: the part of a window in which the system may be down.
   *
   * @param availability the availability of the system, or its target
   * @param window the length of the measurement window
   * @return the unavailable share of the window, rounded to the millisecond
   * @throws NullPointerException if an argument is null
   * @throws IllegalArgumentException if the window is zero or negative
   */
  public static Duration allowedBy(Availability availability, Duration window) {
    Objects.requireNonNull(availability, "availability");
    Objects.requireNonNull(window, "window");
    if (window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("a window must be longer than zero, but was " + window);
    }
    return Duration.ofMillis(Math.round(window.toMillis() * availability.unavailability()));
  }

  /**
   * Expresses a duration in minutes with a fractional part, for reports.
   *
   * @param duration the duration to convert
   * @return the duration in minutes, for example 43.2
   * @throws NullPointerException if the duration is null
   */
  public static double inMinutes(Duration duration) {
    Objects.requireNonNull(duration, "duration");
    return duration.toMillis() / MILLIS_PER_MINUTE;
  }
}
