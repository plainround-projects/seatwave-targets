# Java 25 (the language and the standard library)

## What it is

Java is a programming language. You write **classes** in text files that end in `.java`. A program called the **compiler** (`javac`) turns them into **bytecode**, a compact instruction format stored in `.class` files. A second program, the **Java Virtual Machine** (JVM), reads the bytecode and runs it. The JVM is the same on a Mac, a Windows PC and a Linux server, so the same bytecode runs on all of them.

Java is **statically typed**. Every variable, parameter and return value has a type, such as `int` (a whole number) or `String` (text). The compiler checks the types before the program runs. A mistake such as passing text where a number is expected stops the build instead of crashing later.

The **JDK** (Java Development Kit) is the download that contains the compiler, the JVM, and the **standard library**: ready-made classes such as `String`, `List`, `BigDecimal` and `Duration`. This repository uses **JDK 25**. The machine used to write these notes had Eclipse Temurin 25.0.2. JDK 25 is a long-term-support release, which means it receives fixes for years.

## Why this repository uses it

The repository is the hands-on project of Module 2 of a system design course. It must turn "the system should be available" into numbers a computer can calculate. Java 25 gives the language features that make this small program safe to read:

- **Records** make number-carrying types short, and their constructors reject bad numbers.
- **Sealed interfaces** list every possible shape of a topology, so the compiler proves that every `switch` handles all of them.
- **Pattern-matching `switch`** reads a value's shape and pulls its parts out in one step.
- **`BigDecimal`** moves a decimal point exactly, so 99.9 percent becomes 0.999 with no rounding error.
- The standard library covers everything else, so the project has only one runtime library (`result4j.md`).

## Where it appears

| Path | What it does with Java |
|---|---|
| `src/main/java/io/plainround/seatwave/targets/availability/` | Nine files: the availability value, the topology, the parser, the calculator, the downtime maths, the report and the command-line program. See `java-availability-walkthrough.md`. |
| `src/main/java/io/plainround/seatwave/targets/sheet/` | Sixteen files: the Markdown reader, the row validator, the target records, the error types and the command-line program. See `java-sheet-walkthrough.md`. |
| `src/main/java/io/plainround/seatwave/targets/labs/` | Two files with `main` methods. See `java-labs-walkthrough.md`. |
| `src/test/java/io/plainround/seatwave/targets/` | The tests, in Java too. See `junit.md`. |
| `build.gradle.kts` | Tells Gradle to compile with Java 25, with every warning treated as an error. See `gradle.md`. |

## How it works underneath

**From text to a running program.** You write `Availability.java`. The compiler reads it and writes `Availability.class`. Gradle does this for you: `./gradlew -q compileJava` put 47 files of this kind under `build/classes/java/main/` on my machine (one class file per class, per record and per nested record). To start the program, a JVM process starts, loads the class you name, and calls its `main` method.

**A process** is one running copy of a program, with its own memory. The JVM is one process. When `main` returns, the process ends with **exit status** 0, which means success. A program may call `System.exit(n)` to end at once with another number. By convention a non-zero status means failure. This repository uses 1 and 2 for two kinds of failure.

**Memory.** Two areas matter. The **stack** holds the method calls in progress, with their local variables. Each call adds a frame and each return removes one. A method that calls itself too deep fills the stack and fails with `StackOverflowError`. The **heap** holds objects. `new Availability(0.5)` makes an object on the heap. A variable holds a **reference**, which is like an address that points to the object. The JVM frees objects that no variable can reach any more. This is called **garbage collection**. You never free memory by hand.

**The class path** is the list of places where the JVM looks for compiled classes and libraries (jar files, which are zip files of classes). Gradle builds the right class path for each task.

### The basic words

**Class.** A class is a blueprint for objects. It holds fields (data) and methods (code). Everything in Java lives in a class. A record, an enum and an interface are special kinds of class.

**Package.** A package is a named group of classes, written with dots: `io.plainround.seatwave.targets.availability`. The name must match the folder path under `src/main/java`. It keeps names from clashing. The first line of a file is `package ...;`:

```java
package io.plainround.seatwave.targets.availability;

import java.util.Objects;
```

**Import.** `import java.util.Objects;` lets the file write `Objects` instead of `java.util.Objects`. Classes in the same package need no import. Classes in `java.lang` (such as `String`, `Math`, `Double`, `IllegalArgumentException`) are imported for you.

