package io.plainround.seatwave.targets.sheet;

/**
 * One reason a row of the sheet is not a complete target.
 *
 * <p>These are expected failures of a draft sheet, so the validator returns them as values. The
 * interface is sealed, so a switch over it must handle every reason.
 */
public sealed interface RowProblem {

  /**
   * The row does not have one cell per column.
   *
   * @param expected the number of columns of the sheet
   * @param found the number of cells in the row
   */
  record WrongCellCount(int expected, int found) implements RowProblem {}

  /**
   * A cell is blank or holds a placeholder such as "TBD".
   *
   * @param column the column of the empty cell
   */
  record MissingField(SheetColumn column) implements RowProblem {}

  /**
   * A cell holds a value that is not on the list its column allows.
   *
   * @param column the column of the cell
   * @param found the text of the cell
   */
  record NotAllowed(SheetColumn column, String found) implements RowProblem {}

  /**
   * The threshold has no digit in it, as in "fast" or "highly available".
   *
   * @param found the text of the Threshold cell
   */
  record ThresholdWithoutNumber(String found) implements RowProblem {}

  /**
   * An earlier row already sets a target for the same measure.
   *
   * @param firstLineNumber the line of the earlier row
   */
  record DuplicateOfLine(int firstLineNumber) implements RowProblem {}
}
