# seatwave-targets

An availability calculator and a requirements-sheet validator that turn the SeatWave brief into numeric targets.

## Which module this belongs to

This is the hands-on project of Module 2, "From brief to targets", of the course System Design Fundamentals. The running case is SeatWave, a ticket-booking service with four operations: list events, view seats, hold a seat and confirm a booking.

Module 2 produces two things. The first is a requirements sheet: a Markdown table with one numeric target per row. The second is an availability calculator for chains and redundant (parallel) groups. This repository holds both, plus the sheets and two small labs.

## Prerequisites

- JDK 25. The build was verified on Eclipse Temurin 25.0.2 on macOS on 10 November 2026. The current JDK 25 update on that date was 25.0.5, which was not available on the verification machine.
- Nothing else. The Gradle wrapper (9.9.1) downloads Gradle and the libraries below on the first build.

## Dependencies and build plugins

Every version is exact. Each was checked on 10 November 2026. All were checked against the OSV database on 10 November 2026 with no known vulnerability at the pinned version. The versions live in `gradle/libs.versions.toml`, and `gradle.lockfile` pins the transitive ones.

| Coordinates | Version | Checked | License | Why the JDK alone is not enough |
|---|---|---|---|---|
| `com.github.sviperll:result4j` | 1.2.0 | 10 Nov 2026 | Apache-2.0 | The JDK has no success-or-typed-error type, so expected failures would be exceptions. |
| `org.junit:junit-bom` and `org.junit.jupiter:junit-jupiter` | 6.1.3 | 10 Nov 2026 | EPL-2.0 | The test engine: it finds and runs the tests. |
| `org.assertj:assertj-core` | 3.27.7 | 10 Nov 2026 | Apache-2.0 | Fluent assertions with clear failure messages. |
| `org.instancio:instancio-junit` | 6.2.1 | 10 Nov 2026 | Apache-2.0 | Seeded generated test data for the property tests. |
| `com.diffplug.spotless` (Gradle plugin) | 8.11.0 | 10 Nov 2026 | Apache-2.0 | Runs the formatter in the build. |
| `com.google.googlejavaformat:google-java-format` (used by Spotless) | 2.0.0 | 10 Nov 2026 | Apache-2.0 | One enforced code style, so nobody formats by hand. |
| `org.jacoco:org.jacoco.agent` and the JaCoCo Gradle plugin | 0.8.16 | 10 Nov 2026 | EPL-2.0 | Measures line coverage and enforces the floor. |

`org.junit.platform:junit-platform-launcher` is also on the test runtime path. Its version comes from the JUnit BOM, so it is 6.1.3 too.

## Build and test

```
./gradlew check
```

This formats-checks the code with Spotless, compiles with `-Xlint:all -Werror`, runs 285 tests, and fails if line coverage drops below 0.80. Run `./gradlew spotlessApply` first to fix formatting.

Result on 10 November 2026 (Temurin 25.0.2, macOS): `BUILD SUCCESSFUL`, 285 tests, 0 failures, line coverage 80.2 percent (477 of 595 lines). The untested lines are the `main` methods of the three command-line classes and the two labs. They print and call `System.exit`, so the tests do not call them.

## Run

Each entry point has its own Gradle task. The `-q` flag hides Gradle's own messages.

Availability of a chain of three 99.9 percent parts:

```
./gradlew -q availability --args='chain(gateway=99.9, seatwave=99.9, payments=99.9)'
```

```
Topology: chain(gateway, seatwave, payments)
Parts of the chain:
  gateway                                      99.9 %
  seatwave                                     99.9 %
  payments                                     99.9 %
Weakest part: gateway (99.9 %)
End-to-end availability: 99.7003 %
Downtime budget: 129.47 minutes per 30-day month (43,200 minutes)
                 1,575.22 minutes per 365-day year (525,600 minutes), or 26.25 hours
Assumption: parts fail independently. Shared power, deploys or configuration
make a parallel group worse than the number shown.
```

Validate the arena sheet:

```
./gradlew -q validateSheet --args='sheets/arena-on-sale.md'
```

```
Targets accepted: 24
SHEET ACCEPTED: every target has a metric, a percentile or rate, a threshold, a window, an owner and a reason.
```

The hold-path lab:

```
./gradlew -q holdPathLab
```

```
== BEFORE: every part once ==
Topology: chain(browser, load-balancer, seatwave, database)
Parts of the chain:
  browser                                      100 %
  load-balancer                                99.999 %
  seatwave                                     99.5 %
  database                                     99.95 %
Weakest part: seatwave (99.5 %)
End-to-end availability: 99.449255 %
Downtime budget: 237.92 minutes per 30-day month (43,200 minutes)
                 2,894.71 minutes per 365-day year (525,600 minutes), or 48.25 hours
Assumption: parts fail independently. Shared power, deploys or configuration
make a parallel group worse than the number shown.
Target 99.95 % allows 21.60 minutes per month; this path spends 237.92: MISSES the target

== AFTER: a redundant pair at the weakest part ==
Topology: chain(browser, load-balancer, parallel(seatwave, seatwave), database)
Parts of the chain:
  browser                                      100 %
  load-balancer                                99.999 %
  parallel(seatwave, seatwave)                 99.9975 %
  database                                     99.95 %
Weakest part: database (99.95 %)
End-to-end availability: 99.946502 %
Downtime budget: 23.11 minutes per 30-day month (43,200 minutes)
                 281.19 minutes per 365-day year (525,600 minutes), or 4.69 hours
Assumption: parts fail independently. Shared power, deploys or configuration
make a parallel group worse than the number shown.
Target 99.95 % allows 21.60 minutes per month; this path spends 23.11: MISSES the target
```