**Method.** A method is a named block of code that may take **parameters** (inputs) and may **return** a value. `public static Availability ofPercent(BigDecimal percent)` is a method named `ofPercent`. It takes one `BigDecimal` and returns an `Availability`. `void` means it returns nothing.

**`static`.** A static member belongs to the class, not to one object. You call a static method with the class name: `Availability.ofPercent(...)`. A non-static method needs an object: `a.bothUp(b)`.

**`final`.** On a variable or field, `final` means "assigned once, never changed". On a class, `final` means "no other class may extend it". On a method, "no subclass may override it". A **constant** is a `static final` field:

```java
  /** A part that never works. */
  public static final Availability ALWAYS_DOWN = new Availability(0.0);

  /** A part that never fails. */
  public static final Availability ALWAYS_UP = new Availability(1.0);
```

**Access: `public`, `private`, and nothing.** `public` means any code can use it. `private` means only the same class can. No keyword means **package-private**: only classes in the same package can. This repository keeps helpers `private` and uses package-private for things the tests need (for example `TopologyParser.MAX_EXPRESSION_LENGTH`).

**Constructor.** A constructor is special code that runs when you write `new`. It has the name of the class and no return type. It sets up the object. `private Downtime() {}` is a constructor that is private and empty, so nobody can create a `Downtime` object. A class of only static methods uses this to say "do not instantiate me".

**Exception.** An exception is an object that says "something went wrong". `throw new IllegalArgumentException("...")` stops the current method and travels up the chain of callers until a `catch` block handles it. If none does, the program ends and prints a **stack trace**, the list of calls that led to the failure. There are two families. **Unchecked** exceptions (`NullPointerException`, `IllegalArgumentException`, `IllegalStateException`) are for programmer errors, and the compiler does not force you to handle them. **Checked** exceptions (such as `IOException`) are for outside troubles like a file that cannot be read, and the compiler forces you to catch them or declare them. This repository uses unchecked exceptions for bugs and the `Result` type for expected failures (`result4j.md`).

**Null.** `null` means "no object". Calling a method on null throws `NullPointerException`. This repository rejects null at the door with `Objects.requireNonNull`.

**Types.** `int` is a 32-bit whole number. `long` is a 64-bit whole number. `double` is a 64-bit number with a decimal point. `boolean` is `true` or `false`. `char` is one character. `String` is text, and it is immutable: operations return new strings. `List<String>` is a list of strings: the `<String>` part is a **generic**, which tells the compiler what the list holds.

**Comments.** `// text` runs to the end of the line. `/* ... */` can span lines. `/** ... */` is a **Javadoc** comment (below).

## Walkthrough of this repository's use

Each feature below says which Java version added it. This repository sets `options.release = 25`, so all of them are available. The table lists the minimum version for each, which I confirmed by compiling a small file with `javac --release N` where a language change was involved, or from the API documentation (the "since" tag) for library methods.

| Feature | First final in | Where this repository uses it |
|---|---|---|
| Switch expressions (`switch` that gives a value, `->` arms, `yield`) | Java 14 | `AvailabilityCalculator.evaluate` |
| Records and compact constructors | Java 16 | `Availability`, `Component`, `Topology.Single`, `SheetRow` and many more |
| Pattern matching for `instanceof` | Java 16 | `AvailabilityReport.render` |
| `Stream.toList()` | Java 16 | `SheetColumn.headings` |
| Sealed interfaces | Java 17 | `Topology`, `TopologyError`, `RowProblem`, `SheetError` |
| Pattern-matching `switch` and record patterns | Java 21 | `AvailabilityCalculator`, `TopologyParser`, `SheetReport`, the CLIs |
| `List.getFirst()` | Java 21 | `AvailabilityCalculator.weakestPart`, `MarkdownSheetReader.read` |
| The unnamed pattern `_` | Java 22 | `AvailabilityCli.explain`, `SheetReport.rejected` |
| `Optional`, streams, lambdas, `Collectors.joining` | Java 8 | `Operation`, `QualityAttribute`, `AvailabilityReport` |
| `List.of`, `Set.of`, `private static` interface methods | Java 9 | `CellText`, `Topology` |
| `List.copyOf`, no-argument `Optional.orElseThrow()` | Java 10 | `Topology`, `SheetValidator` |
| `String.isBlank()`, `String.strip()`, `Optional.isEmpty()`, `Path.of` | Java 11 | `Component`, `CellText`, `SheetValidator`, `SheetCli` |

