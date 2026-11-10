package io.plainround.seatwave.targets.sheet;

import com.github.sviperll.result4j.Result;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reads the first Markdown table of a file into rows.
 *
 * <p>This class is the only place that knows the sheet is written in Markdown. Everything after it
 * works on {@link SheetRow} values. Stateless and safe to call from any thread.
 */
public final class MarkdownSheetReader {

  private static final String CELL_DIVIDER = "|";
  // The row under the header, such as |---|:---:|, which carries no data.
  private static final Pattern SEPARATOR_ROW = Pattern.compile("\\|(\\s*:?-{3,}:?\\s*\\|)+");
  // A negative limit keeps trailing empty cells: an empty cell is a missing field to report.
  private static final int KEEP_EMPTY_CELLS = -1;

  private MarkdownSheetReader() {}

  /**
   * Extracts the data rows of the sheet.
   *
   * <p>Lines that do not start with a cell divider are ignored, so a title and notes may surround
   * the table.
   *
   * @param lines the lines of the file, in order
   * @return the rows under the header, or the reason the table cannot be used
   * @throws NullPointerException if the list is null
   */
  public static Result<List<SheetRow>, SheetError> read(List<String> lines) {
    Objects.requireNonNull(lines, "lines");
    List<SheetRow> tableRows = tableRowsOf(lines);
    if (tableRows.isEmpty()) {
      return Result.error(new SheetError.NoTable());
    }
    SheetRow header = tableRows.getFirst();
    if (!isExpectedHeader(header)) {
      return Result.error(new SheetError.WrongHeader(header.lineNumber()));
    }
    List<SheetRow> dataRows = tableRows.subList(1, tableRows.size());
    if (dataRows.isEmpty()) {
      return Result.error(new SheetError.NoTargets());
    }
    return Result.success(List.copyOf(dataRows));
  }

  private static List<SheetRow> tableRowsOf(List<String> lines) {
    List<SheetRow> tableRows = new ArrayList<>();
    for (int lineIndex = 0; lineIndex < lines.size(); lineIndex++) {
      String line = lines.get(lineIndex).strip();
      if (line.startsWith(CELL_DIVIDER) && !SEPARATOR_ROW.matcher(line).matches()) {
        tableRows.add(new SheetRow(lineIndex + 1, cellsOf(line)));
      }
    }
    return tableRows;
  }

  private static List<String> cellsOf(String line) {
    int end = line.length() > 1 && line.endsWith(CELL_DIVIDER) ? line.length() - 1 : line.length();
    String withoutOuterDividers = line.substring(1, end);
    List<String> cells = new ArrayList<>();
    for (String cell : withoutOuterDividers.split(Pattern.quote(CELL_DIVIDER), KEEP_EMPTY_CELLS)) {
      cells.add(cell.strip());
    }
    return cells;
  }

  private static boolean isExpectedHeader(SheetRow header) {
    List<String> expected = SheetColumn.headings();
    if (header.cells().size() != expected.size()) {
      return false;
    }
    for (int columnIndex = 0; columnIndex < expected.size(); columnIndex++) {
      if (!expected.get(columnIndex).equalsIgnoreCase(header.cells().get(columnIndex))) {
        return false;
      }
    }
    return true;
  }
}
