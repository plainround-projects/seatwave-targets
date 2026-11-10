# Test walkthrough: the sheet package

## What it is

This file goes through every test file under `src/test/java/io/plainround/seatwave/targets/sheet/`: the shared helper first, then the small types, then the reader, the validator, the report and the four real sheets. Each fenced block is copied from the real file. Each explanation says what the test proves and why that behavior matters.

The package under test turns a Markdown table (`markdown.md`) into **targets**. A **row** is one line of the table. A **target** is one checked requirement: an operation and an attribute (the **scope**), a metric with a percentile, a threshold and a window (the **measure**), and an owner with a reason (the **ownership**). The **validator** decides which rows become targets and which are rejected, and why.

## Why this repository uses it

Module 2 says a target without a number, a window, an owner or a reason is not a target. These tests make that sentence executable. They prove that every missing field is found, that all problems of a row are reported at once so one run shows all the work, that one bad row rejects the whole sheet, and that the four shipped sheets behave as the course says: three are accepted and the draft is rejected on all four rows.

## Where it appears

| Path | What it checks |
| --- | --- |
| `SheetFixtures.java` | Helper, not a test: builds rows and opens results. |
| `OperationTest.java` | The five allowed operations. 14 results. |
| `QualityAttributeTest.java` | The ten allowed attributes. 18 results. |
| `SheetRowTest.java` | One table row. 5 results. |
| `RowRejectionTest.java` | A rejected row and a rejected sheet. 5 results. |
| `TargetTest.java`, `TargetMeasureTest.java`, `TargetOwnershipTest.java` | The target and its two checked parts. 4, 10 and 7 results. |
| `MarkdownSheetReaderTest.java` | Lines of text into rows. 17 results. |
| `SheetValidatorTest.java` | Rows into targets or rejections. 48 results. |
| `SheetReportTest.java` | The printed accept and reject reports. 14 results. |
| `ShippedSheetsTest.java` | The real files under `sheets/`. 4 results. |

## How it works underneath

1. The reader (`MarkdownSheetReader.read`) takes a list of text lines and returns a `Result`: either a list of `SheetRow` values, or a `SheetError` (`NoTable`, `WrongHeader`, `NoTargets`).
2. The validator (`SheetValidator.validate`) takes the rows and returns either a list of `Target` values or a `SheetError.RowsRejected` that holds the count of good rows and one `RowRejection` per bad row. A rejection lists `RowProblem` values: `MissingField`, `NotAllowed`, `ThresholdWithoutNumber`, `WrongCellCount` and `DuplicateOfLine`.
3. The report (`SheetReport`) turns those values into the text that `SheetCli` prints.
4. Records (`junit.md`, `assertj.md`) let tests compare whole structures with `isEqualTo`.
5. A **placeholder** is a word people write when they do not know yet (`TBD`, `n/a`, `-`). The validator counts it as a missing field.
6. Every test starts from a complete, valid row built by `SheetFixtures.completeRow()` and changes **one** cell, so each failure points at one cause.

## Walkthrough of this repository's use

### `SheetFixtures.java`: the shared builder

```java
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

```

`FIRST_LINE = 5` is the line number given to a test row. Real sheets have a title and notes above the table, so the first data row is rarely on line 1. The class is package-private, `final`, and holds only static helpers.

```java
  private SheetFixtures() {}

  /** Reads a shipped sheet; Gradle runs tests from the project directory. */
  static List<String> linesOfSheet(String fileName) throws IOException {
    return Files.readAllLines(Path.of("sheets", fileName), StandardCharsets.UTF_8);
  }
```

`linesOfSheet(fileName)` reads a real sheet from the `sheets` folder as UTF-8 lines. The relative path works because Gradle runs tests from the project folder (`junit.md`). `throws IOException` says reading can fail.

```java
  /** Reads and validates a shipped sheet in one step. */
  static Result<List<Target>, SheetError> validatedSheet(String fileName) throws IOException {
    return MarkdownSheetReader.read(linesOfSheet(fileName)).flatMap(SheetValidator::validate);
  }
```

`validatedSheet(fileName)` reads a file and validates it in one step. `flatMap` is the chaining method of `Result`: if the reader returned rows, `SheetValidator::validate` is called on them (a method reference); if the reader returned an error, the error passes through unchanged.

```java
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
```

`completeCells()` is one valid row of eight cells: `List events`, `Latency`, a metric, `p99`, `under 500 ms`, a window, an owner and a reason. It is the row every other test spoils on purpose.

```java
  static List<String> cellsWith(SheetColumn column, String cellText) {
    List<String> cells = new ArrayList<>(completeCells());
    cells.set(column.ordinal(), cellText);
    return cells;
  }
```

`cellsWith(column, cellText)` copies the complete cells and replaces one. `column.ordinal()` is the position of an enum constant (0 for `OPERATION`, 1 for `ATTRIBUTE`, up to 7 for `REASON`), which is also the cell position.

```java
  static SheetRow completeRow() {
    return new SheetRow(FIRST_LINE, completeCells());
  }

  static SheetRow rowWith(int lineNumber, SheetColumn column, String cellText) {
    return new SheetRow(lineNumber, cellsWith(column, cellText));
  }

  static SheetRow rowWith(SheetColumn column, String cellText) {
    return rowWith(FIRST_LINE, column, cellText);
  }
```

`completeRow()` and the two `rowWith` variants build `SheetRow` values: a complete row, a row with one cell changed at a given line, and the same at the default line 5.

```java
  static List<Target> targetsOf(List<SheetRow> rows) {
    return switch (SheetValidator.validate(rows)) {
      case Result.Success<List<Target>, SheetError>(List<Target> targets) -> targets;
      case Result.Error<List<Target>, SheetError>(SheetError error) ->
          fail("expected targets but got " + error);
    };
  }
```

`targetsOf(rows)` validates and returns the targets, or fails the test with `expected targets but got ...`. It uses a `switch` with record patterns, the same technique as `ParseResults` (`tests-availability-walkthrough.md`).

```java
  static SheetError errorOf(List<SheetRow> rows) {
    return switch (SheetValidator.validate(rows)) {
      case Result.Success<List<Target>, SheetError>(List<Target> targets) ->
          fail("expected an error but got " + targets);
      case Result.Error<List<Target>, SheetError>(SheetError error) -> error;
    };
  }
```

