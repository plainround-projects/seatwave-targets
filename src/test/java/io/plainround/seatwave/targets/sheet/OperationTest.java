package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class OperationTest {

  @ParameterizedTest
  @EnumSource(Operation.class)
  void findsEveryOperationByItsOwnLabel(Operation operation) {
    assertThat(Operation.fromLabel(operation.label())).contains(operation);
  }

  @Test
  void ignoresCase() {
    assertThat(Operation.fromLabel("hOLD a SEAT")).contains(Operation.HOLD_A_SEAT);
  }

  @Test
  void ignoresSurroundingSpaces() {
    assertThat(Operation.fromLabel("  Confirm a booking\t")).contains(Operation.CONFIRM_A_BOOKING);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "Hold", "Hold  a seat", "Hold a seat now", "Pay"})
  void returnsEmptyForAnUnknownLabel(String unknownLabel) {
    assertThat(Operation.fromLabel(unknownLabel)).isEmpty();
  }

  @Test
  void rejectsNullText() {
    assertThatNullPointerException().isThrownBy(() -> Operation.fromLabel(null));
  }
}
