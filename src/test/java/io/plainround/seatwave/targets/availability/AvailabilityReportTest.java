package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;

class AvailabilityReportTest {

  private static final Topology THREE_PARTS_OF_99_9 =
      Topologies.chain(
          Topologies.part("gateway", "99.9"),
          Topologies.part("seatwave", "99.9"),
          Topologies.part("payments", "99.9"));

  @Test
  void describesAMixedTopologyByNamesOnly() {
    Topology mixed =
        Topologies.chain(
            Topologies.part("gateway", "99.9"),
            Topologies.parallel(
                Topologies.part("seatwave", "99.9"), Topologies.part("seatwave", "99.9")),
            Topologies.part("payments", "99.9"));

    assertThat(AvailabilityReport.describe(mixed))
        .isEqualTo("chain(gateway, parallel(seatwave, seatwave), payments)");
  }

  @Test
  void describesASinglePartByItsName() {
    assertThat(AvailabilityReport.describe(Topologies.part("gateway", "99.9")))
        .isEqualTo("gateway");
  }

  @Test
  void rendersTheEndToEndAvailabilityOfAChain() {
    assertThat(AvailabilityReport.render(THREE_PARTS_OF_99_9))
        .contains("End-to-end availability: 99.7003 %");
  }

  @Test
  void rendersTheWeakestPartOfAChain() {
    assertThat(AvailabilityReport.render(THREE_PARTS_OF_99_9))
        .contains("Weakest part: gateway (99.9 %)");
  }

  @Test
  void rendersTheMonthlyDowntimeBudget() {
    assertThat(AvailabilityReport.render(THREE_PARTS_OF_99_9))
        .contains("129.47 minutes per 30-day month (43,200 minutes)");
  }

  @Test
  void rendersTheYearlyDowntimeBudgetInMinutesAndHours() {
    assertThat(AvailabilityReport.render(THREE_PARTS_OF_99_9))
        .contains("1,575.22 minutes per 365-day year (525,600 minutes), or 26.25 hours");
  }

  @Test
  void rendersOneLinePerPartOfAChain() {
    assertThat(AvailabilityReport.render(THREE_PARTS_OF_99_9))
        .contains("Parts of the chain:")
        .containsPattern("(?m)^ {2}gateway +99\\.9 %$")
        .containsPattern("(?m)^ {2}seatwave +99\\.9 %$")
        .containsPattern("(?m)^ {2}payments +99\\.9 %$");
  }

  @Test
  void leavesOutThePartsListForATopologyThatIsNotAChain() {
    String report = AvailabilityReport.render(Topologies.part("gateway", "99.9"));

    assertThat(report).doesNotContain("Parts of the chain").doesNotContain("Weakest part");
  }

  @Test
  void rendersZeroDowntimeForAnAlwaysUpTopology() {
    assertThat(AvailabilityReport.render(Topologies.part("perfect", "100")))
        .contains("End-to-end availability: 100 %")
        .contains("0.00 minutes per 30-day month");
  }

  @Test
  void endsTheReportWithALineBreakAndStatesTheIndependenceAssumption() {
    String report = AvailabilityReport.render(THREE_PARTS_OF_99_9);

    assertThat(report).endsWith("\n").contains("parts fail independently");
  }

  @Test
  void rejectsANullTopology() {
    assertThatNullPointerException().isThrownBy(() -> AvailabilityReport.describe(null));
    assertThatNullPointerException().isThrownBy(() -> AvailabilityReport.render(null));
  }
}
