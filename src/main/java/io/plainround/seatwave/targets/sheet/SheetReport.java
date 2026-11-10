package io.plainround.seatwave.targets.sheet;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Writes the verdict on a sheet as text for the command line.
 *
 * <p>Stateless and safe to call from any thread.
 */
public final class SheetReport {

  private SheetReport() {}

  /**
   * Renders the verdict for an accepted sheet.
   *
   * @param targets the complete targets of the sheet
   * @return two lines of text that end with a line break
   * @throws NullPointerException if the list is null
   */
  public static String accepted(List<Target> targets) {
    Objects.requireNonNull(targets, "targets");
    return "Targets accepted: "
        + targets.size()
        + "\nSHEET ACCEPTED: every target has a metric, a percentile or rate, a threshold,"
        + " a window, an owner and a reason.\n";
  }

  /**
   * Renders the verdict for a rejected sheet.
   *
   * @param error the reason the sheet is rejected
   * @return one line per problem, then the verdict, ending with a line break
   * @throws NullPointerException if the error is null
   */
  public static String rejected(SheetError error) {
    Objects.requireNonNull(error, "error");
    String detail =
        switch (error) {
          case SheetError.NoTable _ ->
              "REJECTED no table found: the sheet needs a Markdown table with the header "
                  + SheetColumn.headings()
                  + "\n";
          case SheetError.WrongHeader wrongHeader ->
              "REJECTED line "
                  + wrongHeader.lineNumber()
                  + ": the header must be "
                  + SheetColumn.headings()
                  + "\n";
          case SheetError.NoTargets _ -> "REJECTED the table has a header but no targets\n";
          case SheetError.RowsRejected rowsRejected -> rejectedRows(rowsRejected);
        };
    return detail + "SHEET REJECTED: fix every row above before the design starts.\n";
  }

  private static String rejectedRows(SheetError.RowsRejected rowsRejected) {
    StringBuilder text = new StringBuilder();
    text.append("Targets accepted: ").append(rowsRejected.acceptedCount()).append('\n');
    for (RowRejection rejection : rowsRejected.rejections()) {
      text.append("REJECTED line ")
          .append(rejection.lineNumber())
          .append(" (")
          .append(rejection.rowLabel())
          .append("): ")
          .append(
              String.join("; ", rejection.problems().stream().map(SheetReport::explain).toList()))
          .append('\n');
    }
    return text.toString();
  }

  private static String explain(RowProblem problem) {
    return switch (problem) {
      case RowProblem.WrongCellCount wrongCount ->
          "expected " + wrongCount.expected() + " cells but found " + wrongCount.found();
      case RowProblem.MissingField missing ->
          missing.column().heading().toLowerCase(Locale.ROOT) + " is missing";
      case RowProblem.NotAllowed notAllowed -> explainNotAllowed(notAllowed);
      case RowProblem.ThresholdWithoutNumber _ -> "threshold has no number";
      case RowProblem.DuplicateOfLine duplicate ->
          "line " + duplicate.firstLineNumber() + " already sets a target for this measure";
    };
  }

  private static String explainNotAllowed(RowProblem.NotAllowed notAllowed) {
    String allowed =
        switch (notAllowed.column()) {
          case OPERATION -> "one of the four operations or 'All operations'";
          case ATTRIBUTE -> "a known quality attribute";
          case PERCENTILE -> "p50, p95, p99 or one of " + TargetMeasure.rateKinds();
          case METRIC, THRESHOLD, WINDOW, OWNER, REASON -> "a value";
        };
    return notAllowed.column().heading().toLowerCase(Locale.ROOT)
        + " '"
        + notAllowed.found()
        + "' is not allowed: use "
        + allowed;
  }
}
