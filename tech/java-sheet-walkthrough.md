# Java walkthrough: the `sheet` package

## What it is

This file reads, one source file at a time, the Java package `io.plainround.seatwave.targets.sheet`. A **package** is a folder of related Java files. This one reads a **requirements sheet**, which is a Markdown table with one numeric target per row, and decides whether every row is a complete, testable target.

Read `java.md` first. It explains the Java words used here (class, record, sealed, enum, `Optional`, streams, regular expressions and the rest) from zero. Read `result4j.md` for the `Result` type that carries success or failure. This file quotes every line and explains what that line does in this program.

## Why this repository uses it

Module 2 teaches that "fast" is not a target. A target needs a metric, a percentile or rate, a threshold with a number, a window, an owner and a reason. The `sheet` package turns that rule into code. If one row is incomplete, the whole sheet is rejected and the design work does not start. The program lists every bad row, not just the first, so one run shows all the work to do.

## Where it appears

| File | What it does |
|---|---|
| `SheetColumn.java` | The eight columns, in order, as an enum. |
| `Operation.java` | The five things a target may apply to. |
| `QualityAttribute.java` | The ten qualities a target may set a level on. |
| `CellText.java` | The one rule for when a cell counts as empty. |
| `SheetRow.java` | One raw table row with its line number. |
| `TargetScope.java`, `TargetMeasure.java`, `TargetOwnership.java`, `Target.java` | A complete target in three parts, each checking itself. |
| `RowProblem.java` | The five reasons a row can be bad. |
| `RowRejection.java` | One bad row with all its reasons. |
| `SheetError.java` | The four reasons a whole sheet is refused. |
| `MarkdownSheetReader.java` | The only file that knows the sheet is Markdown. |
| `SheetValidator.java` | Turns rows into targets or rejections. |
| `SheetReport.java` | Writes the verdict as text. |
| `SheetCli.java` | The program you start from the terminal. |

## How it works underneath

The flow is a straight line:

1. `SheetCli.main` receives a file path and reads the file's lines into a list of `String`.
2. `MarkdownSheetReader.read` keeps the lines of the first table, checks the header, and returns a list of `SheetRow` values.
3. `SheetValidator.validate` turns each row into a `Target`, or into a `RowRejection` that lists every problem of that row.
4. `SheetReport` writes the verdict and `SheetCli` prints it and ends the program with an exit status: 0 accepted, 1 rejected, 2 the file cannot be used.

A **process exit status** is a small number a program hands back to the shell when it ends. 0 means success and anything else means failure. Everything happens in memory. The only outside contact is reading one file from disk.

A Markdown table is plain text. Each row is a line that starts with `|`. Cells are separated by `|`. The second line has only dashes (`|---|---|`) and separates the header from the data.

## Walkthrough of this repository's use

### `SheetColumn.java`: the eight columns as an enum

An **enum** is a type with a fixed list of values. Each value is a constant object. An enum may carry fields, a constructor and methods, like a class.

**Lines 1 to 19**

```java
package io.plainround.seatwave.targets.sheet;

import java.util.Arrays;
import java.util.List;

/**
 * The columns of the requirements sheet, in the order they appear in the Markdown table.
 *
 * <p>The order of the constants is the order of the cells in a row.
 */
public enum SheetColumn {
  OPERATION("Operation"),
  ATTRIBUTE("Attribute"),
  METRIC("Metric"),
  PERCENTILE("Percentile"),
  THRESHOLD("Threshold"),
  WINDOW("Window"),
  OWNER("Owner"),
  REASON("Reason");
```

- `OPERATION("Operation")` creates the constant `OPERATION` and passes the text `"Operation"` to the constructor. The list ends with `;` because more code follows.
- The order is the order of the table. The Javadoc says so, and the code relies on it: `SheetRow.cell` uses `column.ordinal()` (the position of the constant, starting at 0). `OPERATION` is 0 and `REASON` is 7.

**Lines 21 to 44**

```java
  private final String heading;

  SheetColumn(String heading) {
    this.heading = heading;
  }

  /**
   * Returns the text of the column in the header row.
   *
   * @return the heading, for example "Threshold"
   */
  public String heading() {
    return heading;
  }

  /**
   * Lists the headings of a valid sheet.
   *
   * @return the eight headings in table order
   */
  public static List<String> headings() {
    return Arrays.stream(values()).map(SheetColumn::heading).toList();
  }
}
```

- `private final String heading;` is the field each constant carries. It is set once in the constructor.
- `SheetColumn(String heading)` is the **enum constructor**. It has no `public` or `private`: enum constructors are always private, and Java calls them once per constant when the enum loads. Nobody can make a ninth column.
- `heading()` is a **getter** (a method that returns a field). Records have these for free. An enum does not, so it is written out.
- `values()` is a method Java adds to every enum. It returns an array of all constants, in order. `Arrays.stream(values())` turns the array into a **stream**, a pipeline of items.
- `.map(SheetColumn::heading)` applies the method `heading` to each constant. `SheetColumn::heading` is a **method reference**, short for the lambda `column -> column.heading()`.
- `.toList()` collects the stream into an unmodifiable `List<String>` (Java 16 and later).
- The result is the list `[Operation, Attribute, Metric, Percentile, Threshold, Window, Owner, Reason]`. The reader compares the table header with it. The report prints it.

### `Operation.java` and `QualityAttribute.java`: closed lists with a lookup

**`Operation.java`**

```java
package io.plainround.seatwave.targets.sheet;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * What a target applies to: one of the four SeatWave operations, or all of them together.
 *
 * <p>The list is closed on purpose. A target on an operation that does not exist cannot be tested.
 */
public enum Operation {
  LIST_EVENTS("List events"),
  VIEW_SEATS("View seats"),
  HOLD_A_SEAT("Hold a seat"),
  CONFIRM_A_BOOKING("Confirm a booking"),
  ALL_OPERATIONS("All operations");

  private final String label;

  Operation(String label) {
    this.label = label;
  }
```

- The five constants are what a target applies to: the four SeatWave operations, or `ALL_OPERATIONS`. Each carries a `label`, the exact text used on the sheet.
- The Javadoc line `The list is closed on purpose` explains the design: a target for an operation that does not exist cannot be tested.
- `Optional` is imported for the lookup below.

```java
  /**
   * Returns the text used for the operation on the sheet.
   *
   * @return the label, for example "Hold a seat"
   */
  public String label() {
    return label;
  }

  /**
   * Finds the operation a cell names.
   *
   * @param cellText the text of the Operation cell
   * @return the operation, or empty when the text names none; upper and lower case are equal
   * @throws NullPointerException if the text is null
   */
  public static Optional<Operation> fromLabel(String cellText) {
    Objects.requireNonNull(cellText, "cellText");
    return Arrays.stream(values())
        .filter(operation -> operation.label.equalsIgnoreCase(cellText.strip()))
        .findFirst();
  }
}
```

