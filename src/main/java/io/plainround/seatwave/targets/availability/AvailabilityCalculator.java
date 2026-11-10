package io.plainround.seatwave.targets.availability;

import java.util.Objects;

/**
 * Computes the availability of a topology from the availability of its parts.
 *
 * <p>The arithmetic assumes that parts fail independently. Stateless and safe to call from any
 * thread.
 */
public final class AvailabilityCalculator {

  private AvailabilityCalculator() {}

  /**
   * Evaluates a topology from the inside out.
   *
   * <p>A chain multiplies the availability of its parts. A parallel group multiplies the
   * unavailability of its copies and subtracts the product from 1.
   *
   * @param topology the topology to evaluate
   * @return the end-to-end availability, which the {@link Availability} type keeps between 0 and 1
   * @throws NullPointerException if the topology is null
   */
  public static Availability evaluate(Topology topology) {
    Objects.requireNonNull(topology, "topology");
    return switch (topology) {
      case Topology.Single single -> single.component().availability();
      case Topology.Chain chain -> {
        // A product is a running multiplication: start at "always up" and fold in each part.
        Availability everyPartUp = Availability.ALWAYS_UP;
        for (Topology part : chain.parts()) {
          everyPartUp = everyPartUp.bothUp(evaluate(part));
        }
        yield everyPartUp;
      }
      case Topology.Parallel parallel -> {
        // The group is down only when every copy is down, so start at "always down".
        Availability anyCopyUp = Availability.ALWAYS_DOWN;
        for (Topology copy : parallel.copies()) {
          anyCopyUp = anyCopyUp.eitherUp(evaluate(copy));
        }
        yield anyCopyUp;
      }
    };
  }

  /**
   * Finds the part of a chain to improve first.
   *
   * @param chain the chain to inspect
   * @return the part with the lowest availability; the first one when several are equal
   * @throws NullPointerException if the chain is null
   */
  public static Topology weakestPart(Topology.Chain chain) {
    Objects.requireNonNull(chain, "chain");
    Topology weakest = chain.parts().getFirst();
    for (Topology part : chain.parts()) {
      if (evaluate(part).isLowerThan(evaluate(weakest))) {
        weakest = part;
      }
    }
    return weakest;
  }
}
