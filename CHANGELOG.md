# Changelog

All notable changes to this project are written here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses
[Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-11-10

### Added

- `tech/`: 22 beginner notes, one per technology, with line-by-line walkthroughs of the main code,
  the tests, the build files and the wrapper scripts, and an index with a reading order.

### Fixed

- `.editorconfig` now states four spaces for `*.kts` files, which is what the build files use.

## [1.0.0] - 2026-11-10

### Added

- Availability calculator for chains and redundant (parallel) groups, with a topology parser,
  a downtime budget and a text report.
- Requirements-sheet validator: reads a Markdown table, checks the eight columns and the rules
  for each row, and rejects the whole sheet when one row is incomplete.
- Four sheets: the arena sale, the club, and the Lab 1 draft and solution.
- Two labs: the hold path and the payment cap.
- Tests with JUnit Jupiter, AssertJ and seeded Instancio property tests.
- Build with a pinned Gradle wrapper, a dependency lock file, `-Xlint:all -Werror`, Spotless with
  google-java-format, and a JaCoCo line-coverage floor of 0.80.
- Optional Docker Compose file that runs the build in a JDK 25 container.