- `fromLabel` turns the text of a cell into a constant, or says "none". The return type `Optional<Operation>` is a box that holds one `Operation` or nothing. It makes "not found" part of the type, so a caller cannot forget it and get a null.
- `Objects.requireNonNull(cellText, "cellText")` rejects null.
- `Arrays.stream(values())` streams all five constants. `.filter(operation -> ...)` keeps those for which the lambda is true. A **lambda** `operation -> operation.label.equalsIgnoreCase(cellText.strip())` is a small unnamed function: the part before `->` is its parameter, the part after is its result.
- `cellText.strip()` removes spaces at both ends. `equalsIgnoreCase` compares ignoring upper and lower case, so `hold a seat` and `Hold A Seat` both find `HOLD_A_SEAT`.
- `.findFirst()` returns an `Optional` holding the first match, or empty.
- The lambda uses `cellText`, a parameter of the enclosing method. A lambda may use such a variable only if it never changes after being set (it is **effectively final**). The code never reassigns it.

**`QualityAttribute.java`**

```java
package io.plainround.seatwave.targets.sheet;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The dimension of "how well" that a target sets a level on.
 *
 * <p>The first seven are the quality attributes the module teaches. Security, privacy and
 * compliance are on the list because they are non-functional requirements too.
 */
public enum QualityAttribute {
  LATENCY("Latency"),
  THROUGHPUT("Throughput"),
  SCALABILITY("Scalability"),
  AVAILABILITY("Availability"),
  RELIABILITY("Reliability"),
  EFFICIENCY("Efficiency"),
  MAINTAINABILITY("Maintainability"),
  SECURITY("Security"),
  PRIVACY("Privacy"),
  COMPLIANCE("Compliance");

  private final String label;

  QualityAttribute(String label) {
    this.label = label;
  }
```

- Ten constants, each with a label. The Javadoc says the first seven are the attributes the module teaches, and security, privacy and compliance are added because they are non-functional requirements too.
- The fields and the constructor are as in `Operation`.

```java
  /**
   * Returns the text used for the attribute on the sheet.
   *
   * @return the label, for example "Availability"
   */
  public String label() {
    return label;
  }

  /**
   * Finds the attribute a cell names.
   *
   * @param cellText the text of the Attribute cell
   * @return the attribute, or empty when the text names none; upper and lower case are equal
   * @throws NullPointerException if the text is null
   */
  public static Optional<QualityAttribute> fromLabel(String cellText) {
    Objects.requireNonNull(cellText, "cellText");
    return Arrays.stream(values())
        .filter(attribute -> attribute.label.equalsIgnoreCase(cellText.strip()))
        .findFirst();
  }
}
```

- `fromLabel` is the same lookup, with the lambda parameter named `attribute`. Two classes have the same pattern on purpose: each is small enough to read alone.

### `CellText.java`: when a cell is empty

```java
package io.plainround.seatwave.targets.sheet;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** The rule for when a cell of the sheet counts as empty. */
final class CellText {

  // Words people write when they do not know yet. Each one is a missing field, not a value.
  private static final Set<String> PLACEHOLDERS =
      Set.of("tbd", "todo", "n/a", "na", "none", "unknown", "?", "-");

  private CellText() {}
```

- `final class CellText` has no `public`, so it is **package-private**: only classes of the `sheet` package can see it. It is an internal helper.
- `Set.of("tbd", "todo", ...)` makes an unmodifiable set. A **set** holds items with no duplicates and answers "is this inside?" quickly. The words are the placeholders people write when they do not know a value yet. They are in lower case because the check below lowers the text first.
- `private CellText() {}` stops anyone from creating an object. The class is a home for one static method.

```java
  /**
   * Tells whether a cell holds no real value.
   *
   * @param cellText the text of the cell
   * @return true when the text is blank or is a placeholder such as "TBD"
   */
  static boolean isMissing(String cellText) {
    Objects.requireNonNull(cellText, "cellText");
    String normalized = cellText.strip().toLowerCase(Locale.ROOT);
    return normalized.isEmpty() || PLACEHOLDERS.contains(normalized);
  }
}
```

- `isMissing` is `static` and package-private (no `public`).
- `cellText.strip().toLowerCase(Locale.ROOT)` removes spaces and lowers the case. `Locale.ROOT` means "no country rules". Without it, a computer set to Turkish turns `I` into a dotless `i`, and a comparison could fail on that machine.
- `normalized.isEmpty() || PLACEHOLDERS.contains(normalized)` is true when the cell is empty or one of the placeholder words: `-`, `?`, `n/a`, `na`, `none`, `todo`, `tbd`, `unknown`.

### `SheetRow.java`: one raw row

```java
package io.plainround.seatwave.targets.sheet;

import java.util.List;
import java.util.Objects;

/**
 * One data row of the Markdown table, before it is checked.
 *
 * @param lineNumber the one-based line of the row in the file, for error messages
 * @param cells the text of each cell, stripped of surrounding spaces
 */
public record SheetRow(int lineNumber, List<String> cells) {

  /**
   * Creates a row.
   *
   * @throws NullPointerException if the list or one of its elements is null
   */
  public SheetRow {
    Objects.requireNonNull(cells, "cells");
    cells = List.copyOf(cells);
  }
```

- `record SheetRow(int lineNumber, List<String> cells)` holds the one-based line of the row in the file (the first line is 1, which is what a text editor shows) and the cell texts.
- The compact constructor checks that the list is not null and replaces it with `List.copyOf(cells)`: an unmodifiable copy. `List.copyOf` also throws a `NullPointerException` if one cell is null, which the Javadoc says.

```java
  /**
   * Tells whether the row has one cell per column.
   *
   * @return true when the number of cells equals the number of columns
   */
  public boolean hasEveryColumn() {
    return cells.size() == SheetColumn.values().length;
  }

  /**
   * Returns the text of one cell.
   *
   * @param column the column to read
   * @return the text of the cell in that column
   * @throws IllegalStateException if the row does not have one cell per column
   */
  public String cell(SheetColumn column) {
    Objects.requireNonNull(column, "column");
    if (!hasEveryColumn()) {
      throw new IllegalStateException("line " + lineNumber + " does not have every column");
    }
    return cells.get(column.ordinal());
  }
}
```

- `hasEveryColumn()` compares the number of cells with `SheetColumn.values().length`, which is 8. For an array, `.length` (no parentheses) is the size.
- `cell(SheetColumn column)` returns the text in a given column. It first checks the row has every column. If not, it throws `IllegalStateException`. **Why an exception here and not a `Result`?** A caller must call `hasEveryColumn()` first. Calling `cell` on a short row is a programmer error, not bad user input, so an exception is the right tool. The validator always checks first, so users never see it.
- `cells.get(column.ordinal())` reads the cell at the column's position.

