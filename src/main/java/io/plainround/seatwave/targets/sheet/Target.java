package io.plainround.seatwave.targets.sheet;

import java.util.Objects;

/**
 * One row of the requirements sheet: a complete, testable target.
 *
 * <p>Each of the three parts validates its own fields, so a target with a missing metric,
 * percentile, threshold, window, owner or reason cannot exist. Immutable and safe to share between
 * threads.
 *
 * @param scope the operation and the quality attribute
 * @param measure the metric, the percentile or rate, the threshold and the window
 * @param ownership the owner and the business reason
 */
public record Target(TargetScope scope, TargetMeasure measure, TargetOwnership ownership) {

  /**
   * Creates a target.
   *
   * @throws NullPointerException if a part is null
   */
  public Target {
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(measure, "measure");
    Objects.requireNonNull(ownership, "ownership");
  }
}
