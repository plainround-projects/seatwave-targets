package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class TopologyTest {

  @Test
  void rejectsAnEmptyChain() {
    assertThatThrownBy(() -> new Topology.Chain(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("chain needs at least one part");
  }

  @Test
  void rejectsAnEmptyParallelGroup() {
    assertThatThrownBy(() -> new Topology.Parallel(List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("parallel group needs at least one part");
  }

  @Test
  void rejectsANullListInAChainAndInAParallelGroup() {
    assertThatNullPointerException().isThrownBy(() -> new Topology.Chain(null));
    assertThatNullPointerException().isThrownBy(() -> new Topology.Parallel(null));
  }

  @Test
  void rejectsANullElementInAChain() {
    List<Topology> partsWithNull = Arrays.asList(Topologies.part("gateway", "99.9"), null);

    assertThatNullPointerException().isThrownBy(() -> new Topology.Chain(partsWithNull));
  }

  @Test
  void rejectsANullElementInAParallelGroup() {
    List<Topology> copiesWithNull = Arrays.asList(null, Topologies.part("seatwave", "99.9"));

    assertThatNullPointerException().isThrownBy(() -> new Topology.Parallel(copiesWithNull));
  }

  @Test
  void rejectsANullComponentInASinglePart() {
    assertThatNullPointerException().isThrownBy(() -> new Topology.Single(null));
  }

  @Test
  void copiesTheListSoLaterChangesDoNotReachTheChain() {
    List<Topology> parts = new ArrayList<>(List.of(Topologies.part("gateway", "99.9")));
    Topology.Chain chain = new Topology.Chain(parts);

    parts.add(Topologies.part("payments", "99.9"));

    assertThat(chain.parts()).hasSize(1);
  }

  @Test
  void copiesTheListSoLaterChangesDoNotReachTheParallelGroup() {
    List<Topology> copies = new ArrayList<>(List.of(Topologies.part("seatwave", "99.9")));
    Topology.Parallel group = new Topology.Parallel(copies);

    copies.clear();

    assertThat(group.copies()).hasSize(1);
  }

  @Test
  void exposesNoWayToAddAPartAfterConstruction() {
    Topology.Chain chain = Topologies.chain(Topologies.part("gateway", "99.9"));
    Topology extraPart = Topologies.part("payments", "99.9");
    List<Topology> exposedParts = chain.parts();

    assertThatThrownBy(() -> exposedParts.add(extraPart))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
