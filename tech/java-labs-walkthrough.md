# Java walkthrough: the `labs` package

## What it is

This file reads the two files of the Java package `io.plainround.seatwave.targets.labs`: `HoldPathLab.java` and `PaymentCapLab.java`. A **lab** here is a small program that uses the calculator from the `availability` package on the SeatWave case and prints numbers that you read like an experiment result. Each lab has a `main` method, so each can be started on its own from the terminal.

Read `java.md` first for the Java words, and `java-availability-walkthrough.md` for the classes the labs call (`Availability`, `Topology`, `AvailabilityCalculator`, `AvailabilityReport`, `Downtime`).

## Why this repository uses it

The course lesson is not "here is a formula". It is "here is what the formula does to a real design". Lab 2 shows that doubling the weakest part of a chain lifts the chain, and that the next-weakest part then limits it. Lab 3 shows why one provider that is 99.9 percent available caps the confirm path at 99.9 percent, and what two different changes do to that cap.

## Where it appears

| File | What it does |
|---|---|
| `src/main/java/io/plainround/seatwave/targets/labs/HoldPathLab.java` | Builds the hold path as a chain, prints its report and a verdict, then pairs the weakest part and prints again. |
| `src/main/java/io/plainround/seatwave/targets/labs/PaymentCapLab.java` | Prints a five-row table of designs for the confirm path and whether each reaches 99.99 percent. |

Gradle starts them with `./gradlew -q holdPathLab` and `./gradlew -q paymentCapLab`. The tasks are defined in `build.gradle.kts` (see `gradle.md`). Each task runs one `main` method.

## How it works underneath

Each lab is a process that starts at `main`, builds a few `Topology` objects in memory, asks the calculator for numbers, prints text and ends. There is no input, no file, no network, no random number. Run it twice and the output is the same, byte for byte.

Every availability in a lab is an **assumption** typed in as a constant. The Javadoc of both classes says so: "None is a measurement". The numbers show what follows from the assumptions. They do not claim that a real load balancer is 99.999 percent available.

A few words:

- **Nines.** 99.9 percent is "three nines". 99.99 percent is "four nines". Each extra nine cuts the allowed downtime by ten.
- **Downtime budget.** The minutes a system may be down in a window. At 99.95 percent over a 30-day month (43,200 minutes) that is 0.0005 times 43,200, which is 21.60 minutes.
- **Redundant pair.** Two copies in a parallel group. A request needs only one of them.

## Walkthrough of this repository's use

### `HoldPathLab.java`

The hold path is what happens when a fan presses "hold this seat": the request passes the browser, a load balancer, the SeatWave application and the database. All four must work, so they form a chain.

**Lines 1 to 19: imports and the class header**

```java
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
```

- The first seven `import` lines bring in classes of the `availability` package by their full names. Imports from other packages are required. The package of this file is `labs`, a different package from `availability`, so every class must be imported. That is how the dependency rule in `ARCHITECTURE.md` ("`labs` depends on `availability`") shows up in code.
- `java.time.Duration`, `java.util.List` and `java.util.Locale` come from the standard library.
- `public final class HoldPathLab` has no instance state, so it is `final` and has a private constructor (below).

**Lines 21 to 32: the assumptions**

```java
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
```

- `BROWSER_BY_SCOPE = Availability.ALWAYS_UP` is 1.0. The comment explains the choice: the sheet measures at the load balancer, so the fan's own device and network count as up. A part with availability 1 changes nothing in a product, so it stays in the chain only to show the scope.
- `new Availability(0.99999)` is 99.999 percent, "five nines", for a managed load balancer that already runs several nodes.
- `new Availability(0.995)` is 99.5 percent: the cloud provider's commitment for one machine, used as an estimate. This is the weakest part.
- `new Availability(0.9995)` is 99.95 percent for one database node with no standby.
- `HOLD_TARGET` is the sheet's target for holds, also 99.95 percent, so 0.9995.
- Each constant is `private static final`. Each is created once when the class loads. The constructor of `Availability` checks that each is between 0 and 1.
- `private HoldPathLab() {}` is the private constructor that stops anyone from creating an object.

