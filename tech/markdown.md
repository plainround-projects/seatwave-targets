# Markdown tables (the format of the four sheets)

## What it is

**Markdown** is a plain-text format where a few symbols give structure: `#` at the start of a line makes a heading, a line that starts with `|` is a row of a table. A Markdown viewer draws it nicely, but the file itself stays readable, and any program can read it as plain lines of text. A **table** in Markdown has a header row, a separator row, and data rows. Cells are divided by the pipe character `|`.

## Why this repository uses it

The requirements sheet of Module 2 is a table with one numeric target per row. A Markdown table is readable in an editor, in Git history and on a web page, and it can be checked by a program. The README says: "A sheet is one Markdown table in a file." The four sheets are the data the validator reads, and they are the test data of `ShippedSheetsTest` (`tests-sheet-walkthrough.md`).

## Where it appears

| Path | What it does with Markdown |
| --- | --- |
| `sheets/arena-on-sale.md` | The arena on-sale sheet: 24 targets. |
| `sheets/club.md` | The small club sheet: 6 targets. |
| `sheets/lab1-draft.md` | A deliberately bad draft: 4 rows, all rejected. |
| `sheets/lab1-solution.md` | The answer to the lab: 8 targets. |
| `src/main/java/.../sheet/MarkdownSheetReader.java` | The reader that turns table lines into rows. |
| `README.md`, `ARCHITECTURE.md`, `CHANGELOG.md`, `tech/*.md` | Ordinary Markdown documents. |

## How it works underneath

1. A **table row** is one line that starts with `|`. The cells are the text between the pipes. Spaces around a cell are only for the eye.
2. The **header row** names the columns. The **separator row** comes next and is made of dashes: `|---|---|`. Colons in it set alignment (`:---` left, `---:` right, `:---:` centered) and carry no data.
3. Every row after that is a **data row**.
4. A table needs the same number of cells on every row. In this repository, exactly eight.
5. The text above and below the table (a title, notes) is not part of it. The sheets use that space for the sign-off line, the scope of measurement and the sale window.
6. A pipe character inside a cell would split it, so cell text must not contain `|`.

## Walkthrough of this repository's use

### The header and separator

```markdown
| Operation | Attribute | Metric | Percentile | Threshold | Window | Owner | Reason |
|---|---|---|---|---|---|---|---|
```

The eight columns, in this order. The README lists them, and the reader checks them without regard to case (`MarkdownSheetReaderTest.matchesTheHeaderWithoutRegardToCase`):
- **Operation** is one of the four SeatWave operations or `All operations`.
- **Attribute** is the quality attribute (Latency, Throughput, Availability, ...).
- **Metric** says what is measured.
- **Percentile** is `p50`, `p95`, `p99`, `p99.9` or a kind such as `rate`, `count`, `peak`, `mean`, `max`, `min`, `ratio`.
- **Threshold** is the number that must hold. It must contain a digit.
- **Window** says over which period or load it is measured.
- **Owner** names who answers for it.
- **Reason** says why this number.

### Representative rows from `sheets/arena-on-sale.md`

```markdown
| List events | Latency | Response time at the load balancer | p99 | under 500 ms | Sale window, 10:00 to 10:15 | Platform lead | It is the first page a fan sees, and a slow list delays every later step |
| Hold a seat | Reliability | Seats with more than one active hold or booking | count | 0 | Every on-sale, checked within 1 hour of its end | Booking lead | One double booking means a refund, a fan turned away at the door and a press story |
| All operations | Availability | Share of good minutes | rate | at least 99.95 % | 30-day month of 43,200 minutes | Platform lead | The venue contract allows 21.6 bad minutes a month, and listings run all month |
```

Read the first row cell by cell: operation `List events`, attribute `Latency`, metric `Response time at the load balancer`, percentile `p99`, threshold `under 500 ms`, window `Sale window, 10:00 to 10:15`, owner `Platform lead`, and the reason. The second row uses `count` instead of a percentile because "seats with a double booking" is a count, and its threshold is `0`. The third uses `rate`.