### `TargetScope.java`, `Target.java`: records that carry values

**`TargetScope.java`**

```java
package io.plainround.seatwave.targets.sheet;

import java.util.Objects;

/**
 * What a target is about: an operation and one quality attribute of it.
 *
 * @param operation the operation the target applies to
 * @param attribute the quality attribute the target sets a level on
 */
public record TargetScope(Operation operation, QualityAttribute attribute) {

  /**
   * Creates a scope.
   *
   * @throws NullPointerException if the operation or the attribute is null
   */
  public TargetScope {
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(attribute, "attribute");
  }
}
```

- A scope is "an operation plus one quality attribute of it". The compact constructor rejects null for both. An enum value can never be "empty", so a non-null check is all that is needed.

**`Target.java`**

```java
package io.plainround.seatwave.targets.sheet;

import java.util.Objects;

/**
 * One row of the requirements sheet: a complete, testable target.
 *
 * <p>Each of the three parts validates its own fields, so a target with a missing metric,
 * percentile, threshold, window, owner or reason cannot exist. Immutable and safe to share between
 * threads.
 *
 * @param scope the operation and the quality attribute
 * @param measure the metric, the percentile or rate, the threshold and the window
 * @param ownership the owner and the business reason
 */
public record Target(TargetScope scope, TargetMeasure measure, TargetOwnership ownership) {

  /**
   * Creates a target.
   *
   * @throws NullPointerException if a part is null
   */
  public Target {
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(measure, "measure");
    Objects.requireNonNull(ownership, "ownership");
  }
}
```

- A `Target` has three parts: a scope, a measure and an ownership. Each part is a record that checks itself, so a `Target` is complete by construction: nobody can build one with a missing owner. The constructor only guards against null parts.
- `Target` is this project's type. The name does not clash with anything in Java's standard library.

### `TargetMeasure.java`: the heart of the rules

**Lines 1 to 22: header and Javadoc**

```java
package io.plainround.seatwave.targets.sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * How a target is measured: the metric, the percentile or rate, the threshold and the window.
 *
 * <p>A measure with a blank metric, no percentile or rate, a threshold without a number or no
 * window cannot be built. Immutable and safe to share between threads.
 *
 * @param metric what is measured and where, for example "Response time at the load balancer"
 * @param percentileOrRate a percentile such as "p99", or the kind of number, such as "rate"
 * @param threshold the line to beat, with its unit, for example "under 300 ms"
 * @param window the period over which the target is judged
 */
public record TargetMeasure(
    String metric, String percentileOrRate, String threshold, String window) {
```

- The record has four `String` components: the metric (what is measured), the percentile or rate (how the number is summarized), the threshold (the line to beat) and the window (the period over which it is judged).
- A **percentile** is a position in a sorted list of measurements: p99 is the value 99 percent of requests are faster than. A rate kind is a word for numbers that are not percentiles, such as `count` or `peak`.
- When a record header is longer than the line limit, the formatter wraps it. That is the only reason the header is on two lines.

**Lines 24 to 29: the rules as constants**

```java
  // A percentile such as p50, p95, p99 or p99.9.
  private static final Pattern PERCENTILE = Pattern.compile("p[0-9]{1,2}(\\.[0-9]+)?");
  // For targets that are not a percentile, the kind of number the threshold is.
  private static final Set<String> RATE_KINDS =
      Set.of("rate", "count", "peak", "mean", "max", "min", "ratio");
  private static final Pattern CONTAINS_A_DIGIT = Pattern.compile(".*[0-9].*");
```

- `Pattern.compile("p[0-9]{1,2}(\\.[0-9]+)?")` matches the letter `p`, then one or two digits, then optionally a dot and more digits. `p50`, `p95`, `p99` and `p99.9` match. `p100` does not (three digits), and `p` alone does not. In Java text the regex `\.` must be written `\\.`.
- `RATE_KINDS` is a set of seven words: `rate`, `count`, `peak`, `mean`, `max`, `min`, `ratio`.
- `Pattern.compile(".*[0-9].*")` matches any text that contains at least one digit. The first `.*` means "any characters, any number of them", the `[0-9]` is one digit, and the last `.*` is any characters again. Combined with `matches()` (which must match the whole text), it asks: "is there a digit anywhere?".

**Lines 31 to 46: the constructor**

```java
  /**
   * Creates a measure.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if a field breaks one of the rules of {@link #problemsIn}
   */
  public TargetMeasure {
    List<RowProblem> problems = problemsIn(metric, percentileOrRate, threshold, window);
    if (!problems.isEmpty()) {
      throw new IllegalArgumentException("not a complete measure: " + problems);
    }
    metric = metric.strip();
    percentileOrRate = percentileOrRate.strip();
    threshold = threshold.strip();
    window = window.strip();
  }
```

- The compact constructor first asks `problemsIn(...)` for the list of problems. If the list is not empty it throws `IllegalArgumentException` with the list in the message. Printing a list calls its `toString`, so the message names every problem.
- Then it **normalizes**: `metric = metric.strip();` and the same for the other three. In a compact constructor, assigning to the parameter changes the value that is stored. So the stored values never have spaces at the ends.
- Order: validate first, then normalize. A null makes `problemsIn` throw `NullPointerException` before anything else.

**Lines 48 to 84: `problemsIn`, the rules in one place**

```java
  /**
   * Lists what stops four cells from being a complete measure.
   *
   * <p>The constructor and the sheet validator both call this method, so the rules exist once.
   *
   * @param metric the text of the Metric cell
   * @param percentileOrRate the text of the Percentile cell
   * @param threshold the text of the Threshold cell
   * @param window the text of the Window cell
   * @return every problem found, or an empty list when the four cells form a complete measure
   * @throws NullPointerException if a cell is null
   */
  public static List<RowProblem> problemsIn(
      String metric, String percentileOrRate, String threshold, String window) {
    Objects.requireNonNull(metric, "metric");
    Objects.requireNonNull(percentileOrRate, "percentileOrRate");
    Objects.requireNonNull(threshold, "threshold");
    Objects.requireNonNull(window, "window");
    List<RowProblem> problems = new ArrayList<>();
    if (CellText.isMissing(metric)) {
      problems.add(new RowProblem.MissingField(SheetColumn.METRIC));
    }
    if (CellText.isMissing(percentileOrRate)) {
      problems.add(new RowProblem.MissingField(SheetColumn.PERCENTILE));
    } else if (!isPercentileOrRate(percentileOrRate)) {
      problems.add(new RowProblem.NotAllowed(SheetColumn.PERCENTILE, percentileOrRate.strip()));
    }
    // "Fast" and "highly available" contain no digit, so nobody can test them.
    if (!CONTAINS_A_DIGIT.matcher(threshold).matches()) {
      problems.add(new RowProblem.ThresholdWithoutNumber(threshold.strip()));
    }
    // A number with no window cannot be measured: there is no start and no end.
    if (CellText.isMissing(window)) {
      problems.add(new RowProblem.MissingField(SheetColumn.WINDOW));
    }
    return List.copyOf(problems);
  }
```

