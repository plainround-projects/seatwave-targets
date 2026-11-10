# result4j 1.2.0 (a library for results that are values)

## What it is

result4j is a small Java library. Its main type is `Result<R, E>`. A **result type** is an object that stands for "either this worked and here is the value, or it failed and here is the reason". The letter `R` is the type of the good value and `E` is the type of the reason.

A `Result` is one of exactly two things. `Result.Success` holds a good value. `Result.Error` holds an error value. The caller must look at which one it got. The Java compiler can check that the caller handled both.

The Java standard library has no such type. `Optional` says "a value or nothing" but cannot say why there is nothing. An exception says why, but it jumps out of the normal flow. `Result` fills the gap.

## Why this repository uses it

Bad input is normal in this program. A person types `chain(a=99.9` with a missing bracket. A draft sheet has an empty cell. These are **expected failures**. They should come back as ordinary values that the program can list, count and print, not as exceptions that unwind the program.

So the parser and the sheet reader return `Result<Topology, TopologyError>` and `Result<List<SheetRow>, SheetError>`. **Exceptions are kept for programmer errors**: a null argument, an empty chain built in code, a column read from a short row. Those mean the code is wrong, not the user. The line is: if a person can cause it by typing, it is a `Result`. If only a bug can cause it, it is an exception.

The dependency is declared in `gradle/libs.versions.toml` as `result4j = "1.2.0"`, used in `build.gradle.kts` as `implementation(libs.result4j)`, and pinned in `gradle.lockfile`. `implementation` means the library is needed to compile and run the main code (see `gradle.md`). The Maven coordinates are `com.github.sviperll:result4j`. The license is Apache-2.0. I read the class files of the 1.2.0 jar: they are Java class-file version 65, which is Java 21, so the library runs on the Java 25 used here.

## Where it appears

| File | What it does with `Result` |
|---|---|
| `availability/TopologyParser.java` | Every parsing method returns a `Result`. Uses `Result.success`, `Result.error`, `flatMap` and a pattern-matching `switch`. |
| `availability/AvailabilityCli.java` | Matches the parser's result and prints a report or an error. |
| `sheet/MarkdownSheetReader.java` | Returns `Result<List<SheetRow>, SheetError>`. |
| `sheet/SheetValidator.java` | Returns `Result<List<Target>, SheetError>` and uses `Result<Target, RowRejection>` per row, with `flatMap` and a `switch`. |
| `sheet/SheetCli.java` | Chains the reader and the validator with `flatMap` and matches the outcome. |

No other class imports the library. `Availability`, `Component`, `Topology`, the sheet records and the labs never see a `Result`.

## How it works underneath

`Result<R, E>` is a **sealed interface** with two permitted types, both **records**. I confirmed with `javap -v` that the compiled interface lists `Result$Success` and `Result$Error` under `PermittedSubclasses`. A sealed interface lists every type that may implement it, so the compiler knows the full list. Records are short classes that only carry values (see `java.md`).

- `Result.Success<R, E>` is a record with one component, `result`. Its `toString` prints `Success[result=5]`.
- `Result.Error<R, E>` is a record with one component, `error`. Its `toString` prints `Error[error=negative]`.

Both have the same two type parameters. A `Success` simply never uses `E`, and an `Error` never uses `R`. That is why a pattern is written `Result.Success<Topology, TopologyError>(Topology child)`: both type parameters are spelled out.

Everything is in memory. A `Result` is an ordinary object on the heap. There is no thread, no network and no file. Making one costs one small object.

The methods this repository uses:

- `Result.success(value)` makes a `Success`.
- `Result.error(reason)` makes an `Error`.
- `result.flatMap(function)` runs `function` on the good value and returns the `Result` that it returns. If `result` is an `Error`, `function` is not called and the same error is returned. This is how two steps that can both fail are joined: the second runs only if the first worked.
- A `switch` over the `Result` with record patterns `Result.Success<...>(value)` and `Result.Error<...>(error)`. The compiler knows there are only two cases, so no `default` is needed. If a library update added a third case, the switch would stop compiling.

The library offers more (`map`, `mapError`, `andThen`, `isError`, `recoverError`, `orOnErrorThrow`, `peekError`, `ifSuccess` and others). This repository does not use them.

I ran a small experiment in `jshell` with the 1.2.0 jar on the class path:

```
Success[result=5] | Error[error=negative]
Error[error=too big]        (ok.flatMap(...) where the function returned an error)
Error[error=negative]       (bad.flatMap(...): the function never ran)
Success[result=10]          (ok.map(n -> n * 2))
Error[error=negative]       (bad.map(n -> n * 2))
false true                  (ok.isError(), bad.isError())
got 5                       (a switch with both record patterns)
```

One more observation from that run: `Result.success(null)` is accepted without an exception. The library does not forbid a null value. This repository never passes null.

## Walkthrough of this repository's use

### Building results: `TopologyParser.parse`

```java
  /**
   * Parses one topology expression.
   *
   * @param expression the text typed by a person
   * @return the topology, or the reason the text could not be read
   * @throws NullPointerException if the expression is null
   */
  public static Result<Topology, TopologyError> parse(String expression) {
    Objects.requireNonNull(expression, "expression");
    if (expression.isBlank()) {
      return Result.error(new TopologyError.EmptyExpression());
    }
    if (expression.length() > MAX_EXPRESSION_LENGTH) {
      return Result.error(
          new TopologyError.ExpressionTooLong(expression.length(), MAX_EXPRESSION_LENGTH));
    }
    TopologyParser parser = new TopologyParser(expression);
    return parser.readTopology(0).flatMap(parser::requireEndOfInput);
  }
```

- The return type `Result<Topology, TopologyError>` says in one line what the method gives: a topology, or a reason.
- `Result.error(new TopologyError.EmptyExpression())` makes the failure. `TopologyError` is a sealed interface of six record types, one per reason (see `java-availability-walkthrough.md`).
- `Objects.requireNonNull` stays an exception: null is a programmer error.
- `parser.readTopology(0).flatMap(parser::requireEndOfInput)` joins two steps. The first reads the tree. The second checks that nothing is left. `parser::requireEndOfInput` is a method reference to a method that takes the good value (a `Topology`) and returns a `Result<Topology, TopologyError>`. If the first step failed, the second does not run.

### Passing errors up: the `readGroup` switch

```java
  private Result<Topology, TopologyError> readGroup(String keyword, int depth) {
    if (!CHAIN_KEYWORD.equals(keyword) && !PARALLEL_KEYWORD.equals(keyword)) {
      return Result.error(new TopologyError.UnknownGroup(keyword));
    }
    position++; // Step over the opening parenthesis that the caller saw.
    List<Topology> children = new ArrayList<>();
    do {
      switch (readTopology(depth + 1)) {
        case Result.Success<Topology, TopologyError>(Topology child) -> children.add(child);
        case Result.Error<Topology, TopologyError>(TopologyError error) -> {
          return Result.error(error);
        }
      }
      skipSpaces();
    } while (consumeIf(','));
    if (!consumeIf(')')) {
      return unexpected(position, "',' or ')'");
    }
    return Result.success(
        CHAIN_KEYWORD.equals(keyword)
            ? new Topology.Chain(children)
            : new Topology.Parallel(children));
  }
```

- `switch (readTopology(depth + 1))` calls the child reader and examines its `Result` at once.
- `case Result.Success<Topology, TopologyError>(Topology child) -> children.add(child);` is a **record pattern**. It tests that the value is a `Success`, takes the record's component out and names it `child`, all in one step.
- `case Result.Error<Topology, TopologyError>(TopologyError error) -> { return Result.error(error); }` takes the reason out and returns a new `Error` from `readGroup`. The first bad child ends the group. The reason goes up unchanged, so the top-level caller sees the first error with its position.
- There is no `default`. The switch is a statement over a sealed type with patterns, and Java requires it to be exhaustive. This is the guarantee: you cannot forget the error case.
- At the end, `Result.success(...)` wraps the finished chain or parallel group.

### More builders: `readComponent`, `requireEndOfInput` and `unexpected`

```java
  private Result<Topology, TopologyError> readComponent(String name) {
    skipSpaces();
    int numberStart = position;
    while (Character.isDigit(peek()) || peek() == '.') {
      position++;
    }
    String percentText = expression.substring(numberStart, position);
    // Checking the shape first means BigDecimal can never throw on what a person typed.
    if (!DECIMAL_NUMBER.matcher(percentText).matches()) {
      return unexpected(numberStart, "a percent such as 99.9");
    }
    BigDecimal percent = new BigDecimal(percentText);
    if (!Availability.isPercent(percent)) {
      return Result.error(new TopologyError.PercentOutOfRange(percentText));
    }
    return Result.success(
        new Topology.Single(new Component(name, Availability.ofPercent(percent))));
  }

  private Result<Topology, TopologyError> requireEndOfInput(Topology topology) {
    skipSpaces();
    return position == expression.length()
        ? Result.success(topology)
        : unexpected(position, "the end of the expression");
  }

  private static Result<Topology, TopologyError> unexpected(int position, String expected) {
    return Result.error(new TopologyError.Unexpected(position, expected));
  }
```

