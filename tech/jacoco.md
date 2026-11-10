# JaCoCo 0.8.16 (code coverage)

## What it is

**Code coverage** answers one question: when the tests ran, which lines of the program were executed at least once? A line that no test ever ran is a line whose behavior nobody has checked. **JaCoCo** (Java Code Coverage) is the tool that measures it for Java. It does not tell you whether the tests are good, only which code they touched.

JaCoCo works with a **Java agent**: a small jar that the Java Virtual Machine (JVM) loads before the program starts. The agent watches classes as they are loaded and records which parts run.

## Why this repository uses it

The build has a measured floor: line coverage must be at least 0.80, and the build fails below it. That keeps the tests honest as the code grows. The README reports "line coverage 80.2 percent (477 of 595 lines)".

## Where it appears

| Path | What it does with JaCoCo |
| --- | --- |
| `build.gradle.kts` | Switches the plugin on (`jacoco`), pins its version, wires the report after the tests, sets the 0.80 rule and adds it to `check`. |
| `gradle/libs.versions.toml` | `jacoco = "0.8.16"`. |
| `gradle.lockfile` | Locks `jacocoAgent` and `jacocoAnt` (`dependency-locking.md`). |
| `build/jacoco/test.exec` | The raw recording of one test run (a binary file). |
| `build/reports/jacoco/test/html/index.html` | The readable report. |

## How it works underneath

1. `./gradlew test` starts a new JVM for the tests and passes it the option `-javaagent:` with the JaCoCo agent jar (the `jacocoAgent` configuration). Gradle does this by itself once the `jacoco` plugin is on.
2. As each class loads, the agent inserts small recording instructions into its bytecode, in memory only. The files on disk do not change.
3. When the JVM ends, the agent writes everything it recorded to `build/jacoco/test.exec`.
4. The task `jacocoTestReport` (`finalizedBy` on `test`, so it always runs afterwards) reads `test.exec` plus the compiled classes in `build/classes/java/main` and writes the HTML report. It uses the `jacocoAnt` configuration.
5. The task `jacocoTestCoverageVerification` reads the same data and applies the rules. If a rule is violated, the task fails and so does `check`.

JaCoCo counts several things. The four that matter here:
- **Instructions**: single bytecode instructions. The most fine-grained number.
- **Branches**: each outcome of an `if`, a `switch` or a `&&`. A branch is covered when both outcomes were taken.
- **Lines**: a source line counts as covered if at least one of its instructions ran. This is the counter the build's rule uses.
- **Methods** and **classes**: counted as covered if any part of them ran.

Only code under `src/main/java` is measured. The test classes themselves are not.

## Walkthrough of this repository's use

```kotlin
jacoco {
    toolVersion = libs.versions.jacoco.get()
}
```

`toolVersion = libs.versions.jacoco.get()` fixes JaCoCo at 0.8.16. Without it, Gradle would use its own default.

```kotlin
tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "failed", "skipped")
    }
    finalizedBy(tasks.jacocoTestReport)
}
```

`finalizedBy(tasks.jacocoTestReport)` is what writes the HTML report after every test run.

```kotlin
tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                // The floor for the whole project; the command-line entry points are the untested remainder.
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
```

`counter = "LINE"` picks the line counter. `minimum = "0.80".toBigDecimal()` is the lowest accepted ratio of covered lines to all lines. The comment above it names the untested remainder: the command-line entry points. `tasks.check { dependsOn(...) }` makes the rule part of `check`. When I raised the minimum to 0.95 in a scratch copy, the build failed with `Rule violated for bundle seatwave-targets: lines covered ratio is 0.80, but expected minimum is 0.95`.

### The real numbers (Temurin 25.0.2, 285 tests)

I ran `./gradlew cleanTest check` and read `build/reports/jacoco/test/html/index.html`. The last row of the table, "Total", reads:

