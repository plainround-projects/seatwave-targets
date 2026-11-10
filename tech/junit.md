# JUnit Jupiter 6.1.3

## What it is

JUnit is the standard Java library for writing **automated tests**. A test is an ordinary method, marked with the annotation `@Test`, that runs some code and checks the result. If a check fails, JUnit reports the failing test by name and the build stops. **JUnit Jupiter** is the part of JUnit you write tests with. The **JUnit Platform** is the part that finds and runs them. This repository uses version **6.1.3** of both. JUnit 6 needs Java 17 or newer and keeps the Jupiter style of version 5.

An **annotation** is a label written with `@` in front of a method or class, such as `@Test`. It does nothing by itself. Tools read it.

## Why this repository uses it

The project's tests double as its requirements: every test method has a long name that states one behavior in plain words, and all 285 run with `./gradlew check`. JUnit gives the structure. AssertJ (`assertj.md`) gives the readable checks, and Instancio (`instancio.md`) gives generated data.

## Where it appears

| Path | What it does with JUnit |
| --- | --- |
| `gradle/libs.versions.toml` | `junit = "6.1.3"` and the entries `junit-bom`, `junit-jupiter` and `junit-platform-launcher`. |
| `build.gradle.kts` | `platform(libs.junit.bom)`, `libs.junit.jupiter` and `libs.junit.platform.launcher`, and `useJUnitPlatform()`. |
| `gradle.lockfile` | Locks the eight JUnit libraries at 6.1.3 (`dependency-locking.md`). |
| `src/test/java/**` | 19 test classes with 185 `@Test` methods and 16 parameterized methods that run 100 times, 285 test results in all. |

## How it works underneath

1. `./gradlew test` starts a new JVM and hands it the test classes, with the JUnit Platform launcher (`junit-platform-launcher`) as the entry point.
2. The Platform asks the **Jupiter engine** (`junit-jupiter-engine`) to **discover** tests: it scans the classes under `build/classes/java/test` for methods marked `@Test` or `@ParameterizedTest`.
3. For **each** test method Jupiter creates a **new instance** of the test class, runs the method, and throws the instance away. State cannot leak from one test to the next. This repository keeps no mutable state in test classes, only constants.
4. A test **passes** if the method returns normally. It **fails** if it throws an `AssertionError`, which is what a wrong check throws. It is an **error** if it throws anything else.
5. Results go to `build/test-results/test/*.xml` (one file per class, 19 here), and Gradle prints one line per result because of `testLogging { events("passed", "failed", "skipped") }`.
6. The classes and methods are **package-private** (no `public`). JUnit 5 and 6 allow that, which keeps tests out of the public surface.

JUnit does not promise an order between tests, and no test here depends on one.

## Walkthrough of this repository's use

### `@Test`: one behavior, one sentence

```java
  @Test
  void rejectsANullName() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Component(null, Availability.ALWAYS_UP))
        .withMessageContaining("name");
  }
```

`@Test` marks the method as a test. The name is a sentence about behavior, so a failure reads like a broken requirement: "rejects a null name". The body has three steps that most tests here follow: **arrange** (build the input), **act** (call the code), **assert** (check the outcome).

### `@ParameterizedTest` with `@ValueSource`: one test, many inputs

```java
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

A **parameterized test** is one test method that JUnit runs once per value. `@ParameterizedTest` replaces `@Test`. `@ValueSource(strings = {...})` lists the values. JUnit calls the method five times here, once for each of `""`, `" "`, `"   "`, `"\t"` and `"\n"`, passing the value as the parameter `blankName`. Each run is reported separately, for example `[1] ""`. The classes `ParameterizedTest` and `ValueSource` come from `junit-jupiter-params`, part of the bundle `junit-jupiter`.

`@ValueSource` also takes `doubles` (used in `AvailabilityTest` with `Double.NaN` and the infinities) and `ints` and `longs`, one kind at a time.

### `@EnumSource`: one run per enum constant

```java
  @ParameterizedTest
  @EnumSource(Operation.class)
  void findsEveryOperationByItsOwnLabel(Operation operation) {
    assertThat(Operation.fromLabel(operation.label())).contains(operation);
  }
