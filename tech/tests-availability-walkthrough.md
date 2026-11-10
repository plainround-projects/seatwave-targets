# Test walkthrough: the availability package

## What it is

This file goes through every test file under `src/test/java/io/plainround/seatwave/targets/availability/`, in the order a learner should read them: the two helper classes first, then the tests from the smallest type (`Component`) up to the parser, the calculator, the report and the property tests. Each fenced block is copied from the real file. Each explanation says what the test proves and why that matters.

A **test** here is a method that calls the production code and checks the answer (`junit.md`). A **helper** is a class that builds test input so the tests stay short. **Production code** means the code under `src/main/java` that the tests check.

## Why this repository uses it

Module 2 turns a brief into numeric targets, and the availability calculator is the numeric core: given a chain of parts that must all work and groups of copies of which one is enough, what share of the time does the whole system work, and how many minutes of downtime does that allow? A wrong answer here gives a wrong target. These 139 test results are the proof that the numbers are right, that bad text is refused with a precise message, and that no impossible value (below 0 or above 1) can exist.

## Where it appears

| Path | What it checks |
| --- | --- |
| `Topologies.java`, `ParseResults.java` | Helpers, not tests. |
| `ComponentTest.java` | The named part of a system. 8 results. |
| `AvailabilityTest.java` | The value type for an availability. 42 results. |
| `TopologyTest.java` | The three shapes (single, chain, parallel). 9 results. |
| `TopologyParserTest.java` | Text such as `chain(a=99.9, b=99.5)` into a topology. 42 results. |
| `AvailabilityCalculatorTest.java` | The arithmetic. 12 results. |
| `DowntimeTest.java` | Minutes of downtime a window allows. 11 results. |
| `AvailabilityReportTest.java` | The printed report. 11 results. |
| `AvailabilityPropertyTest.java` | Rules that hold for generated data. 4 results. |

## How it works underneath

1. `./gradlew test` compiles these files with `-Xlint:all -Werror` and runs them on the JVM (`junit.md`, `gradle.md`).
2. Each test builds an input, calls one production method and asserts on the result with AssertJ (`assertj.md`).
3. The production code returns **values** for expected failures. The parser returns `Result<Topology, TopologyError>`, an object that is either a success holding a `Topology` or an error holding a `TopologyError`. The `Result` type comes from the library result4j. Programmer mistakes (a null argument) throw exceptions instead. The tests check both kinds.
4. Records (Java types that are just a named group of fields, with `equals` made automatically) let a test compare a whole parsed structure with `isEqualTo`.

A few words used below. A **topology** is the shape of a system as a tree: a single part, a **chain** (every part must work, so availabilities multiply) or a **parallel** group (one working copy is enough, so unavailabilities multiply). A **component** is a named part with an availability. An **availability** is the share of time a part works, from 0 (never) to 1 (always); the sheets and the report write it as a percent.

## Walkthrough of this repository's use

### `Topologies.java`: the test builder

```java
package io.plainround.seatwave.targets.availability;

import java.math.BigDecimal;
import java.util.List;

/** Builds topologies for tests, so each test reads as the topology it checks. */
final class Topologies {

  private Topologies() {}

```

The class is `final` (nothing can extend it) and its constructor is `private`, so nobody can create an instance: it only holds static helper methods, and it is package-private, so it is visible only to the tests of this package. The Javadoc comment says its job: each test reads as the topology it checks.

```java
  static Topology.Single part(String name, String percentText) {
    return new Topology.Single(
        new Component(name, Availability.ofPercent(new BigDecimal(percentText))));
  }

  static Topology.Chain chain(Topology... parts) {
    return new Topology.Chain(List.of(parts));
  }

  static Topology.Parallel parallel(Topology... copies) {
    return new Topology.Parallel(List.of(copies));
  }

  static Availability percent(String percentText) {
    return Availability.ofPercent(new BigDecimal(percentText));
  }
}
```

- `part(name, percentText)` builds one component from a name and a percent written as text. `new BigDecimal(percentText)` reads the text as an exact decimal number (`"99.9"` is exactly 99.9, not an approximation), and `Availability.ofPercent` turns it into the 0-to-1 form (0.999).
- `chain(Topology... parts)` and `parallel(Topology... copies)` take any number of arguments (`...` is **varargs**) and wrap them in `List.of`, an unmodifiable list. The return types `Topology.Chain` and `Topology.Parallel` are more specific than `Topology`, so tests can call methods only those shapes have.
- `percent(percentText)` builds a bare `Availability`.

### `ParseResults.java`: opening the parser's result

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

`TopologyParser.parse` returns a `Result`. A test usually wants the topology itself, or the error itself. `topologyOf(expression)` unwraps the success and calls `fail(...)` if an error came back, so the test fails with the message "expected a topology but got ..." and shows what the parser said. The `switch` uses **record patterns**: `case Result.Success<Topology, TopologyError>(Topology topology) -> topology` both checks the kind of result and pulls out the value inside it. The switch has no `default` branch because the two cases are the only ones; if result4j ever added a third, this file would stop compiling.

```java

  static TopologyError errorOf(String expression) {
    return switch (TopologyParser.parse(expression)) {
      case Result.Success<Topology, TopologyError>(Topology topology) ->
          fail("expected an error but got " + topology);
      case Result.Error<Topology, TopologyError>(TopologyError error) -> error;
    };
  }
}
```

`errorOf(expression)` is the mirror image: it returns the error and fails the test if the parser succeeded. Both helpers keep the tests free of unwrapping noise and make a wrong kind of result an immediate, readable failure.

### `ComponentTest.java`

```java
package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ComponentTest {

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
  void rejectsABlankName(String blankName) {
    assertThatThrownBy(() -> new Component(blankName, Availability.ALWAYS_UP))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("needs a name");
  }
```

Five blank-ish names (`""`, space, three spaces, tab, newline) must be refused with an `IllegalArgumentException` whose message contains `needs a name`. A part with no name would make a report unreadable ("which part is weakest?"), and a name made only of whitespace looks empty but is not caught by a plain null check, so every whitespace shape is tried.

```java
  @Test
  void rejectsANullName() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Component(null, Availability.ALWAYS_UP))
        .withMessageContaining("name");
  }
```

A `null` name throws a `NullPointerException` whose message names the parameter `name`. Null is a programming error, not bad user text, so it is an exception, and the message points at the culprit.

```java
  @Test
  void rejectsANullAvailability() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Component("gateway", null))
        .withMessageContaining("availability");
  }
```

The same for a `null` availability: the message names `availability`.

```java
  @Test
  void keepsTheNameAndTheAvailabilityItWasGiven() {
    Component component = new Component("gateway", Availability.ALWAYS_UP);

    assertThat(component.name()).isEqualTo("gateway");
    assertThat(component.availability()).isEqualTo(Availability.ALWAYS_UP);
  }
}
```

A good component keeps exactly what it was given. This is the happy path, so the tests above are not passing only because everything is refused.

### `AvailabilityTest.java`