`errorOf(rows)` returns the error, or fails the test if the sheet was accepted.

```java
  static RowRejection onlyRejectionOf(List<SheetRow> rows) {
    if (errorOf(rows) instanceof SheetError.RowsRejected rowsRejected) {
      return rowsRejected.rejections().getFirst();
    }
    return fail("expected rejected rows");
  }
```

`onlyRejectionOf(rows)` returns the first rejected row. `errorOf(rows) instanceof SheetError.RowsRejected rowsRejected` is a **pattern match**: it tests the type and names the value in one step. If the error is another kind, `fail` ends the test.

```java
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
```

`rowsRead(lines)` and `readErrorOf(lines)` do the same for the reader's result.

### `OperationTest.java` and `QualityAttributeTest.java`

These two files have the same shape, so they are explained together. An **enum** is a type with a fixed set of named values (`Operation` has five, `QualityAttribute` has ten). `fromLabel(text)` turns the text of a cell into the matching constant, or an empty `Optional` (a box that holds a value or nothing).

```java
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
```

Every operation is found by its own label: `@EnumSource` runs the test once per constant, so a new operation is covered automatically and a label that does not match its own constant is caught.

```java
  @Test
  void ignoresCase() {
    assertThat(Operation.fromLabel("hOLD a SEAT")).contains(Operation.HOLD_A_SEAT);
  }

  @Test
  void ignoresSurroundingSpaces() {
    assertThat(Operation.fromLabel("  Confirm a booking\t")).contains(Operation.CONFIRM_A_BOOKING);
  }
```

Matching ignores case (`hOLD a SEAT`) and surrounding spaces and tabs. People type cells by hand, so the sheet must not fail on capital letters or a stray space.

```java
  @ParameterizedTest
  @ValueSource(strings = {"", "  ", "Hold", "Hold  a seat", "Hold a seat now", "Pay"})
  void returnsEmptyForAnUnknownLabel(String unknownLabel) {
    assertThat(Operation.fromLabel(unknownLabel)).isEmpty();
  }
```

Unknown labels give an empty `Optional`: the empty string, only spaces, a part of a label (`Hold`), a label with two spaces inside (`Hold  a seat`), a label with extra words and a word that is not an operation (`Pay`). Matching is on the whole label, never a prefix.

```java
  @Test
  void rejectsNullText() {
    assertThatNullPointerException().isThrownBy(() -> Operation.fromLabel(null));
  }
}
```

A `null` throws `NullPointerException`.

```java
package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class QualityAttributeTest {

  @ParameterizedTest
  @EnumSource(QualityAttribute.class)
  void findsEveryAttributeByItsOwnLabel(QualityAttribute attribute) {
    assertThat(QualityAttribute.fromLabel(attribute.label())).contains(attribute);
  }
```

The same "found by its own label" test for the ten attributes (Latency, Throughput, Scalability, Availability, Reliability, Efficiency, Maintainability, Security, Privacy, Compliance).

```java
  @Test
  void ignoresCase() {
    assertThat(QualityAttribute.fromLabel("aVAILABILITY")).contains(QualityAttribute.AVAILABILITY);
  }

  @Test
  void ignoresSurroundingSpaces() {
    assertThat(QualityAttribute.fromLabel("  Latency ")).contains(QualityAttribute.LATENCY);
  }
```

Case and surrounding spaces are ignored: `aVAILABILITY`, `  Latency `.

```java
  @ParameterizedTest
  @ValueSource(strings = {"", " ", "Speed", "Late", "Latency and more"})
  void returnsEmptyForAnUnknownLabel(String unknownLabel) {
    assertThat(QualityAttribute.fromLabel(unknownLabel)).isEmpty();
  }
```

Unknown: empty, space, `Speed`, `Late` (a prefix of Latency) and `Latency and more`.

```java
  @Test
  void rejectsNullText() {
    assertThatNullPointerException().isThrownBy(() -> QualityAttribute.fromLabel(null));
  }
}
```

A `null` throws.

### `SheetRowTest.java`

```java
package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SheetRowTest {

  @Test
  void returnsTheCellOfAColumn() {
    SheetRow row = SheetFixtures.completeRow();

    assertThat(row.cell(SheetColumn.OPERATION)).isEqualTo("List events");
    assertThat(row.cell(SheetColumn.REASON)).isEqualTo("It is the first page a fan sees");
  }
```

`row.cell(SheetColumn.OPERATION)` returns the cell of a column by name, so the validator never counts positions by hand. `OPERATION` is the first cell, `REASON` the last.

```java
  @Test
  void hasEveryColumnOnlyWithEightCells() {
    assertThat(SheetFixtures.completeRow().hasEveryColumn()).isTrue();
    assertThat(new SheetRow(1, List.of("only", "two")).hasEveryColumn()).isFalse();
    assertThat(new SheetRow(1, List.of()).hasEveryColumn()).isFalse();
  }
```

`hasEveryColumn()` is true only with exactly eight cells; two cells and zero cells are false.

```java
  @Test
  void refusesToReadACellOfARowThatLacksColumns() {
    SheetRow shortRow = new SheetRow(1, List.of("only", "two"));

    assertThatThrownBy(() -> shortRow.cell(SheetColumn.REASON))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("line 1");
  }
```

Reading a cell of a row that lacks columns throws `IllegalStateException` mentioning `line 1`. The validator checks the count first, so this exception means a programming mistake, and the message says which line.

```java
  @Test
  void copiesTheCellsSoLaterChangesDoNotReachTheRow() {
    List<String> cells = new ArrayList<>(SheetFixtures.completeCells());
    SheetRow row = new SheetRow(1, cells);

    cells.set(0, "changed");

    assertThat(row.cell(SheetColumn.OPERATION)).isEqualTo("List events");
  }
```

The row copies the cells it is given. Changing the original list afterwards (`cells.set(0, "changed")`) does not reach the row.

```java
  @Test
  void rejectsNullCellsAndANullColumn() {
    assertThatNullPointerException().isThrownBy(() -> new SheetRow(1, null));
    assertThatNullPointerException().isThrownBy(() -> SheetFixtures.completeRow().cell(null));
  }
}
```

A `null` list of cells, and a `null` column, throw `NullPointerException`.

### `RowRejectionTest.java`

```java
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
```