I confirmed by compiling: a `switch` with `case A _ ->` fails with `unnamed variables are not supported in -source 21` under `--release 21` and compiles under `--release 22`. Record patterns in a `switch` fail under `--release 20` and compile under `--release 21`. `List.getFirst()` compiles under `--release 21` and fails with `cannot find symbol` under `--release 17`.

### Records and compact constructors

A **record** is a class whose job is to carry values. You list the **components** in the header. Java writes the private final fields, the constructor, the accessor methods, `equals`, `hashCode` and `toString`.

```java
/**
 * The share of a window in which a part or a whole system works, as a fraction from 0 to 1.
 *
 * <p>A fraction of 0.999 means 99.9 percent. The constructor rejects every value outside 0 to 1,
 * and NaN, so an availability outside that range cannot exist anywhere in the program. Instances
 * are immutable and safe to share between threads.
 *
 * @param fraction the share of the window that is good, from 0 to 1 inclusive
 */
public record Availability(double fraction) {
```

- `public record Availability(double fraction)` makes a class with one field, `fraction`. The accessor is `fraction()` (no `get`). I confirmed with `javap` that the compiled class `extends java.lang.Record`, is `final`, and has `toString`, `hashCode`, `equals` and `fraction()`. In `jshell`, `new Availability(0.999)` printed `Availability[fraction=0.999]`, and `equals` on two records with the same component values gave `true`.
- A record is immutable: the fields cannot change after the constructor. Immutable objects are safe to share between threads.

A **compact constructor** is the record form of a constructor. It has no parameter list. The components are available as variables. You write checks, and you may reassign the variables. Java copies them into the fields when the constructor ends.

```java
  public Availability {
    // Written as a negated range test so that NaN, which fails every comparison, is rejected too.
    if (!(fraction >= 0.0 && fraction <= 1.0)) {
      throw new IllegalArgumentException(
          "an availability must be between 0 and 1, but was " + fraction);
    }
  }
```

- This one only validates. It throws if the number is outside 0 to 1. The `!( ... )` form catches NaN, as explained in `java-availability-walkthrough.md`.

```java
  record Chain(List<Topology> parts) implements Topology {

    /**
     * Creates a chain.
     *
     * @throws NullPointerException if the list or one of its elements is null
     * @throws IllegalArgumentException if the list is empty
     */
    public Chain {
      parts = copyOfNonEmpty(parts, "a chain");
    }
```

- This one **normalizes**: `parts = copyOfNonEmpty(parts, "a chain");` reassigns the parameter, and Java stores the new list in the field. The caller's list is replaced by an unmodifiable copy.

### Sealed interfaces with nested records

An **interface** names a set of abilities and has no state. A **sealed** interface also names who may implement it. The compiler knows the whole list.

```java
public sealed interface Topology {

  /**
   * A single part.
   *
   * @param component the part and its availability
   */
  record Single(Component component) implements Topology {

    /**
     * Creates a single-part topology.
     *
     * @throws NullPointerException if the component is null
     */
    public Single {
      Objects.requireNonNull(component, "component");
    }
  }
```

- `sealed interface Topology` has no `permits` clause: the three records are declared inside the same file, so the compiler infers the list. I confirmed that the compiled class has a `PermittedSubclasses` attribute with `Single`, `Chain` and `Parallel`.
- The records are **nested** inside the interface. A record nested in an interface is implicitly `public` and `static`. They are named `Topology.Single` from outside.
- `implements Topology` says "a `Single` is a kind of `Topology`".
- The point: the shape of the data is written once, and the compiler checks every use of it.

Two more sealed interfaces carry the failures:

```java
public sealed interface TopologyError {

  /** The expression is empty or holds only spaces. */
  record EmptyExpression() implements TopologyError {}
```

- `record EmptyExpression() implements TopologyError {}` is a record with no components and an empty body. It is a marker for "this happened".

### Enums with fields

An **enum** is a type with a fixed list of values. Each value is a constant object. An enum can have fields, a constructor and methods.