| Counter | Missed | Total | Covered |
| --- | --- | --- | --- |
| Instructions | 615 | 2,955 | 79 percent |
| Branches | 25 | 203 | 87 percent |
| Lines | 118 | 595 | 477 lines, 80.2 percent |
| Methods | 16 | 118 | 102 |
| Classes | 4 | 43 | 39 |

The rule needs 0.80 of 595 lines, which is 476. The build has 477. **The margin is one line.** Adding about two untested lines to `src/main/java` would fail `check`.

Per package, the missed lines are:

| Package | Missed lines | Total lines | Why |
| --- | --- | --- | --- |
| `availability` | 24 | 227 | All 24 are `AvailabilityCli`, the `main` method that prints and calls `System.exit`. |
| `sheet` | 24 | 298 | All 24 are `SheetCli`, same reason. |
| `labs` | 70 | 70 | `HoldPathLab` (33 lines) and `PaymentCapLab` (37 lines): two `main` programs, never called by a test. |

Every other class has every line covered. These four classes are exactly the "entry points" named in the build file's comment. The branch counter misses 25 branches, for example one in `MarkdownSheetReader` (95 percent) and the branches inside the four `main` classes.

### Reading the report

Open `index.html`. Each row is a package; click it for its classes, click a class for its methods, click a method for the source with colors: green lines ran, red lines never ran, yellow lines ran only partly (a branch outcome was never taken).

## Try it yourself

1. Run the gate and the report:

```
./gradlew cleanTest check
```

   Expected: 285 `PASSED` lines, `> Task :jacocoTestCoverageVerification`, `BUILD SUCCESSFUL`.

2. Print the totals from the report:

```
grep -o '<tfoot>.*</tfoot>' build/reports/jacoco/test/html/index.html | sed 's/<[^>]*>/ /g'
```

   Expected: `Total  615 of 2,955  79%  25 of 203  87%  31  226  118  595  16  118  4  43`.

3. Print the totals of the `labs` package:

```
grep -o '<tfoot>.*</tfoot>' build/reports/jacoco/test/html/io.plainround.seatwave.targets.labs/index.html | sed 's/<[^>]*>/ /g'
```

   Expected: `Total  398 of 398  0%  6 of 6  0%  15  15  70  70  12  12  2  2`.

4. See the recording and the report folders:

```
ls build/jacoco build/reports/jacoco/test/html
```

   Expected: `test.exec` and `index.html`, `jacoco-sessions.html`, `jacoco-resources` and one folder per package.

5. Open the report in your browser (on a Mac): `open build/reports/jacoco/test/html/index.html`.

## Mistakes people make

- **Treating 80 percent as a goal.** Coverage shows which lines never ran. A line that ran can still be unchecked. The tests here also assert results.
- **Chasing 100 percent for `main` methods.** The build documents them as the untested remainder instead.
- **Running only `./gradlew test` and expecting the rule to be checked.** The rule runs in `jacocoTestCoverageVerification`, which `check` runs.
- **Reading an old report.** The report is rewritten after each `test` run; run the tests first.
- **Forgetting the thin margin.** One line of headroom means new code needs tests in the same change.
- **Deleting a test to make a number look right.** The check then fails for the right reason.

## Check yourself

1. *What does the line counter measure?* The share of source lines in which at least one instruction ran during the tests.
2. *Which task writes the rule check and which writes the report?* `jacocoTestCoverageVerification` checks; `jacocoTestReport` writes the HTML.
3. *Why is coverage 80.2 percent and not 100?* The four `main` classes (two command-line programs and two labs) are never called by a test.
4. *What makes the rule part of `check`?* `tasks.check { dependsOn(tasks.jacocoTestCoverageVerification) }`.
5. *How many lines of headroom are there?* One: 477 covered against 476 required.

## Further reading

- JaCoCo 0.8.16 documentation: <https://www.jacoco.org/jacoco/trunk/doc/>
- JaCoCo counters explained: <https://www.jacoco.org/jacoco/trunk/doc/counters.html>
- Gradle JaCoCo plugin (9.9.1): <https://docs.gradle.org/9.9.1/userguide/jacoco_plugin.html>
