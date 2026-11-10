package io.plainround.seatwave.targets.availability;

import java.util.Objects;

/**
 * One part of a system with a known availability, such as a load balancer or a payment provider.
 *
 * <p>Immutable and safe to share between threads.
 *
 * @param name the name the report prints for the part
 * @param availability the share of the window in which the part works
 */
public record Component(String name, Availability availability) {

  /**
   * Creates a component.
   *
   * @throws NullPointerException if the name or the availability is null
   * @throws IllegalArgumentException if the name is blank
   */
  public Component {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(availability, "availability");
    if (name.isBlank()) {
      throw new IllegalArgumentException("a component needs a name");
    }
  }
}