```java
public enum SheetColumn {
  OPERATION("Operation"),
  ATTRIBUTE("Attribute"),
  METRIC("Metric"),
  PERCENTILE("Percentile"),
  THRESHOLD("Threshold"),
  WINDOW("Window"),
  OWNER("Owner"),
  REASON("Reason");

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
```

- `OPERATION("Operation")` creates the constant `OPERATION` by calling the constructor with the text. The last constant ends with `;`.
- The constructor has no access keyword because an enum constructor is always private.
- `values()` (not written in the file) is added by Java to every enum. It returns an array of all constants in declaration order. `ordinal()` returns the position of a constant, starting at 0. `SheetRow.cell` uses it to find the cell of a column.
- Enums are available since Java 5.

### `switch` expressions, pattern matching and record patterns

A **switch statement** runs code. A **switch expression** produces a value. Its arms use `->` and do not fall through. A multi-statement arm uses braces and `yield` to give the value.

```java
  public static Availability evaluate(Topology topology) {
    Objects.requireNonNull(topology, "topology");
    return switch (topology) {
      case Topology.Single single -> single.component().availability();
      case Topology.Chain chain -> {
        // A product is a running multiplication: start at "always up" and fold in each part.
        Availability everyPartUp = Availability.ALWAYS_UP;
        for (Topology part : chain.parts()) {
          everyPartUp = everyPartUp.bothUp(evaluate(part));
        }
        yield everyPartUp;
      }
      case Topology.Parallel parallel -> {
        // The group is down only when every copy is down, so start at "always down".
        Availability anyCopyUp = Availability.ALWAYS_DOWN;
        for (Topology copy : parallel.copies()) {
          anyCopyUp = anyCopyUp.eitherUp(evaluate(copy));
        }
        yield anyCopyUp;
      }
    };
  }
```

- `case Topology.Single single ->` is a **type pattern**: if the value is a `Single`, bind it to the variable `single` (already typed as `Single`). No cast.
- The switch covers all three types of the sealed interface and has no `default`. The compiler checks **exhaustiveness**. Add a fourth shape and this method stops compiling.
- `yield everyPartUp;` gives the value of the arm.

A **record pattern** takes a record apart in the pattern:

```java
      switch (readTopology(depth + 1)) {
        case Result.Success<Topology, TopologyError>(Topology child) -> children.add(child);
        case Result.Error<Topology, TopologyError>(TopologyError error) -> {
          return Result.error(error);
        }
      }
```

- `Result.Success<Topology, TopologyError>(Topology child)` matches a `Success` and binds the component to `child`. Java can infer the type arguments of a record pattern, but this code writes them out in full for clarity.
- A `return` inside an arm of a switch **statement** leaves the whole method.

The **unnamed pattern** `_` says "there is a value here and I do not need it":

```java
  private static String explain(TopologyError error) {
    return switch (error) {
      case TopologyError.EmptyExpression _ -> "the expression is empty";
      case TopologyError.ExpressionTooLong tooLong ->
```

- `TopologyError.EmptyExpression _` matches the type and does not name the value. The unnamed variable `_` is final in Java 22. It is used where the branch never reads the value.

A switch over an **enum** uses the bare constant names, and with several constants in one arm:

```java
  private static String explainNotAllowed(RowProblem.NotAllowed notAllowed) {
    String allowed =
        switch (notAllowed.column()) {
          case OPERATION -> "one of the four operations or 'All operations'";
          case ATTRIBUTE -> "a known quality attribute";
          case PERCENTILE -> "p50, p95, p99 or one of " + TargetMeasure.rateKinds();
          case METRIC, THRESHOLD, WINDOW, OWNER, REASON -> "a value";
        };
```

- A switch expression over an enum must cover every constant or have a `default`. Here all eight are covered, so a new column makes the compiler ask for a new case.

### Pattern matching for `instanceof`

```java
    if (topology instanceof Topology.Chain chain) {
      appendParts(report, chain);
    }
```

- `topology instanceof Topology.Chain chain` tests the type and, if true, binds `chain` in the same step. Before Java 16 you wrote the test, then a cast on the next line.

### `Optional`

`Optional<T>` is a box that holds one value or nothing. It makes "maybe not found" part of the type.

