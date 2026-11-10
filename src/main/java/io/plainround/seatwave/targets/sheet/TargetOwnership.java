package io.plainround.seatwave.targets.sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Who answers for a target and why the business wants it.
 *
 * <p>An ownership with a blank owner or a blank reason cannot be built. Immutable and safe to share
 * between threads.
 *
 * @param owner the role that agreed to the target and answers for it
 * @param reason the business reason the target exists
 */
public record TargetOwnership(String owner, String reason) {

  /**
   * Creates an ownership.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if a field breaks one of the rules of {@link #problemsIn}
   */
  public TargetOwnership {
    List<RowProblem> problems = problemsIn(owner, reason);
    if (!problems.isEmpty()) {
      throw new IllegalArgumentException("not a complete ownership: " + problems);
    }
    owner = owner.strip();
    reason = reason.strip();
  }

  /**
   * Lists what stops two cells from being a complete ownership.
   *
   * @param owner the text of the Owner cell
   * @param reason the text of the Reason cell
   * @return every problem found, or an empty list when both cells are filled
   * @throws NullPointerException if a cell is null
   */
  public static List<RowProblem> problemsIn(String owner, String reason) {
    Objects.requireNonNull(owner, "owner");
    Objects.requireNonNull(reason, "reason");
    List<RowProblem> problems = new ArrayList<>();
    // A target nobody owns is a target nobody agreed to.
    if (CellText.isMissing(owner)) {
      problems.add(new RowProblem.MissingField(SheetColumn.OWNER));
    }
    if (CellText.isMissing(reason)) {
      problems.add(new RowProblem.MissingField(SheetColumn.REASON));
    }
    return List.copyOf(problems);
  }
}