A rejection with no problems makes no sense (what was rejected for?), so `new RowRejection(1, "row", List.of())` throws `IllegalArgumentException`.

```java
  @Test
  void rejectsANullLabelAndNullProblems() {
    assertThatNullPointerException()
        .isThrownBy(() ->
            new RowRejection(
                1, null, List.of(new RowProblem.NotAllowed(SheetColumn.OPERATION, "x"))));
    assertThatNullPointerException().isThrownBy(() -> new RowRejection(1, "row", null));
  }
```

A `null` label and a `null` list of problems throw `NullPointerException`. `NotAllowed(SheetColumn.OPERATION, "x")` is one example of a problem.

```java
  @Test
  void copiesTheProblemsSoLaterChangesDoNotReachTheRejection() {
    List<RowProblem> problems =
        new ArrayList<>(List.of(new RowProblem.MissingField(SheetColumn.OWNER)));
    RowRejection rejection = new RowRejection(1, "row", problems);

    problems.clear();

    assertThat(rejection.problems()).hasSize(1);
  }
```

The rejection copies its list of problems, so emptying the original list later does not change it.

```java
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
```

The same copy rule for `SheetError.RowsRejected`: the list of rejections of a rejected sheet is copied. `new SheetError.RowsRejected(0, rejections)` means zero accepted rows and these rejections.

```java
  @Test
  void rejectsNullRejections() {
    assertThatNullPointerException().isThrownBy(() -> new SheetError.RowsRejected(0, null));
  }
}
```

A `null` list of rejections throws.

### `TargetTest.java`

```java
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
```

Three constants build a scope (`HOLD_A_SEAT`, `LATENCY`), a measure and an ownership once. They are `static final`, so every test reuses them.

```java
  @Test
  void keepsItsThreeParts() {
    Target target = new Target(SCOPE, MEASURE, OWNERSHIP);

    assertThat(target.scope()).isEqualTo(SCOPE);
    assertThat(target.measure()).isEqualTo(MEASURE);
    assertThat(target.ownership()).isEqualTo(OWNERSHIP);
  }
```

A target keeps its three parts.

```java
  @Test
  void rejectsANullPart() {
    assertThatNullPointerException().isThrownBy(() -> new Target(null, MEASURE, OWNERSHIP));
    assertThatNullPointerException().isThrownBy(() -> new Target(SCOPE, null, OWNERSHIP));
    assertThatNullPointerException().isThrownBy(() -> new Target(SCOPE, MEASURE, null));
  }
```

A `null` scope, measure or ownership throws. No partly-built target can exist.

```java
  @Test
  void rejectsANullOperationOrAttributeInAScope() {
    assertThatNullPointerException()
        .isThrownBy(() -> new TargetScope(null, QualityAttribute.LATENCY));
    assertThatNullPointerException().isThrownBy(() -> new TargetScope(Operation.HOLD_A_SEAT, null));
  }
```

A `null` operation or attribute in a scope throws.

```java
  @Test
  void treatsTwoScopesWithTheSameOperationAndAttributeAsEqual() {
    assertThat(new TargetScope(Operation.HOLD_A_SEAT, QualityAttribute.LATENCY)).isEqualTo(SCOPE);
  }
}
```

Two scopes with the same operation and attribute are equal. This is what makes the duplicate check work: two rows about the same operation and attribute produce equal scopes.

### `TargetMeasureTest.java`

```java
package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TargetMeasureTest {

  @Test
  void stripsEveryField() {
    TargetMeasure measure = new TargetMeasure(" Response time ", " p99 ", " 1 s ", " Month ");

    assertThat(measure.metric()).isEqualTo("Response time");
    assertThat(measure.percentileOrRate()).isEqualTo("p99");
    assertThat(measure.threshold()).isEqualTo("1 s");
    assertThat(measure.window()).isEqualTo("Month");
  }
```

Every field is stripped of surrounding spaces: ` p99 ` becomes `p99`. The measure holds the metric, the percentile or rate, the threshold and the window.

```java
  @Test
  void cannotBeBuiltWithoutAMetric() {
    assertThatThrownBy(() -> new TargetMeasure("", "p99", "1 s", "Month"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MissingField[column=METRIC]");
  }

  @Test
  void cannotBeBuiltWithoutAPercentileOrRate() {
    assertThatThrownBy(() -> new TargetMeasure("Metric", "n/a", "1 s", "Month"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MissingField[column=PERCENTILE]");
  }
```

A measure cannot be built without a metric (the message contains `MissingField[column=METRIC]`) or without a percentile or rate (`n/a` is a placeholder, so `MissingField[column=PERCENTILE]`). The message is the text of the list of problems the constructor found, which is why these strings look like record printouts.

```java
  @Test
  void cannotBeBuiltWithAnUnknownPercentile() {
    assertThatThrownBy(() -> new TargetMeasure("Metric", "average", "1 s", "Month"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NotAllowed");
  }

  @Test
  void cannotBeBuiltWithAThresholdThatHasNoNumber() {
    assertThatThrownBy(() -> new TargetMeasure("Metric", "p99", "fast", "Month"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ThresholdWithoutNumber");
  }
```

`average` is not a percentile or a rate kind (`NotAllowed`), and `fast` has no digit (`ThresholdWithoutNumber`). "Fast" is not a target because nobody can measure it.

```java
  @Test
  void cannotBeBuiltWithoutAWindow() {
    assertThatThrownBy(() -> new TargetMeasure("Metric", "p99", "1 s", "?"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MissingField[column=WINDOW]");
  }
```

A `?` window is a placeholder and counts as missing: `MissingField[column=WINDOW]`. A number without a window has no start and no end.

```java
  @Test
  void rejectsNullFields() {
    assertThatNullPointerException().isThrownBy(() -> new TargetMeasure(null, "p99", "1 s", "M"));
    assertThatNullPointerException().isThrownBy(() -> new TargetMeasure("m", null, "1 s", "M"));
    assertThatNullPointerException().isThrownBy(() -> new TargetMeasure("m", "p99", null, "M"));
    assertThatNullPointerException().isThrownBy(() -> new TargetMeasure("m", "p99", "1 s", null));
  }
```

Each of the four fields throws `NullPointerException` when `null`.

```java
  @Test
  void reportsNoProblemsForACompleteMeasure() {
    assertThat(TargetMeasure.problemsIn("Metric", "p99", "1 s", "Month")).isEmpty();
  }
```