```java
  public static Optional<Operation> fromLabel(String cellText) {
    Objects.requireNonNull(cellText, "cellText");
    return Arrays.stream(values())
        .filter(operation -> operation.label.equalsIgnoreCase(cellText.strip()))
        .findFirst();
  }
```

- The return type `Optional<Operation>` forces a caller to decide what to do when there is no match. `findFirst()` ends a stream and returns the first element, or an empty `Optional`.
- Callers use `isEmpty()` (Java 11) and `orElseThrow()` with no argument (Java 10). The latter throws `NoSuchElementException` when empty. `SheetValidator.toTarget` calls it only after it has checked there is a value.

```java
    Optional<Operation> operation = Operation.fromLabel(row.cell(SheetColumn.OPERATION));
    if (operation.isEmpty()) {
      problems.add(notAllowedOrMissing(row, SheetColumn.OPERATION));
    }
```

- `operation.isEmpty()` asks whether the `Optional` holds nothing. If so, the row gets a problem instead of crashing. The `Optional` also carries the type: `Optional<QualityAttribute>` cannot be confused with `Optional<Operation>`.

### Streams and `Collectors.joining`

A **stream** is a pipeline over a series of items: a source, some steps, and an end. `.map(f)` changes each item. `.filter(p)` keeps items for which `p` is true. The end step (`.toList()`, `.collect(...)`, `.findFirst()`) produces the result. A stream is used once.

```java
  public static String describe(Topology topology) {
    Objects.requireNonNull(topology, "topology");
    return switch (topology) {
      case Topology.Single single -> single.component().name();
      case Topology.Chain chain ->
          chain.parts().stream()
              .map(AvailabilityReport::describe)
              .collect(Collectors.joining(", ", "chain(", ")"));
      case Topology.Parallel parallel ->
          parallel.copies().stream()
              .map(AvailabilityReport::describe)
              .collect(Collectors.joining(", ", "parallel(", ")"));
    };
  }
```

- `chain.parts().stream()` opens the stream. `.map(AvailabilityReport::describe)` applies `describe` to each part. `AvailabilityReport::describe` is a **method reference**, short for the lambda `part -> describe(part)`.
- `Collectors.joining(", ", "chain(", ")")` joins the texts with `, ` between them, `chain(` at the front and `)` at the back. I ran `Stream.of("a","b")` through it and got `chain(a, b)`. For an empty stream the same collector gives `chain()`.
- A **lambda** is a small unnamed function: `operation -> operation.label.equalsIgnoreCase(cellText.strip())`. A lambda can use a surrounding variable only if that variable is never reassigned (it is **effectively final**).

Streams since Java 8, `Collectors.joining` since Java 8, `Stream.toList()` since Java 16:

```java
  public static List<String> headings() {
    return Arrays.stream(values()).map(SheetColumn::heading).toList();
  }
```

### `List.of`, `List.copyOf`, `Set.of` and `getFirst`

`List.of(a, b, c)` makes an **unmodifiable** list: it throws `UnsupportedOperationException` on `add`, `remove` or `set`. I ran `List.of(1).add(2)` and got that exception. It also rejects null elements.

`List.copyOf(list)` makes an unmodifiable copy and rejects null elements. I ran `List.copyOf(Arrays.asList(1, null))` and got a `NullPointerException`.

```java
  private static List<Topology> copyOfNonEmpty(List<Topology> children, String kind) {
    Objects.requireNonNull(children, "children");
    if (children.isEmpty()) {
      throw new IllegalArgumentException(kind + " needs at least one part");
    }
    // An unmodifiable copy: nobody can add an unchecked part after construction.
    return List.copyOf(children);
  }
```

- The record stores the copy, so a caller who keeps the original list and changes it later cannot change the topology.

`Set.of(...)` makes an unmodifiable set, which answers "does it contain this?" quickly and keeps no order:

```java
  // Words people write when they do not know yet. Each one is a missing field, not a value.
  private static final Set<String> PLACEHOLDERS =
      Set.of("tbd", "todo", "n/a", "na", "none", "unknown", "?", "-");
```

`list.getFirst()` returns the first element (Java 21, added to `List` through `SequencedCollection`):

```java
  public static Topology weakestPart(Topology.Chain chain) {
    Objects.requireNonNull(chain, "chain");
    Topology weakest = chain.parts().getFirst();
```