- This method is `public static`, so the validator can call it without building a record. The constructor calls it too. **The rules therefore exist once.**
- The four `requireNonNull` lines make a null a programmer error.
- `List<RowProblem> problems = new ArrayList<>();` collects every problem. The method never stops at the first. That lets the report list all faults of a row.
- Metric: `CellText.isMissing(metric)` adds `MissingField(SheetColumn.METRIC)`.
- Percentile: the `if ... else if` has two cases. An empty or placeholder cell is a `MissingField`. A filled cell that is neither a percentile nor a rate kind is `NotAllowed` with the text. `else if` runs only when the first condition is false.
- Threshold: `!CONTAINS_A_DIGIT.matcher(threshold).matches()` is true when there is no digit. The comment explains: "Fast" and "highly available" contain no digit, so nobody can test them. `matcher(...)` creates a matcher object for the text, and `matches()` asks whether the whole text fits the pattern. A threshold cell is **not** checked for emptiness separately: an empty string has no digit, so it is reported as `ThresholdWithoutNumber`.
- Window: `CellText.isMissing(window)` adds `MissingField(SheetColumn.WINDOW)`. The comment says why: a number with no window has no start and no end.
- `return List.copyOf(problems);` returns an unmodifiable list. An empty list means all four cells are fine.

**Lines 86 to 99: the helpers**

```java
  /**
   * Lists the words the Percentile cell accepts besides a percentile.
   *
   * @return the kinds in alphabetical order
   */
  public static List<String> rateKinds() {
    return RATE_KINDS.stream().sorted().toList();
  }

  private static boolean isPercentileOrRate(String cellText) {
    String normalized = cellText.strip().toLowerCase(Locale.ROOT);
    return PERCENTILE.matcher(normalized).matches() || RATE_KINDS.contains(normalized);
  }
}
```

- `rateKinds()` returns the seven words sorted alphabetically: `[count, max, mean, min, peak, rate, ratio]`. A `Set` has no promised order, so `.sorted()` is needed to get the same text in every error message. `RATE_KINDS.stream().sorted().toList()` is a stream pipeline: open, sort, collect.
- `isPercentileOrRate` lowers the case and tests both rules. `PERCENTILE.matcher(normalized).matches()` is true for a percentile; `RATE_KINDS.contains(normalized)` is true for a kind. `||` means "or".
- It is `private static`: only this class uses it, and it needs no object.

### `TargetOwnership.java`: owner and reason

**Lines 1 to 31**

```java
package io.plainround.seatwave.targets.sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Who answers for a target and why the business wants it.
 *
 * <p>An ownership with a blank owner or a blank reason cannot be built. Immutable and safe to share
 * between threads.
 *
 * @param owner the role that agreed to the target and answers for it
 * @param reason the business reason the target exists
 */
public record TargetOwnership(String owner, String reason) {

  /**
   * Creates an ownership.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if a field breaks one of the rules of {@link #problemsIn}
   */
  public TargetOwnership {
    List<RowProblem> problems = problemsIn(owner, reason);
    if (!problems.isEmpty()) {
      throw new IllegalArgumentException("not a complete ownership: " + problems);
    }
    owner = owner.strip();
    reason = reason.strip();
  }
```

- The same pattern in small: a compact constructor calls `problemsIn`, throws if it returned anything, then strips both fields.
- The constructor's Javadoc says what is thrown: `NullPointerException` for a null, `IllegalArgumentException` for an empty field.

**Lines 33 to 54**

```java
  /**
   * Lists what stops two cells from being a complete ownership.
   *
   * @param owner the text of the Owner cell
   * @param reason the text of the Reason cell
   * @return every problem found, or an empty list when both cells are filled
   * @throws NullPointerException if a cell is null
   */
  public static List<RowProblem> problemsIn(String owner, String reason) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(reason, "reason");
    List<RowProblem> problems = new ArrayList<>();
    // A target nobody owns is a target nobody agreed to.
    if (CellText.isMissing(owner)) {
      problems.add(new RowProblem.MissingField(SheetColumn.OWNER));
    }
    if (CellText.isMissing(reason)) {
      problems.add(new RowProblem.MissingField(SheetColumn.REASON));
    }
    return List.copyOf(problems);
  }
}
```

- The comment gives the rule: a target nobody owns is a target nobody agreed to. Both the owner and the reason are required, and placeholders such as `TBD` count as missing.
- The two `if` statements are independent, so both can fire. A row with no owner and no reason gets two problems.

### `RowProblem.java`, `RowRejection.java`, `SheetError.java`: failures as values

**`RowProblem.java`**

```java
package io.plainround.seatwave.targets.sheet;

/**
 * One reason a row of the sheet is not a complete target.
 *
 * <p>These are expected failures of a draft sheet, so the validator returns them as values. The
 * interface is sealed, so a switch over it must handle every reason.
 */
public sealed interface RowProblem {

  /**
   * The row does not have one cell per column.
   *
   * @param expected the number of columns of the sheet
   * @param found the number of cells in the row
   */
  record WrongCellCount(int expected, int found) implements RowProblem {}

  /**
   * A cell is blank or holds a placeholder such as "TBD".
   *
   * @param column the column of the empty cell
   */
  record MissingField(SheetColumn column) implements RowProblem {}

  /**
   * A cell holds a value that is not on the list its column allows.
   *
   * @param column the column of the cell
   * @param found the text of the cell
   */
  record NotAllowed(SheetColumn column, String found) implements RowProblem {}

  /**
   * The threshold has no digit in it, as in "fast" or "highly available".
   *
   * @param found the text of the Threshold cell
   */
  record ThresholdWithoutNumber(String found) implements RowProblem {}

  /**
   * An earlier row already sets a target for the same measure.
   *
   * @param firstLineNumber the line of the earlier row
   */
  record DuplicateOfLine(int firstLineNumber) implements RowProblem {}
}
```

- A sealed interface with five records. Each is one reason a row is not a complete target. `WrongCellCount` carries the expected and found numbers. `MissingField` carries the column. `NotAllowed` carries the column and the text found. `ThresholdWithoutNumber` carries the threshold text. `DuplicateOfLine` carries the line of the earlier row.
- Because the interface is sealed, a switch over it must cover all five. `SheetReport.explain` does this with no default.
- These are values returned to the caller, not exceptions: a draft sheet with problems is normal.

