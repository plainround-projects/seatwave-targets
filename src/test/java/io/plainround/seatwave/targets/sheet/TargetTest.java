package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class TargetTest {

  private static final TargetScope SCOPE =
      new TargetScope(Operation.HOLD_A_SEAT, QualityAttribute.LATENCY);
  private static final TargetMeasure MEASURE =
      new TargetMeasure("Response time", "p99", "under 500 ms", "Month");
  private static final TargetOwnership OWNERSHIP = new TargetOwnership("Lead", "A reason");

  @Test
  void keepsItsThreeParts() {
    Target target = new Target(SCOPE, MEASURE, OWNERSHIP);

    assertThat(target.scope()).isEqualTo(SCOPE);
    assertThat(target.measure()).isEqualTo(MEASURE);
    assertThat(target.ownership()).isEqualTo(OWNERSHIP);
  }

  @Test
  void rejectsANullPart() {
    assertThatNullPointerException().isThrownBy(() -> new Target(null, MEASURE, OWNERSHIP));
    assertThatNullPointerException().isThrownBy(() -> new Target(SCOPE, null, OWNERSHIP));
    assertThatNullPointerException().isThrownBy(() -> new Target(SCOPE, MEASURE, null));
  }

  @Test
  void rejectsANullOperationOrAttributeInAScope() {
    assertThatNullPointerException()
        .isThrownBy(() -> new TargetScope(null, QualityAttribute.LATENCY));
    assertThatNullPointerException().isThrownBy(() -> new TargetScope(Operation.HOLD_A_SEAT, null));
  }

  @Test
  void treatsTwoScopesWithTheSameOperationAndAttributeAsEqual() {
    assertThat(new TargetScope(Operation.HOLD_A_SEAT, QualityAttribute.LATENCY)).isEqualTo(SCOPE);
  }
}