```java
package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AvailabilityTest {

  // Two doubles that differ only by rounding are equal within this distance.
  private static final double TOLERANCE = 1e-12;
  // The fraction of 99.9 percent, written by hand as the expected value.
  private static final double FRACTION_OF_99_9_PERCENT = 0.999;
```

Imports and two constants. `TOLERANCE = 1e-12` is the allowed rounding distance for decimals. `FRACTION_OF_99_9_PERCENT = 0.999` is the expected value written by hand, so the test does not compute it the same way the code does.

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

The central invariant: **no availability outside 0 to 1 can exist**. The eight values are the edges of the invalid set: a little below zero, `-Double.MIN_VALUE` (the smallest negative number a `double` can hold, so just below zero), a little above one, `1.0000000000000002` (the next `double` after 1, the smallest possible overshoot), `Double.MAX_VALUE`, **NaN** (not a number, which compares false with everything) and both infinities. The constructor must throw `IllegalArgumentException` with `between 0 and 1`. NaN matters most: a check written as "less than 0 or greater than 1" lets NaN through; the code uses a negated range test to catch it.

```java
  @Test
  void acceptsAFractionOfExactlyZero() {
    assertThat(new Availability(0.0).fraction()).isZero();
  }

  @Test
  void acceptsAFractionOfExactlyOne() {
    assertThat(new Availability(1.0).fraction()).isEqualTo(1.0);
  }
```

Both ends are valid. `isZero()` and `isEqualTo(1.0)` prove the boundaries are accepted, so the invalid-value test above is not simply refusing everything.

```java
  @Test
  void convertsPercentToFractionWithoutRoundingError() {
    Availability availability = Availability.ofPercent(new BigDecimal("99.9"));

    assertThat(availability.fraction()).isEqualTo(FRACTION_OF_99_9_PERCENT);
  }
```

`ofPercent(new BigDecimal("99.9"))` must give exactly `0.999`. The code moves the decimal point in decimal arithmetic before converting to `double`. `isEqualTo` (not `isCloseTo`) proves there is no rounding error in the conversion. Why it matters: a mistake of one part in a billion at the first step would show in the sixth decimal of the report.

```java
  @ParameterizedTest
  @ValueSource(strings = {"-0.1", "100.1", "-1", "1000", "100.000001"})
  void rejectsAPercentOutsideZeroTo100(String percentText) {
    BigDecimal percent = new BigDecimal(percentText);

    assertThatThrownBy(() -> Availability.ofPercent(percent))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 0 and 100");
  }
```

Percents outside 0 to 100 are refused with `between 0 and 100`: below zero, above 100, and `100.000001`, which is only just over. The message differs from the fraction message because the caller typed a percent.

```java
  @Test
  void convertsThePercentBoundariesToTheFractionBoundaries() {
    assertThat(Availability.ofPercent(BigDecimal.ZERO)).isEqualTo(Availability.ALWAYS_DOWN);
    assertThat(Availability.ofPercent(new BigDecimal("100"))).isEqualTo(Availability.ALWAYS_UP);
  }
```

The two ends of the percent range convert to the constants `ALWAYS_DOWN` (0.0) and `ALWAYS_UP` (1.0), so a part of 0 percent is down and a part of 100 percent is up.

```java
  @ParameterizedTest
  @ValueSource(strings = {"0", "0.0", "50", "99.9", "100", "100.000"})
  void acceptsPercentsInsideTheRangeIncludingBothEnds(String percentText) {
    assertThat(Availability.isPercent(new BigDecimal(percentText))).isTrue();
  }
```

`isPercent` accepts both ends and values written with extra zeros (`0.0`, `100.000`). The check uses `compareTo`, which ignores trailing zeros, instead of `equals`, which would treat `100.000` and `100` as different.

```java
  @ParameterizedTest
  @ValueSource(strings = {"-0.000001", "-1", "100.000001", "101"})
  void refusesPercentsJustOutsideTheRange(String percentText) {
    assertThat(Availability.isPercent(new BigDecimal(percentText))).isFalse();
  }
```

`isPercent` refuses values that are only just outside the range, such as `-0.000001` and `100.000001`. This is the same boundary from the outside.

```java
  @Test
  void rejectsANullPercent() {
    assertThatNullPointerException().isThrownBy(() -> Availability.isPercent(null));
    assertThatNullPointerException().isThrownBy(() -> Availability.ofPercent(null));
  }
```

A `null` percent throws `NullPointerException` from both `isPercent` and `ofPercent`.

```java
  @Test
  void printsAvailabilityWithoutTrailingZeros() {
    assertThat(Topologies.percent("99.9").asPercent()).isEqualTo("99.9 %");
  }

  @Test
  void printsAvailabilityWithFourDecimalsWhenNeeded() {
    assertThat(new Availability(0.997002999).asPercent()).isEqualTo("99.7003 %");
  }
```

Printing. A value of 0.999 must print as `99.9 %`, with no trailing zeros, and 0.997002999 as `99.7003 %`, four decimals, because it is rounded to six decimals and trailing zeros are removed. `Topologies.percent("99.9").asPercent()` goes through the helper. Why it matters: the report shows these strings to a learner, so they must be tidy and correct.

```java
  @Test
  void printsSixDecimalsWhenTheyAreNeeded() {
    assertThat(new Availability(0.99999975).asPercent()).isEqualTo("99.999975 %");
  }

  @Test
  void printsAlwaysUpAsOneHundredPercent() {
    assertThat(Availability.ALWAYS_UP.asPercent()).isEqualTo("100 %");
  }

  @Test
  void printsAlwaysDownAsZeroPercent() {
    assertThat(Availability.ALWAYS_DOWN.asPercent()).isEqualTo("0 %");
  }
```

Six decimals are kept when needed: 0.99999975 prints as `99.999975 %`, so two 99.95 percent parts in parallel do not round to `100 %`. The two ends print as `100 %` and `0 %`, with no `.0`.

```java
  @Test
  void multipliesFractionsWhenBothPartsMustWork() {
    Availability bothUp = Topologies.percent("50").bothUp(Topologies.percent("90"));

    // 0.5 x 0.9 is 0.45, worked out by hand.
    assertThat(bothUp.fraction()).isCloseTo(0.45, offset(TOLERANCE));
  }
```

`bothUp` is the chain rule: both parts must work, so fractions multiply. 0.5 times 0.9 is 0.45 (worked out by hand, as the comment says). The tolerance covers the binary rounding of the product.

```java
  @Test
  void bothUpWithAlwaysDownGivesAlwaysDown() {
    assertThat(Topologies.percent("99.9").bothUp(Availability.ALWAYS_DOWN))
        .isEqualTo(Availability.ALWAYS_DOWN);
  }

  @Test
  void bothUpWithAlwaysUpChangesNothing() {
    Availability part = Topologies.percent("99.9");

    assertThat(part.bothUp(Availability.ALWAYS_UP)).isEqualTo(part);
  }
```

Two identities. Anything combined with `ALWAYS_DOWN` is down (multiplying by 0). Anything combined with `ALWAYS_UP` is unchanged (multiplying by 1). Identities catch a wrong starting value in the chain loop of the calculator.