**`RowRejection.java`**

```java
package io.plainround.seatwave.targets.sheet;

import java.util.List;
import java.util.Objects;

/**
 * A row that is not a complete target, with every reason found.
 *
 * @param lineNumber the one-based line of the row in the file
 * @param rowLabel a short name for the row, for example "Hold a seat, Latency"
 * @param problems the reasons, at least one
 */
public record RowRejection(int lineNumber, String rowLabel, List<RowProblem> problems) {

  /**
   * Creates a rejection.
   *
   * @throws NullPointerException if the label, the list or one of its elements is null
   * @throws IllegalArgumentException if the list of problems is empty
   */
  public RowRejection {
    Objects.requireNonNull(rowLabel, "rowLabel");
    Objects.requireNonNull(problems, "problems");
    if (problems.isEmpty()) {
      throw new IllegalArgumentException("a rejection needs at least one problem");
    }
    problems = List.copyOf(problems);
  }
}
```

- One bad row with its line number, a short label (for example `Hold a seat, Latency`) and the list of problems.
- The constructor refuses an empty problem list: a rejection with no reason is meaningless. It then copies the list so that nobody can change it later. The Javadoc mentions that `List.copyOf` throws on a null element.

**`SheetError.java`**

```java
package io.plainround.seatwave.targets.sheet;

import java.util.List;
import java.util.Objects;

/**
 * Why a requirements sheet is not accepted.
 *
 * <p>The interface is sealed, so a switch over it must handle every reason.
 */
public sealed interface SheetError {

  /** The file has no Markdown table. */
  record NoTable() implements SheetError {}

  /**
   * The first row of the table is not the expected header.
   *
   * @param lineNumber the one-based line of the header row
   */
  record WrongHeader(int lineNumber) implements SheetError {}

  /** The table has a header and no rows under it. */
  record NoTargets() implements SheetError {}
```

- Four reasons a whole sheet is refused. `NoTable`, `WrongHeader(int lineNumber)` and `NoTargets` are simple. They are records with empty bodies `{}` or a single number.

```java
  /**
   * At least one row is not a complete target.
   *
   * @param acceptedCount the number of rows that are complete targets
   * @param rejections the rejected rows in file order, at least one
   */
  record RowsRejected(int acceptedCount, List<RowRejection> rejections) implements SheetError {

    /**
     * Creates the error.
     *
     * @throws NullPointerException if the list or one of its elements is null
     */
    public RowsRejected {
      Objects.requireNonNull(rejections, "rejections");
      rejections = List.copyOf(rejections);
    }
  }
}
```

- `RowsRejected(int acceptedCount, List<RowRejection> rejections)` has its own compact constructor, indented inside the interface because it is a nested record. It checks the list for null and replaces it with an unmodifiable copy. The count of accepted rows lets the report print `Targets accepted: 0` (or 2, or whatever) first.

### `MarkdownSheetReader.java`: the only code that knows Markdown

**Lines 1 to 23: constants**

```java
package io.plainround.seatwave.targets.sheet;

import com.github.sviperll.result4j.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reads the first Markdown table of a file into rows.
 *
 * <p>This class is the only place that knows the sheet is written in Markdown. Everything after it
 * works on {@link SheetRow} values. Stateless and safe to call from any thread.
 */
public final class MarkdownSheetReader {

  private static final String CELL_DIVIDER = "|";
  // The row under the header, such as |---|:---:|, which carries no data.
  private static final Pattern SEPARATOR_ROW = Pattern.compile("\\|(\\s*:?-{3,}:?\\s*\\|)+");
  // A negative limit keeps trailing empty cells: an empty cell is a missing field to report.
  private static final int KEEP_EMPTY_CELLS = -1;

  private MarkdownSheetReader() {}
```

- `CELL_DIVIDER = "|"` is the character between cells.
- `SEPARATOR_ROW = Pattern.compile("\\|(\\s*:?-{3,}:?\\s*\\|)+")` is the pattern for the dash row, such as `|---|---|` or `| :---: | ---: |`. Read the regular expression piece by piece, remembering that `\\` in Java text is one backslash in the regex:
  - `\|` is a literal vertical bar (a plain `|` in a regex means "or", so it needs the backslash).
  - `(` ... `)+` is a group that repeats one or more times.
  - `\s*` is any number of whitespace characters.
  - `:?` is an optional colon, used in Markdown to set alignment.
  - `-{3,}` is three or more dashes.
  - `:?\s*\|` is another optional colon, spaces, and a closing bar.
- `KEEP_EMPTY_CELLS = -1` is a magic number given a name. It is the second argument of `String.split`: a negative limit tells Java to keep empty strings at the end. By default `split` drops them, so `| a | |` would lose its last empty cell. An empty cell is a missing field to report, so it must stay.
- `private MarkdownSheetReader() {}` makes the class a bag of static methods.

**Lines 25 to 50: `read`**

```java
  /**
   * Extracts the data rows of the sheet.
   *
   * <p>Lines that do not start with a cell divider are ignored, so a title and notes may surround
   * the table.
   *
   * @param lines the lines of the file, in order
   * @return the rows under the header, or the reason the table cannot be used
   * @throws NullPointerException if the list is null
   */
  public static Result<List<SheetRow>, SheetError> read(List<String> lines) {
    Objects.requireNonNull(lines, "lines");
    List<SheetRow> tableRows = tableRowsOf(lines);
    if (tableRows.isEmpty()) {
      return Result.error(new SheetError.NoTable());
    }
    SheetRow header = tableRows.getFirst();
    if (!isExpectedHeader(header)) {
      return Result.error(new SheetError.WrongHeader(header.lineNumber()));
    }
    List<SheetRow> dataRows = tableRows.subList(1, tableRows.size());
    if (dataRows.isEmpty()) {
      return Result.error(new SheetError.NoTargets());
    }
    return Result.success(List.copyOf(dataRows));
  }
```

