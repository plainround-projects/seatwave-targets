# AssertJ 3.27.7

## What it is

An **assertion** is a check inside a test: "this value must be that value". AssertJ is a library of assertions with a **fluent** style: you write `assertThat(actual)` and then chain method calls that read like a sentence, each returning the object so the next call can follow. When a check fails, AssertJ prints the expected and the actual value in a clear message.

## Why this repository uses it

Tests are the requirements here, so they should read like requirements. `assertThat(report).contains("Weakest part: gateway (99.9 %)")` is easier to read than a hand-written `if` and `throw`. The README gives the reason: "Fluent assertions with clear failure messages."

## Where it appears

| Path | What it does with AssertJ |
| --- | --- |
| `gradle/libs.versions.toml` | `assertj = "3.27.7"` and `assertj-core`. |
| `build.gradle.kts` | `testImplementation(libs.assertj.core)`. |
| `gradle.lockfile` | Locks `assertj-core` and its dependency `byte-buddy` 1.18.3 (`dependency-locking.md`). |
| `src/test/java/**` | Every test file uses it. The static imports are: `assertThat` (19 files), `assertThatNullPointerException` (17), `assertThatThrownBy` (9), `fail` (4) and `offset` (3). |

## How it works underneath

1. `assertThat(x)` looks at the type of `x` and returns the matching **assertion object**: one for a `double`, one for a `String`, one for a `List`, one for any object. Each has the methods that make sense for that type.
2. Each method (`isEqualTo`, `contains`, `hasSize`) checks the value. If the check holds, it returns the same assertion object, so another call can follow. If not, it throws an `AssertionError` with a message, and JUnit marks the test as failed (`junit.md`).
3. For exceptions, `assertThatThrownBy(() -> code)` runs the code in a try block, catches whatever is thrown and lets you check its type and message. The `() -> code` part is a **lambda**: a small unnamed function that is handed over and run later, inside AssertJ, so AssertJ can catch what it throws.
4. Because the checks are chained on one `assertThat`, several conditions on the same value come in one statement.

## Walkthrough of this repository's use

### `assertThat(...).isEqualTo(...)`, for values, records and strings

```java
  @Test
  void keepsTheNameAndTheAvailabilityItWasGiven() {
    Component component = new Component("gateway", Availability.ALWAYS_UP);

    assertThat(component.name()).isEqualTo("gateway");
    assertThat(component.availability()).isEqualTo(Availability.ALWAYS_UP);
  }
}
```

`isEqualTo` compares with `equals`. Java **records** (the `Component`, `Availability` and `Topology` types) get a correct `equals` automatically, so two records with the same contents are equal. That is how whole parsed topologies are compared in one line (`TopologyParserTest.parsesAChain`).

### `isCloseTo(value, offset(tolerance))`: comparing decimals fairly

```java
  @Test
  void multipliesThreePartsOfAChain() {
    Topology chain =
        Topologies.chain(
            Topologies.part("gateway", "99.9"),
            Topologies.part("seatwave", "99.9"),
            Topologies.part("payments", "99.9"));

    assertThat(AvailabilityCalculator.evaluate(chain).fraction())
        .isCloseTo(THREE_PARTS_OF_99_9_IN_A_CHAIN, offset(TOLERANCE));
  }
```

Computers store decimals like 0.999 as `double` numbers, which carry tiny rounding errors: `0.999 * 0.999 * 0.999` is not exactly `0.997002999` in binary. `isCloseTo(expected, offset(TOLERANCE))` passes when the two numbers differ by at most the **offset**, here `1e-12`, which is 0.000000000001. `offset(...)` is imported statically from AssertJ. The tolerance is far below anything that matters for a percentage shown to six decimals, but above the rounding noise. Where the code promises an **exact** result, the test uses `isEqualTo` instead: `convertsPercentToFractionWithoutRoundingError` checks that 99.9 percent becomes exactly `0.999`.

### Ranges: `isBetween`, `isLessThanOrEqualTo`, `isGreaterThanOrEqualTo`

```java
  @Test
  void aChainIsNeverMoreAvailableThanItsWeakestPart() {
    List<Double> fractions = generatedFractions();
    Topology.Chain chain = new Topology.Chain(partsWith(fractions));

    double chainFraction = AvailabilityCalculator.evaluate(chain).fraction();

    assertThat(chainFraction).isLessThanOrEqualTo(Collections.min(fractions) + TOLERANCE);
  }

  @Test
  void aParallelGroupIsNeverLessAvailableThanItsBestCopy() {
    List<Double> fractions = generatedFractions();
    Topology.Parallel group = new Topology.Parallel(partsWith(fractions));

    double groupFraction = AvailabilityCalculator.evaluate(group).fraction();

    assertThat(groupFraction).isGreaterThanOrEqualTo(Collections.max(fractions) - TOLERANCE);
  }
```