```java
  @Test
  void combinesUnavailabilityWhenEitherCopyMayServe() {
    Availability eitherUp = Topologies.percent("50").eitherUp(Topologies.percent("90"));

    // 1 - (0.5 x 0.1) is 0.95, worked out by hand.
    assertThat(eitherUp.fraction()).isCloseTo(0.95, offset(TOLERANCE));
  }

  @Test
  void eitherUpWithAlwaysUpGivesAlwaysUp() {
    assertThat(Topologies.percent("1").eitherUp(Availability.ALWAYS_UP))
        .isEqualTo(Availability.ALWAYS_UP);
  }
```

`eitherUp` is the parallel rule: the group is down only when both copies are down, so unavailabilities multiply. A 50 percent part and a 90 percent part give 1 minus 0.5 times 0.1, which is 0.95. A group with an always-up copy is always up.

```java
  @Test
  void unavailabilityIsOneMinusTheFraction() {
    assertThat(Topologies.percent("99.9").unavailability())
        .isCloseTo(1.0 - FRACTION_OF_99_9_PERCENT, offset(TOLERANCE));
  }
```

`unavailability()` is `1 - fraction`. For 99.9 percent it is 0.001, the share of time the part is down.

```java
  @Test
  void isLowerThanIsStrict() {
    Availability lower = Topologies.percent("99");
    Availability higher = Topologies.percent("99.9");

    assertThat(lower.isLowerThan(higher)).isTrue();
    assertThat(higher.isLowerThan(lower)).isFalse();
    assertThat(lower.isLowerThan(lower)).isFalse();
  }
```

`isLowerThan` is a **strict** comparison: 99 is lower than 99.9, 99.9 is not lower than 99, and a value is not lower than itself. The third check is the one that catches a `<=` written by mistake, which would make the weakest-part search move on a tie.

```java
  @Test
  void meetsATargetThatEqualsOrIsBelowIt() {
    Availability target = Topologies.percent("99.9");

    assertThat(target.meets(target)).isTrue();
    assertThat(Topologies.percent("99.95").meets(target)).isTrue();
    assertThat(Topologies.percent("99.89").meets(target)).isFalse();
  }
```

`meets(target)` means "at least as good as": equal passes, higher passes, 99.89 against 99.9 does not. This is the question a design asks: does my availability meet the target?

```java
  @Test
  void rejectsANullOtherAvailability() {
    Availability availability = Topologies.percent("99");

    assertThatNullPointerException().isThrownBy(() -> availability.bothUp(null));
    assertThatNullPointerException().isThrownBy(() -> availability.eitherUp(null));
    assertThatNullPointerException().isThrownBy(() -> availability.isLowerThan(null));
    assertThatNullPointerException().isThrownBy(() -> availability.meets(null));
  }
}
```

Every method that takes another availability throws a `NullPointerException` for `null`. Four methods, four checks in one test.

### `TopologyTest.java`

```java
package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TopologyTest {

  @Test
  void rejectsAnEmptyChain() {
    assertThatThrownBy(() -> new Topology.Chain(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("chain needs at least one part");
  }

  @Test
  void rejectsAnEmptyParallelGroup() {
    assertThatThrownBy(() -> new Topology.Parallel(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("parallel group needs at least one part");
  }
```

A chain or a parallel group with no parts is meaningless (an empty product has no sensible meaning for a system), so both constructors throw `IllegalArgumentException`, each with its own message (`chain needs at least one part`, `parallel group needs at least one part`).

```java
  @Test
  void rejectsANullListInAChainAndInAParallelGroup() {
    assertThatNullPointerException().isThrownBy(() -> new Topology.Chain(null));
    assertThatNullPointerException().isThrownBy(() -> new Topology.Parallel(null));
  }
```

A `null` list throws `NullPointerException` for both shapes.

```java
  @Test
  void rejectsANullElementInAChain() {
    List<Topology> partsWithNull = Arrays.asList(Topologies.part("gateway", "99.9"), null);

    assertThatNullPointerException().isThrownBy(() -> new Topology.Chain(partsWithNull));
  }

  @Test
  void rejectsANullElementInAParallelGroup() {
    List<Topology> copiesWithNull = Arrays.asList(null, Topologies.part("seatwave", "99.9"));

    assertThatNullPointerException().isThrownBy(() -> new Topology.Parallel(copiesWithNull));
  }
```

A list that contains a `null` element also throws. `Arrays.asList(part, null)` is used because `List.of` itself refuses null, so it could not build the bad input. Two tests, one per shape, one with the null first and one with it last.

```java
  @Test
  void rejectsANullComponentInASinglePart() {
    assertThatNullPointerException().isThrownBy(() -> new Topology.Single(null));
  }
```

`new Topology.Single(null)` throws. A single part must hold a component.

```java
  @Test
  void copiesTheListSoLaterChangesDoNotReachTheChain() {
    List<Topology> parts = new ArrayList<>(List.of(Topologies.part("gateway", "99.9")));
    Topology.Chain chain = new Topology.Chain(parts);

    parts.add(Topologies.part("payments", "99.9"));

    assertThat(chain.parts()).hasSize(1);
  }

  @Test
  void copiesTheListSoLaterChangesDoNotReachTheParallelGroup() {
    List<Topology> copies = new ArrayList<>(List.of(Topologies.part("seatwave", "99.9")));
    Topology.Parallel group = new Topology.Parallel(copies);

    copies.clear();

    assertThat(group.copies()).hasSize(1);
  }
```

**Defensive copy.** The constructor copies the list it receives. The test builds a mutable list, creates the chain, then adds a second part to the original list and checks the chain still has one part. Without the copy, code far away could change a topology after it was checked. The parallel test does the same by clearing the original list.

```java
  @Test
  void exposesNoWayToAddAPartAfterConstruction() {
    Topology.Chain chain = Topologies.chain(Topologies.part("gateway", "99.9"));
    Topology extraPart = Topologies.part("payments", "99.9");
    List<Topology> exposedParts = chain.parts();

    assertThatThrownBy(() -> exposedParts.add(extraPart))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
```

The list a chain gives out cannot be changed: adding to `chain.parts()` throws `UnsupportedOperationException`. The expected exception is a Java standard one for unmodifiable lists. Together with the copy this makes a topology **immutable**: once built, valid forever.

### `TopologyParserTest.java`

```java
package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TopologyParserTest {

  // The number of parts in the very long chain; each part is about 8 characters, so it fits.
  private static final int PARTS_IN_A_LONG_CHAIN = 150;
```

A constant for the long-chain test below. `ParameterizedTest` and `ValueSource` are imported for the tests that run many inputs.

```java
  @Test
  void parsesASingleComponent() {
    assertThat(ParseResults.topologyOf("gateway=99.9"))
        .isEqualTo(Topologies.part("gateway", "99.9"));
  }
```

The smallest valid text, `gateway=99.9`, gives a single part.

