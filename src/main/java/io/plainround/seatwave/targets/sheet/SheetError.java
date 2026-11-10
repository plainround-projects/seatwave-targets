package io.plainround.seatwave.targets.sheet;

import java.util.List;
import java.util.Objects;

/**
 * Why a requirements sheet is not accepted.
 *
 * <p>The interface is sealed, so a switch over it must handle every reason.
 */
public sealed interface SheetError {

  /** The file has no Markdown table. */
  record NoTable() implements SheetError {}

  /**
   * The first row of the table is not the expected header.
   *
   * @param lineNumber the one-based line of the header row
   */
  record WrongHeader(int lineNumber) implements SheetError {}

  /** The table has a header and no rows under it. */
  record NoTargets() implements SheetError {}

  /**
   * At least one row is not a complete target.
   *
   * @param acceptedCount the number of rows that are complete targets
   * @param rejections the rejected rows in file order, at least one
   */
  record RowsRejected(int acceptedCount, List<RowRejection> rejections) implements SheetError {

    /**
     * Creates the error.
     *
     * @throws NullPointerException if the list or one of its elements is null
     */
    public RowsRejected {
      Objects.requireNonNull(rejections, "rejections");
      rejections = List.copyOf(rejections);
    }
  }
}