`problemsIn` is the shared rule used by both the constructor and the validator. A complete measure has no problems.

```java
  @Test
  void reportsEveryProblemOfAnEmptyMeasureAtOnce() {
    assertThat(TargetMeasure.problemsIn("", "", "", ""))
        .containsExactly(
            new RowProblem.MissingField(SheetColumn.METRIC),
            new RowProblem.MissingField(SheetColumn.PERCENTILE),
            new RowProblem.ThresholdWithoutNumber(""),
            new RowProblem.MissingField(SheetColumn.WINDOW));
  }
```

An empty measure reports **all four** problems at once, in column order: metric, percentile, threshold and window. The validator relies on this so that one run shows every missing piece.

```java
  @Test
  void listsTheRateKindsInAlphabeticalOrder() {
    List<String> rateKinds = TargetMeasure.rateKinds();

    assertThat(rateKinds).isSorted().contains("rate", "count", "peak");
  }
}
```

`rateKinds()` returns the allowed non-percentile words in alphabetical order, and contains `rate`, `count` and `peak`. `isSorted()` checks the order. The report prints this list in its help text, so a fixed order keeps the message stable.

### `TargetOwnershipTest.java`

```java
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
```

Owner and reason are stripped of spaces.

```java
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
```

An owner of spaces only, an owner of `TBD`, and an empty reason are refused with `IllegalArgumentException`. A target with no owner has nobody who answers for it; a target with no reason cannot be questioned later.

```java
  @Test
  void rejectsNullFields() {
    assertThatNullPointerException().isThrownBy(() -> new TargetOwnership(null, "A reason"));
    assertThatNullPointerException().isThrownBy(() -> new TargetOwnership("Lead", null));
  }
```

A `null` owner or reason throws `NullPointerException`.

```java
  @Test
  void reportsBothMissingFieldsAtOnce() {
    assertThat(TargetOwnership.problemsIn("?", "n/a"))
        .containsExactly(
            new RowProblem.MissingField(SheetColumn.OWNER),
            new RowProblem.MissingField(SheetColumn.REASON));
  }
```

`problemsIn("?", "n/a")` returns both problems, owner first, as `MissingField` values.

```java
  @Test
  void reportsNoProblemsForACompleteOwnership() {
    assertThat(TargetOwnership.problemsIn("Lead", "A reason")).isEmpty();
  }
}
```

A complete ownership has no problems.

### `MarkdownSheetReaderTest.java`

```java
package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MarkdownSheetReaderTest {

  private static final String HEADER =
      "| Operation | Attribute | Metric | Percentile | Threshold | Window | Owner | Reason |";
  private static final String SEPARATOR = "|---|---|---|---|---|---|---|---|";
  private static final String ROW =
      "| List events | Latency | Response time | p99 | 1 s | Month | Lead | Why |";
```

Three constants: the header line, the separator line and one data row, written the way the sheets write them (`markdown.md`).

```java
  @Test
  void readsOneRowWithItsLineNumberAndEightStrippedCells() {
    List<String> lines = List.of(HEADER, SEPARATOR, ROW);

    List<SheetRow> rows = SheetFixtures.rowsRead(lines);

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().lineNumber()).isEqualTo(3);
    assertThat(rows.getFirst().cells())
        .containsExactly(
            "List events", "Latency", "Response time", "p99", "1 s", "Month", "Lead", "Why");
  }
```

The happy path. A header, a separator and a row give one `SheetRow` whose line number is 3 (lines are counted from 1) and whose eight cells have no surrounding spaces. `containsExactly` checks the cells in order.

```java
  @Test
  void ignoresTextAroundTheTable() {
    List<String> lines =
        List.of("# Title", "", "Some notes.", HEADER, SEPARATOR, ROW, "", "More notes.");

    List<SheetRow> rows = SheetFixtures.rowsRead(lines);

    assertThat(rows).hasSize(1);
    // The title and notes take lines 1 to 3, so the row is on line 6.
    assertThat(rows.getFirst().lineNumber()).isEqualTo(6);
  }
```

Text around the table is ignored. Three lines come before the header, so the row is on line 6: line numbers count lines of the whole file, so a person can find the row in an editor.

```java
  @Test
  void reportsNoTableWhenNoLineStartsWithADivider() {
    assertThat(SheetFixtures.readErrorOf(List.of("# Title", "no table here")))
        .isEqualTo(new SheetError.NoTable());
  }
```

No line starts with the cell divider, so the result is `NoTable`.

```java
  @Test
  void reportsNoTableForAnEmptyFile() {
    assertThat(SheetFixtures.readErrorOf(List.of())).isEqualTo(new SheetError.NoTable());
  }
```

An empty file is `NoTable`, not a crash.

```java
  @Test
  void reportsNoTableWhenOnlyASeparatorRowIsPresent() {
    assertThat(SheetFixtures.readErrorOf(List.of(SEPARATOR))).isEqualTo(new SheetError.NoTable());
  }
```

A file with only a separator row is `NoTable`: separator rows are dropped before anything else is decided.

```java
  @Test
  void reportsTheLineOfAWrongHeader() {
    List<String> lines =
        List.of(
            "intro",
            "| Operation | Attribute | Metric | Percentile | Threshold | Window | Owner |",
            SEPARATOR,
            ROW);

    assertThat(SheetFixtures.readErrorOf(lines)).isEqualTo(new SheetError.WrongHeader(2));
  }
```

A header with seven columns gives `WrongHeader(2)`: the error carries the line of the header (line 1 is the word `intro`).

```java
  @Test
  void reportsAHeaderWithTheRightWordsInTheWrongOrder() {
    String swapped =
        "| Attribute | Operation | Metric | Percentile | Threshold | Window | Owner | Reason |";

    assertThat(SheetFixtures.readErrorOf(List.of(swapped, SEPARATOR, ROW)))
        .isEqualTo(new SheetError.WrongHeader(1));
  }
```

The right words in the wrong order are also a wrong header. Order matters because the validator reads cells by position.

```java
  @Test
  void matchesTheHeaderWithoutRegardToCase() {
    String shouting = HEADER.toUpperCase(Locale.ROOT);

    assertThat(SheetFixtures.rowsRead(List.of(shouting, SEPARATOR, ROW))).hasSize(1);
  }
```

