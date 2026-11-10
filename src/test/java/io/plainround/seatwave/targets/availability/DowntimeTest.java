package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DowntimeTest {

  private static final double TOLERANCE = 1e-9;
  // Each figure is the unavailable share of 43,200 minutes, worked out by hand.
  private static final double MINUTES_OF_99_9_IN_A_MONTH = 43.2;
  private static final double MINUTES_OF_99_95_IN_A_MONTH = 21.6;
  private static final double MINUTES_OF_99_IN_A_MONTH = 432.0;
  private static final double MINUTES_OF_99_99_IN_A_MONTH = 4.32;
  // 525,600 minutes in a year, times 0.001.
  private static final double MINUTES_OF_99_9_IN_A_YEAR = 525.6;
  // 30 x 24 x 60 and 365 x 24 x 60, as stated in the Javadoc of the window constants.
  private static final long MINUTES_IN_30_DAYS = 43_200L;
  private static final long MINUTES_IN_365_DAYS = 525_600L;

  @Test
  void allows43Point2MinutesAMonthAt99Point9Percent() {
    Duration allowed = Downtime.allowedBy(Topologies.percent("99.9"), Downtime.MONTH_OF_30_DAYS);

    assertThat(Downtime.inMinutes(allowed))
        .isCloseTo(MINUTES_OF_99_9_IN_A_MONTH, offset(TOLERANCE));
  }

  @Test
  void allows525Point6MinutesAYearAt99Point9Percent() {
    Duration allowed = Downtime.allowedBy(Topologies.percent("99.9"), Downtime.YEAR_OF_365_DAYS);

    assertThat(Downtime.inMinutes(allowed)).isCloseTo(MINUTES_OF_99_9_IN_A_YEAR, offset(TOLERANCE));
  }

  @Test
  void allows21Point6MinutesAMonthAt99Point95Percent() {
    Duration allowed = Downtime.allowedBy(Topologies.percent("99.95"), Downtime.MONTH_OF_30_DAYS);

    assertThat(Downtime.inMinutes(allowed))
        .isCloseTo(MINUTES_OF_99_95_IN_A_MONTH, offset(TOLERANCE));
  }

  @Test
  void allows432MinutesAMonthAt99Percent() {
    Duration allowed = Downtime.allowedBy(Topologies.percent("99"), Downtime.MONTH_OF_30_DAYS);

    assertThat(Downtime.inMinutes(allowed)).isCloseTo(MINUTES_OF_99_IN_A_MONTH, offset(TOLERANCE));
  }

  @Test
  void allows4Point32MinutesAMonthAt99Point99Percent() {
    Duration allowed = Downtime.allowedBy(Topologies.percent("99.99"), Downtime.MONTH_OF_30_DAYS);

    assertThat(Downtime.inMinutes(allowed))
        .isCloseTo(MINUTES_OF_99_99_IN_A_MONTH, offset(TOLERANCE));
  }

  @Test
  void allowsNoDowntimeForAnAlwaysUpPart() {
    assertThat(Downtime.allowedBy(Availability.ALWAYS_UP, Downtime.YEAR_OF_365_DAYS)).isZero();
  }

  @Test
  void allowsTheWholeWindowForAnAlwaysDownPart() {
    assertThat(Downtime.allowedBy(Availability.ALWAYS_DOWN, Downtime.MONTH_OF_30_DAYS))
        .isEqualTo(Downtime.MONTH_OF_30_DAYS);
  }

  @Test
  void rejectsAZeroWindow() {
    assertThatThrownBy(() -> Downtime.allowedBy(Availability.ALWAYS_UP, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("longer than zero");
  }

  @Test
  void rejectsANegativeWindow() {
    Duration negativeWindow = Downtime.MONTH_OF_30_DAYS.negated();

    assertThatThrownBy(() -> Downtime.allowedBy(Availability.ALWAYS_UP, negativeWindow))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsNullArguments() {
    assertThatNullPointerException()
        .isThrownBy(() -> Downtime.allowedBy(null, Downtime.MONTH_OF_30_DAYS));
    assertThatNullPointerException()
        .isThrownBy(() -> Downtime.allowedBy(Availability.ALWAYS_UP, null));
    assertThatNullPointerException().isThrownBy(() -> Downtime.inMinutes(null));
  }

  @Test
  void keepsTheNamedWindowsAtTheirDocumentedLength() {
    assertThat(Downtime.MONTH_OF_30_DAYS.toMinutes()).isEqualTo(MINUTES_IN_30_DAYS);
    assertThat(Downtime.YEAR_OF_365_DAYS.toMinutes()).isEqualTo(MINUTES_IN_365_DAYS);
  }
}