The payment-cap lab:

```
./gradlew -q paymentCapLab
```

```
Design                                                 Availability  Failed of 20000   Meets 99.99 %
1. Perfect own parts, one provider in the chain        99.9 %        20.0              no
2. Real own parts, one provider in the chain           99.896479 %   20.7              no
3. Option A: two providers, independent                99.996375 %   0.7               yes
4. Option A, but both providers share one dependency   99.946377 %   10.7              no
5. Option B: confirm first, take payment afterwards    99.996475 %   0.7               yes
```

### A rejected sheet

```
./gradlew -q validateSheet --args='sheets/lab1-draft.md'
```

```
Targets accepted: 0
REJECTED line 5 (List events, Latency): percentile is missing
REJECTED line 6 (View seats, Latency): window is missing
REJECTED line 7 (Hold a seat, Latency): percentile is missing; threshold has no number; window is missing; owner is missing; reason is missing
REJECTED line 8 (Confirm a booking, Availability): threshold has no number; owner is missing
SHEET REJECTED: fix every row above before the design starts.
```

The program exits with status 1. Gradle reports that as a failed task: `Execution failed for task ':validateSheet'` and `finished with non-zero exit value 1`. The Gradle process itself also exits with 1.

### An invalid topology

```
./gradlew -q availability --args='chain(a=99.9, b=101)'
```

```
invalid topology: a percent must be between 0 and 100, but was 101
```

The program exits with status 2, the shell convention for bad usage. Gradle reports `Execution failed for task ':availability'` and `finished with non-zero exit value 2`. The Gradle process itself exits with 1.

## Project structure

```
seatwave-targets/
  build.gradle.kts            Build: Java 25, -Werror, Spotless, JaCoCo floor, one task per entry point
  settings.gradle.kts         Project name and the single repository (Maven Central)
  gradle/                     Wrapper files and libs.versions.toml, the one place for versions
  gradle.lockfile             Pinned transitive versions (settings-gradle.lockfile does the same for settings)
  sheets/                     The four requirements sheets, as Markdown tables
  src/main/java/io/plainround/seatwave/targets/
    availability/             Topology, parser, calculator, downtime and report; the availability command
    sheet/                    Markdown reader, row validator, target records and report; the sheet command
    labs/                     The hold-path lab and the payment-cap lab
  src/test/java/io/plainround/seatwave/targets/
    availability/             Tests for the availability package
    sheet/                    Tests for the sheet package, including the shipped sheets
```

## Topology grammar

```
topology  = component | group
component = name "=" percent
group     = ("chain" | "parallel") "(" topology { "," topology } ")"
name      = one or more letters, digits, "-" or "_"
percent   = a decimal number from 0 to 100, such as 99.9
```

A chain needs every part. A parallel group needs only one copy. Spaces are ignored. The parser accepts at most 2,000 characters and 16 levels of nesting.

## The sheet

A sheet is one Markdown table in a file. The header must have these eight columns, in this order: Operation, Attribute, Metric, Percentile, Threshold, Window, Owner, Reason.

The validator enforces these rules:

- Every row has exactly eight cells.
- Operation is one of: List events, View seats, Hold a seat, Confirm a booking, All operations.
- Attribute is one of: Latency, Throughput, Scalability, Availability, Reliability, Efficiency, Maintainability, Security, Privacy, Compliance.
- Metric, Window, Owner and Reason are not empty.
- Percentile is a percentile such as p50, p95, p99 or p99.9, or one of: count, max, mean, min, peak, rate, ratio.
- Threshold contains at least one digit. "fast" is not a target.
- Placeholders count as empty: TBD, TODO, n/a, na, none, unknown, ?, and -.
- Two rows may not set a target for the same measure (same operation, attribute, metric, percentile and window, ignoring case). The later row is rejected.
- One bad row rejects the whole sheet. The report lists every bad row, so one run shows all the work.

## What is simplified for teaching

- The availabilities are assumptions typed in by the learner, not measurements.
- The arithmetic assumes that parts fail independently. Shared power, shared deploys and shared configuration make a real parallel group worse than the number shown.
- No service is started. The calculator works on numbers only.
- A production tool would read the sheet from a managed source and keep the limits in configuration. Here the parser limits are constants, because they protect the parser rather than tune it.

## Continuous integration

There is no CI workflow in this repository on purpose. `./gradlew check` is the gate: run it before every commit.

## Docker Compose (optional)

`compose.yaml` runs `./gradlew --no-daemon check` inside `eclipse-temurin:25.0.5_8-jdk-noble`, with the repository mounted at `/workspace` and a named volume for `/root/.gradle`. The image tag was checked on Docker Hub on 10 November 2026.

```
docker compose up
```

[not run] Docker was not running on the verification machine, so this path was not tested. It would also use JDK 25.0.5, which is newer than the 25.0.2 used for every result above.

## License

MIT. Copyright 2026 Sumit Srivastava. See `LICENSE`.
