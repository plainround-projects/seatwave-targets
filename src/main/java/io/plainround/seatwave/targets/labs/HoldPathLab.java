package io.plainround.seatwave.targets.labs;

import io.plainround.seatwave.targets.availability.Availability;
import io.plainround.seatwave.targets.availability.AvailabilityCalculator;
import io.plainround.seatwave.targets.availability.AvailabilityReport;
import io.plainround.seatwave.targets.availability.Component;
import io.plainround.seatwave.targets.availability.Downtime;
import io.plainround.seatwave.targets.availability.Topology;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Lab 2: the hold path as a chain, then the same chain with a redundant pair at its weakest part.
 *
 * <p>Each availability below is an assumption with an owner on the requirements sheet. None is a
 * measurement.
 */
public final class HoldPathLab {

  // The sheet measures at the load balancer, so the fan's own device and network count as up.
  private static final Availability BROWSER_BY_SCOPE = Availability.ALWAYS_UP;
  // Assumed for a managed load balancer that already runs several nodes.
  private static final Availability LOAD_BALANCER = new Availability(0.99999);
  // The instance-level commitment of the cloud provider for one machine, used as an estimate.
  private static final Availability SEATWAVE_MACHINE = new Availability(0.995);
  // Assumed for one database node without a standby.
  private static final Availability DATABASE_NODE = new Availability(0.9995);
  // The sheet's target for holds: 99.95 percent.
  private static final Availability HOLD_TARGET = new Availability(0.9995);

  private HoldPathLab() {}

  /**
   * Prints both versions of the hold path and whether each meets the target.
   *
   * @param arguments not used
   */
  public static void main(String[] arguments) {
    Topology.Chain holdPath =
        new Topology.Chain(
            List.of(
                part("browser", BROWSER_BY_SCOPE),
                part("load-balancer", LOAD_BALANCER),
                part("seatwave", SEATWAVE_MACHINE),
                part("database", DATABASE_NODE)));
    printWithVerdict("BEFORE: every part once", holdPath);
    printWithVerdict(
        "AFTER: a redundant pair at the weakest part", withWeakestPartPaired(holdPath));
  }

  private static Topology.Chain withWeakestPartPaired(Topology.Chain path) {
    Topology weakest = AvailabilityCalculator.weakestPart(path);
    // Compared by identity on purpose: only the weakest part is paired, not an equal twin.
    List<Topology> parts =
        path.parts().stream()
            .map(part -> part == weakest ? new Topology.Parallel(List.of(part, part)) : part)
            .toList();
    return new Topology.Chain(parts);
  }

  private static Topology part(String name, Availability availability) {
    return new Topology.Single(new Component(name, availability));
  }

  private static void printWithVerdict(String title, Topology.Chain path) {
    Availability availability = AvailabilityCalculator.evaluate(path);
    Duration budget = Downtime.allowedBy(HOLD_TARGET, Downtime.MONTH_OF_30_DAYS);
    Duration spent = Downtime.allowedBy(availability, Downtime.MONTH_OF_30_DAYS);
    System.out.println("== " + title + " ==");
    System.out.print(AvailabilityReport.render(path));
    System.out.printf(
        Locale.ROOT,
        "Target %s allows %.2f minutes per month; this path spends %.2f: %s%n%n",
        HOLD_TARGET.asPercent(),
        Downtime.inMinutes(budget),
        Downtime.inMinutes(spent),
        availability.meets(HOLD_TARGET) ? "MEETS the target" : "MISSES the target");
  }
}