```java
  @Test
  void parsesAChain() {
    assertThat(ParseResults.topologyOf("chain(gateway=99.9, payments=99.5)"))
        .isEqualTo(
            Topologies.chain(
                Topologies.part("gateway", "99.9"), Topologies.part("payments", "99.5")));
  }
```

A chain of two. The expected value is built with the helpers, so the test reads like the grammar: `chain(...)` gives a `Topology.Chain` of two `Topology.Single` parts.

```java
  @Test
  void parsesAParallelGroup() {
    assertThat(ParseResults.topologyOf("parallel(seatwave=99.9, seatwave=99.9)"))
        .isEqualTo(
            Topologies.parallel(
                Topologies.part("seatwave", "99.9"), Topologies.part("seatwave", "99.9")));
  }
```

The keyword `parallel` gives a `Topology.Parallel`.

```java
  @Test
  void parsesAGroupNestedInAChain() {
    String expression =
        "chain(gateway=99.9, parallel(seatwave=99.9, seatwave=99.9), payments=99.9)";

    assertThat(ParseResults.topologyOf(expression))
        .isEqualTo(
            Topologies.chain(
                Topologies.part("gateway", "99.9"),
                Topologies.parallel(
                    Topologies.part("seatwave", "99.9"), Topologies.part("seatwave", "99.9")),
                Topologies.part("payments", "99.9")));
  }
```

Nesting. A parallel group inside a chain, with three parts in the chain. This is the real shape of a redundant design: gateway, a pair of SeatWave copies, payments.

```java
  @Test
  void parsesAChainWithOnePart() {
    assertThat(ParseResults.topologyOf("chain(gateway=99.9)"))
        .isEqualTo(Topologies.chain(Topologies.part("gateway", "99.9")));
  }
```

A chain with one part is allowed; only an empty one is refused.

```java
  @Test
  void ignoresSpacesAndTabsBetweenTokens() {
    String spaced = " chain ( gateway = 99.9 ,\tparallel ( a = 99.5 , a = 99.5 ) ) ";
    String tight = "chain(gateway=99.9,parallel(a=99.5,a=99.5))";

    assertThat(ParseResults.topologyOf(spaced)).isEqualTo(ParseResults.topologyOf(tight));
  }
```

Spaces and tabs between tokens are ignored: the spaced text and the tight text must parse to equal topologies. `\t` is a tab. A learner will type spaces after commas, so the parser must accept them.

```java
  @Test
  void acceptsPercentsAtBothEndsOfTheRange() {
    assertThat(ParseResults.topologyOf("a=0")).isEqualTo(Topologies.part("a", "0"));
    assertThat(ParseResults.topologyOf("a=100")).isEqualTo(Topologies.part("a", "100"));
  }
```

The percent range includes both ends: 0 and 100 are valid.

```java
  @Test
  void acceptsNamesWithHyphensUnderscoresAndDigits() {
    assertThat(ParseResults.topologyOf("load-balancer_2=99.999"))
        .isEqualTo(Topologies.part("load-balancer_2", "99.999"));
  }
```

A name may contain letters, digits, hyphens and underscores: `load-balancer_2`. Real part names look like this.

```java
  @ParameterizedTest
  @ValueSource(strings = {"", " ", "   ", "\t\n"})
  void rejectsEmptyAndBlankText(String blankExpression) {
    assertThat(ParseResults.errorOf(blankExpression))
        .isEqualTo(new TopologyError.EmptyExpression());
  }
```

Empty and blank text (empty, spaces, a tab and a newline) gives the error `EmptyExpression`. The comparison uses `isEqualTo(new TopologyError.EmptyExpression())`, so the test fixes the error type, not only that there was an error.

```java
  @Test
  void rejectsTextLongerThanTheLimit() {
    String tooLong = "a".repeat(TopologyParser.MAX_EXPRESSION_LENGTH + 1);

    assertThat(ParseResults.errorOf(tooLong))
        .isEqualTo(
            new TopologyError.ExpressionTooLong(
                tooLong.length(), TopologyParser.MAX_EXPRESSION_LENGTH));
  }
```

Text longer than the limit (`MAX_EXPRESSION_LENGTH`, 2,000 characters) gives `ExpressionTooLong(actualLength, limit)`. The test builds 2,001 letters with `"a".repeat(...)`. The limit exists so hostile text cannot make the parser work for a long time.

```java
  @Test
  void acceptsTextOfExactlyTheLimitInsteadOfReportingItAsTooLong() {
    String atLimit = "a".repeat(TopologyParser.MAX_EXPRESSION_LENGTH);

    assertThat(ParseResults.errorOf(atLimit))
        .isNotInstanceOf(TopologyError.ExpressionTooLong.class);
  }
```

Text of exactly the limit is **not** reported as too long (it fails later for a different reason, because 2,000 letters are not valid). `isNotInstanceOf` pins the boundary: the limit is inclusive.

```java
  @Test
  void rejectsGroupsNestedDeeperThanTheLimit() {
    String tooDeep = nestedChains(TopologyParser.MAX_NESTING_DEPTH + 1);

    assertThat(ParseResults.errorOf(tooDeep))
        .isEqualTo(new TopologyError.NestingTooDeep(TopologyParser.MAX_NESTING_DEPTH));
  }
```

Nesting deeper than `MAX_NESTING_DEPTH` (16) gives `NestingTooDeep(16)`. The helper at the bottom of the file builds `chain(chain(...a=99.9...))` with 17 levels. The limit stops a typo of a thousand parentheses from crashing the program with a stack overflow, an error the program could not report neatly.

```java
  @Test
  void acceptsGroupsNestedExactlyAsDeepAsTheLimit() {
    assertThat(ParseResults.topologyOf(nestedChains(TopologyParser.MAX_NESTING_DEPTH)))
        .isInstanceOf(Topology.Chain.class);
  }
```

Exactly 16 levels parse fine and give a `Chain`. Again the boundary is inclusive.

```java
  @Test
  void rejectsAnUnknownGroupKeyword() {
    assertThat(ParseResults.errorOf("ring(a=99.9)"))
        .isEqualTo(new TopologyError.UnknownGroup("ring"));
  }
```

`ring(a=99.9)` gives `UnknownGroup("ring")`: only `chain` and `parallel` are group words.

```java
  @Test
  void rejectsKeywordsWrittenInCapitalLetters() {
    assertThat(ParseResults.errorOf("Chain(a=99.9)"))
        .isEqualTo(new TopologyError.UnknownGroup("Chain"));
  }
```

`Chain(a=99.9)` with a capital is also unknown. Keywords are case sensitive, and the error carries the word as typed.

```java
  @ParameterizedTest
  @ValueSource(strings = {"chain(a=101)", "a=100.0001", "chain(a=99.9, b=1000)"})
  void rejectsAPercentAbove100(String expression) {
    assertThat(ParseResults.errorOf(expression))
        .isInstanceOf(TopologyError.PercentOutOfRange.class);
  }
```

Percents above 100 give a `PercentOutOfRange` error, in three shapes: inside a chain, as a lone component with 100.0001, and as the second part of a chain.