The header matches without regard to case: `HEADER.toUpperCase(Locale.ROOT)`. `Locale.ROOT` means "no country rules", so the result is the same on every machine (a Turkish computer would otherwise turn `i` into a dotted capital).

```java
  @Test
  void reportsNoTargetsForAHeaderWithNoRows() {
    assertThat(SheetFixtures.readErrorOf(List.of(HEADER, SEPARATOR)))
        .isEqualTo(new SheetError.NoTargets());
  }
```

A header with no rows gives `NoTargets`: a table of nothing is not a sheet.

```java
  @Test
  void keepsEmptyCellsAsEmptyStrings() {
    String rowWithGaps = "| List events | Latency | Response time |  | 1 s |  | Lead | Why |";

    List<SheetRow> rows = SheetFixtures.rowsRead(List.of(HEADER, SEPARATOR, rowWithGaps));

    assertThat(rows.getFirst().cells()).hasSize(8).containsSequence("Response time", "", "1 s", "");
  }
```

Empty cells are kept as empty strings, in place: the result has eight cells and contains the sequence `Response time`, empty, `1 s`, empty. Two dividers with only spaces between them make an empty cell. Keeping them is what lets the validator report "percentile is missing" instead of reporting a wrong cell count.

```java
  @Test
  void keepsATrailingEmptyCell() {
    String rowWithEmptyReason = "| List events | Latency | Metric | p99 | 1 s | Month | Lead |  |";

    List<SheetRow> rows = SheetFixtures.rowsRead(List.of(HEADER, SEPARATOR, rowWithEmptyReason));

    assertThat(rows.getFirst().cells()).hasSize(8);
    assertThat(rows.getFirst().cells().getLast()).isEmpty();
  }
```

A trailing empty cell is kept too: the last cell is the empty string. `isEmpty()` checks that. This is the subtle case of splitting text: a naive split drops empty items at the end.

```java
  @Test
  void readsARowWithoutAClosingDivider() {
    String noClosingDivider = "| List events | Latency | Metric | p99 | 1 s | Month | Lead | Why";

    List<SheetRow> rows = SheetFixtures.rowsRead(List.of(HEADER, SEPARATOR, noClosingDivider));

    assertThat(rows.getFirst().cells()).hasSize(8);
    assertThat(rows.getFirst().cells().getLast()).isEqualTo("Why");
  }
```

A row with no closing divider still has eight cells; the last is `Why`. People forget the last pipe.

```java
  @Test
  void readsARowIndentedWithSpaces() {
    List<SheetRow> rows = SheetFixtures.rowsRead(List.of(HEADER, SEPARATOR, "   " + ROW));

    assertThat(rows).hasSize(1);
  }
```

A row indented with spaces is still read.

```java
  @Test
  void acceptsAlignmentMarkersInTheSeparatorRow() {
    String aligned = "|:---|---:|:---:|---|---|---|---|---|";

    assertThat(SheetFixtures.rowsRead(List.of(HEADER, aligned, ROW))).hasSize(1);
  }
```

A separator row with alignment colons (`|:---|---:|:---:|...`) is recognized as a separator and dropped. So the result is one row (the data row).

```java
  @Test
  void keepsTheRowsInFileOrder() {
    String secondRow = ROW.replace("List events", "View seats");

    List<SheetRow> rows = SheetFixtures.rowsRead(List.of(HEADER, SEPARATOR, ROW, secondRow));

    assertThat(rows).extracting(SheetRow::lineNumber).containsExactly(3, 4);
  }
```

Rows keep the order of the file, and their line numbers are 3 and 4. `extracting(SheetRow::lineNumber)` collects the line numbers into a list to compare.

```java
  @Test
  void returnsRowsThatCannotBeChanged() {
    List<SheetRow> rows = SheetFixtures.rowsRead(List.of(HEADER, SEPARATOR, ROW));
    SheetRow extraRow = rows.getFirst();

    assertThatThrownBy(() -> rows.add(extraRow)).isInstanceOf(UnsupportedOperationException.class);
  }
```

The list of rows cannot be changed: `rows.add(extraRow)` throws `UnsupportedOperationException`.

```java
  @Test
  void rejectsNullLines() {
    assertThatNullPointerException().isThrownBy(() -> MarkdownSheetReader.read(null));
  }
}
```

`read(null)` throws `NullPointerException`.

### `SheetValidatorTest.java`

```java
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

```

Three line numbers (5, 6, 7) are built from `SheetFixtures.FIRST_LINE`, so rows in one test have distinct lines.

```java
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
```

A complete row with spaces around every cell becomes exactly one target, with stripped fields. The scope is `HOLD_A_SEAT` and `RELIABILITY`, found by their labels; the measure is `Seats with two holds`, `count`, `0`, `Every night`; the ownership is `Developer` and `No double booking`. `isEqualTo(new TargetMeasure(...))` compares the whole record.

```java
  @Test
  void rejectsAThresholdWithNoNumber() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(
            List.of(SheetFixtures.rowWith(SheetColumn.THRESHOLD, "fast")));

    assertThat(rejection.problems()).containsExactly(new RowProblem.ThresholdWithoutNumber("fast"));
  }
```

Only the threshold is spoiled (`fast`), so the row is rejected with exactly one problem: `ThresholdWithoutNumber("fast")`. `containsExactly` makes sure there is no second, surprising problem.

```java
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
```

A row with an empty percentile **and** an empty window reports both problems, in column order. This is the "all problems at once" promise. The test spoils the second cell by calling `cells.set(...)` on the list that `cellsWith` returned.

```java
  @Test
  void rejectsAnUnknownPercentileWord() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(
            List.of(SheetFixtures.rowWith(SheetColumn.PERCENTILE, "average")));

    assertThat(rejection.problems())
        .containsExactly(new RowProblem.NotAllowed(SheetColumn.PERCENTILE, "average"));
  }
```

An unknown percentile word (`average`) is `NotAllowed(PERCENTILE, "average")`, which is different from a missing one. The report tells the user which.

```java
  @ParameterizedTest
  @ValueSource(strings = {"p50", "p95", "p99", "p99.9", "P99", "rate", "count", "peak", "Mean"})
  void acceptsPercentilesAndRateKinds(String percentileOrRate) {
    assertThat(
            SheetFixtures.targetsOf(
                List.of(SheetFixtures.rowWith(SheetColumn.PERCENTILE, percentileOrRate))))
        .hasSize(1);
  }
```

