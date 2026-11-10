package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.fail;

import com.github.sviperll.result4j.Result;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SheetReportTest {

  private static final String REJECTED_FOOTER =
      "SHEET REJECTED: fix every row above before the design starts.\n";
  private static final String HEADER_LIST =
      "[Operation, Attribute, Metric, Percentile, Threshold, Window, Owner, Reason]";

  @Test
  void reportsTheNumberOfAcceptedTargetsOfTheArenaSheet() throws IOException {
    List<Target> targets =
        switch (SheetFixtures.validatedSheet("arena-on-sale.md")) {
          case Result.Success<List<Target>, SheetError>(List<Target> accepted) -> accepted;
          case Result.Error<List<Target>, SheetError>(SheetError error) ->
              fail("rejected " + error);
        };

    assertThat(SheetReport.accepted(targets))
        .isEqualTo(
            "Targets accepted: 24\n"
                + "SHEET ACCEPTED: every target has a metric, a percentile or rate, a threshold,"
                + " a window, an owner and a reason.\n");
  }

  @Test
  void reportsEveryRejectedRowOfTheLabDraft() throws IOException {
    SheetError error =
        switch (SheetFixtures.validatedSheet("lab1-draft.md")) {
          case Result.Success<List<Target>, SheetError>(List<Target> targets) ->
              fail("accepted " + targets);
          case Result.Error<List<Target>, SheetError>(SheetError rejection) -> rejection;
        };

    assertThat(SheetReport.rejected(error))
        .isEqualTo(
            """
            Targets accepted: 0
            REJECTED line 5 (List events, Latency): percentile is missing
            REJECTED line 6 (View seats, Latency): window is missing
            REJECTED line 7 (Hold a seat, Latency): percentile is missing; threshold has no number; window is missing; owner is missing; reason is missing
            REJECTED line 8 (Confirm a booking, Availability): threshold has no number; owner is missing
            SHEET REJECTED: fix every row above before the design starts.
            """);
  }

  @Test
  void reportsAMissingTable() {
    assertThat(SheetReport.rejected(new SheetError.NoTable()))
        .isEqualTo(
            "REJECTED no table found: the sheet needs a Markdown table with the header "
                + HEADER_LIST
                + "\n"
                + REJECTED_FOOTER);
  }

  @Test
  void reportsAWrongHeaderWithItsLine() {
    assertThat(SheetReport.rejected(new SheetError.WrongHeader(2)))
        .isEqualTo("REJECTED line 2: the header must be " + HEADER_LIST + "\n" + REJECTED_FOOTER);
  }

  @Test
  void reportsATableWithNoTargets() {
    assertThat(SheetReport.rejected(new SheetError.NoTargets()))
        .isEqualTo("REJECTED the table has a header but no targets\n" + REJECTED_FOOTER);
  }

  @Test
  void explainsAWrongCellCount() {
    SheetError error = rejectedWith(new RowProblem.WrongCellCount(8, 7));

    assertThat(SheetReport.rejected(error)).contains("(row): expected 8 cells but found 7");
  }

  @Test
  void explainsADuplicateByNamingTheFirstLine() {
    SheetError error = rejectedWith(new RowProblem.DuplicateOfLine(5));

    assertThat(SheetReport.rejected(error))
        .contains("line 5 already sets a target for this measure");
  }

  @Test
  void explainsAnOperationThatIsNotAllowed() {
    SheetError error = rejectedWith(new RowProblem.NotAllowed(SheetColumn.OPERATION, "Pay"));

    assertThat(SheetReport.rejected(error))
        .contains(
            "operation 'Pay' is not allowed: use one of the four operations or 'All operations'");
  }

  @Test
  void explainsAnAttributeThatIsNotAllowed() {
    SheetError error = rejectedWith(new RowProblem.NotAllowed(SheetColumn.ATTRIBUTE, "Beauty"));

    assertThat(SheetReport.rejected(error))
        .contains("attribute 'Beauty' is not allowed: use a known quality attribute");
  }

  @Test
  void explainsAPercentileThatIsNotAllowedAndListsTheRateKinds() {
    SheetError error = rejectedWith(new RowProblem.NotAllowed(SheetColumn.PERCENTILE, "average"));

    assertThat(SheetReport.rejected(error))
        .contains("percentile 'average' is not allowed: use p50, p95, p99 or one of")
        .contains(TargetMeasure.rateKinds().toString());
  }

  @Test
  void explainsAColumnWithAnyOtherValueAsAValue() {
    SheetError error = rejectedWith(new RowProblem.NotAllowed(SheetColumn.WINDOW, "x"));

    assertThat(SheetReport.rejected(error)).contains("window 'x' is not allowed: use a value");
  }

  @Test
  void explainsAThresholdWithoutANumber() {
    SheetError error = rejectedWith(new RowProblem.ThresholdWithoutNumber("fast"));

    assertThat(SheetReport.rejected(error)).contains("threshold has no number");
  }

  @Test
  void countsAnEmptyListOfAcceptedTargets() {
    assertThat(SheetReport.accepted(List.of())).startsWith("Targets accepted: 0\n");
  }

  @Test
  void rejectsNullArguments() {
    assertThatNullPointerException().isThrownBy(() -> SheetReport.accepted(null));
    assertThatNullPointerException().isThrownBy(() -> SheetReport.rejected(null));
  }

  private static SheetError rejectedWith(RowProblem problem) {
    return new SheetError.RowsRejected(0, List.of(new RowRejection(1, "row", List.of(problem))));
  }
}
