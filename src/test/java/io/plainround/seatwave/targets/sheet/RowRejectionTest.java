package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RowRejectionTest {

  @Test
  void needsAtLeastOneProblem() {
    assertThatThrownBy(() -> new RowRejection(1, "row", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsANullLabelAndNullProblems() {
    assertThatNullPointerException()
        .isThrownBy(() ->
            new RowRejection(
                1, null, List.of(new RowProblem.NotAllowed(SheetColumn.OPERATION, "x"))));
    assertThatNullPointerException().isThrownBy(() -> new RowRejection(1, "row", null));
  }

  @Test
  void copiesTheProblemsSoLaterChangesDoNotReachTheRejection() {
    List<RowProblem> problems =
        new ArrayList<>(List.of(new RowProblem.MissingField(SheetColumn.OWNER)));
    RowRejection rejection = new RowRejection(1, "row", problems);

    problems.clear();

    assertThat(rejection.problems()).hasSize(1);
  }

  @Test
  void copiesTheRejectionsOfARejectedSheet() {
    List<RowRejection> rejections =
        new ArrayList<>(
            List.of(
                new RowRejection(
                    1, "row", List.of(new RowProblem.MissingField(SheetColumn.OWNER)))));
    SheetError.RowsRejected rejected = new SheetError.RowsRejected(0, rejections);

    rejections.clear();

    assertThat(rejected.rejections()).hasSize(1);
  }

  @Test
  void rejectsNullRejections() {
    assertThatNullPointerException().isThrownBy(() -> new SheetError.RowsRejected(0, null));
  }
}
