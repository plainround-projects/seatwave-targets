package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.fail;

import com.github.sviperll.result4j.Result;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Builds rows and opens validation results for the sheet tests. */
final class SheetFixtures {

  static final int FIRST_LINE = 5;

  private SheetFixtures() {}

  /** Reads a shipped sheet; Gradle runs tests from the project directory. */
  static List<String> linesOfSheet(String fileName) throws IOException {
    return Files.readAllLines(Path.of("sheets", fileName), StandardCharsets.UTF_8);
  }

  /** Reads and validates a shipped sheet in one step. */
  static Result<List<Target>, SheetError> validatedSheet(String fileName) throws IOException {
    return MarkdownSheetReader.read(linesOfSheet(fileName)).flatMap(SheetValidator::validate);
  }

  static List<String> completeCells() {
    return List.of(
        "List events",
        "Latency",
        "Response time at the load balancer",
        "p99",
        "under 500 ms",
        "Sale window, 10:00 to 10:15",
        "Platform lead",
        "It is the first page a fan sees");
  }

  static List<String> cellsWith(SheetColumn column, String cellText) {
    List<String> cells = new ArrayList<>(completeCells());
    cells.set(column.ordinal(), cellText);
    return cells;
  }

  static SheetRow completeRow() {
    return new SheetRow(FIRST_LINE, completeCells());
  }

  static SheetRow rowWith(int lineNumber, SheetColumn column, String cellText) {
    return new SheetRow(lineNumber, cellsWith(column, cellText));
  }

  static SheetRow rowWith(SheetColumn column, String cellText) {
    return rowWith(FIRST_LINE, column, cellText);
  }

  static List<Target> targetsOf(List<SheetRow> rows) {
    return switch (SheetValidator.validate(rows)) {
      case Result.Success<List<Target>, SheetError>(List<Target> targets) -> targets;
      case Result.Error<List<Target>, SheetError>(SheetError error) ->
          fail("expected targets but got " + error);
    };
  }

  static SheetError errorOf(List<SheetRow> rows) {
    return switch (SheetValidator.validate(rows)) {
      case Result.Success<List<Target>, SheetError>(List<Target> targets) ->
          fail("expected an error but got " + targets);
      case Result.Error<List<Target>, SheetError>(SheetError error) -> error;
    };
  }

  static RowRejection onlyRejectionOf(List<SheetRow> rows) {
    if (errorOf(rows) instanceof SheetError.RowsRejected rowsRejected) {
      return rowsRejected.rejections().getFirst();
    }
    return fail("expected rejected rows");
  }

  static List<SheetRow> rowsRead(List<String> lines) {
    return switch (MarkdownSheetReader.read(lines)) {
      case Result.Success<List<SheetRow>, SheetError>(List<SheetRow> rows) -> rows;
      case Result.Error<List<SheetRow>, SheetError>(SheetError error) ->
          fail("expected rows but got " + error);
    };
  }

  static SheetError readErrorOf(List<String> lines) {
    return switch (MarkdownSheetReader.read(lines)) {
      case Result.Success<List<SheetRow>, SheetError>(List<SheetRow> rows) ->
          fail("expected an error but got " + rows);
      case Result.Error<List<SheetRow>, SheetError>(SheetError error) -> error;
    };
  }
}