**Lines 34 to 50: `main`**

```java
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
```

- `new Topology.Chain(List.of(part(...), part(...), ...))` builds a chain from four parts. `List.of(...)` makes an unmodifiable list of the four items. `Topology.Chain` copies it again.
- The type of `holdPath` is `Topology.Chain`, not just `Topology`, because `weakestPart` below needs a chain.
- `printWithVerdict("BEFORE: every part once", holdPath)` prints the first report.
- The second call passes `withWeakestPartPaired(holdPath)`, a new chain in which the weakest part is doubled. The call is wrapped over two lines by the formatter.
- The Javadoc `@param arguments not used` says the lab ignores what is typed after its name.

**Lines 52 to 60: pairing the weakest part**

```java
  private static Topology.Chain withWeakestPartPaired(Topology.Chain path) {
    Topology weakest = AvailabilityCalculator.weakestPart(path);
    // Compared by identity on purpose: only the weakest part is paired, not an equal twin.
    List<Topology> parts =
        path.parts().stream()
            .map(part -> part == weakest ? new Topology.Parallel(List.of(part, part)) : part)
            .toList();
    return new Topology.Chain(parts);
  }
```

- `AvailabilityCalculator.weakestPart(path)` finds the part with the lowest availability. For the "before" chain it is `seatwave` at 99.5 percent.
- `path.parts().stream().map(part -> part == weakest ? new Topology.Parallel(List.of(part, part)) : part).toList()` walks the parts. The lambda says: if this part is the weakest one, replace it with a parallel group of two copies of it. Otherwise keep it as it is. The result is a new list. `.toList()` collects it. The original chain is unchanged because records are immutable.
- `part == weakest` compares by **identity**: are these the very same object? The comment says "on purpose". If the chain held two separate but equal parts, `equals` would pair both of them. With `==`, only the one object `weakest` points to is paired. Here the four parts are four different objects anyway, so the answer is the same.
- `List.of(part, part)` puts the same object in the list twice. That is fine: a topology is immutable, so two references to one object behave as two copies.
- The last line wraps the new list in a new `Chain`.

**Lines 62 to 64: the `part` helper**

```java
  private static Topology part(String name, Availability availability) {
    return new Topology.Single(new Component(name, availability));
  }
```

- A small method that builds a one-part topology. It exists only to keep the lines in `main` short.

**Lines 66 to 79: `printWithVerdict`**

```java
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
```

- `AvailabilityCalculator.evaluate(path)` computes the end-to-end availability.
- `Downtime.allowedBy(HOLD_TARGET, Downtime.MONTH_OF_30_DAYS)` is the **budget**: the minutes the target allows per month. `Downtime.allowedBy(availability, ...)` is the minutes the design actually spends. The variable name `spent` is a short name for "downtime this design allows itself to cost".
- `System.out.println("== " + title + " ==")` prints the heading.
- `System.out.print(AvailabilityReport.render(path))` prints the full report from the availability package.
- `System.out.printf(Locale.ROOT, "...", ...)` prints text built from a template. `%s` is a text slot, `%.2f` is a decimal slot with two decimals, `%n` is a line break. `%n%n` ends the line and prints one empty line.
- The last argument is a conditional expression: `availability.meets(HOLD_TARGET) ? "MEETS the target" : "MISSES the target"`. `meets` is `>=`.
- `Downtime.inMinutes(budget)` converts a `Duration` to a number of minutes.

#### The real output of `./gradlew -q holdPathLab`

I ran it in this repository. The output was:

```
== BEFORE: every part once ==
Topology: chain(browser, load-balancer, seatwave, database)
Parts of the chain:
  browser                                      100 %
  load-balancer                                99.999 %
  seatwave                                     99.5 %
  database                                     99.95 %
Weakest part: seatwave (99.5 %)
End-to-end availability: 99.449255 %
Downtime budget: 237.92 minutes per 30-day month (43,200 minutes)
                 2,894.71 minutes per 365-day year (525,600 minutes), or 48.25 hours
Assumption: parts fail independently. Shared power, deploys or configuration
make a parallel group worse than the number shown.
Target 99.95 % allows 21.60 minutes per month; this path spends 237.92: MISSES the target

== AFTER: a redundant pair at the weakest part ==
Topology: chain(browser, load-balancer, parallel(seatwave, seatwave), database)
Parts of the chain:
  browser                                      100 %
  load-balancer                                99.999 %
  parallel(seatwave, seatwave)                 99.9975 %
  database                                     99.95 %
Weakest part: database (99.95 %)
End-to-end availability: 99.946502 %
Downtime budget: 23.11 minutes per 30-day month (43,200 minutes)
                 281.19 minutes per 365-day year (525,600 minutes), or 4.69 hours
Assumption: parts fail independently. Shared power, deploys or configuration
make a parallel group worse than the number shown.
Target 99.95 % allows 21.60 minutes per month; this path spends 23.11: MISSES the target

```

What each number means:

| Number | Where it comes from |
|---|---|
| `100 %`, `99.999 %`, `99.5 %`, `99.95 %` | The four assumptions, printed by `asPercent`. |
| `99.449255 %` | The chain rule: 1.0 times 0.99999 times 0.995 times 0.9995. First 0.995 times 0.9995 is 0.9945025. Times 0.99999 takes off 0.00000994..., leaving 0.99449255. |
| `237.92 minutes` | The down share, 1 minus 0.99449255 is 0.00550745, times 43,200 minutes. |
| `2,894.71 minutes`, `48.25 hours` | The same share times 525,600 minutes, and that divided by 60. |
| `21.60 minutes` | The target's budget: (1 minus 0.9995) times 43,200 is 0.0005 times 43,200. |
| `99.9975 %` | One pair of 99.5 percent parts. Each fails 0.005 of the time. Both fail 0.005 times 0.005, which is 0.000025. The pair works 0.999975. |
| `99.946502 %` | The chain with the pair: 0.99999 times 0.999975 times 0.9995. |
| `23.11 minutes` | (1 minus 0.99946502) times 43,200. |

What it teaches:

- The first chain spends 237.92 minutes a month against a budget of 21.60. It misses by a factor of about eleven, and `seatwave` is the weakest part, so the lab pairs it.
- After pairing, the chain is 99.946502 percent, and the **weakest part moves** to `database` at 99.95 percent. The spend falls from 237.92 to 23.11 minutes. It still misses the 99.95 percent target by 1.5 minutes.
- A chain can never be better than its weakest part. The database alone is exactly 99.95 percent, so a chain that contains it and any other part below 100 percent must fall below 99.95 percent. To meet the target, the database must also be paired (see `PaymentCapLab`, which does that), or its availability must rise.

### `PaymentCapLab.java`

The confirm path is what happens when a fan pays. A payment provider is a company outside SeatWave that moves the money. Assume it commits to 99.9 percent. The product owner asks for 99.99 percent on confirming. The lab shows why that cannot work with one provider in the chain, and what two designs do about it.

**Lines 1 to 17: imports and the class header**

```java
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
```

- The imports are the three `availability` classes the lab uses, plus `List` and `Locale`. `AvailabilityReport` and `Downtime` are not needed here.

**Lines 19 to 35: assumptions, the sale-window size and the table format**

```java
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
```

- `LOAD_BALANCER` (99.999 percent) and `SEATWAVE_MACHINE` (99.5 percent) are the same assumptions as in the other lab. `DATABASE_NODE` is 99.95 percent for one node. The comment says that the design runs a primary and a standby, which is what `pairOf` below models.
- `PAYMENT_PROVIDER = new Availability(0.999)` is what the provider commits to.
- `SHARED_BY_BOTH_PROVIDERS = new Availability(0.9995)` is **illustrative**: it stands for something both providers rely on, such as one card network or one cloud region.
- `REQUESTED_TARGET = new Availability(0.9999)` is 99.99 percent.
- `CONFIRMS_IN_SALE_WINDOW = 20_000` is an `int`. The underscore is only for reading. The comment: 20,000 seats, so 20,000 confirms in the sale window.
- `ROW_FORMAT = "%-54s %-13s %-17s %s%n"` is the template for every table line. `%-54s` is a text slot padded with spaces on the right to 54 characters. The next two slots are 13 and 17 wide. The last `%s` has no width because it is the last column. `%n` ends the line. Using one constant for the header and the rows keeps the columns lined up.