- `readComponent` returns `unexpected(...)` for a wrong shape, `Result.error(new TopologyError.PercentOutOfRange(percentText))` for a percent outside 0 to 100, and `Result.success(new Topology.Single(...))` when it is good. All three have the same type.
- `requireEndOfInput` uses a conditional expression to pick `Result.success(topology)` or an error in one `return`.
- `unexpected` is a helper that builds the error `Result`. Its type parameters are inferred from the declared return type.

### Matching at the edge: `AvailabilityCli.main`

```java
  public static void main(String[] arguments) {
    if (arguments.length == 0) {
      System.err.println("usage:   availability <topology>");
      System.err.println("example: availability 'chain(a=99.9, parallel(b=99.5, b=99.5))'");
      System.exit(EXIT_BAD_INPUT);
    }
    switch (TopologyParser.parse(String.join(" ", arguments))) {
      case Result.Success<Topology, TopologyError>(Topology topology) ->
          System.out.print(AvailabilityReport.render(topology));
      case Result.Error<Topology, TopologyError>(TopologyError error) -> {
        System.err.println("invalid topology: " + explain(error));
        System.exit(EXIT_BAD_INPUT);
      }
    }
  }
```

- `TopologyParser.parse(String.join(" ", arguments))` returns the `Result`. The `switch` then decides what the program does.
- On `Success`, it prints the report. On `Error`, it prints one line to standard error and exits with status 2.
- This is where an expected failure is finally turned into behavior. Inside the parser it was only a value.

### A second reader: `MarkdownSheetReader.read`

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

- The method has three ways to fail and one to succeed, and each is a visible `return`. `Result.error(new SheetError.NoTable())`, `WrongHeader` with the line number, `NoTargets`, and `Result.success(List.copyOf(dataRows))`.
- Each return is a normal value. There is no hidden path out of the method for bad input.

### The validator: a `Result` per row, a `Result` for the sheet

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

- The method has two layers. The inner layer, `toTarget(row).flatMap(target -> rejectDuplicate(...))`, returns a `Result<Target, RowRejection>` for each row. The error type is `RowRejection`, which lists all problems of the row. The lambda inside `flatMap` runs only if `toTarget` succeeded.
- The `switch` then collects: successes go to `targets` and errors go to `rejections`. The loop does not stop at the first error. That is the point of using values: an exception would end the loop and the user would learn about one bad row per run.
- The outer layer returns `Result<List<Target>, SheetError>`: all targets, or `SheetError.RowsRejected` with the count and every rejection.
- The two layers use two different error types. `flatMap` requires the error type to stay the same inside one chain, and it does: `RowRejection` throughout the per-row chain. The two layers meet in the loop, which turns many `RowRejection` values into one `SheetError`.

```java
  private static Result<Target, RowRejection> toTarget(SheetRow row) {
    if (!row.hasEveryColumn()) {
      RowProblem wrongCount =
          new RowProblem.WrongCellCount(SheetColumn.values().length, row.cells().size());
      return Result.error(new RowRejection(row.lineNumber(), "row", List.of(wrongCount)));
    }
```

- A short row returns `Result.error(new RowRejection(...))` at once, before anything reads its cells.

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

- `rejectDuplicate` returns `Result.success(target)` for a first-time measure and `Result.error(...)` for a duplicate. Its signature has the same `Result<Target, RowRejection>` type as `toTarget`, which is what makes `flatMap` fit.

### Chaining two steps: `SheetCli.main`

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

- `MarkdownSheetReader.read(lines).flatMap(SheetValidator::validate)` joins the reader and the validator. The reader gives `Result<List<SheetRow>, SheetError>`. `SheetValidator::validate` takes a `List<SheetRow>` and returns `Result<List<Target>, SheetError>`. Both share `SheetError`, so `flatMap` fits.
- If the table is missing, `validate` never runs, and the `NoTable` error goes straight to the `switch`.
- The `Success` branch prints the accepted report. The `Error` branch prints the rejected report and exits with status 1.

## Why expected failures are values and exceptions are kept for bugs