```java
  @Test
  void reportsThePercentTextAsTyped() {
    assertThat(ParseResults.errorOf("chain(a=101)"))
        .isEqualTo(new TopologyError.PercentOutOfRange("101"));
  }
```

The error carries the percent text exactly as typed (`"101"`), so the message can quote it back.

```java
  @Test
  void rejectsAChainThatIsNeverClosed() {
    String unclosed = "chain(a=99.9";

    assertThat(ParseResults.errorOf(unclosed))
        .isEqualTo(new TopologyError.Unexpected(unclosed.length(), "',' or ')'"));
  }
```

`chain(a=99.9` (never closed) gives `Unexpected(12, "',' or ')'")`: the error says where (the position after the last character, here 12) and what was expected. Positions are 0-based character indexes into the text.

```java
  @Test
  void rejectsAPercentWithTwoDecimalPoints() {
    // The number starts at index 8, right after "chain(a=".
    assertThat(ParseResults.errorOf("chain(a=9.9.9)"))
        .isEqualTo(new TopologyError.Unexpected(8, "a percent such as 99.9"));
  }
```

`chain(a=9.9.9)` has a number with two decimal points. The number starts at index 8 (`chain(a=` is 8 characters), and the error is `Unexpected(8, "a percent such as 99.9")`. The parser checks the shape of the number before it converts it, so the conversion can never throw on what a person typed.

```java
  @Test
  void rejectsAChainWithNoParts() {
    // Index 6 is the closing parenthesis, where a name was expected.
    assertThat(ParseResults.errorOf("chain()"))
        .isEqualTo(new TopologyError.Unexpected(6, "a name"));
  }
```

`chain()` gives `Unexpected(6, "a name")`: index 6 is the closing parenthesis, where a name was expected.

```java
  @Test
  void rejectsANameWithNoPercent() {
    String nameOnly = "seatwave";

    assertThat(ParseResults.errorOf(nameOnly))
        .isEqualTo(new TopologyError.Unexpected(nameOnly.length(), "'=' or '('"));
  }
```

A name with no `=` or `(` after it (`seatwave`) gives `Unexpected(8, "'=' or '('")`, at the end of the text.

```java
  @Test
  void rejectsTextAfterTheTopology() {
    // The word "extra" starts at index 7, after "a=99.9 ".
    assertThat(ParseResults.errorOf("a=99.9 extra"))
        .isEqualTo(new TopologyError.Unexpected(7, "the end of the expression"));
  }
```

Text after the topology (`a=99.9 extra`) gives `Unexpected(7, "the end of the expression")`. The word `extra` starts at index 7. Without this check a typo after a valid topology would be silently ignored.

```java
  @Test
  void rejectsANegativePercent() {
    // The minus sign sits at index 2, where digits were expected.
    assertThat(ParseResults.errorOf("a=-1"))
        .isEqualTo(new TopologyError.Unexpected(2, "a percent such as 99.9"));
  }
```

A negative percent `a=-1` gives `Unexpected(2, ...)`: the minus sign at index 2 is where digits were expected. Negative numbers are not valid percents, so they are a syntax error here, not a range error.

```java
  @ParameterizedTest
  @ValueSource(strings = {"a=", "a=.", "a=99.", "a=.5", "a=1e2", "a=ninety"})
  void rejectsAMalformedPercent(String expression) {
    assertThat(ParseResults.errorOf(expression)).isInstanceOf(TopologyError.Unexpected.class);
  }
```

Six malformed percents (`a=`, `a=.`, `a=99.`, `a=.5`, `a=1e2`, `a=ninety`) are all `Unexpected`. None may be accepted as a number and none may throw. `1e2` shows scientific notation is refused.

```java
  @ParameterizedTest
  @ValueSource(
      strings = {"chain(a=99.9,)", "chain(,a=99.9)", "chain(a=99.9 b=99.9)", "=99.9", "(a=99.9)"})
  void rejectsMalformedSeparatorsAndMissingNames(String expression) {
    assertThat(ParseResults.errorOf(expression)).isInstanceOf(TopologyError.Unexpected.class);
  }
```

Malformed separators and names: a trailing comma, a leading comma, a missing comma, a missing name before `=`, a missing keyword before `(`. All give `Unexpected`.

```java
  @Test
  void rejectsTrailingCharactersAfterAClosedGroup() {
    assertThat(ParseResults.errorOf("chain(a=99.9))"))
        .isEqualTo(new TopologyError.Unexpected(13, "the end of the expression"));
  }
```

An extra closing parenthesis after a complete group gives `Unexpected(13, "the end of the expression")`.

```java
  @Test
  void parsesAVeryLongValidChain() {
    StringBuilder expression = new StringBuilder("chain(");
    for (int partIndex = 0; partIndex < PARTS_IN_A_LONG_CHAIN; partIndex++) {
      expression.append(partIndex == 0 ? "" : ",").append("p").append(partIndex).append("=99.9");
    }
    expression.append(')');

    Topology topology = ParseResults.topologyOf(expression.toString());

    assertThat(topology).isInstanceOf(Topology.Chain.class);
    assertThat(((Topology.Chain) topology).parts()).hasSize(PARTS_IN_A_LONG_CHAIN);
  }
```

A chain of 150 parts built in a loop with a `StringBuilder` (a growable text buffer). The expression is about 1,400 characters, below the limit of 2,000, and the result must be a `Chain` with `hasSize(150)`. It proves the parser has no small hidden limit on the number of parts (`partIndex == 0 ? "" : ","` is the way to put commas between parts but not before the first).

```java
  @Test
  void rejectsANullExpression() {
    assertThatNullPointerException()
        .isThrownBy(() -> TopologyParser.parse(null))
        .withMessageContaining("expression");
  }

  private static String nestedChains(int levels) {
    return "chain(".repeat(levels) + "a=99.9" + ")".repeat(levels);
  }
}
```

A `null` expression throws `NullPointerException` mentioning `expression`. The private helper `nestedChains(levels)` builds `chain(chain(...a=99.9...))` using `String.repeat`.

### `AvailabilityCalculatorTest.java`

```java
package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.offset;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailabilityCalculatorTest {

  private static final double TOLERANCE = 1e-12;
  private static final double LOOSE_TOLERANCE = 1e-9;
  // 0.999 x 0.999 x 0.999, worked out by hand.
  private static final double THREE_PARTS_OF_99_9_IN_A_CHAIN = 0.997002999;
  // 1 - 0.005 x 0.005, worked out by hand.
  private static final double TWO_COPIES_OF_99_5_IN_PARALLEL = 0.999975;
  // The size of the longest chain the test builds, far beyond any real system.
  private static final int PARTS_IN_A_VERY_LONG_CHAIN = 1_000;
```

Constants with expected values worked out by hand: three parts of 99.9 percent in a chain give 0.999 x 0.999 x 0.999 = 0.997002999; two copies of 99.5 percent in parallel give 1 - 0.005 x 0.005 = 0.999975. Writing the expected number by hand, not by running the same formula, keeps the test independent of the code. `PARTS_IN_A_VERY_LONG_CHAIN` is 1,000. The underscore in `1_000` is only for reading.

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

