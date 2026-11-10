# Instancio 6.2.1

## What it is

Tests need input data. Writing it by hand is slow, and it only covers the cases you think of. **Instancio** is a Java library that **generates** test data: it can fill a list with random values or build an object with random fields. A generator that is random but **seeded** is repeatable: the **seed** is the starting number of the random sequence. The same seed always produces the same values, so a test that fails today fails the same way tomorrow.

## Why this repository uses it

`AvailabilityPropertyTest` checks rules that must hold for any list of availabilities: a chain is never better than its weakest part, a parallel group is never worse than its best copy, and every result stays between 0 and 1. A property is only convincing if it is tried on many values, not three chosen ones. Instancio supplies 40 values per test, and the fixed seed keeps every run identical.

## Where it appears

| Path | What it does with Instancio |
| --- | --- |
| `gradle/libs.versions.toml` | `instancio = "6.2.1"` and `instancio-junit`. |
| `build.gradle.kts` | `testImplementation(libs.instancio.junit)`. |
| `gradle.lockfile` | `instancio-junit`, `instancio-core`, `jspecify` 1.0.1 and `slf4j-api` 2.0.20 (`dependency-locking.md`). |
| `src/test/java/.../availability/AvailabilityPropertyTest.java` | The only file that uses it. |

## How it works underneath

1. `Instancio.ofList(Double.class)` starts a **builder** for a list of `Double` values.
2. Each builder call (`size`, `generate`, `withSeed`) changes one setting and returns the builder.
3. `create()` builds the list. For each element Instancio asks a **generator** for a value. A generator is the piece of code that makes one kind of value.
4. The random numbers come from a random-number generator that starts from the seed. With `withSeed(SEED)` the sequence is fixed, so `create()` gives the same list on every run and every machine.
5. Without `withSeed`, Instancio picks a random seed each run and (when its JUnit extension is used) prints it on failure so the run can be replayed. This repository does not use the extension and does not need it: the seed is always known.

`instancio-junit` is the artifact that brings the JUnit extension and `instancio-core`. The code here calls only the core builder API, so it works as a plain library.

## Walkthrough of this repository's use

### The seed and the constants

```java
package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.Test;

/** Properties that must hold for every list of availabilities, checked on seeded random data. */
class AvailabilityPropertyTest {

  // A fixed seed makes every run generate the same lists, so a failure can always be replayed.
  private static final long SEED = 20_261_110L;
  private static final int PARTS_PER_LIST = 40;
  // Rounding in a long product or sum of doubles stays far below this distance.
  private static final double TOLERANCE = 1e-12;
```

- `SEED = 20_261_110L` is the seed. Java lets you write underscores in numbers for readability and `L` marks a `long`. The digits are a date, 2026-11-10. The comment says why it is fixed: "a fixed seed makes every run generate the same lists, so a failure can always be replayed."
- `PARTS_PER_LIST = 40` is how many availabilities each test generates.
- `TOLERANCE = 1e-12` allows for rounding in a long product or sum (`assertj.md`).

### The generator

```java
  private static List<Double> generatedFractions() {
    return Instancio.ofList(Double.class)
        .size(PARTS_PER_LIST)
        .generate(Select.all(Double.class), generator -> generator.doubles().range(0.0, 1.0))
        .withSeed(SEED)
        .create();
  }
```

- `Instancio.ofList(Double.class)` is the builder for a list of `Double`.
- `.size(PARTS_PER_LIST)` makes it 40 elements long.
- `.generate(Select.all(Double.class), generator -> generator.doubles().range(0.0, 1.0))` says how to make each `Double`. `Select.all(Double.class)` is a **selector** that picks every `Double` in what is being built. The lambda `generator -> ...` receives Instancio's generators, and `generator.doubles().range(0.0, 1.0)` restricts the values to the range 0.0 to 1.0, the only valid range for an `Availability`. Without the range the generator would produce large positive numbers and the `Availability` constructor would throw.
- `.withSeed(SEED)` fixes the random sequence.
- `.create()` builds and returns the list.

I confirmed the repeatability with a small program that uses this exact call with a list of three values: calling it twice with the seed 20261110 printed `[0.01, 0.3, 0.29]` both times, and seed 1 printed `[0.41, 0.33, 0.71]`. The same run also shows that the default `doubles()` generator rounds to two decimal places (the values are 0.01, 0.3, 0.29), so these tests try fractions such as 0.37, not 0.3714285714.