Nine valid percentile or rate words are accepted: `p50`, `p95`, `p99`, `p99.9`, a capital `P99`, `rate`, `count`, `peak` and `Mean`. Case does not matter.

```java
  @ParameterizedTest
  @ValueSource(strings = {"p", "p100x", "p999", "99", "pp99", "p99.", "median"})
  void rejectsMalformedPercentiles(String percentileOrRate) {
    assertThat(
            SheetFixtures.onlyRejectionOf(
                    List.of(SheetFixtures.rowWith(SheetColumn.PERCENTILE, percentileOrRate)))
                .problems())
        .containsExactly(new RowProblem.NotAllowed(SheetColumn.PERCENTILE, percentileOrRate));
  }
```

Seven malformed words are rejected: `p`, `p100x`, `p999`, `99`, `pp99`, `p99.` and `median`. A percentile is `p` plus one or two digits, with an optional decimal part. Each rejection is `NotAllowed` and carries the text as typed.

```java
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
```

Twelve placeholders and blanks (`TBD`, `tbd`, `n/a`, `N/A`, `?`, `-`, `none`, `unknown`, `todo`, `na`, the empty string and spaces) all count as a **missing owner**, not as a name. This is the rule "a placeholder is a missing field" and it is the reason the lab draft fails on its `TBD` owner.

```java
  @Test
  void countsAPlaceholderReasonAsAMissingReason() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(List.of(SheetFixtures.rowWith(SheetColumn.REASON, "TBD")));

    assertThat(rejection.problems())
        .containsExactly(new RowProblem.MissingField(SheetColumn.REASON));
  }
```

A placeholder reason (`TBD`) is a missing reason.

```java
  @Test
  void rejectsAnUnknownOperation() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(
            List.of(SheetFixtures.rowWith(SheetColumn.OPERATION, "Delete an event")));

    assertThat(rejection.problems())
        .containsExactly(new RowProblem.NotAllowed(SheetColumn.OPERATION, "Delete an event"));
  }
```

An operation that is not in the list (`Delete an event`) is `NotAllowed(OPERATION, ...)`.

```java
  @Test
  void rejectsAnUnknownAttribute() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(
            List.of(SheetFixtures.rowWith(SheetColumn.ATTRIBUTE, "Beauty")));

    assertThat(rejection.problems())
        .containsExactly(new RowProblem.NotAllowed(SheetColumn.ATTRIBUTE, "Beauty"));
  }
```

An unknown attribute (`Beauty`) is `NotAllowed(ATTRIBUTE, ...)`.

```java
  @Test
  void treatsAnEmptyOperationAsMissingInsteadOfNotAllowed() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(List.of(SheetFixtures.rowWith(SheetColumn.OPERATION, "")));

    assertThat(rejection.problems())
        .containsExactly(new RowProblem.MissingField(SheetColumn.OPERATION));
  }
```

An **empty** operation is reported as `MissingField`, not `NotAllowed`: nothing was typed, so nothing is "not allowed". The message the user reads must match what they did.

```java
  @Test
  void rejectsARowWithSevenCells() {
    List<String> sevenCells = SheetFixtures.completeCells().subList(0, 7);

    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(List.of(new SheetRow(FIRST_LINE, sevenCells)));

    assertThat(rejection.problems()).containsExactly(new RowProblem.WrongCellCount(8, 7));
    assertThat(rejection.lineNumber()).isEqualTo(FIRST_LINE);
  }
```

A row with seven cells gives one problem, `WrongCellCount(8, 7)`, and keeps its line number (5). With the wrong number of cells the validator cannot know which is which, so it reports nothing else.

```java
  @Test
  void rejectsARowWithNineCells() {
    List<String> nineCells = new ArrayList<>(SheetFixtures.completeCells());
    nineCells.add("extra");

    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(List.of(new SheetRow(FIRST_LINE, nineCells)));

    assertThat(rejection.problems()).containsExactly(new RowProblem.WrongCellCount(8, 9));
  }
```

Nine cells give `WrongCellCount(8, 9)`.

```java
  @Test
  void rejectsARowWithNoCells() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(List.of(new SheetRow(FIRST_LINE, List.of())));

    assertThat(rejection.problems()).containsExactly(new RowProblem.WrongCellCount(8, 0));
  }
```

No cells give `WrongCellCount(8, 0)`.

```java
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
```

**Duplicates.** Two rows set a target for the same measure (same operation, attribute, metric, percentile and window) with different thresholds. The later row (line 6) is rejected with `DuplicateOfLine(5)`, which names the first. Two thresholds for one measure cannot both be the target. `acceptedCount()` is 1: the first row was fine. The test casts the error to `SheetError.RowsRejected` after checking its type.

```java
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
```

The duplicate check ignores case and spaces: a row written in capitals is still a duplicate of the first. Only the threshold and the reason differ, and those are not part of the measure's identity.

```java
  @Test
  void acceptsTwoRowsThatDifferInTheWindow() {
    SheetRow first = SheetFixtures.completeRow();
    SheetRow otherWindow = SheetFixtures.rowWith(SECOND_LINE, SheetColumn.WINDOW, "Whole month");

    assertThat(SheetFixtures.targetsOf(List.of(first, otherWindow))).hasSize(2);
  }
```

Two rows that differ only in the window are **not** duplicates; both are accepted. A target for the sale window and a target for the whole month can both exist.

```java
  @Test
  void acceptsTwoRowsThatDifferInTheOperation() {
    SheetRow first = SheetFixtures.completeRow();
    SheetRow otherOperation =
        SheetFixtures.rowWith(SECOND_LINE, SheetColumn.OPERATION, "View seats");

    assertThat(SheetFixtures.targetsOf(List.of(first, otherOperation))).hasSize(2);
  }
```

Two rows that differ in the operation are not duplicates either.

```java
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
```

Three copies: each later row names line 5, the first. Not the previous row. The test pulls the first problem of each rejection with `extracting`.

```java
  @Test
  void rejectsTheWholeSheetWhenOneRowIsBadAndCountsTheGoodRows() {
    SheetRow good = SheetFixtures.completeRow();
    SheetRow otherGood = SheetFixtures.rowWith(SECOND_LINE, SheetColumn.WINDOW, "Whole month");
    SheetRow bad = SheetFixtures.rowWith(THIRD_LINE, SheetColumn.OWNER, "TBD");

    SheetError error = SheetFixtures.errorOf(List.of(good, bad, otherGood));

    assertThat(error).isInstanceOf(SheetError.RowsRejected.class);
    assertThat(((SheetError.RowsRejected) error).acceptedCount()).isEqualTo(2);
  }
```