### Representative rows from `sheets/lab1-draft.md`

This sheet starts with a title on line 1 and a blank line 2, so the header is on line 3, the separator on line 4 and the first data row on line 5. That is why the validator reports `REJECTED line 5`.

```markdown
| List events | Latency | Response time at the load balancer |  | under 500 ms | Sale window, 10:00 to 10:15 | Platform lead | It is the first page a fan sees |
| Hold a seat | Latency | Response time |  | fast |  |  |  |
| Confirm a booking | Availability | Share of good requests | rate | highly available | Sale window, 10:00 to 10:15 | TBD | Fans must be able to pay |
```

- Row 1 has an empty Percentile cell (two pipes with only spaces between them). The reader keeps it as an empty string, and the validator says `percentile is missing`.
- Row 2 has `fast` as the threshold (no number), and empty Window, Owner and Reason cells.
- Row 3 has `TBD` as the owner. `TBD` is a placeholder and counts as empty. Its threshold, `highly available`, has no digit.

### How the program reads a sheet

`MarkdownSheetReader` is the one class that knows about Markdown. In order: it takes the lines of the file, keeps only the lines that start with `|` after stripping spaces, drops the separator row (it matches a pattern of pipes and runs of three or more dashes with optional colons), splits each remaining line at the pipes, keeping empty cells, strips each cell, and remembers each row's line number (1-based). The first kept row must be the header; if its eight names do not match, the result is `WrongHeader(line)`. No table at all gives `NoTable`; a header with no rows gives `NoTargets`. Every other row becomes a `SheetRow(lineNumber, cells)` that goes on to the validator. Each of these behaviors has a test (`tests-sheet-walkthrough.md`).

## Try it yourself

1. Validate the arena sheet:

```
./gradlew -q validateSheet --args='sheets/arena-on-sale.md'
```

   Expected output:

```
Targets accepted: 24
SHEET ACCEPTED: every target has a metric, a percentile or rate, a threshold, a window, an owner and a reason.
```

2. Validate the bad draft and read the line numbers:

```
./gradlew -q validateSheet --args='sheets/lab1-draft.md'
```

   Expected: four lines starting `REJECTED line 5`, `line 6`, `line 7`, `line 8`, then `SHEET REJECTED: fix every row above before the design starts.` The program exits with status 1.

3. Count the table lines of a sheet (the header plus 24 rows):

```
grep -c '^| ' sheets/arena-on-sale.md
```

   Expected: `25`.

4. Count the cells of the first data row. The split at `|` gives ten fields: eight cells and an empty piece before the first and after the last pipe:

```
awk -F'|' 'NR==10{print NF}' sheets/arena-on-sale.md
```

   Expected: `10`.

## Mistakes people make

- **Putting a `|` inside a cell.** It splits the cell. Rewrite the text.
- **A row with seven or nine cells.** The validator reports `expected 8 cells but found 7`.
- **Leaving a cell blank** or writing `TBD`, `n/a` or `-`. These count as missing.
- **Writing `fast` or `highly available` as a threshold.** Without a digit nobody can test it.
- **Two rows for the same measure.** The later one is rejected as a duplicate.
- **Changing the order of the header columns.** The reader checks the order.
- **Forgetting that line numbers count the lines above the table too.**

## Check yourself

1. *What makes a line a table row for this reader?* It starts with `|` after stripping spaces, and it is not the separator row.
2. *What does the separator row look like?* `|---|---|...`, optionally with colons for alignment.
3. *Why is the first data row of `lab1-draft.md` on line 5?* Line 1 is the title, 2 is blank, 3 is the header, 4 is the separator.
4. *How many cells must a row have?* Exactly eight.
5. *Are empty cells kept?* Yes, as empty strings, so the validator can report them.

## Further reading

- CommonMark specification, 0.31.2: <https://spec.commonmark.org/0.31.2/>
- GitHub Flavored Markdown tables (the table syntax used here): <https://github.github.com/gfm/#tables-extension->
