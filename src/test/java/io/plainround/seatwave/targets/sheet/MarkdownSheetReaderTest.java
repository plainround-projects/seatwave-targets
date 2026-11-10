package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MarkdownSheetReaderTest {

  private static final String HEADER =
      "| Operation | Attribute | Metric | Percentile | Threshold | Window | Owner | Reason |";
  private static final String SEPARATOR = "|---|---|---|---|---|---|---|---|";
  private static final String ROW =
      "| List events | Latency | Response time | p99 | 1 s | Month | Lead | Why |";

  @Test
  void readsOneRowWithItsLineNumberAndEightStrippedCells() {
    List<String> lines = List.of(HEADER, SEPARATOR, ROW);

    List<SheetRow> rows = SheetFixtures.rowsRead(lines);

    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().lineNumber()).isEqualTo(3);
    assertThat(rows.getFirst().cells())
        .containsExactly(
            "List events", "Latency", "Response time", "p99", "1 s", "Month", "Lead", "Why");
  }

  @Test
  void ignoresTextAroundTheTable() {
    List<String> lines =
        List.of("# Title", "", "Some notes.", HEADER, SEPARATOR, ROW, "", "More notes.");

    List<SheetRow> rows = SheetFixtures.rowsRead(lines);

    assertThat(rows).hasSize(1);
    // The title and notes take lines 1 to 3, so the row is on line 6.
    assertThat(rows.getFirst().lineNumber()).isEqualTo(6);
  }

  @Test
  void reportsNoTableWhenNoLineStartsWithADivider() {
    assertThat(SheetFixtures.readErrorOf(List.of("# Title", "no table here")))
        .isEqualTo(new SheetError.NoTable());
  }

  @Test
  void reportsNoTableForAnEmptyFile() {
    assertThat(SheetFixtures.readErrorOf(List.of())).isEqualTo(new SheetError.NoTable());
  }

  @Test
  void reportsNoTableWhenOnlyASeparatorRowIsPresent() {
    assertThat(SheetFixtures.readErrorOf(List.of(SEPARATOR))).isEqualTo(new SheetError.NoTable());
  }

  @Test
  void reportsTheLineOfAWrongHeader() {
    List<String> lines =
        List.of(
            "intro",
            "| Operation | Attribute | Metric | Percentile | Threshold | Window | Owner |",
            SEPARATOR,
            ROW);

    assertThat(SheetFixtures.readErrorOf(lines)).isEqualTo(new SheetError.WrongHeader(2));
  }

  @Test
  void reportsAHeaderWithTheRightWordsInTheWrongOrder() {
    String swapped =
        "| Attribute | Operation | Metric | Percentile | Threshold | Window | Owner | Reason |";

    assertThat(SheetFixtures.readErrorOf(List.of(swapped, SEPARATOR, ROW)))
        .isEqualTo(new SheetError.WrongHeader(1));
  }

  @Test
  void matchesTheHeaderWithoutRegardToCase() {
    String shouting = HEADER.toUpperCase(Locale.ROOT);

    assertThat(SheetFixtures.rowsRead(List.of(shouting, SEPARATOR, ROW))).hasSize(1);
  }

  @Test
  void reportsNoTargetsForAHeaderWithNoRows() {
    assertThat(SheetFixtures.readErrorOf(List.of(HEADER, SEPARATOR)))
        .isEqualTo(new SheetError.NoTargets());
  }

  @Test
  void keepsEmptyCellsAsEmptyStrings() {
    String rowWithGaps = "| List events | Latency | Response time |  | 1 s |  | Lead | Why |";

    List<SheetRow> rows = SheetFixtures.rowsRead(List.of(HEADER, SEPARATOR, rowWithGaps));

    assertThat(rows.getFirst().cells()).hasSize(8).containsSequence("Response time", "", "1 s", "");
  }

  @Test
  void keepsATrailingEmptyCell() {
    String rowWithEmptyReason = "| List events | Latency | Metric | p99 | 1 s | Month | Lead |  |";

    List<SheetRow> rows = SheetFixtures.rowsRead(List.of(HEADER, SEPARATOR, rowWithEmptyReason));

    assertThat(rows.getFirst().cells()).hasSize(8);
    assertThat(rows.getFirst().cells().getLast()).isEmpty();
  }

  @Test
  void readsARowWithoutAClosingDivider() {
    String noClosingDivider = "| List events | Latency | Metric | p99 | 1 s | Month | Lead | Why";

    List<SheetRow> rows = SheetFixtures.rowsRead(List.of(HEADER, SEPARATOR, noClosingDivider));

    assertThat(rows.getFirst().cells()).hasSize(8);
    assertThat(rows.getFirst().cells().getLast()).isEqualTo("Why");
  }

  @Test
  void readsARowIndentedWithSpaces() {
    List<SheetRow> rows = SheetFixtures.rowsRead(List.of(HEADER, SEPARATOR, "   " + ROW));

    assertThat(rows).hasSize(1);
  }

  @Test
  void acceptsAlignmentMarkersInTheSeparatorRow() {
    String aligned = "|:---|---:|:---:|---|---|---|---|---|";

    assertThat(SheetFixtures.rowsRead(List.of(HEADER, aligned, ROW))).hasSize(1);
  }

  @Test
  void keepsTheRowsInFileOrder() {
    String secondRow = ROW.replace("List events", "View seats");

    List<SheetRow> rows = SheetFixtures.rowsRead(List.of(HEADER, SEPARATOR, ROW, secondRow));

    assertThat(rows).extracting(SheetRow::lineNumber).containsExactly(3, 4);
  }

  @Test
  void returnsRowsThatCannotBeChanged() {
    List<SheetRow> rows = SheetFixtures.rowsRead(List.of(HEADER, SEPARATOR, ROW));
    SheetRow extraRow = rows.getFirst();

    assertThatThrownBy(() -> rows.add(extraRow)).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void rejectsNullLines() {
    assertThatNullPointerException().isThrownBy(() -> MarkdownSheetReader.read(null));
  }
}