One bad row rejects the whole sheet, and the count of good rows (2) is still reported. The bad row is in the middle of the list to show it does not stop the others from being counted.

```java
  @Test
  void rejectsAnEmptyRowList() {
    assertThat(SheetFixtures.errorOf(List.of())).isEqualTo(new SheetError.NoTargets());
  }
```

An empty list of rows gives `NoTargets`.

```java
  @Test
  void labelsARejectionWithTheOperationAndTheAttribute() {
    RowRejection rejection =
        SheetFixtures.onlyRejectionOf(List.of(SheetFixtures.rowWith(SheetColumn.OWNER, "")));

    assertThat(rejection.rowLabel()).isEqualTo("List events, Latency");
  }
```

A rejection is labeled `List events, Latency`, the operation and attribute, so the report names the row. An empty owner makes the row reject so the label can be read.

```java
  @Test
  void rejectsNullRows() {
    assertThatNullPointerException().isThrownBy(() -> SheetValidator.validate(null));
  }
}
```

`validate(null)` throws `NullPointerException`.

### `SheetReportTest.java`

```java
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

```

Two constants for the repeated parts of the text: the footer `SHEET REJECTED: fix every row above before the design starts.` and the list of header names. `fail` is imported to stop a test when a result is the wrong kind.

```java
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
```

The arena sheet is read and validated, and the accepted report must be exactly two lines: `Targets accepted: 24` and the sentence `SHEET ACCEPTED: every target has a metric, a percentile or rate, a threshold, a window, an owner and a reason.`. `isEqualTo` compares the whole text. The `+` joins pieces so the long line fits the formatter's 100 columns.

```java
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
```

The lab draft is read and validated, and the rejected report is compared with a **text block**, a Java string written between `"""` lines that keeps line breaks. The report has one `REJECTED line N (Operation, Attribute): ...` line per bad row, with the problems joined by semicolons, and the footer. This is the output a learner sees, so it is pinned word for word. Line 7 has five problems at once: percentile missing, threshold has no number, window missing, owner missing, reason missing.

```java
  @Test
  void reportsAMissingTable() {
    assertThat(SheetReport.rejected(new SheetError.NoTable()))
        .isEqualTo(
            "REJECTED no table found: the sheet needs a Markdown table with the header "
                + HEADER_LIST
                + "\n"
                + REJECTED_FOOTER);
  }
```

A missing table: `REJECTED no table found: the sheet needs a Markdown table with the header [Operation, Attribute, ...]` plus the footer.

```java
  @Test
  void reportsAWrongHeaderWithItsLine() {
    assertThat(SheetReport.rejected(new SheetError.WrongHeader(2)))
        .isEqualTo("REJECTED line 2: the header must be " + HEADER_LIST + "\n" + REJECTED_FOOTER);
  }
```

A wrong header names its line: `REJECTED line 2: the header must be [...]`.

```java
  @Test
  void reportsATableWithNoTargets() {
    assertThat(SheetReport.rejected(new SheetError.NoTargets()))
        .isEqualTo("REJECTED the table has a header but no targets\n" + REJECTED_FOOTER);
  }
```

A table with no targets: `REJECTED the table has a header but no targets`.

```java
  @Test
  void explainsAWrongCellCount() {
    SheetError error = rejectedWith(new RowProblem.WrongCellCount(8, 7));

    assertThat(SheetReport.rejected(error)).contains("(row): expected 8 cells but found 7");
  }
```

A wrong cell count is explained as `(row): expected 8 cells but found 7`. The label is `row` because the row had no usable operation or attribute.

```java
  @Test
  void explainsADuplicateByNamingTheFirstLine() {
    SheetError error = rejectedWith(new RowProblem.DuplicateOfLine(5));

    assertThat(SheetReport.rejected(error))
        .contains("line 5 already sets a target for this measure");
  }
```

A duplicate is explained by naming the first line: `line 5 already sets a target for this measure`.

```java
  @Test
  void explainsAnOperationThatIsNotAllowed() {
    SheetError error = rejectedWith(new RowProblem.NotAllowed(SheetColumn.OPERATION, "Pay"));

    assertThat(SheetReport.rejected(error))
        .contains(
            "operation 'Pay' is not allowed: use one of the four operations or 'All operations'");
  }
```

A not-allowed operation: `operation 'Pay' is not allowed: use one of the four operations or 'All operations'`.

```java
  @Test
  void explainsAnAttributeThatIsNotAllowed() {
    SheetError error = rejectedWith(new RowProblem.NotAllowed(SheetColumn.ATTRIBUTE, "Beauty"));

    assertThat(SheetReport.rejected(error))
        .contains("attribute 'Beauty' is not allowed: use a known quality attribute");
  }
```

A not-allowed attribute: `attribute 'Beauty' is not allowed: use a known quality attribute`.

```java
  @Test
  void explainsAPercentileThatIsNotAllowedAndListsTheRateKinds() {
    SheetError error = rejectedWith(new RowProblem.NotAllowed(SheetColumn.PERCENTILE, "average"));

    assertThat(SheetReport.rejected(error))
        .contains("percentile 'average' is not allowed: use p50, p95, p99 or one of")
        .contains(TargetMeasure.rateKinds().toString());
  }
```

A not-allowed percentile lists the rate words: `percentile 'average' is not allowed: use p50, p95, p99 or one of` and then the list from `TargetMeasure.rateKinds().toString()`. The test reads the list from the code, so a new rate kind does not break it.

```java
  @Test
  void explainsAColumnWithAnyOtherValueAsAValue() {
    SheetError error = rejectedWith(new RowProblem.NotAllowed(SheetColumn.WINDOW, "x"));

    assertThat(SheetReport.rejected(error)).contains("window 'x' is not allowed: use a value");
  }
```

A not-allowed value in any other column says `use a value`.

```java
  @Test
  void explainsAThresholdWithoutANumber() {
    SheetError error = rejectedWith(new RowProblem.ThresholdWithoutNumber("fast"));

    assertThat(SheetReport.rejected(error)).contains("threshold has no number");
  }
```