A chain multiplies: the numeric core. `isCloseTo(..., offset(TOLERANCE))` allows for binary rounding (`assertj.md`).

```java
  @Test
  void combinesTwoCopiesInParallel() {
    Topology group =
        Topologies.parallel(
            Topologies.part("seatwave", "99.5"), Topologies.part("seatwave", "99.5"));

    assertThat(AvailabilityCalculator.evaluate(group).fraction())
        .isCloseTo(TWO_COPIES_OF_99_5_IN_PARALLEL, offset(TOLERANCE));
  }
```

A parallel pair gives 99.9975 percent. This is the lesson of Module 2: two 99.5 percent copies beat one 99.99 percent part.

```java
  @Test
  void evaluatesAGroupNestedInAChain() {
    Topology mixed =
        Topologies.chain(
            Topologies.part("gateway", "99.9"),
            Topologies.parallel(
                Topologies.part("seatwave", "99.9"), Topologies.part("seatwave", "99.9")),
            Topologies.part("payments", "99.9"));

    // 0.999 x (1 - 0.001 x 0.001) x 0.999, the formula of the topology written out.
    double expected = 0.999 * (1.0 - 0.001 * 0.001) * 0.999;

    assertThat(AvailabilityCalculator.evaluate(mixed).fraction())
        .isCloseTo(expected, offset(TOLERANCE));
  }
```

A parallel group inside a chain: gateway x (1 minus 0.001 x 0.001) x payments. The expected value is the formula of the topology written out by hand, so the test proves that nested evaluation follows the tree.

```java
  @Test
  void aSinglePartGivesItsOwnAvailability() {
    Topology.Single single = Topologies.part("gateway", "99.9");

    assertThat(AvailabilityCalculator.evaluate(single))
        .isEqualTo(single.component().availability());
  }
```

A single part gives its own availability, with `isEqualTo`: no arithmetic should happen.

```java
  @Test
  void aChainWithAnAlwaysDownPartGivesZero() {
    Topology chain =
        Topologies.chain(
            Topologies.part("gateway", "99.9"),
            Topologies.part("broken", "0"),
            Topologies.part("payments", "99.9"));

    assertThat(AvailabilityCalculator.evaluate(chain)).isEqualTo(Availability.ALWAYS_DOWN);
  }
```

A chain with a part that is 0 percent gives exactly `ALWAYS_DOWN`. One dead part kills the chain. `isEqualTo` (not a tolerance) proves the result is exactly zero.

```java
  @Test
  void aParallelGroupWithAnAlwaysUpCopyGivesOne() {
    Topology group =
        Topologies.parallel(Topologies.part("flaky", "1"), Topologies.part("solid", "100"));

    assertThat(AvailabilityCalculator.evaluate(group)).isEqualTo(Availability.ALWAYS_UP);
  }
```

A parallel group with a 100 percent copy gives exactly `ALWAYS_UP`. One perfect copy saves the group. It also includes a 1 percent part, to show it does not matter.

```java
  @Test
  void aVeryLongChainStaysBetweenZeroAndOne() {
    List<Topology> parts = new ArrayList<>();
    for (int partIndex = 0; partIndex < PARTS_IN_A_VERY_LONG_CHAIN; partIndex++) {
      parts.add(Topologies.part("part", "99.9"));
    }

    double fraction = AvailabilityCalculator.evaluate(new Topology.Chain(parts)).fraction();

    assertThat(fraction)
        .isBetween(0.0, 1.0)
        .isCloseTo(Math.pow(0.999, PARTS_IN_A_VERY_LONG_CHAIN), offset(LOOSE_TOLERANCE));
  }
```

A chain of 1,000 parts of 99.9 percent stays between 0 and 1 and is close to `Math.pow(0.999, 1000)`. The loose tolerance `1e-9` allows for a thousand multiplications. This catches overflow, underflow or a loop that forgets a term.

```java
  @Test
  void rejectsANullTopology() {
    assertThatNullPointerException()
        .isThrownBy(() -> AvailabilityCalculator.evaluate(null))
        .withMessageContaining("topology");
  }
```

A `null` topology throws `NullPointerException` with the message containing `topology`.

```java
  @Test
  void findsTheLowestPartOfAChain() {
    Topology.Chain chain =
        Topologies.chain(
            Topologies.part("load-balancer", "99.999"),
            Topologies.part("seatwave", "99.5"),
            Topologies.part("database", "99.95"));

    assertThat(AvailabilityCalculator.weakestPart(chain))
        .isEqualTo(Topologies.part("seatwave", "99.5"));
  }
```

`weakestPart` finds the lowest part of a chain: here `seatwave` at 99.5 percent among 99.999 and 99.95. This is what the report prints as `Weakest part`.

```java
  @Test
  void returnsTheFirstPartWhenAllPartsAreEqual() {
    Topology.Chain chain =
        Topologies.chain(
            Topologies.part("first", "99.9"),
            Topologies.part("second", "99.9"),
            Topologies.part("third", "99.9"));

    assertThat(AvailabilityCalculator.weakestPart(chain))
        .isEqualTo(Topologies.part("first", "99.9"));
  }
```

When all parts are equal, the **first** is returned. That fixes tie behavior, so output is stable. It is why `isLowerThan` is strict.

```java
  @Test
  void comparesAGroupInsideAChainByItsCombinedAvailability() {
    Topology.Chain chain =
        Topologies.chain(
            Topologies.part("gateway", "99.9"),
            Topologies.parallel(
                Topologies.part("seatwave", "90"), Topologies.part("seatwave", "90")));

    // The pair gives 99 percent, which is still below the gateway.
    assertThat(AvailabilityCalculator.weakestPart(chain)).isEqualTo(chain.parts().get(1));
  }
```

A group inside a chain is compared by its **combined** availability. Two 90 percent copies give 99 percent, which is below the gateway's 99.9, so the group is the weakest part. The comment says so. `chain.parts().get(1)` is that group.

```java
  @Test
  void rejectsANullChain() {
    assertThatNullPointerException()
        .isThrownBy(() -> AvailabilityCalculator.weakestPart(null))
        .withMessageContaining("chain");
  }
}
```

A `null` chain throws, with the message naming `chain`.

### `DowntimeTest.java`

```java
package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DowntimeTest {

  private static final double TOLERANCE = 1e-9;
  // Each figure is the unavailable share of 43,200 minutes, worked out by hand.
  private static final double MINUTES_OF_99_9_IN_A_MONTH = 43.2;
  private static final double MINUTES_OF_99_95_IN_A_MONTH = 21.6;
  private static final double MINUTES_OF_99_IN_A_MONTH = 432.0;
  private static final double MINUTES_OF_99_99_IN_A_MONTH = 4.32;
  // 525,600 minutes in a year, times 0.001.
  private static final double MINUTES_OF_99_9_IN_A_YEAR = 525.6;
  // 30 x 24 x 60 and 365 x 24 x 60, as stated in the Javadoc of the window constants.
  private static final long MINUTES_IN_30_DAYS = 43_200L;
  private static final long MINUTES_IN_365_DAYS = 525_600L;
```

