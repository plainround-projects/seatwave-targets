package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import org.instancio.Instancio;
import org.instancio.Select;
import org.junit.jupiter.api.Test;

/** Properties that must hold for every list of availabilities, checked on seeded random data. */
class AvailabilityPropertyTest {

  // A fixed seed makes every run generate the same lists, so a failure can always be replayed.
  private static final long SEED = 20_261_110L;
  private static final int PARTS_PER_LIST = 40;
  // Rounding in a long product or sum of doubles stays far below this distance.
  private static final double TOLERANCE = 1e-12;

  @Test
  void aChainIsNeverMoreAvailableThanItsWeakestPart() {
    List<Double> fractions = generatedFractions();
    Topology.Chain chain = new Topology.Chain(partsWith(fractions));

    double chainFraction = AvailabilityCalculator.evaluate(chain).fraction();

    assertThat(chainFraction).isLessThanOrEqualTo(Collections.min(fractions) + TOLERANCE);
  }

  @Test
  void aParallelGroupIsNeverLessAvailableThanItsBestCopy() {
    List<Double> fractions = generatedFractions();
    Topology.Parallel group = new Topology.Parallel(partsWith(fractions));

    double groupFraction = AvailabilityCalculator.evaluate(group).fraction();

    assertThat(groupFraction).isGreaterThanOrEqualTo(Collections.max(fractions) - TOLERANCE);
  }

  @Test
  void everyChainAndEveryParallelGroupStaysBetweenZeroAndOne() {
    List<Topology> parts = partsWith(generatedFractions());

    assertThat(AvailabilityCalculator.evaluate(new Topology.Chain(parts)).fraction())
        .isBetween(0.0, 1.0);
    assertThat(AvailabilityCalculator.evaluate(new Topology.Parallel(parts)).fraction())
        .isBetween(0.0, 1.0);
  }

  @Test
  void aGroupInsideAChainStaysBetweenZeroAndOne() {
    List<Topology> parts = partsWith(generatedFractions());
    Topology mixed =
        new Topology.Chain(List.of(new Topology.Parallel(parts), new Topology.Chain(parts)));

    assertThat(AvailabilityCalculator.evaluate(mixed).fraction()).isBetween(0.0, 1.0);
  }

  private static List<Double> generatedFractions() {
    return Instancio.ofList(Double.class)
        .size(PARTS_PER_LIST)
        .generate(Select.all(Double.class), generator -> generator.doubles().range(0.0, 1.0))
        .withSeed(SEED)
        .create();
  }

  private static List<Topology> partsWith(List<Double> fractions) {
    return fractions.stream()
        .<Topology>map(
            fraction -> new Topology.Single(new Component("part", new Availability(fraction))))
        .toList();
  }
}
