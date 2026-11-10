package io.plainround.seatwave.targets.availability;

import java.util.List;
import java.util.Objects;

/**
 * The way the parts of a system depend on each other: one part, a chain or a parallel group.
 *
 * <p>The interface is sealed, so these three shapes are the full list and the compiler rejects a
 * switch that forgets one. A chain or a parallel group can hold other chains and groups, which is
 * how a mixed topology is written. Every shape is immutable and safe to share between threads.
 */
public sealed interface Topology {

  /**
   * A single part.
   *
   * @param component the part and its availability
   */
  record Single(Component component) implements Topology {

    /**
     * Creates a single-part topology.
     *
     * @throws NullPointerException if the component is null
     */
    public Single {
      Objects.requireNonNull(component, "component");
    }
  }

  /**
   * Parts in a chain: a request needs every one of them.
   *
   * @param parts the parts, in the order a request meets them
   */
  record Chain(List<Topology> parts) implements Topology {

    /**
     * Creates a chain.
     *
     * @throws NullPointerException if the list or one of its elements is null
     * @throws IllegalArgumentException if the list is empty
     */
    public Chain {
      parts = copyOfNonEmpty(parts, "a chain");
    }
  }

  /**
   * Copies in parallel: a request needs only one of them.
   *
   * <p>The arithmetic for a parallel group assumes that the copies fail independently.
   *
   * @param copies the copies, each able to do the whole job alone
   */
  record Parallel(List<Topology> copies) implements Topology {

    /**
     * Creates a parallel group.
     *
     * @throws NullPointerException if the list or one of its elements is null
     * @throws IllegalArgumentException if the list is empty
     */
    public Parallel {
      copies = copyOfNonEmpty(copies, "a parallel group");
    }
  }

  private static List<Topology> copyOfNonEmpty(List<Topology> children, String kind) {
    Objects.requireNonNull(children, "children");
    if (children.isEmpty()) {
      throw new IllegalArgumentException(kind + " needs at least one part");
    }
    // An unmodifiable copy: nobody can add an unchecked part after construction.
    return List.copyOf(children);
  }
}
