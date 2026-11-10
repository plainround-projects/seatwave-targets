package io.plainround.seatwave.targets.availability;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Writes the text report of a topology: its parts, its weakest part, its end-to-end availability
 * and the downtime that availability allows.
 *
 * <p>Stateless and safe to call from any thread.
 */
public final class AvailabilityReport {

  private static final double MINUTES_PER_HOUR = Duration.ofHours(1).toMinutes();

  private AvailabilityReport() {}

  /**
   * Names a topology in one line.
   *
   * @param topology the topology to name
   * @return for example "chain(gateway, parallel(app, app))"
   * @throws NullPointerException if the topology is null
   */
  public static String describe(Topology topology) {
    Objects.requireNonNull(topology, "topology");
    return switch (topology) {
      case Topology.Single single -> single.component().name();
      case Topology.Chain chain ->
          chain.parts().stream()
              .map(AvailabilityReport::describe)
              .collect(Collectors.joining(", ", "chain(", ")"));
      case Topology.Parallel parallel ->
          parallel.copies().stream()
              .map(AvailabilityReport::describe)
              .collect(Collectors.joining(", ", "parallel(", ")"));
    };
  }

  /**
   * Renders the full report.
   *
   * @param topology the topology to report on
   * @return several lines of text that end with a line break
   * @throws NullPointerException if the topology is null
   */
  public static String render(Topology topology) {
    Objects.requireNonNull(topology, "topology");
    StringBuilder report = new StringBuilder();
    report.append("Topology: ").append(describe(topology)).append('\n');
    if (topology instanceof Topology.Chain chain) {
      appendParts(report, chain);
    }
    Availability availability = AvailabilityCalculator.evaluate(topology);
    report.append("End-to-end availability: ").append(availability.asPercent()).append('\n');
    appendDowntime(report, availability);
    report.append("Assumption: parts fail independently. Shared power, deploys or configuration\n");
    report.append("make a parallel group worse than the number shown.\n");
    return report.toString();
  }

  private static void appendParts(StringBuilder report, Topology.Chain chain) {
    report.append("Parts of the chain:\n");
    for (Topology part : chain.parts()) {
      String percent = AvailabilityCalculator.evaluate(part).asPercent();
      report.append(String.format(Locale.ROOT, "  %-44s %s%n", describe(part), percent));
    }
    Topology weakest = AvailabilityCalculator.weakestPart(chain);
    report.append(
        String.format(
            Locale.ROOT,
            "Weakest part: %s (%s)%n",
            describe(weakest),
            AvailabilityCalculator.evaluate(weakest).asPercent()));
  }

  private static void appendDowntime(StringBuilder report, Availability availability) {
    double minutesPerMonth =
        Downtime.inMinutes(Downtime.allowedBy(availability, Downtime.MONTH_OF_30_DAYS));
    double minutesPerYear =
        Downtime.inMinutes(Downtime.allowedBy(availability, Downtime.YEAR_OF_365_DAYS));
    report.append(
        String.format(
            Locale.ROOT,
            "Downtime budget: %,.2f minutes per 30-day month (%,d minutes)%n",
            minutesPerMonth,
            Downtime.MONTH_OF_30_DAYS.toMinutes()));
    report.append(
        String.format(
            Locale.ROOT,
            "                 %,.2f minutes per 365-day year (%,d minutes), or %,.2f hours%n",
            minutesPerYear,
            Downtime.YEAR_OF_365_DAYS.toMinutes(),
            minutesPerYear / MINUTES_PER_HOUR));
  }
}
