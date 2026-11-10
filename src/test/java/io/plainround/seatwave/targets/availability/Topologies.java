package io.plainround.seatwave.targets.availability;

import java.math.BigDecimal;
import java.util.List;

/** Builds topologies for tests, so each test reads as the topology it checks. */
final class Topologies {

  private Topologies() {}

  static Topology.Single part(String name, String percentText) {
    return new Topology.Single(
        new Component(name, Availability.ofPercent(new BigDecimal(percentText))));
  }

  static Topology.Chain chain(Topology... parts) {
    return new Topology.Chain(List.of(parts));
  }

  static Topology.Parallel parallel(Topology... copies) {
    return new Topology.Parallel(List.of(copies));
  }

  static Availability percent(String percentText) {
    return Availability.ofPercent(new BigDecimal(percentText));
  }
}