- `read` returns `Result<List<SheetRow>, SheetError>`: the rows, or a reason.
- It first extracts every table row, then applies three checks, each returning an error value: no table at all (`NoTable`), a header that is wrong (`WrongHeader` with the header's line number), and a table with a header and no rows (`NoTargets`).
- `tableRows.getFirst()` is the first row. The list is not empty here, because the earlier check returned.
- `tableRows.subList(1, tableRows.size())` is a **view** of the list from index 1 (the second element) to the end. It is not a copy, so `List.copyOf(dataRows)` makes the real unmodifiable copy for the result.
- The Javadoc line `Lines that do not start with a cell divider are ignored` is why a title and notes may surround the table.

**Lines 52 to 71: `tableRowsOf` and `cellsOf`**

```java
  private static List<SheetRow> tableRowsOf(List<String> lines) {
    List<SheetRow> tableRows = new ArrayList<>();
    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      String line = lines.get(lineIndex).strip();
      if (line.startsWith(CELL_DIVIDER) && !SEPARATOR_ROW.matcher(line).matches()) {
        tableRows.add(new SheetRow(lineIndex + 1, cellsOf(line)));
      }
    }
    return tableRows;
  }

  private static List<String> cellsOf(String line) {
    int end = line.length() > 1 && line.endsWith(CELL_DIVIDER) ? line.length() - 1 : line.length();
    String withoutOuterDividers = line.substring(1, end);
    List<String> cells = new ArrayList<>();
    for (String cell : withoutOuterDividers.split(Pattern.quote(CELL_DIVIDER), KEEP_EMPTY_CELLS)) {
      cells.add(cell.strip());
    }
    return cells;
  }
```

- The `for` loop is the classic form: `int lineIndex = 0; lineIndex < lines.size(); lineIndex++`. It runs with the index 0, then 1, then 2, up to the last line. The index is needed to compute the line number.
- `lines.get(lineIndex).strip()` takes one line and removes spaces at both ends.
- `line.startsWith(CELL_DIVIDER) && !SEPARATOR_ROW.matcher(line).matches()` keeps lines that start with `|` and are not the dash row. `&&` means "and".
- `lineIndex + 1` converts the zero-based index to a one-based line number, the number a person sees in an editor.
- `cellsOf` works out where the last cell ends. `line.length() > 1 && line.endsWith(CELL_DIVIDER) ? line.length() - 1 : line.length()` is a conditional expression: if the line is longer than 1 character and ends with `|`, the end is one character earlier, which drops the closing bar. Otherwise the end is the line length. The `length() > 1` guard matters for a line that is only `|`, where `substring(1, 0)` would throw.
- `line.substring(1, end)` cuts out the middle, without the first `|` and the closing `|`.
- `withoutOuterDividers.split(Pattern.quote(CELL_DIVIDER), KEEP_EMPTY_CELLS)` splits at each `|`. `split` expects a regular expression and `|` means "or" in one. `Pattern.quote("|")` wraps the text so it is taken literally.
- `cells.add(cell.strip())` strips each cell and collects it. For the line `| Hold a seat | Latency | Response time |  | fast |  |  |  |` the result is eight cells and some of them are empty strings.

**Lines 73 to 85: `isExpectedHeader`**

```java
  private static boolean isExpectedHeader(SheetRow header) {
    List<String> expected = SheetColumn.headings();
    if (header.cells().size() != expected.size()) {
      return false;
    }
    for (int columnIndex = 0; columnIndex < expected.size(); columnIndex++) {
      if (!expected.get(columnIndex).equalsIgnoreCase(header.cells().get(columnIndex))) {
        return false;
      }
    }
    return true;
  }
}
```

- It gets the eight expected headings from `SheetColumn.headings()`. If the row has a different number of cells, it returns false.
- The loop compares cell by cell with `equalsIgnoreCase`. Any difference returns false at once. If the loop ends, every heading matched, so it returns true.
- Changing the columns therefore means changing only `SheetColumn`. The header check reads that list.

### `SheetValidator.java`: rows to targets

**Lines 1 to 20**

```java
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
```

- The imports include `HashMap` (an implementation of `Map`, a lookup table from keys to values) and `Optional`.
- The class is stateless, with a private constructor.

**Lines 22 to 48: `validate`**

```java
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
```

- `validate(List<SheetRow> rows)` returns the targets or a `SheetError`. An empty row list is refused with `NoTargets`.
- `Map<MeasureKey, Integer> firstLineOfMeasure = new HashMap<>();` remembers, for each measure seen so far, the line that first set it. The type `Integer` is the object form of `int`, needed because a map holds objects. It is a local variable in `validate`, so each call has its own and the class stays thread-safe.
- `toTarget(row)` returns `Result<Target, RowRejection>`. `.flatMap(target -> rejectDuplicate(row, target, firstLineOfMeasure))` continues only if the row was fine: then it checks for a duplicate. If `toTarget` already returned an error, the duplicate check is skipped and the error passes through. The lambda uses `row`, the loop variable, and `firstLineOfMeasure`: both are effectively final (never reassigned), which a lambda requires.
- The `switch` statement then matches the final `Result`. A `Success` adds the target to `targets`. An `Error` adds the rejection to `rejections`. The record patterns pull the value out into `target` and `rejection`. (`target` in the lambda and `target` in the pattern are different variables in different scopes.)
- The loop does not stop at a bad row. It goes on, so every row is judged.
- At the end: `rejections.isEmpty() ? Result.success(List.copyOf(targets)) : Result.error(new SheetError.RowsRejected(targets.size(), rejections))`. The comment says: fail safe. One incomplete row rejects the whole sheet, so the design does not start on it.

**Lines 50 to 77: `toTarget`**

```java
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
```

- A short row is refused first. A row without eight cells gets one problem, `WrongCellCount(8, found)`, and the label `row`, since the operation and attribute cells may not exist. This must come first because `row.cell(...)` would throw for a short row.
- `Operation.fromLabel(row.cell(SheetColumn.OPERATION))` returns an `Optional`. If it is empty, `notAllowedOrMissing` decides which problem to add. The same is done for the attribute.
- `problems.addAll(measureProblems(row))` and `problems.addAll(TargetOwnership.problemsIn(...))` append all problems of the measure and the ownership. Because the rules are in `problemsIn`, the validator and the records can never disagree.
- If any problem was found, the method returns `Result.error(new RowRejection(row.lineNumber(), labelOf(row), problems))`.
- Otherwise it builds the record. `operation.orElseThrow()` unwraps the `Optional`. The no-argument form throws `NoSuchElementException` if empty, which cannot happen here: an empty one would have added a problem and returned above. `measureOf(row)` and `new TargetOwnership(...)` run their constructor checks a second time. The ARCHITECTURE file calls this trade-off trivial: the check runs twice per good row.

**Lines 79 to 88: `rejectDuplicate`**

```java
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
```

- `firstLineOfMeasure.putIfAbsent(key, value)` puts the pair in the map only if the key is not there. It returns the old value, or `null` if the key was absent. So `firstLine == null` means this is the first row with that measure: the row is fine and the line number is now recorded.
- If `firstLine` is not null, a row on an earlier line already set a target for the same measure. A new `DuplicateOfLine(firstLine)` problem is returned and the later row is rejected. The comment says why: two thresholds for one measure cannot both be the target, so someone has to choose.
- `firstLine` is an `Integer`, so it can be null. `new RowProblem.DuplicateOfLine(firstLine)` unboxes it into an `int` automatically, which is safe because it is non-null at that point.

**Lines 90 to 115: four small helpers**

```java
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
```

- `notAllowedOrMissing` picks the right problem for an operation or attribute cell that matched nothing. A blank or placeholder cell is `MissingField`. Any other text is `NotAllowed`, carrying the text so the report can quote it.
- `measureProblems` and `measureOf` pass the four cells in the order the record expects: metric, percentile, threshold, window. Keeping them as separate methods avoids repeating four long lines.
- `labelOf` builds the short name of a row for messages, for example `Hold a seat, Latency`. It joins two cell texts with `", "`.

**Lines 117 to 129: the `MeasureKey` record**

```java
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
```

- `private record MeasureKey(...)` is a record nested in a class and hidden from outside. It exists only to be a key in the map. A nested record is implicitly `static`.
- Records have `equals` and `hashCode` written by Java, based on all components. Two `MeasureKey` values are equal when their scope, metric, percentile and window are equal. The threshold, owner and reason are not part of the key. That is exactly the definition in the README: the same operation, attribute, metric, percentile and window, ignoring case.
- The method `of` lowers the case of the three text fields with `Locale.ROOT`, so `P99` and `p99` count as the same. `target.scope()` is a record of enums and already compares by value.
- `static MeasureKey of(Target target)` is a **factory method**: a static method that builds an object. It has no `public`, but it can still be called from the enclosing class.

### `SheetReport.java`: the verdict as text

**Lines 1 to 29: `accepted`**

```java
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
```

- `accepted` builds a two-line string by joining pieces with `+`. A string literal can be split over lines with `+`. The line break inside the text is written `\n`.
- The ending `" a window, an owner and a reason.\n"` starts with a space because the previous piece ends with a comma and no space.

**Lines 31 to 56: `rejected`**

```java
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
```

- A switch expression assigns its value to `detail`. Each case builds the text for one kind of `SheetError`.
- `case SheetError.NoTable _ ->` uses the **unnamed pattern** `_`: the branch does not need the object, so it is not named. The same for `NoTargets _`. `WrongHeader wrongHeader` is named because the branch reads its line number.
- `SheetColumn.headings()` is a list. Adding a list to a string with `+` calls `toString` on it, which prints `[Operation, Attribute, ...]`. That is why the output reads `the header must be [Operation, Attribute, Metric, ...]`.
- The last case calls `rejectedRows`.
- After the switch: `detail + "SHEET REJECTED: fix every row above before the design starts.\n"`. So every rejected report ends with the same line.

**Lines 58 to 72: `rejectedRows`**

```java
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
```

- First line: `Targets accepted: ` and the count of good rows.
- Then one line per rejected row: `REJECTED line 7 (Hold a seat, Latency): problem; problem`. The long chain of `.append(...)` is the builder style: each call returns the same `StringBuilder`.
- `rejection.problems().stream().map(SheetReport::explain).toList()` turns each problem into a sentence. `String.join("; ", list)` glues them with a semicolon.
- `text.toString()` returns the whole text.

**Lines 74 to 100: `explain` and `explainNotAllowed`**

```java
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
```

- `explain` is a switch expression with no default branch over the sealed `RowProblem`. Each case writes a sentence. For `MissingField`, `missing.column().heading().toLowerCase(Locale.ROOT) + " is missing"` makes `percentile is missing` and `owner is missing`. For `ThresholdWithoutNumber _` the unnamed pattern is used again.
- `explainNotAllowed` has a second switch, this time on the enum `SheetColumn`. Cases for an enum are written with the bare constant names (`OPERATION`), not `SheetColumn.OPERATION`. The last case lists five constants with commas: `case METRIC, THRESHOLD, WINDOW, OWNER, REASON -> "a value"`. A switch expression on an enum must cover every constant or have a `default`. All eight are covered, so adding a column to `SheetColumn` makes this switch stop compiling until a case is added.
- The assembled message reads like `percentile 'fast' is not allowed: use p50, p95, p99 or one of [count, max, mean, min, peak, rate, ratio]`.

### `SheetCli.java`: reading the file and ending the program

**Lines 1 to 25**

```java
package io.plainround.seatwave.targets.sheet;

import com.github.sviperll.result4j.Result;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command-line entry point of the sheet validator.
 *
 * <p>It reads one Markdown file, prints the verdict and exits with 0 when the sheet is accepted, 1
 * when it is rejected and 2 when the file cannot be used at all.
 */
public final class SheetCli {

  private static final int EXIT_SHEET_REJECTED = 1;
  // Exit status 2 is the shell convention for "the command was used incorrectly".
  private static final int EXIT_BAD_INPUT = 2;
  // A requirements sheet is a page or two of text. One mebibyte is far above any real sheet and
  // keeps a wrong path, such as a video file, from being read into memory.
  private static final long MAX_SHEET_BYTES = 1L << 20;

  private SheetCli() {}
```

- `Path`, `Files` and `StandardCharsets` are the standard types for files and text encodings. A **charset** is the rule that turns bytes into characters. UTF-8 is the common one.
- Three exit statuses: 1 for a rejected sheet, 2 for a file that cannot be used.
- `1L << 20` is a **left shift**: it moves the bits of the number 1 twenty places left, which is 2 to the power 20, which is 1,048,576 (I printed it in `jshell`). The `L` makes it a `long`. That is one mebibyte: 1,048,576 bytes. The comment gives the reason: a requirements sheet is a page or two of text, and the limit stops a wrong path (such as a video file) from being read into memory.

**Lines 27 to 47: `main`**

```java
  /**
   * Runs the validator.
   *
   * @param arguments exactly one argument: the path of the sheet
   */
  public static void main(String[] arguments) {
    if (arguments.length != 1) {
      System.err.println("usage: validateSheet <requirements-sheet.md>");
      System.exit(EXIT_BAD_INPUT);
    }
    Path sheetFile = Path.of(arguments[0]);
    List<String> lines = readLinesOrExit(sheetFile);
    switch (MarkdownSheetReader.read(lines).flatMap(SheetValidator::validate)) {
      case Result.Success<List<Target>, SheetError>(List<Target> targets) ->
          System.out.print(SheetReport.accepted(targets));
      case Result.Error<List<Target>, SheetError>(SheetError error) -> {
        System.out.print(SheetReport.rejected(error));
        System.exit(EXIT_SHEET_REJECTED);
      }
    }
  }
```

- It needs exactly one argument: the path. Otherwise it prints usage and ends with 2.
- `Path.of(arguments[0])` turns the text into a `Path` object. `arguments[0]` is the first element of the array.
- `readLinesOrExit(sheetFile)` returns the lines or ends the program.
- `MarkdownSheetReader.read(lines).flatMap(SheetValidator::validate)` chains the two steps. `read` returns `Result<List<SheetRow>, SheetError>`. `flatMap` runs `validate`, a static method taken as `SheetValidator::validate`, only if the first step succeeded. The two steps share the error type `SheetError`, so the errors line up. The result has the type `Result<List<Target>, SheetError>`.
- The switch matches `Success` and `Error` as in the availability program. On success it prints the verdict, and exits normally with 0. On error it prints and calls `System.exit(EXIT_SHEET_REJECTED)`.
- The verdict goes to standard output (`System.out`) for both outcomes, because it is the program's result. Usage and file errors go to standard error (`System.err`).

**Lines 49 to 66: `readLinesOrExit`**

```java
  private static List<String> readLinesOrExit(Path sheetFile) {
    try {
      if (!Files.isRegularFile(sheetFile)) {
        System.err.println("not a file: " + sheetFile);
        System.exit(EXIT_BAD_INPUT);
      }
      if (Files.size(sheetFile) > MAX_SHEET_BYTES) {
        System.err.println("larger than " + MAX_SHEET_BYTES + " bytes: " + sheetFile);
        System.exit(EXIT_BAD_INPUT);
      }
      return Files.readAllLines(sheetFile, StandardCharsets.UTF_8);
    } catch (IOException unreadable) {
      // The file exists but cannot be read as UTF-8 text. Nothing here can repair that.
      System.err.println("cannot read " + sheetFile + ": " + unreadable.getMessage());
      System.exit(EXIT_BAD_INPUT);
      throw new IllegalStateException("unreachable after exit", unreadable);
    }
  }
}
```

- `try { ... } catch (IOException unreadable) { ... }` is how Java handles an **exception that a method may throw**. `IOException` is a **checked exception**: the compiler forces every caller to catch it or declare it. `Files.size` and `Files.readAllLines` can throw it, for example when the file cannot be decoded as UTF-8.
- `Files.isRegularFile(sheetFile)` is false for a missing file or a directory. The program prints `not a file: <path>` and exits with 2.
- `Files.size(sheetFile) > MAX_SHEET_BYTES` rejects a file over the limit.
- `Files.readAllLines(sheetFile, StandardCharsets.UTF_8)` reads the whole file as a list of lines, decoding as UTF-8.
- In the `catch` block the message uses `unreadable.getMessage()`, the text the exception carries.
- The last line, `throw new IllegalStateException("unreachable after exit", unreadable);`, exists because the compiler does not know that `System.exit` never returns. Without a `return` or `throw`, the method would not compile. The second argument keeps the original exception as the **cause**. At run time the line is never reached.
- The two `System.exit` calls inside the `try` body also never return in practice. After them the code would go on to the next line only if `exit` came back, which it does not.

## Try it yourself

Run these in `seatwave-targets/`.

1. An accepted sheet:

```
./gradlew -q validateSheet --args='sheets/arena-on-sale.md'
```

I got `Targets accepted: 24` and `SHEET ACCEPTED: every target has a metric, a percentile or rate, a threshold, a window, an owner and a reason.` The other shipped sheets also pass: `sheets/club.md` gave 6, `sheets/lab1-solution.md` gave 8.

2. A rejected sheet, with every bad row listed:

```
./gradlew -q validateSheet --args='sheets/lab1-draft.md'
```

I got:

```
Targets accepted: 0
REJECTED line 5 (List events, Latency): percentile is missing
REJECTED line 6 (View seats, Latency): window is missing
REJECTED line 7 (Hold a seat, Latency): percentile is missing; threshold has no number; window is missing; owner is missing; reason is missing
REJECTED line 8 (Confirm a booking, Availability): threshold has no number; owner is missing
SHEET REJECTED: fix every row above before the design starts.
```

Open `sheets/lab1-draft.md` and match each message to its cell. Line 7 shows all the rules firing at once: the row has an empty percentile, the text `fast` (no digit), and three empty cells.

3. A file that is not a sheet:

```
./gradlew -q validateSheet --args='README.md'
```

I got `REJECTED line 20: the header must be [Operation, Attribute, Metric, Percentile, Threshold, Window, Owner, Reason]`. The README contains a Markdown table (the dependency table) at line 20, and the reader takes the first table it finds.

4. A path that does not exist:

```
./gradlew -q validateSheet --args='nope.md'
```

I got `not a file: nope.md`, and Gradle reported a failed task with exit value 2.

5. Break a sheet yourself: copy `sheets/club.md` to `/tmp/mine.md`, change one threshold to `fast`, and run `./gradlew -q validateSheet --args='/tmp/mine.md'`. Expect `threshold has no number` for that line.

## Mistakes people make

- **Stopping at the first error.** The validator collects all problems of a row and all bad rows, so one run shows all the work.
- **Throwing exceptions for expected bad input.** A draft sheet with gaps is normal. The package returns `Result` values and keeps exceptions for programmer errors such as a null.
- **Letting `String.split` drop empty trailing cells.** The limit `-1` keeps them.
- **Forgetting that `|` is special in a regular expression.** `Pattern.quote` and `\\|` handle it.
- **Comparing text without caring about case or locale.** The code uses `equalsIgnoreCase` and `toLowerCase(Locale.ROOT)`.
- **Writing the same rule twice.** `problemsIn` is the one place where each rule lives. The records call it in their constructors and the validator calls it before.
- **Accepting a duplicate silently.** Keeping the last of two conflicting targets would hide a disagreement. The later row is rejected.

## Check yourself

1. Why does `MarkdownSheetReader` pass `-1` to `split`? Answer: a negative limit keeps empty cells at the end of the row, and an empty cell is a missing field the report must show.
2. Two rows have the same operation, attribute, metric and window. One says `p99` and the other says `P99`. Are they duplicates? Answer: yes. The key is built from scope, metric, percentile and window in lower case, so `p99` and `P99` are equal. The threshold is not part of the key, so the later row gets `DuplicateOfLine`.
3. Why does `SheetRow.cell` throw an exception, while `MarkdownSheetReader.read` returns an error value? Answer: a short row reaching `cell` means the caller broke the rule of checking first, which is a programmer error. A bad table in a file is expected input.
4. What does `putIfAbsent` return when the key is new? Answer: `null`, and it stores the pair.
5. Why must the switch in `explainNotAllowed` list all eight columns? Answer: a switch expression over an enum must be exhaustive. With no `default`, adding a column makes the code stop compiling until a case is written.

## Further reading

- `java.lang.Enum` and enum types, https://docs.oracle.com/en/java/javase/25/language/enums.html
- `java.util.Optional`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Optional.html
- `java.util.regex.Pattern`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/regex/Pattern.html
- `java.nio.file.Files`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/nio/file/Files.html
- `java.util.Map` (`putIfAbsent`), https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Map.html
- Pattern matching for `switch`, https://docs.oracle.com/en/java/javase/25/language/pattern-matching.html