Constants for the expected minutes, each worked out by hand from 43,200 minutes in a 30-day month (30 x 24 x 60) and 525,600 in a 365-day year: 0.1 percent of 43,200 is 43.2; 0.05 percent is 21.6; 1 percent is 432; 0.01 percent is 4.32; 0.1 percent of 525,600 is 525.6. `Duration` is Java's type for a length of time.

```java
  @Test
  void allows43Point2MinutesAMonthAt99Point9Percent() {
    Duration allowed = Downtime.allowedBy(Topologies.percent("99.9"), Downtime.MONTH_OF_30_DAYS);

    assertThat(Downtime.inMinutes(allowed))
        .isCloseTo(MINUTES_OF_99_9_IN_A_MONTH, offset(TOLERANCE));
  }

  @Test
  void allows525Point6MinutesAYearAt99Point9Percent() {
    Duration allowed = Downtime.allowedBy(Topologies.percent("99.9"), Downtime.YEAR_OF_365_DAYS);

    assertThat(Downtime.inMinutes(allowed)).isCloseTo(MINUTES_OF_99_9_IN_A_YEAR, offset(TOLERANCE));
  }

  @Test
  void allows21Point6MinutesAMonthAt99Point95Percent() {
    Duration allowed = Downtime.allowedBy(Topologies.percent("99.95"), Downtime.MONTH_OF_30_DAYS);

    assertThat(Downtime.inMinutes(allowed))
        .isCloseTo(MINUTES_OF_99_95_IN_A_MONTH, offset(TOLERANCE));
  }

  @Test
  void allows432MinutesAMonthAt99Percent() {
    Duration allowed = Downtime.allowedBy(Topologies.percent("99"), Downtime.MONTH_OF_30_DAYS);

    assertThat(Downtime.inMinutes(allowed)).isCloseTo(MINUTES_OF_99_IN_A_MONTH, offset(TOLERANCE));
  }

  @Test
  void allows4Point32MinutesAMonthAt99Point99Percent() {
    Duration allowed = Downtime.allowedBy(Topologies.percent("99.99"), Downtime.MONTH_OF_30_DAYS);

    assertThat(Downtime.inMinutes(allowed))
        .isCloseTo(MINUTES_OF_99_99_IN_A_MONTH, offset(TOLERANCE));
  }
```