### `BigDecimal` and why money and percents use it

A `double` stores numbers in binary and cannot hold most decimal fractions exactly. I ran `0.1 + 0.2` in `jshell` and got `0.30000000000000004`. For a bank balance or a rate that you must show exactly, that is unacceptable.

`BigDecimal` stores the decimal digits and a scale (the number of digits after the point). Its operations are exact. It is slower than `double`, which does not matter here.

```java
  // A percent is a fraction with the decimal point moved two places: 99.9 percent is 0.999.
  private static final int PERCENT_SHIFT = 2;
  private static final BigDecimal HIGHEST_PERCENT = BigDecimal.ONE.movePointRight(PERCENT_SHIFT);
  // Six decimals show a pair of 99.95 percent parts as 99.999975 instead of rounding it to 100.
  private static final int PERCENT_DECIMALS = 6;
```

```java
  public static Availability ofPercent(BigDecimal percent) {
    if (!isPercent(percent)) {
      throw new IllegalArgumentException("a percent must be between 0 and 100, but was " + percent);
    }
    // The point moves in decimal arithmetic, so 99.9 becomes exactly 0.999 before it is a double.
    return new Availability(percent.movePointLeft(PERCENT_SHIFT).doubleValue());
  }
```

- `movePointLeft(2)` divides by 100 by moving the decimal point. `new BigDecimal("99.9").movePointLeft(2)` is exactly 0.999. The parser makes the `BigDecimal` from the text the person typed, so 99.9 never passes through a binary `double` until the last step.
- `.doubleValue()` converts to a `double`. The result is the closest `double` to 0.999, which prints as 0.999.

A different trap: `compareTo` compares the **value**, and `equals` also compares the scale, so 100 and 100.0 are not `equals`. The code uses `compareTo`:

```java
  public static boolean isPercent(BigDecimal percent) {
    Objects.requireNonNull(percent, "percent");
    return percent.signum() >= 0 && percent.compareTo(HIGHEST_PERCENT) <= 0;
  }
```

Printing uses `BigDecimal` too:

```java
  public String asPercent() {
    // BigDecimal.valueOf uses the shortest decimal text of the double, so 0.999 prints as 99.9.
    BigDecimal percent =
        BigDecimal.valueOf(fraction)
            .movePointRight(PERCENT_SHIFT)
            .setScale(PERCENT_DECIMALS, RoundingMode.HALF_UP)
            .stripTrailingZeros();
    return percent.toPlainString() + " %";
  }
```

- `setScale(6, RoundingMode.HALF_UP)` fixes six decimals with school rounding. `stripTrailingZeros()` removes the zeros at the end. `toPlainString()` never uses scientific notation: I ran `stripTrailingZeros()` on 100.000000 and `toString()` gave `1E+2`, while `toPlainString()` gave `100`.

The README says availability uses `double`, not `BigDecimal`, for the arithmetic itself. `ARCHITECTURE.md` explains why: the inputs have at most six significant digits, the output shows six decimals, and the rounding error of a product of a few dozen doubles is far smaller than that. Money would need `BigDecimal` throughout. Percents are converted with it so that the typed text stays exact.

### `Duration`

`java.time.Duration` is a length of time (not a moment on the clock). It is immutable.

```java
  // The assumptions behind every "per month" and "per year" figure in the course.
  private static final int DAYS_IN_MONTH = 30;
  private static final int DAYS_IN_YEAR = 365;

  /** A month of 30 days, which is 43,200 minutes. */
  public static final Duration MONTH_OF_30_DAYS = Duration.ofDays(DAYS_IN_MONTH);

  /** A year of 365 days, which is 525,600 minutes. */
  public static final Duration YEAR_OF_365_DAYS = Duration.ofDays(DAYS_IN_YEAR);

  private static final double MILLIS_PER_MINUTE = Duration.ofMinutes(1).toMillis();

  private Downtime() {}
```

- `Duration.ofDays(30)` is 30 days. I checked with `toMinutes()`, which gave `43200`, and `toMillis()`, which gave `2592000000`.
- `Duration.ofMinutes(1).toMillis()` is 60,000.
- `Duration.ofMillis(Math.round(...))` builds a duration from a number of milliseconds. The report converts back with `toMillis()` and divides.

