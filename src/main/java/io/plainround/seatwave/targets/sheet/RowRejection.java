package io.plainround.seatwave.targets.sheet;

import java.util.List;
import java.util.Objects;

/**
 * A row that is not a complete target, with every reason found.
 *
 * @param lineNumber the one-based line of the row in the file
 * @param rowLabel a short name for the row, for example "Hold a seat, Latency"
 * @param problems the reasons, at least one
 */
public record RowRejection(int lineNumber, String rowLabel, List<RowProblem> problems) {

  /**
   * Creates a rejection.
   *
   * @throws NullPointerException if the label, the list or one of its elements is null
   * @throws IllegalArgumentException if the list of problems is empty
   */
  public RowRejection {
    Objects.requireNonNull(rowLabel, "rowLabel");
    Objects.requireNonNull(problems, "problems");
    if (problems.isEmpty()) {
      throw new IllegalArgumentException("a rejection needs at least one problem");
    }
    problems = List.copyOf(problems);
  }
}
