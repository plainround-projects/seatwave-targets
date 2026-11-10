package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SheetValidatorTest {

  private static final int FIRST_LINE = SheetFixtures.FIRST_LINE;
  private static final int SECOND_LINE = FIRST_LINE + 1;
  private static final int THIRD_LINE = FIRST_LINE + 2;

  @Test
  void turnsACompleteRowIntoOneTargetWithStrippedFields() {
    SheetRow paddedRow =
        new SheetRow(
            FIRST_LINE,
            List.of(
                "  Hold a seat ",
                " Reliability",
                " Seats with two holds ",
                " count ",
                " 0 ",
                " Every night ",
                " Developer ",
                " No double booking "));

    List<Target> targets = SheetFixtures.targetsOf(List.of(paddedRow));

    assertThat(targets).hasSize(1);
    Target target = targets.getFirst();
    assertThat(target.scope().operation()).isEqualTo(Operation.HOLD_A_SEAT);
    assertThat(target.scope().attribute()).isEqualTo(QualityAttribute.RELIABILITY);
    assertThat(target.measure())
        .isEqualTo(new TargetMeasure("Seats with two holds", "count", "0", "Every night"));
    assertThat(target.ownership()).isEqualTo(new TargetOwnership("Developer", "No double booking"));
  }

  @Test
  void rejectsAThresholdWithNoNumber() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(
            List.of(SheetFixtures.rowWith(SheetColumn.THRESHOLD, "fast")));

    assertThat(rejection.problems()).containsExactly(new RowProblem.ThresholdWithoutNumber("fast"));
  }

  @Test
  void reportsAMissingPercentileAndAMissingWindowForTheSameRow() {
    List<String> cells = SheetFixtures.cellsWith(SheetColumn.PERCENTILE, "");
    cells.set(SheetColumn.WINDOW.ordinal(), "");

    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(List.of(new SheetRow(FIRST_LINE, cells)));

    assertThat(rejection.problems())
        .containsExactly(
            new RowProblem.MissingField(SheetColumn.PERCENTILE),
            new RowProblem.MissingField(SheetColumn.WINDOW));
  }

  @Test
  void rejectsAnUnknownPercentileWord() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(
            List.of(SheetFixtures.rowWith(SheetColumn.PERCENTILE, "average")));

    assertThat(rejection.problems())
        .containsExactly(new RowProblem.NotAllowed(SheetColumn.PERCENTILE, "average"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"p50", "p95", "p99", "p99.9", "P99", "rate", "count", "peak", "Mean"})
  void acceptsPercentilesAndRateKinds(String percentileOrRate) {
    assertThat(
            SheetFixtures.targetsOf(
                List.of(SheetFixtures.rowWith(SheetColumn.PERCENTILE, percentileOrRate))))
        .hasSize(1);
  }

  @ParameterizedTest
  @ValueSource(strings = {"p", "p100x", "p999", "99", "pp99", "p99.", "median"})
  void rejectsMalformedPercentiles(String percentileOrRate) {
    assertThat(
            SheetFixtures.onlyRejectionOf(
                    List.of(SheetFixtures.rowWith(SheetColumn.PERCENTILE, percentileOrRate)))
                .problems())
        .containsExactly(new RowProblem.NotAllowed(SheetColumn.PERCENTILE, percentileOrRate));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"TBD", "tbd", "n/a", "N/A", "?", "-", "none", "unknown", "todo", "na", "", "   "})
  void countsPlaceholdersAndBlanksAsAMissingOwner(String placeholder) {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(
            List.of(SheetFixtures.rowWith(SheetColumn.OWNER, placeholder)));

    assertThat(rejection.problems())
        .containsExactly(new RowProblem.MissingField(SheetColumn.OWNER));
  }

  @Test
  void countsAPlaceholderReasonAsAMissingReason() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(List.of(SheetFixtures.rowWith(SheetColumn.REASON, "TBD")));

    assertThat(rejection.problems())
        .containsExactly(new RowProblem.MissingField(SheetColumn.REASON));
  }

  @Test
  void rejectsAnUnknownOperation() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(
            List.of(SheetFixtures.rowWith(SheetColumn.OPERATION, "Delete an event")));

    assertThat(rejection.problems())
        .containsExactly(new RowProblem.NotAllowed(SheetColumn.OPERATION, "Delete an event"));
  }

  @Test
  void rejectsAnUnknownAttribute() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(
            List.of(SheetFixtures.rowWith(SheetColumn.ATTRIBUTE, "Beauty")));

    assertThat(rejection.problems())
        .containsExactly(new RowProblem.NotAllowed(SheetColumn.ATTRIBUTE, "Beauty"));
  }

  @Test
  void treatsAnEmptyOperationAsMissingInsteadOfNotAllowed() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(List.of(SheetFixtures.rowWith(SheetColumn.OPERATION, "")));

    assertThat(rejection.problems())
        .containsExactly(new RowProblem.MissingField(SheetColumn.OPERATION));
  }

  @Test
  void rejectsARowWithSevenCells() {
    List<String> sevenCells = SheetFixtures.completeCells().subList(0, 7);

    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(List.of(new SheetRow(FIRST_LINE, sevenCells)));

    assertThat(rejection.problems()).containsExactly(new RowProblem.WrongCellCount(8, 7));
    assertThat(rejection.lineNumber()).isEqualTo(FIRST_LINE);
  }

  @Test
  void rejectsARowWithNineCells() {
    List<String> nineCells = new ArrayList<>(SheetFixtures.completeCells());
    nineCells.add("extra");

    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(List.of(new SheetRow(FIRST_LINE, nineCells)));

    assertThat(rejection.problems()).containsExactly(new RowProblem.WrongCellCount(8, 9));
  }

  @Test
  void rejectsARowWithNoCells() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(List.of(new SheetRow(FIRST_LINE, List.of())));

    assertThat(rejection.problems()).containsExactly(new RowProblem.WrongCellCount(8, 0));
  }

  @Test
  void rejectsASecondRowWithTheSameMeasureAndNamesTheFirstLine() {
    SheetRow first = SheetFixtures.rowWith(FIRST_LINE, SheetColumn.THRESHOLD, "under 500 ms");
    SheetRow second = SheetFixtures.rowWith(SECOND_LINE, SheetColumn.THRESHOLD, "under 900 ms");

    SheetError error = SheetFixtures.errorOf(List.of(first, second));

    assertThat(error).isInstanceOf(SheetError.RowsRejected.class);
    SheetError.RowsRejected rejected = (SheetError.RowsRejected) error;
    assertThat(rejected.acceptedCount()).isEqualTo(1);
    assertThat(rejected.rejections()).hasSize(1);
    assertThat(rejected.rejections().getFirst().lineNumber()).isEqualTo(SECOND_LINE);
    assertThat(rejected.rejections().getFirst().problems())
        .containsExactly(new RowProblem.DuplicateOfLine(FIRST_LINE));
  }

  @Test
  void ignoresCaseAndSpacesWhenLookingForADuplicateMeasure() {
    SheetRow first = SheetFixtures.completeRow();
    SheetRow shouting =
        new SheetRow(
            SECOND_LINE,
            List.of(
                "LIST EVENTS",
                "LATENCY",
                "RESPONSE TIME AT THE LOAD BALANCER",
                "P99",
                "under 900 ms",
                "SALE WINDOW, 10:00 TO 10:15",
                "Platform lead",
                "A different reason"));

    assertThat(SheetFixtures.onlyRejectionOf(List.of(first, shouting)).problems())
        .containsExactly(new RowProblem.DuplicateOfLine(FIRST_LINE));
  }

  @Test
  void acceptsTwoRowsThatDifferInTheWindow() {
    SheetRow first = SheetFixtures.completeRow();
    SheetRow otherWindow = SheetFixtures.rowWith(SECOND_LINE, SheetColumn.WINDOW, "Whole month");

    assertThat(SheetFixtures.targetsOf(List.of(first, otherWindow))).hasSize(2);
  }

  @Test
  void acceptsTwoRowsThatDifferInTheOperation() {
    SheetRow first = SheetFixtures.completeRow();
    SheetRow otherOperation =
        SheetFixtures.rowWith(SECOND_LINE, SheetColumn.OPERATION, "View seats");

    assertThat(SheetFixtures.targetsOf(List.of(first, otherOperation))).hasSize(2);
  }

  @Test
  void namesTheFirstLineForEveryLaterCopy() {
    SheetRow first = SheetFixtures.completeRow();
    SheetRow second = SheetFixtures.rowWith(SECOND_LINE, SheetColumn.REASON, "Second copy");
    SheetRow third = SheetFixtures.rowWith(THIRD_LINE, SheetColumn.REASON, "Third copy");

    SheetError.RowsRejected rejected =
        (SheetError.RowsRejected) SheetFixtures.errorOf(List.of(first, second, third));

    assertThat(rejected.rejections())
        .extracting(rejection -> rejection.problems().getFirst())
        .containsExactly(
            new RowProblem.DuplicateOfLine(FIRST_LINE), new RowProblem.DuplicateOfLine(FIRST_LINE));
  }

  @Test
  void rejectsTheWholeSheetWhenOneRowIsBadAndCountsTheGoodRows() {
    SheetRow good = SheetFixtures.completeRow();
    SheetRow otherGood = SheetFixtures.rowWith(SECOND_LINE, SheetColumn.WINDOW, "Whole month");
    SheetRow bad = SheetFixtures.rowWith(THIRD_LINE, SheetColumn.OWNER, "TBD");

    SheetError error = SheetFixtures.errorOf(List.of(good, bad, otherGood));

    assertThat(error).isInstanceOf(SheetError.RowsRejected.class);
    assertThat(((SheetError.RowsRejected) error).acceptedCount()).isEqualTo(2);
  }

  @Test
  void rejectsAnEmptyRowList() {
    assertThat(SheetFixtures.errorOf(List.of())).isEqualTo(new SheetError.NoTargets());
  }

  @Test
  void labelsARejectionWithTheOperationAndTheAttribute() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(List.of(SheetFixtures.rowWith(SheetColumn.OWNER, "")));

    assertThat(rejection.rowLabel()).isEqualTo("List events, Latency");
  }

  @Test
  void rejectsNullRows() {
    assertThatNullPointerException().isThrownBy(() -> SheetValidator.validate(null));
  }
}
