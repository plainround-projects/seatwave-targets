package io.plainround.seatwave.targets.labs;

import io.plainround.seatwave.targets.availability.Availability;
import io.plainround.seatwave.targets.availability.AvailabilityCalculator;
import io.plainround.seatwave.targets.availability.Component;
import io.plainround.seatwave.targets.availability.Topology;
import java.util.List;
import java.util.Locale;

/**
 * Lab 3: why a 99.9 percent payment provider caps the confirm path, and two changes that lift the
 * cap.
 *
 * <p>Each availability below is an assumption for the sale window with an owner on the requirements
 * sheet. None is a measurement.
 */
public final class PaymentCapLab {

  // Assumed for a managed load balancer that already runs several nodes.
  private static final Availability LOAD_BALANCER = new Availability(0.99999);
  // The instance-level commitment of the cloud provider for one machine, used as an estimate.
  private static final Availability SEATWAVE_MACHINE = new Availability(0.995);
  // Assumed for one database node; the design runs a primary and a standby.
  private static final Availability DATABASE_NODE = new Availability(0.9995);
  // What the payment provider commits to.
  private static final Availability PAYMENT_PROVIDER = new Availability(0.999);
  // Illustrative: something both providers need, such as one card network or one cloud region.
  private static final Availability SHARED_BY_BOTH_PROVIDERS = new Availability(0.9995);
  // What the product owner asks for in the sale window: 99.99 percent.
  private static final Availability REQUESTED_TARGET = new Availability(0.9999);
  // 20,000 seats, so 20,000 confirms in the sale window.
  private static final int CONFIRMS_IN_SALE_WINDOW = 20_000;
  private static final String ROW_FORMAT = "%-54s %-13s %-17s %s%n";

  private PaymentCapLab() {}

  /**
   * Prints one row per design: its availability, the confirms it is expected to fail and whether it
   * meets the requested target.
   *
   * @param arguments not used
   */
  public static void main(String[] arguments) {
    Topology loadBalancer = part("load-balancer", LOAD_BALANCER);
    Topology seatwavePair = pairOf("seatwave", SEATWAVE_MACHINE);
    Topology databasePair = pairOf("database", DATABASE_NODE);
    Topology providerOne = part("provider-1", PAYMENT_PROVIDER);
    Topology bothProviders =
        new Topology.Parallel(List.of(providerOne, part("provider-2", PAYMENT_PROVIDER)));
    Topology sharedByBoth = part("shared-by-both", SHARED_BY_BOTH_PROVIDERS);

    System.out.printf(
        Locale.ROOT,
        ROW_FORMAT,
        "Design",
        "Availability",
        "Failed of " + CONFIRMS_IN_SALE_WINDOW,
        "Meets " + REQUESTED_TARGET.asPercent());
    printRow(
        "1. Perfect own parts, one provider in the chain",
        chainOf(part("own-parts", Availability.ALWAYS_UP), providerOne));
    printRow(
        "2. Real own parts, one provider in the chain",
        chainOf(loadBalancer, seatwavePair, databasePair, providerOne));
    printRow(
        "3. Option A: two providers, independent",
        chainOf(loadBalancer, seatwavePair, databasePair, bothProviders));
    printRow(
        "4. Option A, but both providers share one dependency",
        chainOf(loadBalancer, seatwavePair, databasePair, bothProviders, sharedByBoth));
    printRow(
        "5. Option B: confirm first, take payment afterwards",
        chainOf(loadBalancer, seatwavePair, databasePair));
  }

  private static Topology part(String name, Availability availability) {
    return new Topology.Single(new Component(name, availability));
  }

  private static Topology pairOf(String name, Availability availabilityOfEachCopy) {
    Topology copy = part(name, availabilityOfEachCopy);
    return new Topology.Parallel(List.of(copy, copy));
  }

  private static Topology chainOf(Topology... parts) {
    return new Topology.Chain(List.of(parts));
  }

  private static void printRow(String design, Topology topology) {
    Availability availability = AvailabilityCalculator.evaluate(topology);
    // An availability is also the chance that one confirm finds every needed part up.
    double expectedFailures = availability.unavailability() * CONFIRMS_IN_SALE_WINDOW;
    System.out.printf(
        Locale.ROOT,
        ROW_FORMAT,
        design,
        availability.asPercent(),
        String.format(Locale.ROOT, "%.1f", expectedFailures),
        availability.meets(REQUESTED_TARGET) ? "yes" : "no");
  }
}
