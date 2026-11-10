package io.plainround.seatwave.targets.sheet;

import com.github.sviperll.result4j.Result;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Turns the rows of a sheet into targets, or says exactly which rows are incomplete.
 *
 * <p>A sheet is accepted only when every row is a complete target. Stateless and safe to call from
 * any thread.
 */
public final class SheetValidator {

  private SheetValidator() {}

  /**
   * Validates every row.
   *
   * @param rows the data rows of the sheet, in file order
   * @return the targets when every row is complete; otherwise the rejected rows with their reasons
   * @throws NullPointerException if the list is null
   */
  public static Result<List<Target>, SheetError> validate(List<SheetRow> rows) {
    Objects.requireNonNull(rows, "rows");
    if (rows.isEmpty()) {
      return Result.error(new SheetError.NoTargets());
    }
    List<Target> targets = new ArrayList<>();
    List<RowRejection> rejections = new ArrayList<>();
    Map<MeasureKey, Integer> firstLineOfMeasure = new HashMap<>();
    for (SheetRow row : rows) {
      switch (toTarget(row).flatMap(target -> rejectDuplicate(row, target, firstLineOfMeasure))) {
        case Result.Success<Target, RowRejection>(Target target) -> targets.add(target);
        case Result.Error<Target, RowRejection>(RowRejection rejection) ->
            rejections.add(rejection);
      }
    }
    // Fail safe: one incomplete row rejects the whole sheet, so the design does not start on it.
    return rejections.isEmpty()
        ? Result.success(List.copyOf(targets))
        : Result.error(new SheetError.RowsRejected(targets.size(), rejections));
  }

  private static Result<Target, RowRejection> toTarget(SheetRow row) {
    if (!row.hasEveryColumn()) {
      RowProblem wrongCount =
          new RowProblem.WrongCellCount(SheetColumn.values().length, row.cells().size());
      return Result.error(new RowRejection(row.lineNumber(), "row", List.of(wrongCount)));
    }
    List<RowProblem> problems = new ArrayList<>();
    Optional<Operation> operation = Operation.fromLabel(row.cell(SheetColumn.OPERATION));
    if (operation.isEmpty()) {
      problems.add(notAllowedOrMissing(row, SheetColumn.OPERATION));
    }
    Optional<QualityAttribute> attribute =
        QualityAttribute.fromLabel(row.cell(SheetColumn.ATTRIBUTE));
    if (attribute.isEmpty()) {
      problems.add(notAllowedOrMissing(row, SheetColumn.ATTRIBUTE));
    }
    problems.addAll(measureProblems(row));
    problems.addAll(
        TargetOwnership.problemsIn(row.cell(SheetColumn.OWNER), row.cell(SheetColumn.REASON)));
    if (!problems.isEmpty()) {
      return Result.error(new RowRejection(row.lineNumber(), labelOf(row), problems));
    }
    return Result.success(
        new Target(
            new TargetScope(operation.orElseThrow(), attribute.orElseThrow()),
            measureOf(row),
            new TargetOwnership(row.cell(SheetColumn.OWNER), row.cell(SheetColumn.REASON))));
  }

  private static Result<Target, RowRejection> rejectDuplicate(
      SheetRow row, Target target, Map<MeasureKey, Integer> firstLineOfMeasure) {
    Integer firstLine = firstLineOfMeasure.putIfAbsent(MeasureKey.of(target), row.lineNumber());
    if (firstLine == null) {
      return Result.success(target);
    }
    // Two thresholds for one measure cannot both be the target, so the later row is refused.
    RowProblem duplicate = new RowProblem.DuplicateOfLine(firstLine);
    return Result.error(new RowRejection(row.lineNumber(), labelOf(row), List.of(duplicate)));
  }

  private static RowProblem notAllowedOrMissing(SheetRow row, SheetColumn column) {
    String cellText = row.cell(column);
    return CellText.isMissing(cellText)
        ? new RowProblem.MissingField(column)
        : new RowProblem.NotAllowed(column, cellText);
  }

  private static List<RowProblem> measureProblems(SheetRow row) {
    return TargetMeasure.problemsIn(
        row.cell(SheetColumn.METRIC),
        row.cell(SheetColumn.PERCENTILE),
        row.cell(SheetColumn.THRESHOLD),
        row.cell(SheetColumn.WINDOW));
  }

  private static TargetMeasure measureOf(SheetRow row) {
    return new TargetMeasure(
        row.cell(SheetColumn.METRIC),
        row.cell(SheetColumn.PERCENTILE),
        row.cell(SheetColumn.THRESHOLD),
        row.cell(SheetColumn.WINDOW));
  }

  private static String labelOf(SheetRow row) {
    return row.cell(SheetColumn.OPERATION) + ", " + row.cell(SheetColumn.ATTRIBUTE);
  }

  /** What makes two rows targets for the same measure, whatever their thresholds say. */
  private record MeasureKey(TargetScope scope, String metric, String percentile, String window) {

    static MeasureKey of(Target target) {
      TargetMeasure measure = target.measure();
      return new MeasureKey(
          target.scope(),
          measure.metric().toLowerCase(Locale.ROOT),
          measure.percentileOrRate().toLowerCase(Locale.ROOT),
          measure.window().toLowerCase(Locale.ROOT));
    }
  }
}