Five tests of the same rule: `allowedBy(availability, window)` returns the time the system may be down. 99.9 percent allows 43.2 minutes a month and 525.6 a year; 99.95 percent allows 21.6 minutes a month (the arena sheet's target); 99 percent allows 432 minutes (7.2 hours); 99.99 percent allows 4.32 minutes. `Downtime.inMinutes` turns the `Duration` into a number of minutes.

```java
  @Test
  void allowsNoDowntimeForAnAlwaysUpPart() {
    assertThat(Downtime.allowedBy(Availability.ALWAYS_UP, Downtime.YEAR_OF_365_DAYS)).isZero();
  }

  @Test
  void allowsTheWholeWindowForAnAlwaysDownPart() {
    assertThat(Downtime.allowedBy(Availability.ALWAYS_DOWN, Downtime.MONTH_OF_30_DAYS))
        .isEqualTo(Downtime.MONTH_OF_30_DAYS);
  }
```

Two extremes: an always-up part is allowed zero downtime (`isZero()`), and an always-down part is allowed the whole window (`isEqualTo(Downtime.MONTH_OF_30_DAYS)`).

```java
  @Test
  void rejectsAZeroWindow() {
    assertThatThrownBy(() -> Downtime.allowedBy(Availability.ALWAYS_UP, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("longer than zero");
  }

  @Test
  void rejectsANegativeWindow() {
    Duration negativeWindow = Downtime.MONTH_OF_30_DAYS.negated();

    assertThatThrownBy(() -> Downtime.allowedBy(Availability.ALWAYS_UP, negativeWindow))
        .isInstanceOf(IllegalArgumentException.class);
  }
```

A window of zero or less is refused: `IllegalArgumentException`, with the message `longer than zero` in the zero case. A zero window would make the answer meaningless.

```java
  @Test
  void rejectsNullArguments() {
    assertThatNullPointerException()
        .isThrownBy(() -> Downtime.allowedBy(null, Downtime.MONTH_OF_30_DAYS));
    assertThatNullPointerException()
        .isThrownBy(() -> Downtime.allowedBy(Availability.ALWAYS_UP, null));
    assertThatNullPointerException().isThrownBy(() -> Downtime.inMinutes(null));
  }
```

`null` arguments throw `NullPointerException`: the availability, the window and the duration passed to `inMinutes`.

```java
  @Test
  void keepsTheNamedWindowsAtTheirDocumentedLength() {
    assertThat(Downtime.MONTH_OF_30_DAYS.toMinutes()).isEqualTo(MINUTES_IN_30_DAYS);
    assertThat(Downtime.YEAR_OF_365_DAYS.toMinutes()).isEqualTo(MINUTES_IN_365_DAYS);
  }
}
```

The named windows are exactly 43,200 and 525,600 minutes. Their Javadoc states 30 and 365 days, and every figure in the course depends on those assumptions, so a test pins them.

### `AvailabilityReportTest.java`

```java
package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class AvailabilityReportTest {

  private static final Topology THREE_PARTS_OF_99_9 =
      Topologies.chain(
          Topologies.part("gateway", "99.9"),
          Topologies.part("seatwave", "99.9"),
          Topologies.part("payments", "99.9"));
```

`THREE_PARTS_OF_99_9` is the same three-part chain as the README example, shared by several tests.

```java
  @Test
  void describesAMixedTopologyByNamesOnly() {
    Topology mixed =
        Topologies.chain(
            Topologies.part("gateway", "99.9"),
            Topologies.parallel(
                Topologies.part("seatwave", "99.9"), Topologies.part("seatwave", "99.9")),
            Topologies.part("payments", "99.9"));

    assertThat(AvailabilityReport.describe(mixed))
        .isEqualTo("chain(gateway, parallel(seatwave, seatwave), payments)");
  }
```

`describe` gives a topology as text using names only: `chain(gateway, parallel(seatwave, seatwave), payments)`. It does not print percents, so the report line stays short.

```java
  @Test
  void describesASinglePartByItsName() {
    assertThat(AvailabilityReport.describe(Topologies.part("gateway", "99.9")))
        .isEqualTo("gateway");
  }
```

A single part is described by its name.

```java
  @Test
  void rendersTheEndToEndAvailabilityOfAChain() {
    assertThat(AvailabilityReport.render(THREE_PARTS_OF_99_9))
        .contains("End-to-end availability: 99.7003 %");
  }

  @Test
  void rendersTheWeakestPartOfAChain() {
    assertThat(AvailabilityReport.render(THREE_PARTS_OF_99_9))
        .contains("Weakest part: gateway (99.9 %)");
  }
```

The report contains `End-to-end availability: 99.7003 %` and `Weakest part: gateway (99.9 %)`. These are exact strings from the README. `contains` is used so each test checks one line of the report and a change in one line breaks one test.

```java
  @Test
  void rendersTheMonthlyDowntimeBudget() {
    assertThat(AvailabilityReport.render(THREE_PARTS_OF_99_9))
        .contains("129.47 minutes per 30-day month (43,200 minutes)");
  }

  @Test
  void rendersTheYearlyDowntimeBudgetInMinutesAndHours() {
    assertThat(AvailabilityReport.render(THREE_PARTS_OF_99_9))
        .contains("1,575.22 minutes per 365-day year (525,600 minutes), or 26.25 hours");
  }
```

The monthly and yearly downtime lines: `129.47 minutes per 30-day month (43,200 minutes)` and `1,575.22 minutes per 365-day year (525,600 minutes), or 26.25 hours`. The thousands separator and the two decimals are part of the promise. 0.002997001 of 43,200 minutes is 129.47.

```java
  @Test
  void rendersOneLinePerPartOfAChain() {
    assertThat(AvailabilityReport.render(THREE_PARTS_OF_99_9))
        .contains("Parts of the chain:")
        .containsPattern("(?m)^ {2}gateway +99\\.9 %$")
        .containsPattern("(?m)^ {2}seatwave +99\\.9 %$")
        .containsPattern("(?m)^ {2}payments +99\\.9 %$");
  }
```

One line per part of a chain. `containsPattern` takes a **regular expression** (a small language for describing text patterns). `(?m)` makes `^` and `$` match at each line, `^ {2}gateway +99\\.9 %$` means: line start, exactly two spaces, `gateway`, one or more spaces, `99.9 %`, line end. The pattern checks the column layout without depending on how many spaces pad the name.

```java
  @Test
  void leavesOutThePartsListForATopologyThatIsNotAChain() {
    String report = AvailabilityReport.render(Topologies.part("gateway", "99.9"));

    assertThat(report).doesNotContain("Parts of the chain").doesNotContain("Weakest part");
  }
```

For a topology that is not a chain, the report has no parts list and no weakest part: `doesNotContain` for both phrases.

```java
  @Test
  void rendersZeroDowntimeForAnAlwaysUpTopology() {
    assertThat(AvailabilityReport.render(Topologies.part("perfect", "100")))
        .contains("End-to-end availability: 100 %")
        .contains("0.00 minutes per 30-day month");
  }
```

A 100 percent part gives `End-to-end availability: 100 %` and `0.00 minutes per 30-day month`: zero downtime prints as zero, not as a tiny number.

```java
  @Test
  void endsTheReportWithALineBreakAndStatesTheIndependenceAssumption() {
    String report = AvailabilityReport.render(THREE_PARTS_OF_99_9);

    assertThat(report).endsWith("\n").contains("parts fail independently");
  }
```

The report ends with a line break and always states the assumption that parts fail independently. That sentence is the honest limit of the arithmetic, so a test makes sure it is never dropped.

```java
  @Test
  void rejectsANullTopology() {
    assertThatNullPointerException().isThrownBy(() -> AvailabilityReport.describe(null));
    assertThatNullPointerException().isThrownBy(() -> AvailabilityReport.render(null));
  }
}
```

`describe(null)` and `render(null)` throw `NullPointerException`.

### `AvailabilityPropertyTest.java`

Covered in detail in `instancio.md` (the seeded generator) and `assertj.md` (the range checks). In short: four tests try rules on 40 generated fractions, with a fixed seed so that every run is identical.

```java
  @Test
  void aChainIsNeverMoreAvailableThanItsWeakestPart() {
    List<Double> fractions = generatedFractions();
    Topology.Chain chain = new Topology.Chain(partsWith(fractions));

    double chainFraction = AvailabilityCalculator.evaluate(chain).fraction();

    assertThat(chainFraction).isLessThanOrEqualTo(Collections.min(fractions) + TOLERANCE);
  }
```

A chain is never more available than its weakest part: the product of numbers between 0 and 1 is at most the smallest of them. The other three tests check the parallel counterpart, the range 0 to 1 for both shapes, and the range for a mix.

## Try it yourself

1. Run the whole package:

```
./gradlew cleanTest test --tests 'io.plainround.seatwave.targets.availability.*'
```

   Expected: `BUILD SUCCESSFUL` and one `PASSED` line per result. Those lines add up to 139 for this package.

2. Run one class and read the names as requirements:

```
./gradlew cleanTest test --tests '*DowntimeTest'
```

   Expected: eleven lines such as `DowntimeTest > allows432MinutesAMonthAt99Percent() PASSED`.

3. Run one parameterized test and see each value:

```
./gradlew cleanTest test --tests '*ComponentTest.rejectsABlankName'
```

   Expected: five lines such as `ComponentTest > rejectsABlankName(String) > [1] "" PASSED`.

4. Try the program the tests protect:

```
./gradlew -q availability --args='chain(a=99.9, b=99.5)'
```

   Expected: the report starting `Topology: chain(a, b)` and containing `End-to-end availability: 99.4005 %`.

5. Try an invalid input and read the message:

```
./gradlew -q availability --args='chain(a=99.9, b=101)' 2>&1 | head -3
```

   Expected: `invalid topology: a percent must be between 0 and 100, but was 101`, then Gradle's `FAILURE: Build failed` text.

## Mistakes people make

- **Writing the expected value with the same formula as the code.** Then a wrong formula passes. These tests write it by hand.
- **Testing only the happy path.** Each rule here has its boundary tested from both sides (2,000 and 2,001 characters, 16 and 17 levels, 0 and below 0).
- **Comparing decimals exactly.** Use `isCloseTo` for computed values and `isEqualTo` for exact ones.
- **Asserting only that "an error happened".** The tests pin the error type and its position or message.
- **Letting tests share mutable data.** Topologies are immutable and the tests build their own.
- **Forgetting that `main` is not covered.** `AvailabilityCli` is outside these tests (`jacoco.md`).

## Check yourself

1. *Why does `AvailabilityTest` try NaN?* NaN compares false with everything, so a simple range test lets it through; the constructor must reject it.
2. *What do the boundary tests at 2,000 and 2,001 characters prove?* That the limit is inclusive: exactly 2,000 is accepted by the length check and 2,001 is refused.
3. *Why does `TopologyTest` add to the original list after building a chain?* To prove the chain made a defensive copy.
4. *What does `weakestPart` return when all parts are equal?* The first one.
5. *Why does the parser return `Result` values for bad text but throw for a null argument?* Bad user text is an expected event; a null is a programmer's mistake.

## Further reading

- JUnit 6 user guide (6.1.3): <https://docs.junit.org/6.1.3/overview.html>
- AssertJ core documentation: <https://assertj.github.io/doc/>
- Records (JEP 395): <https://openjdk.org/jeps/395>
- Record patterns (JEP 440): <https://openjdk.org/jeps/440>