These check a **property** (a rule that must hold for any input), not a single value: a chain is never more available than its weakest part, and a parallel group is never less available than its best copy. `Collections.min` and `Collections.max` (Java's own functions) find the smallest and largest generated value, and `+ TOLERANCE` and `- TOLERANCE` allow for the rounding noise.

### Exceptions: `assertThatThrownBy`

```java
  @ParameterizedTest
  @ValueSource(
      doubles = {
        -0.1,
        -Double.MIN_VALUE,
        1.1,
        1.0000000000000002,
        Double.MAX_VALUE,
        Double.NaN,
        Double.POSITIVE_INFINITY,
        Double.NEGATIVE_INFINITY
      })
  void rejectsAFractionOutsideZeroToOne(double fraction) {
    assertThatThrownBy(() -> new Availability(fraction))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 0 and 1");
  }
```

`assertThatThrownBy(() -> new Availability(fraction))` runs the constructor and expects it to throw. `.isInstanceOf(IllegalArgumentException.class)` checks the type of the exception, and `.hasMessageContaining("between 0 and 1")` checks that its message includes that text. Checking the message proves the error says what is wrong, not just that something went wrong.

### Null checks: `assertThatNullPointerException().isThrownBy(...)`

```java
  @Test
  void rejectsANullTopology() {
    assertThatNullPointerException()
        .isThrownBy(() -> AvailabilityCalculator.evaluate(null))
        .withMessageContaining("topology");
  }
```

This form is a shortcut for "a `NullPointerException` must be thrown". `.isThrownBy(() -> ...)` supplies the code, and `.withMessageContaining("topology")` checks that the message names the parameter. The code under test uses `Objects.requireNonNull(topology, "topology")`, which produces exactly that message. Seventeen test files use this form.

### Collections: `hasSize`, `containsExactly`, `containsSequence`, `extracting`, `isSorted`

```java
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

`containsExactly(a, b)` checks that the list has exactly those elements in exactly that order. `hasSize(n)` checks the length. Other forms in this repository:
- `containsSequence("Response time", "", "1 s", "")` (`MarkdownSheetReaderTest.keepsEmptyCellsAsEmptyStrings`): those elements appear next to each other, in that order.
- `extracting(SheetRow::lineNumber).containsExactly(3, 4)`: first turns each element into one of its fields, then checks the resulting list. `SheetRow::lineNumber` is a **method reference**, a short way to write "call `lineNumber()` on each element".
- `isSorted()` (`TargetMeasureTest.listsTheRateKindsInAlphabeticalOrder`): the list is in ascending order.
- `isEmpty()` and `isZero()`, `isTrue()` and `isFalse()`, `contains(...)`, `startsWith`, `endsWith`, `doesNotContain` and `containsPattern(regex)` on strings (`AvailabilityReportTest`).

### `fail(...)`: stopping a test on purpose

```java
package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.fail;

import com.github.sviperll.result4j.Result;

/** Opens the result of the parser, so a test states only what it expects. */
final class ParseResults {

  private ParseResults() {}

  static Topology topologyOf(String expression) {
    return switch (TopologyParser.parse(expression)) {
      case Result.Success<Topology, TopologyError>(Topology topology) -> topology;
      case Result.Error<Topology, TopologyError>(TopologyError error) ->
          fail("expected a topology but got " + error);
    };
  }
```

`fail("message")` ends the test with a failure. It is used as an **expression** that returns a value of any type, so it can sit in a `switch` branch whose other branch returns a real value. These helper methods turn the parser's `Result` (success or error) into the plain value a test wants, and fail with a readable message when the wrong kind comes back (`tests-availability-walkthrough.md`).

### Why `byte-buddy` is in the lockfile

AssertJ depends on `net.bytebuddy:byte-buddy` (a library that builds classes at run time) for some of its features. It is not called by name anywhere in the tests, but it appears in `gradle.lockfile` as a transitive dependency (`dependency-locking.md`).

## Try it yourself

1. See which assertion methods are imported and how often:

```
grep -rhn "import static org.assertj" src/test | sed 's/.*Assertions\.//' | sort | uniq -c
```

   Expected: `19 assertThat;`, `17 assertThatNullPointerException;`, `9 assertThatThrownBy;`, `4 fail;`, `3 offset;`.

2. Find every use of `isCloseTo`:

```
grep -rn "isCloseTo" src/test | wc -l
```

   Expected: `12`, the number of lines that compare decimals with a tolerance.

3. Look at AssertJ in the dependency tree:

```
./gradlew -q dependencies --configuration testRuntimeClasspath | grep -A1 '^+--- org.assertj:assertj-core:3.27.7'
```

   Expected: `+--- org.assertj:assertj-core:3.27.7` followed by `|    \--- net.bytebuddy:byte-buddy:1.18.3`.

4. Run the report tests that use `contains` on long text:

```
./gradlew cleanTest test --tests '*AvailabilityReportTest'
```

   Expected: eleven lines ending in `PASSED`.

## Mistakes people make

- **Comparing decimals with `isEqualTo`.** Rounding noise makes it fail. Use `isCloseTo` with an offset, as here.
- **Forgetting the final assertion.** `assertThat(x)` alone checks nothing. Only the chained call does.
- **Checking only the exception type.** The message check proves the failure is the right one.
- **Putting code outside the lambda in `assertThatThrownBy`.** The exception would escape before AssertJ can catch it.
- **A too-wide tolerance.** A tolerance of 0.1 would hide a real error. These tests use 1e-12 and 1e-9.
- **Asserting on text that includes line breaks without care.** The report tests use `contains` and `containsPattern("(?m)...")` rather than the whole text where only a part matters.

## Check yourself

1. *What does `assertThat(a).isEqualTo(b)` do when `a` and `b` differ?* Throws an `AssertionError` that shows both values; JUnit marks the test failed.
2. *Why use `isCloseTo(x, offset(1e-12))` instead of `isEqualTo(x)` for the chain result?* A product of doubles has rounding noise; the offset allows for it.
3. *What does `assertThatNullPointerException().isThrownBy(() -> f(null))` prove?* That `f(null)` throws a `NullPointerException`.
4. *What does `containsExactly` check?* The elements and their order, and that there are no others.
5. *Why can `fail(...)` be used inside a `switch` that returns a value?* It returns a value of any type, so it fits where a value is expected.

## Further reading

- AssertJ core documentation: <https://assertj.github.io/doc/>
- AssertJ 3.27.7 Javadoc: <https://javadoc.io/doc/org.assertj/assertj-core/3.27.7/index.html>