A threshold without a number says `threshold has no number`.

```java
  @Test
  void countsAnEmptyListOfAcceptedTargets() {
    assertThat(SheetReport.accepted(List.of())).startsWith("Targets accepted: 0\n");
  }
```

An empty list of accepted targets still prints `Targets accepted: 0`.

```java
  @Test
  void rejectsNullArguments() {
    assertThatNullPointerException().isThrownBy(() -> SheetReport.accepted(null));
    assertThatNullPointerException().isThrownBy(() -> SheetReport.rejected(null));
  }

  private static SheetError rejectedWith(RowProblem problem) {
    return new SheetError.RowsRejected(0, List.of(new RowRejection(1, "row", List.of(problem))));
  }
}
```

`null` arguments throw. The private helper `rejectedWith(problem)` builds a rejected sheet with one rejection that holds the given problem, so each explain-test is one line of setup.

### `ShippedSheetsTest.java`

```java
package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.github.sviperll.result4j.Result;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShippedSheetsTest {

  // The number of targets in each shipped sheet, counted by hand from its table.
  private static final int ARENA_TARGETS = 24;
  private static final int CLUB_TARGETS = 6;
  private static final int LAB_SOLUTION_TARGETS = 8;
  private static final int DRAFT_REJECTIONS = 4;
```

The counts were taken by hand from each table: the arena sheet has 24 targets, the club 6, the lab solution 8. The draft has 4 rejected rows.

```java
  @Test
  void acceptsTheArenaSheetWithItsTargets() throws IOException {
    assertThat(acceptedTargetsIn("arena-on-sale.md")).hasSize(ARENA_TARGETS);
  }

  @Test
  void acceptsTheClubSheetWithItsTargets() throws IOException {
    assertThat(acceptedTargetsIn("club.md")).hasSize(CLUB_TARGETS);
  }

  @Test
  void acceptsTheLabSolutionSheetWithItsTargets() throws IOException {
    assertThat(acceptedTargetsIn("lab1-solution.md")).hasSize(LAB_SOLUTION_TARGETS);
  }
```

The three good sheets are accepted with exactly those counts. This is the most valuable test in the package: it runs the **real files** through the **real code**. A change to a rule or to a sheet that breaks the course material is caught here.

```java
  @Test
  void rejectsTheLabDraftWithNoAcceptedRowsAndFourRejections() throws IOException {
    switch (SheetFixtures.validatedSheet("lab1-draft.md")) {
      case Result.Success<List<Target>, SheetError>(List<Target> targets) ->
          fail("expected the draft to be rejected but got " + targets);
      case Result.Error<List<Target>, SheetError>(SheetError error) -> {
        assertThat(error).isInstanceOf(SheetError.RowsRejected.class);
        SheetError.RowsRejected rejected = (SheetError.RowsRejected) error;
        assertThat(rejected.acceptedCount()).isZero();
        assertThat(rejected.rejections()).hasSize(DRAFT_REJECTIONS);
      }
    }
  }
```

The lab draft is rejected, with zero accepted rows and four rejections. The `switch` is used as a statement here: the success branch fails the test and the error branch checks the numbers.

```java
  private static List<Target> acceptedTargetsIn(String fileName) throws IOException {
    return switch (SheetFixtures.validatedSheet(fileName)) {
      case Result.Success<List<Target>, SheetError>(List<Target> targets) -> targets;
      case Result.Error<List<Target>, SheetError>(SheetError error) ->
          fail("expected " + fileName + " to be accepted but got " + error);
    };
  }
}
```

`acceptedTargetsIn(fileName)` is a helper that returns the targets or fails with `expected <file> to be accepted but got ...`.

## Try it yourself

1. Run the whole package:

```
./gradlew cleanTest test --tests 'io.plainround.seatwave.targets.sheet.*'
```

   Expected: `BUILD SUCCESSFUL` and 146 `PASSED` lines.

2. Run the real-sheet tests:

```
./gradlew cleanTest test --tests '*ShippedSheetsTest'
```

   Expected: four lines, such as `ShippedSheetsTest > acceptsTheArenaSheetWithItsTargets() PASSED`.

3. See the report the tests pin, for the rejected draft:

```
./gradlew -q validateSheet --args='sheets/lab1-draft.md' 2>&1 | head -6
```

   Expected: `Targets accepted: 0` then four `REJECTED line ...` lines and the footer.

4. See the accepted solution sheet:

```
./gradlew -q validateSheet --args='sheets/lab1-solution.md'
```

   Expected: `Targets accepted: 8` and the `SHEET ACCEPTED` line.

5. List the names of the placeholder tests as requirements:

```
./gradlew cleanTest test --tests '*SheetValidatorTest' | grep -c PASSED
```

   Expected: `48`.

## Mistakes people make

- **Spoiling several cells in a test.** Then a failure has several possible causes. These tests change one cell, except where "all problems at once" is the point.
- **Checking only that a sheet was rejected.** The tests pin each problem with `containsExactly`, so a new, surprising problem fails the test.
- **Dropping empty cells when splitting a line.** Then "percentile is missing" becomes "expected 8 cells but found 7". The reader keeps them.
- **Counting lines from 0.** People count from 1. Line numbers here start at 1.
- **Comparing the report with a loose `contains` when the text is the product.** The two sheet reports are the product, so they are compared whole.
- **Using `toUpperCase()` without a locale.** `Locale.ROOT` removes the machine's language rules.

## Check yourself

1. *Why does every validator test start from `completeRow()`?* So changing one cell shows one cause.
2. *What does a duplicate rejection say?* `DuplicateOfLine(n)`, naming the first line that set the target.
3. *Which words count as a missing owner?* Empty text, spaces and the placeholders `TBD`, `todo`, `n/a`, `na`, `none`, `unknown`, `?` and `-` in any case.
4. *Why is `ShippedSheetsTest` valuable?* It runs the real sheets through the real validator, so the course files and the code cannot drift apart unnoticed.
5. *Why are line numbers counted from 1?* They are the numbers an editor shows.

## Further reading

- JUnit 6 user guide (6.1.3): <https://docs.junit.org/6.1.3/overview.html>
- AssertJ core documentation: <https://assertj.github.io/doc/>
- Java text blocks (JEP 378): <https://openjdk.org/jeps/378>
