# Technology notes for seatwave-targets

These notes teach every technology this repository uses, from zero, through this repository's own files. They assume you have never seen a terminal or Java. Each file follows the same nine headings: what it is, why this repository uses it, where it appears, how it works underneath, a walkthrough of this repository's use, things to try, common mistakes, questions to check yourself, and official reading.

The repository is the hands-on project of Module 2 of a system design course. It holds an availability calculator and a requirements-sheet validator for a ticket-booking case called SeatWave, written in Java 25 and built with Gradle.

## The files

**The language and the library**

| File | Role |
|---|---|
| `java.md` | Java 25 from zero: class, package, method, exception, records, sealed interfaces, enums, `switch` with patterns, `Optional`, streams, `BigDecimal`, `Duration`, `String.format`, Javadoc. |
| `result4j.md` | The result4j 1.2.0 library: a `Result` that is either a value or an error, and why expected failures are values here. |

**The build tool and its files**

| File | Role |
|---|---|
| `gradle.md` | Gradle: the build tool, `build.gradle.kts`, `settings.gradle.kts`, the tasks that run each program. |
| `gradle-wrapper.md` | The Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) that downloads the right Gradle. |
| `posix-shell.md` | The `gradlew` script, which is a POSIX shell script. |
| `windows-batch.md` | The `gradlew.bat` script, a Windows batch file. |
| `toml-version-catalog.md` | `gradle/libs.versions.toml`, the one place for dependency versions. |
| `dependency-locking.md` | `gradle.lockfile` and `settings-gradle.lockfile`, which pin exact versions. |
| `spotless-google-java-format.md` | The formatter that the build enforces. |
| `jacoco.md` | The line-coverage tool and its 80 percent floor. |

**The code walkthroughs**

| File | Role |
|---|---|
| `java-availability-walkthrough.md` | Every file of the `availability` package, line by line: the number type, the topology, the parser, the calculator, the report. |
| `java-sheet-walkthrough.md` | Every file of the `sheet` package, line by line: the Markdown reader, the validator, the target records, the report. |
| `java-labs-walkthrough.md` | The two labs, with their real output and what each number means. |

**The tests**

| File | Role |
|---|---|
| `junit.md` | JUnit: how the test engine finds and runs the tests. |
| `assertj.md` | AssertJ: the fluent assertions used in the tests. |
| `instancio.md` | Instancio: seeded generated data for the property tests. |
| `tests-availability-walkthrough.md` | The tests of the `availability` package, line by line. |
| `tests-sheet-walkthrough.md` | The tests of the `sheet` package, line by line. |

**Everything else**

| File | Role |
|---|---|
| `markdown.md` | Markdown: the file format of the sheets, the README and these notes. |
| `docker-compose.md` | `compose.yaml`, the optional way to run the build in a container. |
| `editorconfig.md` | `.editorconfig`, the editor settings. |
| `git.md` | Git: the version control that tracks the files. |

## Reading order

1. **The language first.** `java.md`, then `result4j.md`. Do not skip `java.md`: every walkthrough relies on it.
2. **The build tool.** `gradle.md`, `gradle-wrapper.md`, `posix-shell.md`, `windows-batch.md`, `toml-version-catalog.md`, `dependency-locking.md`, `spotless-google-java-format.md`, `jacoco.md`.
3. **The code walkthroughs.** `java-availability-walkthrough.md`, `java-sheet-walkthrough.md`, `java-labs-walkthrough.md`.
4. **The tests.** `junit.md`, `assertj.md`, `instancio.md`, `tests-availability-walkthrough.md`, `tests-sheet-walkthrough.md`.
5. **The rest.** `markdown.md`, `docker-compose.md`, `editorconfig.md`, `git.md`.

## If you have ten minutes

1. `java.md`: the words and features that every other file uses. Read the walkthrough section on records, sealed interfaces and `switch`.
2. `java-availability-walkthrough.md`: the heart of the project. Read the parts on `Availability.java` and `AvailabilityCalculator.java` to see why a chain multiplies and a parallel group multiplies the failure shares.
3. `result4j.md`: why a typo in the input is a value, not an exception.

## How to run what the notes describe

Every command in these notes was run in this repository, on Java 25.0.2 and Gradle 9.9.1 through `./gradlew`, on 10 November 2026. Start with:

```
./gradlew -q availability --args='chain(gateway=99.9, seatwave=99.9, payments=99.9)'
```

It should print `End-to-end availability: 99.7003 %`.