| Question | Result value | Exception |
|---|---|---|
| Who can cause it? | A user typing, a draft file. | A bug in the calling code. |
| Does the compiler make the caller deal with it? | Yes: a pattern `switch` over the sealed type must handle both cases. | Not for unchecked exceptions such as `NullPointerException`. |
| Can the program continue and collect more problems? | Yes. | No: the method is left. |
| Cost | One small object. | A stack trace, which costs more to build. |

Two examples in this repository show the line. `Availability` throws `IllegalArgumentException` for 1.2 because a programmer passed a bad number. `TopologyParser.parse` returns `Error[PercentOutOfRange[101]]` for the text `chain(a=101)` because a person typed it. The parser checks the range first, so the user text never reaches the throwing path.

## Try it yourself

1. See a success and an error as values. Compile first so that the classes exist, then open the Java shell with the jar on the class path:

```
./gradlew -q compileJava
jshell --class-path build/classes/java/main:$HOME/.gradle/caches/modules-2/files-2.1/com.github.sviperll/result4j/1.2.0/1d73de9cf73a3a26f53be521c0cfdaf8cebdd96a/result4j-1.2.0.jar
```

Then type these two lines (one at a time). `import io.plainround.seatwave.targets.availability.*;` and then `System.out.println(TopologyParser.parse("chain(a=99.9, b=99.5)"));`. I got a `Success[result=Chain[parts=[Single[component=Component[name=a, ...` line. Then `System.out.println(TopologyParser.parse("chain(a=101)"));` printed `Error[error=PercentOutOfRange[percentText=101]]`. The long hash in the path is where Gradle stored the jar on my machine. Run `find ~/.gradle -name 'result4j-1.2.0.jar'` to find yours. Type `/exit` to leave.

2. See that the first error wins. In the same shell, `System.out.println(TopologyParser.parse("chain(a=99.9"));` printed `Error[error=Unexpected[position=12, expected=',' or ')']]`.

3. See the library in the build:

```
grep -n result4j gradle/libs.versions.toml build.gradle.kts gradle.lockfile
```

I got the version line `result4j = "1.2.0"`, the library entry, the `implementation(libs.result4j)` line and the lockfile entry `com.github.sviperll:result4j:1.2.0=compileClasspath,runtimeClasspath,testCompileClasspath,testRuntimeClasspath`.

4. See the error path end to end from the terminal: `./gradlew -q availability --args='foo(a=1)'` prints `invalid topology: unknown group 'foo'; use chain(...) or parallel(...)` and the task ends with exit value 2.

## Mistakes people make

- **Ignoring the error case.** A `Result` that is never examined hides a failure. The sealed `switch` makes the compiler insist on both cases.
- **Using `Result` for bugs.** A null argument is not something to handle at run time. It should crash early with a clear message.
- **Mixing error types in one `flatMap` chain.** The error type must be the same on both sides. Convert with `mapError` if two steps use different types. This repository avoids it by using one error type per chain.
- **Nesting results.** `map` with a function that returns a `Result` gives a `Result` inside a `Result`. `flatMap` avoids that.
- **Passing null into `Result.success`.** The library accepts it. A later method that expects a value would then fail far away.

## Check yourself

1. What are the two cases of `Result`? Answer: `Result.Success`, which holds the value in `result`, and `Result.Error`, which holds the reason in `error`.
2. What does `flatMap` do when the result is an `Error`? Answer: it does not call the function and returns the same error.
3. Why does the `switch` in `readGroup` need no `default`? Answer: `Result` is sealed with exactly two permitted types, both handled, so the compiler knows the switch is exhaustive.
4. Which of these is a `Result` in this repository and which is an exception: an empty topology text, and `new Availability(Double.NaN)`? Answer: the empty text is `Error[EmptyExpression]` because a person can type it. The NaN is an `IllegalArgumentException` because only a bug in the program can produce it.
5. Why does `SheetValidator.validate` not stop at the first bad row? Answer: it collects each row's `Result` in a loop and reports all rejections, so the author sees every fix in one run.

## Further reading

- result4j project page, https://github.com/sviperll/result4j (version 1.2.0 on Maven Central, `com.github.sviperll:result4j`).
- Sealed classes and interfaces, https://docs.oracle.com/en/java/javase/25/language/sealed-classes-and-interfaces.html
- Record patterns and pattern matching for `switch`, https://docs.oracle.com/en/java/javase/25/language/pattern-matching.html
- Records, https://docs.oracle.com/en/java/javase/25/language/records.html