**Lines 37 to 74: `main`**

```java
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
```

- The first six lines build parts once and reuse them: a load balancer, a **pair** of SeatWave machines, a pair of database nodes, `providerOne`, `bothProviders` (a parallel group of two providers), and a single `sharedByBoth` part.
- `System.out.printf(Locale.ROOT, ROW_FORMAT, "Design", "Availability", "Failed of " + CONFIRMS_IN_SALE_WINDOW, "Meets " + REQUESTED_TARGET.asPercent())` prints the table header. The third header is the text `Failed of ` joined with the number 20000, so `Failed of 20000`. The fourth is `Meets ` joined with `99.99 %`.
- Then five `printRow` calls, one per design.
  1. `chainOf(part("own-parts", Availability.ALWAYS_UP), providerOne)`: our own parts are perfect (availability 1) and one provider stands in the chain. This isolates the effect of the provider.
  2. `chainOf(loadBalancer, seatwavePair, databasePair, providerOne)`: real own parts, with pairs, and one provider.
  3. The same with `bothProviders`. This is **Option A**: two providers, either of which can take the payment.
  4. Option A plus `sharedByBoth` in the chain: both providers depend on one shared thing.
  5. `chainOf(loadBalancer, seatwavePair, databasePair)`: **Option B**, confirm first and take the payment afterwards. The payment is no longer in the chain of the confirm.
- The same part objects are used in several chains. That is safe because they are immutable.

**Lines 76 to 87: three small builders**

```java
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
```

- `part` builds one part.
- `pairOf` builds a part once and puts the same object into a parallel group twice: `new Topology.Parallel(List.of(copy, copy))`. Two copies with the same availability.
- `chainOf(Topology... parts)` takes a **varargs** parameter. The three dots mean "any number of arguments". Inside the method `parts` is an array of `Topology`. `List.of(parts)` accepts an array and makes a list of its elements. `chainOf(a, b, c)` therefore builds a chain of three. This keeps the calls in `main` short.

**Lines 89 to 100: `printRow`**

```java
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
```

- It evaluates the topology to an `Availability`.
- `availability.unavailability() * CONFIRMS_IN_SALE_WINDOW` is the **expected number of failed confirms**: the chance that one confirm finds a needed part down, times the number of confirms. The comment explains: an availability is also the chance that one confirm finds every needed part up. This is an average. It is not a promise that exactly that many fail in any one sale.
- The `printf` prints four columns. `String.format(Locale.ROOT, "%.1f", expectedFailures)` rounds the failures to one decimal. A decimal slot takes a `double`. The lab passes primitive values and Java wraps them into objects automatically. The last column is `yes` or `no`: `meets` tests `>=` against 99.99 percent.

#### The real output of `./gradlew -q paymentCapLab`

I ran it in this repository. The output was:

```
Design                                                 Availability  Failed of 20000   Meets 99.99 %
1. Perfect own parts, one provider in the chain        99.9 %        20.0              no
2. Real own parts, one provider in the chain           99.896479 %   20.7              no
3. Option A: two providers, independent                99.996375 %   0.7               yes
4. Option A, but both providers share one dependency   99.946377 %   10.7              no
5. Option B: confirm first, take payment afterwards    99.996475 %   0.7               yes
```

What each row means:

| Row | The arithmetic | What it shows |
|---|---|---|
| 1 | 1.0 times 0.999 is 0.999. Failures: 0.001 times 20,000 is 20.0. | Even with perfect own parts, one 99.9 percent provider caps the path at 99.9 percent. The request of 99.99 percent cannot be met. |
| 2 | 0.99999 times 0.999975 times 0.99999975 times 0.999 is about 0.99896479. | Real parts make it a little worse than the cap. The pairs of SeatWave machines (0.999975) and database nodes (0.99999975) lift their parts, but the provider still dominates. |
| 3 | Two providers: 1 minus 0.001 times 0.001 is 0.999999. The chain is 0.99999 times 0.999975 times 0.99999975 times 0.999999, about 0.99996375. | A second provider removes the cap: 0.7 expected failures and the target is met, **if** the providers fail independently. |
| 4 | Row 3 times the shared part: 0.99996375 times 0.9995 is about 0.99946377. | If both providers need one shared dependency, that dependency is back in the chain and the gain mostly disappears: 10.7 failures, target missed. |
| 5 | 0.99999 times 0.999975 times 0.99999975, about 0.99996475. | Taking payment after the confirm removes the provider from the confirm path. The result is slightly better than row 3 and needs no second provider. |

What it teaches:

- A part in a chain caps the whole chain at its own availability. The two ways to lift a cap are to make the part redundant (Option A) or to take it out of the chain (Option B).
- Redundancy is only as good as the independence assumption. Row 4 shows the effect of a hidden shared dependency.
- Option B changes the product, not just the architecture: the booking is confirmed before money is taken. That is a business decision. The lab shows the cost in nines and leaves the decision to the owners.

## Try it yourself

Run these in `seatwave-targets/`.

1. The hold-path lab:

```
./gradlew -q holdPathLab
```

Look for the verdict line at the end of each block: `MISSES the target` twice. Check `237.92` by hand: 0.00550745 times 43,200.

2. The payment-cap lab:

```
./gradlew -q paymentCapLab
```

Look at the third column: rows 3 and 5 show `0.7` and answer `yes`, and rows 1, 2 and 4 answer `no`. Check row 1: 0.001 times 20,000 is 20.0.

3. Compute the same chain without the lab, with the calculator program:

```
./gradlew -q availability --args='chain(lb=99.999, app=99.5, db=99.95)'
```

I got `End-to-end availability: 99.449255 %`, `Weakest part: app (99.5 %)` and `237.92 minutes per 30-day month`, the same numbers as the "BEFORE" block. (The browser part has availability 1 and does not change a product.)

## Mistakes people make

- **Reading the lab numbers as measurements.** They follow from assumptions that someone must own on the sheet.
- **Comparing with `==` when you mean "equal"**, or the other way round. `HoldPathLab` uses `==` on purpose to pair one object only. Elsewhere in the project, records are compared with `equals`.
- **Pairing a part and stopping.** After one fix the weakest part moves. Run the calculator again, as the lab does, to find the next one.
- **Treating two providers as independent when they share something.** Row 4 shows why the report ends with the independence warning.
- **Putting the cap in the wrong place.** A downstream part that is in the chain caps the whole chain, however good the others are.

## Check yourself

1. In `HoldPathLab`, why does the "after" chain still miss 99.95 percent? Answer: pairing the seatwave part moved the weakest link to the database at 99.95 percent. A chain with that part and any other part below 100 percent is below 99.95 percent.
2. What does `%-54s` do in `ROW_FORMAT`? Answer: it prints a text slot padded with spaces on the right to a width of 54 characters, so the columns line up.
3. What does `Topology... parts` mean in `chainOf`? Answer: a varargs parameter. The caller may pass any number of topologies and the method sees them as an array.
4. Why does row 4 of the payment table fail even though two providers are in parallel? Answer: both providers need one shared dependency at 99.95 percent. That part sits in the chain next to the pair and costs about 0.05 percent.
5. Where does the 20.0 in row 1 come from? Answer: the unavailability 0.001 times 20,000 confirms.

## Further reading

- `java.util.Formatter` (the `%s`, `%.2f` and `%n` slots), https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/Formatter.html
- `java.util.List` (`List.of`), https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/List.html
- `java.io.PrintStream` (`printf`), https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/io/PrintStream.html
- `java.time.Duration`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Duration.html
