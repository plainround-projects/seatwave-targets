package io.plainround.seatwave.targets.sheet;

import java.util.List;
import java.util.Objects;

/**
 * One data row of the Markdown table, before it is checked.
 *
 * @param lineNumber the one-based line of the row in the file, for error messages
 * @param cells the text of each cell, stripped of surrounding spaces
 */
public record SheetRow(int lineNumber, List<String> cells) {

  /**
   * Creates a row.
   *
   * @throws NullPointerException if the list or one of its elements is null
   */
  public SheetRow {
    Objects.requireNonNull(cells, "cells");
    cells = List.copyOf(cells);
  }

  /**
   * Tells whether the row has one cell per column.
   *
   * @return true when the number of cells equals the number of columns
   */
  public boolean hasEveryColumn() {
    return cells.size() == SheetColumn.values().length;
  }

  /**
   * Returns the text of one cell.
   *
   * @param column the column to read
   * @return the text of the cell in that column
   * @throws IllegalStateException if the row does not have one cell per column
   */
  public String cell(SheetColumn column) {
    Objects.requireNonNull(column, "column");
    if (!hasEveryColumn()) {
      throw new IllegalStateException("line " + lineNumber + " does not have every column");
    }
    return cells.get(column.ordinal());
  }
}
