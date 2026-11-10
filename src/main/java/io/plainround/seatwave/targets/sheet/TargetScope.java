package io.plainround.seatwave.targets.sheet;

import java.util.Objects;

/**
 * What a target is about: an operation and one quality attribute of it.
 *
 * @param operation the operation the target applies to
 * @param attribute the quality attribute the target sets a level on
 */
public record TargetScope(Operation operation, QualityAttribute attribute) {

  /**
   * Creates a scope.
   *
   * @throws NullPointerException if the operation or the attribute is null
   */
  public TargetScope {
    Objects.requireNonNull(operation, "operation");
    Objects.requireNonNull(attribute, "attribute");
  }
}