### How the list is used

```java
  private static List<Topology> partsWith(List<Double> fractions) {
    return fractions.stream()
        .<Topology>map(
            fraction -> new Topology.Single(new Component("part", new Availability(fraction))))
        .toList();
  }
}
```

`partsWith` turns each generated fraction into a topology part named `part`. `.<Topology>map(...)` tells the stream the result type is `Topology`, because the lambda builds a `Topology.Single`. `new Availability(fraction)` throws if a value were outside 0 to 1, so the range in the generator is what keeps this valid.

### The four properties

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

  @Test
  void everyChainAndEveryParallelGroupStaysBetweenZeroAndOne() {
    List<Topology> parts = partsWith(generatedFractions());

    assertThat(AvailabilityCalculator.evaluate(new Topology.Chain(parts)).fraction())
        .isBetween(0.0, 1.0);
    assertThat(AvailabilityCalculator.evaluate(new Topology.Parallel(parts)).fraction())
        .isBetween(0.0, 1.0);
  }

  @Test
  void aGroupInsideAChainStaysBetweenZeroAndOne() {
    List<Topology> parts = partsWith(generatedFractions());
    Topology mixed =
        new Topology.Chain(List.of(new Topology.Parallel(parts), new Topology.Chain(parts)));

    assertThat(AvailabilityCalculator.evaluate(mixed).fraction()).isBetween(0.0, 1.0);
  }
```

Each test calls `generatedFractions()`, so each gets the same 40 numbers (same seed, same builder). They check:
1. The chain result is at most the smallest fraction (plus tolerance).
2. The parallel result is at least the largest fraction (minus tolerance).
3. Both stay between 0 and 1.
4. A chain that contains a parallel group and another chain also stays between 0 and 1.

### Why a fixed seed

A random test that sometimes fails and sometimes passes is worse than none: nobody can reproduce the failure. A fixed seed trades some variety for trust. The values never change, so a failure means the code changed. To search more widely you would change the seed and run again, on purpose, and record any failure as a normal named test.

## Try it yourself

1. Run the property tests:

```
./gradlew cleanTest test --tests '*AvailabilityPropertyTest'
```

   Expected: four `PASSED` lines: `aChainIsNeverMoreAvailableThanItsWeakestPart()`, `aParallelGroupIsNeverLessAvailableThanItsBestCopy()`, `everyChainAndEveryParallelGroupStaysBetweenZeroAndOne()` and `aGroupInsideAChainStaysBetweenZeroAndOne()`.

2. Confirm that Instancio is only used in one file:

```
grep -rln "instancio" src
```

   Expected: `src/test/java/io/plainround/seatwave/targets/availability/AvailabilityPropertyTest.java`.

3. Look at Instancio's own dependencies:

```
./gradlew -q dependencies --configuration testRuntimeClasspath | grep -A3 '^+--- org.instancio:instancio-junit:6.2.1'
```

   Expected: `org.instancio:instancio-junit:6.2.1`, `org.instancio:instancio-core:6.2.1`, `org.jspecify:jspecify:1.0.1` and `org.slf4j:slf4j-api:2.0.20`.

## Mistakes people make

- **Using random data without a seed.** Failures can not be reproduced.
- **Forgetting the range.** Unconstrained doubles can be outside 0 to 1 and the domain type rejects them.
- **Treating generated data as a replacement for named examples.** The other 281 tests use hand-written values with answers worked out by hand. Generated data tests rules, not exact numbers.
- **Asserting exact equality on generated products.** Use a tolerance.
- **Changing the seed to make a test pass.** That hides a real defect.
- **Believing the values are more varied than they are.** The default generator rounds to two decimals, as the experiment above shows.

## Check yourself

1. *What is a seed?* The starting number of a random sequence. The same seed gives the same values.
2. *Why is `SEED` a constant in this test?* So every run generates the same lists and any failure can be replayed.
3. *What does `Select.all(Double.class)` select?* Every `Double` in the structure being generated.
4. *Why restrict the generator to `range(0.0, 1.0)`?* An `Availability` only accepts fractions from 0 to 1.
5. *What does a property test check?* A rule that must hold for any input, here tried on 40 generated values.

## Further reading

- Instancio user guide (6.2.1): <https://www.instancio.org/user-guide/>
- Seed and reproducibility: <https://www.instancio.org/user-guide/#seed>
- Instancio project: <https://www.instancio.org/>