```

An **enum** is a Java type with a fixed list of named values. `@EnumSource(Operation.class)` makes JUnit run the method once for every constant of the enum `Operation`. If someone adds a sixth operation, this test covers it with no change.

### Static imports and the assertion style

All tests start with lines such as `import static org.assertj.core.api.Assertions.assertThat;`. A **static import** lets you write `assertThat(...)` instead of `Assertions.assertThat(...)`. The checks come from AssertJ, not from JUnit's own `Assertions` class (`assertj.md`).

### What this repository does not use

I searched the test sources for annotations: only `@Test` (185), `@ParameterizedTest` (16), `@ValueSource` (14) and `@EnumSource` (2) appear. There is no `@BeforeEach`, `@AfterEach`, `@TempDir`, `@Nested`, `@DisplayName` or `@ExtendWith`. The tests build what they need with ordinary helper classes (`Topologies`, `ParseResults`, `SheetFixtures`; see `tests-availability-walkthrough.md` and `tests-sheet-walkthrough.md`). That keeps each test readable from top to bottom.

### Where tests run from

`ShippedSheetsTest` and `SheetReportTest` read the real files in `sheets/` with a relative path:

```java
  /** Reads a shipped sheet; Gradle runs tests from the project directory. */
  static List<String> linesOfSheet(String fileName) throws IOException {
    return Files.readAllLines(Path.of("sheets", fileName), StandardCharsets.UTF_8);
  }
```

Gradle runs the tests with the project folder as the working directory, so `Path.of("sheets", fileName)` finds `sheets/arena-on-sale.md`. A test run from another folder would fail with a file-not-found error.

### The build wiring

`testImplementation(libs.junit.jupiter)` makes the annotations and the engine available. `testRuntimeOnly(libs.junit.platform.launcher)` adds the launcher. `useJUnitPlatform()` tells the `test` task to use the Platform. The BOM `platform(libs.junit.bom)` fixes every JUnit library at 6.1.3 (`gradle.md`).

## Try it yourself

1. Run all tests and count the results:

```
./gradlew cleanTest test | grep -c PASSED
```

   Expected: `285`.

2. Run one class:

```
./gradlew cleanTest test --tests '*AvailabilityPropertyTest'
```

   Expected: four lines ending in `PASSED` (the four property tests) and `BUILD SUCCESSFUL`.

3. Run one method:

```
./gradlew cleanTest test --tests '*ComponentTest.rejectsANullName'
```

   Expected: one `PASSED` line.

4. Count the annotations:

```
grep -rhoE "^\s*@[A-Za-z]+" src/test | sort | uniq -c
```

   Expected: `2 @EnumSource`, `16 @ParameterizedTest`, `185 @Test`, `14 @ValueSource`.

5. Read the totals from the result files:

```
grep -ho 'tests="[0-9]*"' build/test-results/test/*.xml | head -3
```

   Each of the 19 files has a `tests="..."` count after a full run; they add up to 285.

## Mistakes people make

- **Vague names** such as `test1`. A good name is a sentence about behavior, as here.
- **Tests that depend on each other's order.** JUnit gives no order promise. Each test here builds its own input.
- **Asserting nothing.** A test that only calls code passes even if the code is wrong.
- **Using a parameterized test for a different behavior per value.** All values of one `@ValueSource` must mean the same thing; here each list is one rule (for example "a blank name is refused").
- **Forgetting the launcher with Gradle 9.** Without `junit-platform-launcher` on the test runtime classpath the tests can not start. The build declares it.
- **Running the tests from the wrong folder.** The sheet tests need the project folder.

## Check yourself

1. *What does `@Test` do?* Marks a method that JUnit runs as a test.
2. *Why does JUnit create a new instance of the class for every test?* So no test can see state left by another.
3. *How does `@ParameterizedTest` differ from `@Test`?* It runs the same method once per supplied value.
4. *What does `@EnumSource(Operation.class)` do?* Runs the test once for each constant of `Operation`.
5. *How can 185 `@Test` methods give 285 results?* 16 parameterized methods run 100 times in all: 185 + 100 = 285.

## Further reading

- JUnit 6 user guide (6.1.3): <https://docs.junit.org/6.1.3/overview.html>
- Writing tests and annotations: <https://docs.junit.org/6.1.3/writing-tests/annotations.html>
- Parameterized tests: <https://docs.junit.org/6.1.3/writing-tests/parameterized-classes-and-tests.html>
- Running JUnit on Gradle: <https://docs.gradle.org/9.9.1/userguide/java_testing.html>
