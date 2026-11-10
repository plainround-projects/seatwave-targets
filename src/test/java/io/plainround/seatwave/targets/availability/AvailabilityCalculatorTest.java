package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.offset;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AvailabilityCalculatorTest {

  private static final double TOLERANCE = 1e-12;
  private static final double LOOSE_TOLERANCE = 1e-9;
  // 0.999 x 0.999 x 0.999, worked out by hand.
  private static final double THREE_PARTS_OF_99_9_IN_A_CHAIN = 0.997002999;
  // 1 - 0.005 x 0.005, worked out by hand.
  private static final double TWO_COPIES_OF_99_5_IN_PARALLEL = 0.999975;
  // The size of the longest chain the test builds, far beyond any real system.
  private static final int PARTS_IN_A_VERY_LONG_CHAIN = 1_000;

  @Test
  void multipliesThreePartsOfAChain() {
    Topology chain =
        Topologies.chain(
            Topologies.part("gateway", "99.9"),
            Topologies.part("seatwave", "99.9"),
            Topologies.part("payments", "99.9"));

    assertThat(AvailabilityCalculator.evaluate(chain).fraction())
        .isCloseTo(THREE_PARTS_OF_99_9_IN_A_CHAIN, offset(TOLERANCE));
  }

  @Test
  void combinesTwoCopiesInParallel() {
    Topology group =
        Topologies.parallel(
            Topologies.part("seatwave", "99.5"), Topologies.part("seatwave", "99.5"));

    assertThat(AvailabilityCalculator.evaluate(group).fraction())
        .isCloseTo(TWO_COPIES_OF_99_5_IN_PARALLEL, offset(TOLERANCE));
  }

  @Test
  void evaluatesAGroupNestedInAChain() {
    Topology mixed =
        Topologies.chain(
            Topologies.part("gateway", "99.9"),
            Topologies.parallel(
                Topologies.part("seatwave", "99.9"), Topologies.part("seatwave", "99.9")),
            Topologies.part("payments", "99.9"));

    // 0.999 x (1 - 0.001 x 0.001) x 0.999, the formula of the topology written out.
    double expected = 0.999 * (1.0 - 0.001 * 0.001) * 0.999;

    assertThat(AvailabilityCalculator.evaluate(mixed).fraction())
        .isCloseTo(expected, offset(TOLERANCE));
  }

  @Test
  void aSinglePartGivesItsOwnAvailability() {
    Topology.Single single = Topologies.part("gateway", "99.9");

    assertThat(AvailabilityCalculator.evaluate(single))
        .isEqualTo(single.component().availability());
  }

  @Test
  void aChainWithAnAlwaysDownPartGivesZero() {
    Topology chain =
        Topologies.chain(
            Topologies.part("gateway", "99.9"),
            Topologies.part("broken", "0"),
            Topologies.part("payments", "99.9"));

    assertThat(AvailabilityCalculator.evaluate(chain)).isEqualTo(Availability.ALWAYS_DOWN);
  }

  @Test
  void aParallelGroupWithAnAlwaysUpCopyGivesOne() {
    Topology group =
        Topologies.parallel(Topologies.part("flaky", "1"), Topologies.part("solid", "100"));

    assertThat(AvailabilityCalculator.evaluate(group)).isEqualTo(Availability.ALWAYS_UP);
  }

  @Test
  void aVeryLongChainStaysBetweenZeroAndOne() {
    List<Topology> parts = new ArrayList<>();
    for (int partIndex = 0; partIndex < PARTS_IN_A_VERY_LONG_CHAIN; partIndex++) {
      parts.add(Topologies.part("part", "99.9"));
    }

    double fraction = AvailabilityCalculator.evaluate(new Topology.Chain(parts)).fraction();

    assertThat(fraction)
        .isBetween(0.0, 1.0)
        .isCloseTo(Math.pow(0.999, PARTS_IN_A_VERY_LONG_CHAIN), offset(LOOSE_TOLERANCE));
  }

  @Test
  void rejectsANullTopology() {
    assertThatNullPointerException()
        .isThrownBy(() -> AvailabilityCalculator.evaluate(null))
        .withMessageContaining("topology");
  }

  @Test
  void findsTheLowestPartOfAChain() {
    Topology.Chain chain =
        Topologies.chain(
            Topologies.part("load-balancer", "99.999"),
            Topologies.part("seatwave", "99.5"),
            Topologies.part("database", "99.95"));

    assertThat(AvailabilityCalculator.weakestPart(chain))
        .isEqualTo(Topologies.part("seatwave", "99.5"));
  }

  @Test
  void returnsTheFirstPartWhenAllPartsAreEqual() {
    Topology.Chain chain =
        Topologies.chain(
            Topologies.part("first", "99.9"),
            Topologies.part("second", "99.9"),
            Topologies.part("third", "99.9"));

    assertThat(AvailabilityCalculator.weakestPart(chain))
        .isEqualTo(Topologies.part("first", "99.9"));
  }

  @Test
  void comparesAGroupInsideAChainByItsCombinedAvailability() {
    Topology.Chain chain =
        Topologies.chain(
            Topologies.part("gateway", "99.9"),
            Topologies.parallel(
                Topologies.part("seatwave", "90"), Topologies.part("seatwave", "90")));

    // The pair gives 99 percent, which is still below the gateway.
    assertThat(AvailabilityCalculator.weakestPart(chain)).isEqualTo(chain.parts().get(1));
  }

  @Test
  void rejectsANullChain() {
    assertThatNullPointerException()
        .isThrownBy(() -> AvailabilityCalculator.weakestPart(null))
        .withMessageContaining("chain");
  }
}