### `String.format` and `Locale.ROOT`

`String.format(template, values...)` fills slots in a template: `%s` text, `%d` a whole number, `%f` a decimal number, `%n` a line break. A flag or number before the letter shapes the output: `%-44s` pads text to width 44 on the right, `%,d` adds thousands separators, `%.2f` keeps two decimals.

```java
  private static void appendParts(StringBuilder report, Topology.Chain chain) {
    report.append("Parts of the chain:\n");
    for (Topology part : chain.parts()) {
      String percent = AvailabilityCalculator.evaluate(part).asPercent();
      report.append(String.format(Locale.ROOT, "  %-44s %s%n", describe(part), percent));
    }
    Topology weakest = AvailabilityCalculator.weakestPart(chain);
    report.append(
        String.format(
            Locale.ROOT,
            "Weakest part: %s (%s)%n",
            describe(weakest),
            AvailabilityCalculator.evaluate(weakest).asPercent()));
  }
```

- The first argument, `Locale.ROOT`, says "use no country's rules". Without it the computer's language setting decides the decimal mark: a German machine would print `2.894,71`. With `Locale.ROOT`, every machine prints `2,894.71`. I ran `String.format(Locale.ROOT, "%,.2f|%,d|%-6s|%.1f", 1575.2166, 43200L, "ab", 20.04)` and got `1,575.22|43,200|ab    |20.0`.
- `System.out.printf(...)` is the same thing, printed.
- `Locale.ROOT` is also used in `toLowerCase(Locale.ROOT)`. In Turkish, `I` lowers to a dotless `i`, which would break comparisons on a Turkish computer.

### `Objects.requireNonNull`

`Objects.requireNonNull(value, "name")` returns the value if it is not null and throws `NullPointerException` with the message `name` otherwise. I ran it with null and got `java.lang.NullPointerException: name`. It makes a null fail at the door, with the name of the bad argument, instead of deep inside later code.

```java
  public Component {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(availability, "availability");
    if (name.isBlank()) {
      throw new IllegalArgumentException("a component needs a name");
    }
  }
```

### Lambdas, method references, generics, varargs, loops and the rest

- **Generics** such as `Result<Topology, TopologyError>` and `List<Topology>` tell the compiler what a container holds. `new ArrayList<>()` uses the **diamond** `<>`: Java fills in the type from the left side.
- **Varargs.** `Topology... parts` means "any number of `Topology` arguments", received as an array. `PaymentCapLab.chainOf` uses it.
- **Conditional expression.** `condition ? a : b` picks one of two values.
- **Loops.** The enhanced `for (Topology part : chain.parts())` visits each element. The classic `for (int i = 0; i < n; i++)` counts. `do { ... } while (condition)` runs the body once before it tests.
- **`1L << 20`** is a left shift: 2 to the power 20, 1,048,576. The `L` makes it a `long`.
- **Underscores in numbers.** `2_000` is 2000. It is only for reading (since Java 7).
- **Autoboxing.** `Integer` is the object form of `int`. A `Map<MeasureKey, Integer>` holds objects, and Java converts between `int` and `Integer` automatically.

### Javadoc comments

A **Javadoc** comment starts with `/**` and is attached to the class, field or method that follows. A tool turns them into web pages. Tags: `@param name text` documents a parameter (for a record, a component), `@return text` documents the result, `@throws Type text` documents an exception, `{@code x}` shows code, `{@link Type}` makes a link, `<p>` starts a paragraph.

```java
  /**
   * Tells whether a percent can be turned into an availability.
   *
   * @param percent the percent to check, for example 99.9
   * @return true when the percent is from 0 to 100 inclusive
   */
  public static boolean isPercent(BigDecimal percent) {
    Objects.requireNonNull(percent, "percent");
    return percent.signum() >= 0 && percent.compareTo(HIGHEST_PERCENT) <= 0;
  }
```

- Every public method in this repository has a Javadoc with the `@param`, `@return` and `@throws` it needs. That is why the walkthroughs can quote the promises of each method.
- Plain `//` comments are used for the reason behind a line. The convention in this code: Javadoc says what a thing promises, a `//` comment says why a line is written the way it is.

### The compiler settings

The relevant lines of `build.gradle.kts`:

```
options.release = 25
options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
```

The build compiles with `options.release = 25`, UTF-8 source encoding, and `-Xlint:all -Werror`. `-Xlint:all` turns on every compiler warning. `-Werror` turns each warning into an error. A warning that is allowed to stay is a defect nobody owns, says the comment in `build.gradle.kts`.

## Try it yourself

Run these in `seatwave-targets/`.

1. See the Java version:

```
java --version
```

I got `openjdk 25.0.2 2026-01-20 LTS` and `OpenJDK Runtime Environment Temurin-25.0.2+10`.

2. Compile and run a program:

```
./gradlew -q compileJava
./gradlew -q availability --args='chain(gateway=99.9, seatwave=99.9, payments=99.9)'
```

The first command prints nothing and ends with exit status 0 (I checked: `rc=0`). The second prints the report, including `End-to-end availability: 99.7003 %`.

3. Look at what a record became:

```
javap -cp build/classes/java/main 'io.plainround.seatwave.targets.availability.Topology$Chain'
```

I got `public final class ...Topology$Chain extends java.lang.Record implements ...Topology`, then a constructor, `toString`, `hashCode`, `equals` and `parts()`. `javap` is the JDK's class-file reader.

4. Prove the NaN rule. Start `jshell --class-path build/classes/java/main`, then type `import io.plainround.seatwave.targets.availability.*;` and `new Availability(Double.NaN)`. I got `java.lang.IllegalArgumentException: an availability must be between 0 and 1, but was NaN`. Type `/exit` to leave.

5. See `BigDecimal` against `double`. In `jshell`: `0.1 + 0.2` prints `0.30000000000000004`, and `new java.math.BigDecimal("99.9").movePointLeft(2).doubleValue()` prints `0.999`.

## Mistakes people make

- **Comparing with `==` when you mean "equal".** For objects, `==` asks "same object?". Use `equals`. (`HoldPathLab` uses `==` on purpose, and says so in a comment.)
- **Forgetting that NaN fails every comparison.** The range test in `Availability` is written to catch it.
- **Using `double` for exact decimals.** Use `BigDecimal`, as the percent conversion does.
- **Catching an exception and ignoring it.** The code catches only `IOException` and reports it.
- **Using a mutable list inside a record.** The record would look immutable and would not be. This code copies with `List.copyOf`.
- **Using the default locale in `String.format` or `toLowerCase`.** The output then depends on the computer. Pass `Locale.ROOT`.
- **Adding a `default` branch to a switch over a sealed type.** It hides the compiler's reminder to handle a new case. This repository has none.
- **Throwing exceptions for ordinary bad input.** See `result4j.md`.

## Check yourself

1. What is the difference between `static` and `final`? Answer: `static` means the member belongs to the class, not to an object. `final` means it is assigned once. A constant is both.
2. What does a compact constructor of a record do? Answer: it runs before the fields are set. It may validate and may reassign the component variables. Java then stores them in the fields.
3. Why does the switch in `AvailabilityCalculator.evaluate` have no `default` branch? Answer: `Topology` is sealed, so the compiler knows all three shapes and checks that all are handled. A new shape breaks the build until every switch handles it.
4. Why is `Locale.ROOT` passed to `String.format`? Answer: so that the decimal mark and the thousands separator do not depend on the computer's language.
5. Which Java version does the unnamed pattern `_` need, and how can you check? Answer: Java 22. Compile a file that uses `case A _ ->` with `javac --release 21` and read the error `unnamed variables are not supported in -source 21`.

## Further reading

- Java SE 25 API documentation, https://docs.oracle.com/en/java/javase/25/docs/api/
- The Java Language Guide for JDK 25: records, https://docs.oracle.com/en/java/javase/25/language/records.html
- Sealed classes and interfaces, https://docs.oracle.com/en/java/javase/25/language/sealed-classes-and-interfaces.html
- Pattern matching, https://docs.oracle.com/en/java/javase/25/language/pattern-matching.html
- Enums, https://docs.oracle.com/en/java/javase/25/language/enums.html
- `java.math.BigDecimal`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html
- `java.time.Duration`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Duration.html
- `java.util.Formatter`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Formatter.html
- Javadoc tool, https://docs.oracle.com/en/java/javase/25/javadoc/javadoc.html
