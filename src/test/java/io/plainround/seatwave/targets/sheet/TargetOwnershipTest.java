package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TargetOwnershipTest {

  @Test
  void stripsTheOwnerAndTheReason() {
    TargetOwnership ownership = new TargetOwnership(" Lead ", " A reason ");

    assertThat(ownership.owner()).isEqualTo("Lead");
    assertThat(ownership.reason()).isEqualTo("A reason");
  }

  @Test
  void cannotBeBuiltWithoutAnOwner() {
    assertThatThrownBy(() -> new TargetOwnership("  ", "A reason"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MissingField[column=OWNER]");
  }

  @Test
  void cannotBeBuiltWithAPlaceholderOwner() {
    assertThatThrownBy(() -> new TargetOwnership("TBD", "A reason"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void cannotBeBuiltWithoutAReason() {
    assertThatThrownBy(() -> new TargetOwnership("Lead", ""))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MissingField[column=REASON]");
  }

  @Test
  void rejectsNullFields() {
    assertThatNullPointerException().isThrownBy(() -> new TargetOwnership(null, "A reason"));
    assertThatNullPointerException().isThrownBy(() -> new TargetOwnership("Lead", null));
  }

  @Test
  void reportsBothMissingFieldsAtOnce() {
    assertThat(TargetOwnership.problemsIn("?", "n/a"))
        .containsExactly(
            new RowProblem.MissingField(SheetColumn.OWNER),
            new RowProblem.MissingField(SheetColumn.REASON));
  }

  @Test
  void reportsNoProblemsForACompleteOwnership() {
    assertThat(TargetOwnership.problemsIn("Lead", "A reason")).isEmpty();
  }
}
