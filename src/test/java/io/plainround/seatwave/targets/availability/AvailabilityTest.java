package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AvailabilityTest {

  // Two doubles that differ only by rounding are equal within this distance.
  private static final double TOLERANCE = 1e-12;
  // The fraction of 99.9 percent, written by hand as the expected value.
  private static final double FRACTION_OF_99_9_PERCENT = 0.999;

  @ParameterizedTest
  @ValueSource(
      doubles = {
        -0.1,
        -Double.MIN_VALUE,
        1.1,
        1.0000000000000002,
        Double.MAX_VALUE,
        Double.NaN,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY
      })
  void rejectsAFractionOutsideZeroToOne(double fraction) {
    assertThatThrownBy(() -> new Availability(fraction))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 0 and 1");
  }

  @Test
  void acceptsAFractionOfExactlyZero() {
    assertThat(new Availability(0.0).fraction()).isZero();
  }

  @Test
  void acceptsAFractionOfExactlyOne() {
    assertThat(new Availability(1.0).fraction()).isEqualTo(1.0);
  }

  @Test
  void convertsPercentToFractionWithoutRoundingError() {
    Availability availability = Availability.ofPercent(new BigDecimal("99.9"));

    assertThat(availability.fraction()).isEqualTo(FRACTION_OF_99_9_PERCENT);
  }

  @ParameterizedTest
  @ValueSource(strings = {"-0.1", "100.1", "-1", "1000", "100.000001"})
  void rejectsAPercentOutsideZeroTo100(String percentText) {
    BigDecimal percent = new BigDecimal(percentText);

    assertThatThrownBy(() -> Availability.ofPercent(percent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 0 and 100");
  }

  @Test
  void convertsThePercentBoundariesToTheFractionBoundaries() {
    assertThat(Availability.ofPercent(BigDecimal.ZERO)).isEqualTo(Availability.ALWAYS_DOWN);
    assertThat(Availability.ofPercent(new BigDecimal("100"))).isEqualTo(Availability.ALWAYS_UP);
  }

  @ParameterizedTest
  @ValueSource(strings = {"0", "0.0", "50", "99.9", "100", "100.000"})
  void acceptsPercentsInsideTheRangeIncludingBothEnds(String percentText) {
    assertThat(Availability.isPercent(new BigDecimal(percentText))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {"-0.000001", "-1", "100.000001", "101"})
  void refusesPercentsJustOutsideTheRange(String percentText) {
    assertThat(Availability.isPercent(new BigDecimal(percentText))).isFalse();
  }

  @Test
  void rejectsANullPercent() {
    assertThatNullPointerException().isThrownBy(() -> Availability.isPercent(null));
    assertThatNullPointerException().isThrownBy(() -> Availability.ofPercent(null));
  }

  @Test
  void printsAvailabilityWithoutTrailingZeros() {
    assertThat(Topologies.percent("99.9").asPercent()).isEqualTo("99.9 %");
  }

  @Test
  void printsAvailabilityWithFourDecimalsWhenNeeded() {
    assertThat(new Availability(0.997002999).asPercent()).isEqualTo("99.7003 %");
  }

  @Test
  void printsSixDecimalsWhenTheyAreNeeded() {
    assertThat(new Availability(0.99999975).asPercent()).isEqualTo("99.999975 %");
  }

  @Test
  void printsAlwaysUpAsOneHundredPercent() {
    assertThat(Availability.ALWAYS_UP.asPercent()).isEqualTo("100 %");
  }

  @Test
  void printsAlwaysDownAsZeroPercent() {
    assertThat(Availability.ALWAYS_DOWN.asPercent()).isEqualTo("0 %");
  }

  @Test
  void multipliesFractionsWhenBothPartsMustWork() {
    Availability bothUp = Topologies.percent("50").bothUp(Topologies.percent("90"));

    // 0.5 x 0.9 is 0.45, worked out by hand.
    assertThat(bothUp.fraction()).isCloseTo(0.45, offset(TOLERANCE));
  }

  @Test
  void bothUpWithAlwaysDownGivesAlwaysDown() {
    assertThat(Topologies.percent("99.9").bothUp(Availability.ALWAYS_DOWN))
        .isEqualTo(Availability.ALWAYS_DOWN);
  }

  @Test
  void bothUpWithAlwaysUpChangesNothing() {
    Availability part = Topologies.percent("99.9");

    assertThat(part.bothUp(Availability.ALWAYS_UP)).isEqualTo(part);
  }

  @Test
  void combinesUnavailabilityWhenEitherCopyMayServe() {
    Availability eitherUp = Topologies.percent("50").eitherUp(Topologies.percent("90"));

    // 1 - (0.5 x 0.1) is 0.95, worked out by hand.
    assertThat(eitherUp.fraction()).isCloseTo(0.95, offset(TOLERANCE));
  }

  @Test
  void eitherUpWithAlwaysUpGivesAlwaysUp() {
    assertThat(Topologies.percent("1").eitherUp(Availability.ALWAYS_UP))
        .isEqualTo(Availability.ALWAYS_UP);
  }

  @Test
  void unavailabilityIsOneMinusTheFraction() {
    assertThat(Topologies.percent("99.9").unavailability())
        .isCloseTo(1.0 - FRACTION_OF_99_9_PERCENT, offset(TOLERANCE));
  }

  @Test
  void isLowerThanIsStrict() {
    Availability lower = Topologies.percent("99");
    Availability higher = Topologies.percent("99.9");

    assertThat(lower.isLowerThan(higher)).isTrue();
    assertThat(higher.isLowerThan(lower)).isFalse();
    assertThat(lower.isLowerThan(lower)).isFalse();
  }

  @Test
  void meetsATargetThatEqualsOrIsBelowIt() {
    Availability target = Topologies.percent("99.9");

    assertThat(target.meets(target)).isTrue();
    assertThat(Topologies.percent("99.95").meets(target)).isTrue();
    assertThat(Topologies.percent("99.89").meets(target)).isFalse();
  }

  @Test
  void rejectsANullOtherAvailability() {
    Availability availability = Topologies.percent("99");

    assertThatNullPointerException().isThrownBy(() -> availability.bothUp(null));
    assertThatNullPointerException().isThrownBy(() -> availability.eitherUp(null));
    assertThatNullPointerException().isThrownBy(() -> availability.isLowerThan(null));
    assertThatNullPointerException().isThrownBy(() -> availability.meets(null));
  }
}
