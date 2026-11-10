# Java walkthrough: the `availability` package

## What it is

This file reads, one source file at a time, the Java package `io.plainround.seatwave.targets.availability`. A **package** is a folder of related Java files. This one holds the arithmetic that turns "this part works 99.9 percent of the time" into "the whole system works 99.7 percent of the time", and the program that reads a typed description of a system.

Read `java.md` first. It explains the Java words used here (class, record, sealed, switch, `Optional`, `BigDecimal` and the rest) from zero. This file does not repeat those explanations in full. It points at the feature, quotes the line, and explains what that line does in this program.

## Why this repository uses it

The lesson of Module 2 is that a target must be a number you can calculate. The availability package is where the calculation lives. Every rule is a small piece of code: a number must be between 0 and 1, a chain multiplies, a parallel group multiplies the failure shares, and the report prints the result as minutes of downtime.

## Where it appears

| File | What it does |
|---|---|
| `Availability.java` | A number from 0 to 1 that cannot be wrong, plus the two combining rules. |
| `Component.java` | A named part with an availability. |
| `Topology.java` | The three shapes a system can have: one part, a chain, a parallel group. |
| `TopologyError.java` | The six reasons that typed text can be refused. |
| `Downtime.java` | Turns an availability into minutes of allowed downtime. |
| `AvailabilityCalculator.java` | Evaluates a topology and finds its weakest part. |
| `TopologyParser.java` | Reads text such as `chain(a=99.9, b=99.5)` into a topology. |
| `AvailabilityReport.java` | Builds the text report. |
| `AvailabilityCli.java` | The program you start from the terminal. |

## How it works underneath

The flow is one straight line, left to right:

1. The terminal passes the text to `AvailabilityCli.main`.
2. `TopologyParser.parse` turns the text into a tree of `Topology` objects, or into a `TopologyError`.
3. `AvailabilityReport.render` asks `AvailabilityCalculator.evaluate` to compute the end-to-end `Availability`, asks `Downtime` for minutes, and builds one string.
4. `main` prints the string. All of this happens in memory inside one **process** (one running copy of the Java program). Nothing is written to disk and nothing goes over the network.

A **tree** is a shape where each node can hold child nodes. In `chain(a=99.9, parallel(b=99.5, b=99.5))` the chain is the root. It has two children: the single part `a` and a parallel group. The parallel group has two children, both single parts.

Two ideas carry most of the arithmetic.

- **Availability** is the share of a time window in which something works. 99.9 percent is the fraction 0.999. If a part works 0.999 of the time, it is down 0.001 of the time.
- **Independence** means that one part failing tells you nothing about another part failing. The arithmetic assumes it. Real parts often share power, a deploy or a configuration, so real parallel groups are a little worse than the number shown.

## Walkthrough of this repository's use

### `Availability.java`

The file holds one record, `Availability`, with one field named `fraction`. A **record** is a short way to write a class that only carries values. Java writes the constructor, the accessor `fraction()`, `equals`, `hashCode` and `toString` for you.

**Lines 1 to 16: package, imports and the record header**

```java
package io.plainround.seatwave.targets.availability;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The share of a window in which a part or a whole system works, as a fraction from 0 to 1.
 *
 * <p>A fraction of 0.999 means 99.9 percent. The constructor rejects every value outside 0 to 1,
 * and NaN, so an availability outside that range cannot exist anywhere in the program. Instances
 * are immutable and safe to share between threads.
 *
 * @param fraction the share of the window that is good, from 0 to 1 inclusive
 */
public record Availability(double fraction) {
```

- `package ...availability;` says which folder-like group this file belongs to. The file lives in `src/main/java/io/plainround/seatwave/targets/availability/`, and the package name must match that path.
- Each `import` brings a class from the standard library (called the **JDK**, the Java Development Kit) into reach by a short name. `BigDecimal` is a number type that stores decimal digits exactly. `RoundingMode` lists the ways to round. `Objects` is a helper class with null checks.
- Lines 7 to 15 are a **Javadoc comment**. It starts with `/**` and ends with `*/`. Tools turn it into documentation. `{@code ...}` marks code, `<p>` starts a paragraph, and `@param fraction` documents the record's field. For a record, `@param` documents the components.
- Line 16 declares `public record Availability(double fraction)`. `public` means any package can use it. `double` is a 64-bit number with a decimal point. Because it is a record, the class is also `final` (it cannot be extended) and its field is `private final` (set once, never changed). So an `Availability` can never change after it is made. Immutable values are safe to share between threads.

**Lines 18 to 28: constants**

```java
  /** A part that never works. */
  public static final Availability ALWAYS_DOWN = new Availability(0.0);

  /** A part that never fails. */
  public static final Availability ALWAYS_UP = new Availability(1.0);

  // A percent is a fraction with the decimal point moved two places: 99.9 percent is 0.999.
  private static final int PERCENT_SHIFT = 2;
  private static final BigDecimal HIGHEST_PERCENT = BigDecimal.ONE.movePointRight(PERCENT_SHIFT);
  // Six decimals show a pair of 99.95 percent parts as 99.999975 instead of rounding it to 100.
  private static final int PERCENT_DECIMALS = 6;
```

- `static` means the member belongs to the class, not to one object. `final` on a variable means it is assigned once. Together, `public static final` is a constant. `ALWAYS_DOWN` is the availability 0.0 and `ALWAYS_UP` is 1.0. Other code writes `Availability.ALWAYS_UP`.
- `new Availability(0.0)` calls the constructor you meet at line 35. The two constants are made when the class is first loaded.
- `PERCENT_SHIFT = 2` names the number 2. A percent is the fraction times 100, which moves the decimal point two places. A **magic number** is a bare number with no name. Naming it explains it.
- `BigDecimal.ONE.movePointRight(PERCENT_SHIFT)` takes the number 1 and moves its decimal point two places right. The result is exactly 100. `HIGHEST_PERCENT` is therefore 100, as a `BigDecimal`.
- `PERCENT_DECIMALS = 6` is how many decimals the printed percent has. The comment gives the reason: two 99.95 percent parts in parallel give 99.999975 percent. With fewer decimals, that would round to 100 and hide the improvement.
- Lines that start with `//` are one-line comments. The compiler ignores them.

**Lines 30 to 41: the compact constructor and the NaN trap**

```java
  /**
   * Creates an availability.
   *
   * @throws IllegalArgumentException if the fraction is below 0, above 1 or not a number
   */
  public Availability {
    // Written as a negated range test so that NaN, which fails every comparison, is rejected too.
    if (!(fraction >= 0.0 && fraction <= 1.0)) {
      throw new IllegalArgumentException(
          "an availability must be between 0 and 1, but was " + fraction);
    }
  }
```

- A **constructor** is the code that runs when `new Availability(...)` builds an object. A **compact constructor** is the record form: it has no parameter list, it sees the record's fields as parameters, and Java assigns the fields after it ends. This one only checks.
- `throw new IllegalArgumentException(...)` stops the constructor. An **exception** is an object that says "this went wrong". When it is thrown, normal execution stops and Java looks for a `catch` block up the call chain. If none exists, the program ends with a stack trace. `IllegalArgumentException` is the standard type for "you gave me a value I cannot accept".
- The condition is `!(fraction >= 0.0 && fraction <= 1.0)`. Read it as "it is NOT true that fraction is at least 0 and at most 1". `&&` means "and", `!` means "not".
- Why not write `fraction < 0.0 || fraction > 1.0`? Because of NaN. **NaN** ("not a number") is a special `double` value, for example the result of `0.0 / 0.0`. By the floating-point standard, every comparison with NaN is false: `NaN >= 0.0` is false and `NaN <= 1.0` is false. I ran this: `(NaN >= 0.0)` printed `false`, `(NaN <= 1.0)` printed `false`. So the "below 0 or above 1" test is false for NaN too, and NaN would slip through as if it were a valid number. The negated range test says "reject anything that is not clearly inside the range", and NaN is not clearly inside, so it is rejected. I ran `new Availability(Double.NaN)` in `jshell` and got `an availability must be between 0 and 1, but was NaN`.
- The `+ fraction` joins text and a number. Java turns the number into text for you.
- The Javadoc line `@throws IllegalArgumentException` documents the exception.

**Lines 43 to 52: `isPercent`**

```java
  /**
   * Tells whether a percent can be turned into an availability.
   *
   * @param percent the percent to check, for example 99.9
   * @return true when the percent is from 0 to 100 inclusive
   */
  public static boolean isPercent(BigDecimal percent) {
    Objects.requireNonNull(percent, "percent");
    return percent.signum() >= 0 && percent.compareTo(HIGHEST_PERCENT) <= 0;
  }
```

- A **method** is a named block of code. This one is `static`, so you call it as `Availability.isPercent(x)` with no object. It takes a `BigDecimal` and returns a `boolean` (true or false).
- `Objects.requireNonNull(percent, "percent")` throws a `NullPointerException` with the message `percent` if the argument is `null`. **Null** means "no object here". Calling a method on null would crash later in a confusing place. This check crashes at once, with the name of the bad argument. A null here is a programmer error, not bad user text.
- `percent.signum()` returns -1, 0 or 1 for negative, zero or positive. `>= 0` means "not negative".
- `percent.compareTo(HIGHEST_PERCENT) <= 0` compares with 100. `compareTo` returns a negative number, 0 or a positive number. Using it instead of `equals` matters: `BigDecimal` `equals` also compares the scale, so 100 and 100.0 are not `equals`, but `compareTo` says they are equal. I ran `new BigDecimal("101").compareTo(100)`, which printed `1`.

**Lines 54 to 67: `ofPercent`**

```java
  /**
   * Converts a percent into an availability.
   *
   * @param percent a percent from 0 to 100, for example 99.9
   * @return the same availability as a fraction, for example 0.999
   * @throws IllegalArgumentException if the percent is outside 0 to 100
   */
  public static Availability ofPercent(BigDecimal percent) {
    if (!isPercent(percent)) {
      throw new IllegalArgumentException("a percent must be between 0 and 100, but was " + percent);
    }
    // The point moves in decimal arithmetic, so 99.9 becomes exactly 0.999 before it is a double.
    return new Availability(percent.movePointLeft(PERCENT_SHIFT).doubleValue());
  }
```

- It first calls `isPercent`. If the percent is outside 0 to 100, it throws `IllegalArgumentException`. The parser always checks first, so users never see this exception. It protects against a programmer calling the method wrongly.
- `percent.movePointLeft(PERCENT_SHIFT)` moves the decimal point two places left in decimal arithmetic: 99.9 becomes exactly 0.999. Only then does `.doubleValue()` convert it to a `double`. The comment says why: if you divided a `double` 99.9 by 100, you could get 0.9990000000000001 because `double` stores numbers in binary and cannot hold 99.9 exactly. I ran `new BigDecimal("99.9").movePointLeft(2).doubleValue()` and got `0.999`.

**Lines 69 to 90: the two combining rules**

```java
  /**
   * Combines two parts in a chain, where a request needs both.
   *
   * @param other the next part of the chain
   * @return the share of the window in which both parts work, assuming they fail independently
   */
  public Availability bothUp(Availability other) {
    Objects.requireNonNull(other, "other");
    return new Availability(fraction * other.fraction);
  }

  /**
   * Combines two copies in parallel, where a request needs only one of them.
   *
   * @param other the other copy
   * @return the share of the window in which at least one copy works, assuming they fail
   *     independently
   */
  public Availability eitherUp(Availability other) {
    Objects.requireNonNull(other, "other");
    return new Availability(1.0 - unavailability() * other.unavailability());
  }
```

- `bothUp` is the **chain** rule. A request needs part A and part B. Under independence, the chance that both work is the product: `fraction * other.fraction`. Example: 0.999 times 0.999 is 0.998001. Two parts that each work 99.9 percent give 99.8001 percent. I ran this and got `99.8001 %`. Multiplying always makes the number smaller or equal, because both factors are at most 1. That is why long chains are fragile.
- `eitherUp` is the **parallel** rule. A request needs only one of two copies. The group fails only when both fail. Under independence the chance that both fail is the product of the two failure shares: `unavailability() * other.unavailability()`. The chance that at least one works is 1 minus that. Example: two copies of 0.995. Each fails 0.005 of the time. Both fail together 0.005 times 0.005, which is 0.000025. So the group works 1 minus 0.000025, which is 0.999975, or 99.9975 percent. Multiplying failure shares, which are at most 1, makes the number smaller, so the group fails less often than one copy.
- Both methods call `new Availability(...)`, so the constructor check still guards the result. A floating-point product can never leave 0 to 1 here, but the check costs nothing and makes "no bad availability exists" true by construction.
- `other.fraction` reads the private field of another object of the same class. A class may read the private fields of any object of its own type.
- The odd-looking Javadoc line `* independently` is only the second line of the `@return` text. Javadoc text continues on indented lines.

**Lines 92 to 121: three small questions**

```java
  /**
   * Returns the share of the window in which the part does not work.
   *
   * @return 1 minus the fraction
   */
  public double unavailability() {
    return 1.0 - fraction;
  }

  /**
   * Tells whether this availability is lower than another.
   *
   * @param other the availability to compare with
   * @return true when this fraction is strictly smaller
   */
  public boolean isLowerThan(Availability other) {
    Objects.requireNonNull(other, "other");
    return fraction < other.fraction;
  }

  /**
   * Tells whether this availability reaches a target.
   *
   * @param target the availability that must be reached
   * @return true when this fraction is at least the target's
   */
  public boolean meets(Availability target) {
    Objects.requireNonNull(target, "target");
    return fraction >= target.fraction;
  }
```

- `unavailability()` returns 1 minus the fraction: the down share. 0.999 gives 0.001 (in floating point, about 0.0010000000000000009, which the report never shows because it rounds).
- `isLowerThan` is a strict comparison (`<`): equal is not lower. The calculator uses it so that when two parts tie, the first one stays "weakest".
- `meets` is `>=`: a system meets a target when it is at least as available. Equal counts as meeting.

**Lines 123 to 136: printing as a percent**

```java
  /**
   * Formats the availability as a percent for people to read.
   *
   * @return for example "99.7003 %", with at most six decimals and no trailing zeros
   */
  public String asPercent() {
    // BigDecimal.valueOf uses the shortest decimal text of the double, so 0.999 prints as 99.9.
    BigDecimal percent =
        BigDecimal.valueOf(fraction)
            .movePointRight(PERCENT_SHIFT)
            .setScale(PERCENT_DECIMALS, RoundingMode.HALF_UP)
            .stripTrailingZeros();
    return percent.toPlainString() + " %";
  }
}
```

- `BigDecimal.valueOf(fraction)` converts the `double` to a `BigDecimal` using the shortest decimal text that identifies the double. So 0.999 becomes the decimal 0.999, not 0.99899999999999999911182158029987. That is why the comment says 0.999 prints as 99.9.
- `.movePointRight(2)` multiplies by 100: 99.9.
- `.setScale(6, RoundingMode.HALF_UP)` keeps exactly six decimals. `HALF_UP` is the rounding you learned at school: 0.5 and above rounds up.
- `.stripTrailingZeros()` removes zeros at the end: 99.900000 becomes 99.9.
- `percent.toPlainString() + " %"` writes the number without scientific notation and adds the unit. `toPlainString` matters because `stripTrailingZeros` on 100.000000 gives `1E+2`, which `toString` would print as text `1E+2`. I ran this: `toString()` printed `1E+2`, and `toPlainString()` printed `100`.
- The long chain of dots is a **fluent** style: each call returns a new `BigDecimal` (the type is immutable), and the next call works on it.

### `Component.java`

```java
package io.plainround.seatwave.targets.availability;

import java.util.Objects;

/**
 * One part of a system with a known availability, such as a load balancer or a payment provider.
 *
 * <p>Immutable and safe to share between threads.
 *
 * @param name the name the report prints for the part
 * @param availability the share of the window in which the part works
 */
public record Component(String name, Availability availability) {
```

- A `Component` is a name plus an `Availability`. `Component` here is this project's class, not a Java library class.
- Notice that the record field `availability` has the type `Availability`, which is the record from the previous file. Records can hold other records.

```java
  /**
   * Creates a component.
   *
   * @throws NullPointerException if the name or the availability is null
   * @throws IllegalArgumentException if the name is blank
   */
  public Component {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(availability, "availability");
    if (name.isBlank()) {
      throw new IllegalArgumentException("a component needs a name");
    }
  }
}
```

- The compact constructor first checks both fields for null with `Objects.requireNonNull`. A null is a programmer error, so a `NullPointerException` is right.
- `name.isBlank()` is true for an empty string or one made only of spaces. A blank name is bad data that a programmer passed in. The parser never builds one, because its words are at least one character. The constructor throws `IllegalArgumentException` for it.
- The order matters: the null check for `name` must come before `name.isBlank()`. Otherwise a null name would crash with a less helpful message.

### `Topology.java`

This file defines a **sealed interface**. An **interface** is a list of what a type can do. A **sealed** interface lists every type allowed to implement it, so the compiler knows the full list. Here the list is `Single`, `Chain` and `Parallel`.

```java
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
```

- `sealed interface Topology` with no `permits` clause: when the permitted types are declared inside the same file, the compiler works out the list for itself. I confirmed with `javap -v` that the compiled class has a `PermittedSubclasses` entry naming `Single`, `Chain` and `Parallel`.
- `import java.util.List;` brings in the list type. A `List` holds items in order.

**Lines 15 to 30: `Single`**

```java
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
```

- `record Single(Component component) implements Topology` is a record nested inside the interface. A nested record in an interface is implicitly `public` and `static`. It `implements Topology`, which means it counts as one kind of `Topology`.
- Its compact constructor rejects null. Without it, `new Single(null)` would build a broken object.

**Lines 32 to 48: `Chain`**

```java
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
```

- `List<Topology> parts` is a list whose items are `Topology` values. The angle brackets are **generics**: they say what the list holds. Because `Chain` holds `Topology`, and `Chain` is itself a `Topology`, a chain can hold chains. That makes the type recursive and the tree possible.
- The compact constructor does not only check. It **replaces** the parameter: `parts = copyOfNonEmpty(parts, "a chain");`. In a compact constructor, assigning to the parameter changes what Java stores in the field at the end. So the stored list is the copy, not the caller's list.

**Lines 50 to 68: `Parallel`**

```java
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
```

- It has the same shape with the field named `copies`. The name explains the idea: each element is a copy that can do the job alone.

**Lines 70 to 77: the shared helper**

```java
  private static List<Topology> copyOfNonEmpty(List<Topology> children, String kind) {
    Objects.requireNonNull(children, "children");
    if (children.isEmpty()) {
      throw new IllegalArgumentException(kind + " needs at least one part");
    }
    // An unmodifiable copy: nobody can add an unchecked part after construction.
    return List.copyOf(children);
  }
```

- An interface may hold a `private static` method (allowed since Java 9). Both compact constructors call it, so the two rules are written once.
- `children.isEmpty()` rejects an empty list: a chain with no parts has no meaning. The message is built as `kind + " needs at least one part"`, for example `a chain needs at least one part`.
- `List.copyOf(children)` returns an **unmodifiable** copy. It throws `NullPointerException` if the list contains a null element (I ran `List.copyOf(Arrays.asList(1, null))` and got a `NullPointerException`). It also protects the topology: if a caller keeps the original list and adds to it later, the topology does not change. The record is therefore truly immutable.

### `TopologyError.java`

```java
package io.plainround.seatwave.targets.availability;

/**
 * Why a topology expression typed by a person could not be read.
 *
 * <p>These are expected failures of bad input, so the parser returns them as values instead of
 * throwing. The interface is sealed, so a switch over it must handle every reason.
 */
public sealed interface TopologyError {

  /** The expression is empty or holds only spaces. */
  record EmptyExpression() implements TopologyError {}
```

- The interface is sealed again: there are exactly six reasons that text can fail. Each reason is a record.
- `record EmptyExpression() implements TopologyError {}` has no fields. The empty braces are an empty body. Such a record is a plain "this happened" marker.

```java
  /**
   * The expression is longer than the parser accepts.
   *
   * @param length the number of characters given
   * @param limit the largest number of characters accepted
   */
  record ExpressionTooLong(int length, int limit) implements TopologyError {}

  /**
   * Groups are nested more deeply than the parser accepts.
   *
   * @param limit the deepest nesting accepted
   */
  record NestingTooDeep(int limit) implements TopologyError {}

  /**
   * The text at a position is not what the grammar allows there.
   *
   * @param position the zero-based index of the first unexpected character
   * @param expected what the grammar allows at that position, in words
   */
  record Unexpected(int position, String expected) implements TopologyError {}

  /**
   * A group has a keyword other than chain or parallel.
   *
   * @param keyword the word found before the opening parenthesis
   */
  record UnknownGroup(String keyword) implements TopologyError {}

  /**
   * A percent is outside 0 to 100.
   *
   * @param percentText the percent as it was typed
   */
  record PercentOutOfRange(String percentText) implements TopologyError {}
```

- `ExpressionTooLong(int length, int limit)` carries the two numbers so the message can say "the expression has 2001 characters; the limit is 2000". `int` is a whole number.
- `NestingTooDeep(int limit)` carries the limit.
- `Unexpected(int position, String expected)` carries where the parser stopped and what it wanted. `position` is zero-based: the first character is position 0.
- `UnknownGroup(String keyword)` carries the word that was not `chain` or `parallel`.
- `PercentOutOfRange(String percentText)` carries the percent as the person typed it, so the message can quote it.

These are the **expected failures** of this program: a person can type anything. They are values that `Result` carries (see `result4j.md`), not exceptions.

### `Downtime.java`

```java
package io.plainround.seatwave.targets.availability;

import java.time.Duration;
import java.util.Objects;

/**
 * Converts an availability into the downtime it allows in a window.
 *
 * <p>Stateless and safe to call from any thread.
 */
public final class Downtime {

  // The assumptions behind every "per month" and "per year" figure in the course.
  private static final int DAYS_IN_MONTH = 30;
  private static final int DAYS_IN_YEAR = 365;

  /** A month of 30 days, which is 43,200 minutes. */
  public static final Duration MONTH_OF_30_DAYS = Duration.ofDays(DAYS_IN_MONTH);

  /** A year of 365 days, which is 525,600 minutes. */
  public static final Duration YEAR_OF_365_DAYS = Duration.ofDays(DAYS_IN_YEAR);

  private static final double MILLIS_PER_MINUTE = Duration.ofMinutes(1).toMillis();

  private Downtime() {}
```

- `public final class Downtime` is a normal class, not a record, because it holds no data. `final` means no class can extend it.
- `import java.time.Duration;` brings in `Duration`, the standard type for a length of time. A `Duration` is stored as seconds and nanoseconds. It is immutable.
- `Duration.ofDays(DAYS_IN_MONTH)` makes a duration of 30 days. I checked: it is 43,200 minutes (`toMinutes()` printed `43200`) and 2,592,000,000 milliseconds. The comment documents the teaching assumption: a month is 30 days and a year is 365 days. A real month has 28 to 31 days.
- `MILLIS_PER_MINUTE = Duration.ofMinutes(1).toMillis()` is 60,000. It is stored as a `double` so later division keeps the decimals. Java converts the `long` (a 64-bit whole number) to `double` automatically here.
- `private Downtime() {}` is a **private constructor with an empty body**. Nobody outside the class can write `new Downtime()`. The class only has static methods, so an object would be pointless. The empty `{}` is the whole body.

```java
  /**
   * Computes the downtime budget: the part of a window in which the system may be down.
   *
   * @param availability the availability of the system, or its target
   * @param window the length of the measurement window
   * @return the unavailable share of the window, rounded to the millisecond
   * @throws NullPointerException if an argument is null
   * @throws IllegalArgumentException if the window is zero or negative
   */
  public static Duration allowedBy(Availability availability, Duration window) {
    Objects.requireNonNull(availability, "availability");
    Objects.requireNonNull(window, "window");
    if (window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("a window must be longer than zero, but was " + window);
    }
    return Duration.ofMillis(Math.round(window.toMillis() * availability.unavailability()));
  }
```

- `window.isZero() || window.isNegative()` rejects a window with no length or a backwards one. `||` means "or".
- `window.toMillis()` is the window in milliseconds (a `long`). Multiplying it by `availability.unavailability()` (a `double`) gives the milliseconds of allowed downtime as a `double`.
- `Math.round(...)` rounds to the nearest `long`. `Duration.ofMillis(...)` wraps it back into a `Duration`. The Javadoc says "rounded to the millisecond".
- Worked example: 99.7002999 percent availability over 30 days. The down share is 0.002997001. Times 2,592,000,000 ms is about 7,768,227 ms, which is 129.47 minutes. The README shows `129.47 minutes per 30-day month` for the three 99.9 percent chain, and my run of `./gradlew -q availability` printed the same.

```java
  /**
   * Expresses a duration in minutes with a fractional part, for reports.
   *
   * @param duration the duration to convert
   * @return the duration in minutes, for example 43.2
   * @throws NullPointerException if the duration is null
   */
  public static double inMinutes(Duration duration) {
    Objects.requireNonNull(duration, "duration");
    return duration.toMillis() / MILLIS_PER_MINUTE;
  }
}
```

- `duration.toMillis() / MILLIS_PER_MINUTE` divides a `long` by a `double`, so the answer is a `double` with decimals: 7,768,227 ms divided by 60,000 is about 129.47.

### `AvailabilityCalculator.java`

```java
package io.plainround.seatwave.targets.availability;

import java.util.Objects;

/**
 * Computes the availability of a topology from the availability of its parts.
 *
 * <p>The arithmetic assumes that parts fail independently. Stateless and safe to call from any
 * thread.
 */
public final class AvailabilityCalculator {

  private AvailabilityCalculator() {}

```

- Another class with a private constructor and only static methods: a bag of functions with no state. "Stateless" means it keeps nothing between calls, so many threads can call it at once.

**Lines 15 to 46: `evaluate`**

```java
  /**
   * Evaluates a topology from the inside out.
   *
   * <p>A chain multiplies the availability of its parts. A parallel group multiplies the
   * unavailability of its copies and subtracts the product from 1.
   *
   * @param topology the topology to evaluate
   * @return the end-to-end availability, which the {@link Availability} type keeps between 0 and 1
   * @throws NullPointerException if the topology is null
   */
  public static Availability evaluate(Topology topology) {
    Objects.requireNonNull(topology, "topology");
    return switch (topology) {
      case Topology.Single single -> single.component().availability();
      case Topology.Chain chain -> {
        // A product is a running multiplication: start at "always up" and fold in each part.
        Availability everyPartUp = Availability.ALWAYS_UP;
        for (Topology part : chain.parts()) {
          everyPartUp = everyPartUp.bothUp(evaluate(part));
        }
        yield everyPartUp;
      }
      case Topology.Parallel parallel -> {
        // The group is down only when every copy is down, so start at "always down".
        Availability anyCopyUp = Availability.ALWAYS_DOWN;
        for (Topology copy : parallel.copies()) {
          anyCopyUp = anyCopyUp.eitherUp(evaluate(copy));
        }
        yield anyCopyUp;
      }
    };
  }
```

- `{@link Availability}` in the Javadoc makes a clickable link to that type.
- The method body is one `return switch (topology) {...};`. A **switch expression** is a `switch` that produces a value. Each `case ... ->` is one branch. `case Topology.Single single ->` is **type-pattern matching**: if `topology` is a `Single`, Java puts it in a new variable called `single` with the type `Single`, and runs the branch. You do not cast by hand.
- The three branches are exactly the three permitted types of the sealed interface, and there is no `default` branch. The compiler checks **exhaustiveness**: if a fourth shape were added, this switch would stop compiling until someone handled it. That is the safety the sealed interface buys.
- `case Topology.Single single -> single.component().availability();` A single part is its own availability.
- `case Topology.Chain chain -> { ... yield everyPartUp; }` A branch that needs several statements uses braces and ends with `yield`, which hands the value to the switch expression.
- Inside the chain branch: start with `ALWAYS_UP` (1.0), the neutral element of multiplication. Then `for (Topology part : chain.parts())` visits each part. This `for` is the **enhanced for loop**: take each element of the list in turn. `everyPartUp = everyPartUp.bothUp(evaluate(part));` calls `evaluate` again on the part. A **recursive** method calls itself. Here a part may be another chain or group, and recursion handles any depth. The base case is `Single`, which does not recurse.
- Chain trace for `chain(a=99.9, b=99.5)`: start 1.0. Fold in a: 1.0 times 0.999 is 0.999. Fold in b: 0.999 times 0.995 is 0.994005. The end-to-end availability is 99.4005 percent. It is lower than the weakest part, 99.5, because every part adds more chances to fail.
- The parallel branch starts with `ALWAYS_DOWN` (0.0). Think of the group as down until a copy proves otherwise. Folding in the first copy gives `1.0 - (1.0 - 0.0) * (1.0 - a)`, which is `a`. Folding in the second copy gives `1 - (1 - a) * (1 - b)`, the failure shares multiplied. For three copies it keeps multiplying: the group is down only if all are down.
- `yield anyCopyUp;` returns the value.

**Lines 48 to 64: `weakestPart`**

```java
  /**
   * Finds the part of a chain to improve first.
   *
   * @param chain the chain to inspect
   * @return the part with the lowest availability; the first one when several are equal
   * @throws NullPointerException if the chain is null
   */
  public static Topology weakestPart(Topology.Chain chain) {
    Objects.requireNonNull(chain, "chain");
    Topology weakest = chain.parts().getFirst();
    for (Topology part : chain.parts()) {
      if (evaluate(part).isLowerThan(evaluate(weakest))) {
        weakest = part;
      }
    }
    return weakest;
  }
}
```

- The parameter type is `Topology.Chain`, not `Topology`. That means the compiler already guarantees a chain, so no check is needed.
- `chain.parts().getFirst()` returns the first element. `getFirst()` on a `List` exists since Java 21. The list cannot be empty, because `Chain` refuses empty lists.
- The loop keeps the lowest availability seen so far. `isLowerThan` is strict, so on a tie the earlier part stays, as the Javadoc promises (`the first one when several are equal`).
- `weakest` is a variable that is reassigned in the loop, so it is not `final`. A **variable** is a named box for a value.
- The method returns a `Topology`, not an `Availability`. It returns the part itself, so the report can print its name.
- Cost note: `evaluate(weakest)` is recomputed in each loop step. For this size of input that is harmless.

### `TopologyParser.java`

The parser reads text such as `chain(gateway=99.9, parallel(app=99.5, app=99.5))`. It is a **recursive-descent parser**: one method per grammar rule, each method reads its piece of the text and calls the others for nested pieces. It keeps a **position**, the index of the next unread character, and moves that position forward as it consumes characters.

**Lines 1 to 20: imports and the class header**

```java
package io.plainround.seatwave.targets.availability;

import com.github.sviperll.result4j.Result;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Reads a topology from text such as {@code chain(gateway=99.9, parallel(app=99.5, app=99.5))}.
 *
 * <p>The grammar has three rules. A component is {@code name=percent}. A chain is {@code
 * chain(...)} and a parallel group is {@code parallel(...)}, each holding one or more components or
 * groups separated by commas. Spaces are ignored.
 *
 * <p>One instance reads one expression and is confined to the call that created it, so the class is
 * safe to use from any thread through {@link #parse(String)}.
 */
public final class TopologyParser {
```

- `import com.github.sviperll.result4j.Result;` brings in a library type (see `result4j.md`). The package name is written backwards from a web address; that convention keeps names unique.
- `ArrayList` is a list that grows. `Pattern` is the regular-expression class. A **regular expression** is a compact text pattern for matching text, for example "digits, then maybe a dot and more digits".
- `{@link #parse(String)}` links to the method `parse` of this class.
- The class is `final` and the Javadoc says one instance reads one expression. An instance holds a mutable `position`, so an instance must not be shared between threads. The class stays safe because the only public way in is the static method `parse`, which makes its own private instance for each call. Each call has its own `position`.

**Lines 22 to 37: constants, fields and the private constructor**

```java
  // Safety bounds of the parser, not tunable limits: a typo must not become a stack overflow.
  static final int MAX_EXPRESSION_LENGTH = 2_000;
  static final int MAX_NESTING_DEPTH = 16;

  private static final String CHAIN_KEYWORD = "chain";
  private static final String PARALLEL_KEYWORD = "parallel";
  private static final Pattern DECIMAL_NUMBER = Pattern.compile("[0-9]+(\\.[0-9]+)?");
  // The NUL character cannot appear in a name or a number, so it safely means "nothing left".
  private static final char END_OF_INPUT = '\0';

  private final String expression;
  private int position;

  private TopologyParser(String expression) {
    this.expression = expression;
  }
```

- `static final int MAX_EXPRESSION_LENGTH = 2_000;` The underscore in a number is only for reading: `2_000` is 2000. The constant has no `private`, so it is **package-private**: visible to the other classes of the same package, which includes the tests.
- The comment gives the reason for the limits: this parser calls itself for nested groups, and each nested group uses stack memory. The **stack** is the memory that keeps track of method calls in progress. Without a depth limit, a long run of `chain(chain(chain(...` could fill the stack and crash with `StackOverflowError`. With limits, a typo becomes an error value.
- `Pattern.compile("[0-9]+(\\.[0-9]+)?")` builds the pattern once. In Java source, a backslash inside a string must be written twice, so `\\.` is the two-character regex `\.`, which means a literal dot (an unescaped dot means "any character"). The pattern means: one or more digits, then optionally a dot followed by one or more digits. So `99`, `99.9` and `0.5` match; `.5`, `99.` and `9.9.9` do not.
- `private static final char END_OF_INPUT = '\0';` A `char` is one character. `'\0'` is the NUL character, code 0. It is used as a signal for "there is nothing left to read".
- The two fields `expression` and `position` are the parser's state. `expression` is `final` (never reassigned). `position` is not final: it starts at 0, which is the default for an `int` field, and moves forward.
- The constructor is `private`, so only code inside the class can start a parser. `this.expression = expression;` assigns the argument to the field: `this` means "this object", needed because the parameter has the same name as the field.

**Lines 39 to 57: `parse`, the only public door**

```java
  /**
   * Parses one topology expression.
   *
   * @param expression the text typed by a person
   * @return the topology, or the reason the text could not be read
   * @throws NullPointerException if the expression is null
   */
  public static Result<Topology, TopologyError> parse(String expression) {
    Objects.requireNonNull(expression, "expression");
    if (expression.isBlank()) {
      return Result.error(new TopologyError.EmptyExpression());
    }
    if (expression.length() > MAX_EXPRESSION_LENGTH) {
      return Result.error(
          new TopologyError.ExpressionTooLong(expression.length(), MAX_EXPRESSION_LENGTH));
    }
    TopologyParser parser = new TopologyParser(expression);
    return parser.readTopology(0).flatMap(parser::requireEndOfInput);
  }
```

- The return type is `Result<Topology, TopologyError>`: either a good `Topology` or a `TopologyError`. `Result.error(...)` makes the failure value, `Result.success(...)` makes the good one.
- Three guards run first. A null expression is a programmer error, so `requireNonNull` throws. A blank or too-long expression is bad user text, so they return error values. The length check comes before any reading, so a hostile 10-million-character input is refused at once.
- `TopologyParser parser = new TopologyParser(expression);` creates the private instance.
- `parser.readTopology(0)` reads the whole tree and returns a `Result`. `.flatMap(parser::requireEndOfInput)` then runs `requireEndOfInput` only if the first result succeeded. `parser::requireEndOfInput` is a **method reference**: it means "the method `requireEndOfInput` of this particular parser object, used as a function". `flatMap` is explained in `result4j.md`. If `readTopology` already returned an error, `flatMap` skips the check and passes the error through untouched.
- Why the end check is needed: `chain(a=99.9) x` reads a complete topology and leaves `x`. Without the check, junk after a valid expression would be ignored. I ran it and got `expected the end of the expression at position 14`.

**Lines 59 to 77: `readTopology`, the entry of every rule**

```java
  private Result<Topology, TopologyError> readTopology(int depth) {
    if (depth > MAX_NESTING_DEPTH) {
      return Result.error(new TopologyError.NestingTooDeep(MAX_NESTING_DEPTH));
    }
    skipSpaces();
    int wordStart = position;
    String word = readWord();
    if (word.isEmpty()) {
      return unexpected(wordStart, "a name");
    }
    skipSpaces();
    if (peek() == '(') {
      return readGroup(word, depth);
    }
    if (consumeIf('=')) {
      return readComponent(word);
    }
    return unexpected(position, "'=' or '('");
  }
```

- `depth` counts how many groups enclose this item. `readTopology(0)` is the top level. When it exceeds 16 the method returns `NestingTooDeep`. I ran 16 nested chains around one part, which was accepted, and 17, which printed `NestingTooDeep[limit=16]`.
- `skipSpaces();` moves `position` past spaces. This is how "spaces are ignored" is implemented.
- `int wordStart = position;` remembers where the word begins, so an error can point to it.
- `String word = readWord();` reads letters, digits, hyphens and underscores and advances `position` past them.
- `word.isEmpty()` means the text did not start with a name character, for example `chain(=99)` or `(a=1)`. The error is `unexpected(wordStart, "a name")`.
- After the word, `skipSpaces()` and `peek()` look at the next character without consuming it. If it is `(`, the word was a group keyword, so `readGroup` takes over. If it is `=`, `consumeIf('=')` consumes it and `readComponent` reads the percent. Anything else is an error at the current position: `'=' or '('`.
- Parsing decisions use one character of look-ahead: the parser never has to back up. That keeps it short and predictable.

**Lines 79 to 101: `readGroup`**

```java
  private Result<Topology, TopologyError> readGroup(String keyword, int depth) {
    if (!CHAIN_KEYWORD.equals(keyword) && !PARALLEL_KEYWORD.equals(keyword)) {
      return Result.error(new TopologyError.UnknownGroup(keyword));
    }
    position++; // Step over the opening parenthesis that the caller saw.
    List<Topology> children = new ArrayList<>();
    do {
      switch (readTopology(depth + 1)) {
        case Result.Success<Topology, TopologyError>(Topology child) -> children.add(child);
        case Result.Error<Topology, TopologyError>(TopologyError error) -> {
          return Result.error(error);
        }
      }
      skipSpaces();
    } while (consumeIf(','));
    if (!consumeIf(')')) {
      return unexpected(position, "',' or ')'");
    }
    return Result.success(
        CHAIN_KEYWORD.equals(keyword)
            ? new Topology.Chain(children)
            : new Topology.Parallel(children));
  }
```

- The keyword check is first: only `chain` and `parallel` are groups. `foo(a=1)` returns `UnknownGroup("foo")`. I ran it and got `unknown group 'foo'; use chain(...) or parallel(...)`. The check uses `CHAIN_KEYWORD.equals(keyword)` with the constant first, which is a habit: a constant is never null, so the call cannot fail with a null.
- `position++; // Step over the opening parenthesis` moves one character forward. `++` adds 1. The caller already saw `(` with `peek()` but did not consume it.
- `List<Topology> children = new ArrayList<>();` makes an empty list to collect children. `<>` is the "diamond": Java fills in the type from the left side.
- The `do { ... } while (consumeIf(','));` loop runs at least once (a group needs one child), then repeats as long as a comma follows. `consumeIf(',')` returns true and advances when the next character is a comma.
- `switch (readTopology(depth + 1)) { case ... }` is a **pattern-matching switch on a record**. `readTopology` returns a `Result`. `Result.Success<Topology, TopologyError>(Topology child)` is a **record pattern**: it matches a `Success` and pulls its value out into the variable `child` in one step. The `Error` case pulls the error into `error`. Both generic types are spelled out in full. The switch has no default branch because `Result` is sealed with exactly `Success` and `Error` (see `result4j.md`).
- `case Result.Error<...>(TopologyError error) -> { return Result.error(error); }` returns from the whole method, `readGroup`. A `return` inside a switch statement leaves the method. This is the early exit: the first bad child stops the group, and the same error goes up. The error value is wrapped again with `Result.error(error)` because the variable `error` holds only the `TopologyError` inside the failed result, and the method must return a `Result`.
- After a child, `skipSpaces()` makes room for spaces before a comma or a closing parenthesis.
- After the loop, `if (!consumeIf(')'))` means: no closing parenthesis. The error is `',' or ')'` at the current position. For `chain(a=99.9` the position is 12, the end of the text. I ran it: `expected ',' or ')' at position 12`.
- The last statement builds the group. `CHAIN_KEYWORD.equals(keyword) ? new Topology.Chain(children) : new Topology.Parallel(children)` is a **conditional expression**: `condition ? valueIfTrue : valueIfFalse`. The record constructor copies the list, so `children` can be discarded.

**Lines 103 to 120: `readComponent`**

```java
  private Result<Topology, TopologyError> readComponent(String name) {
    skipSpaces();
    int numberStart = position;
    while (Character.isDigit(peek()) || peek() == '.') {
      position++;
    }
    String percentText = expression.substring(numberStart, position);
    // Checking the shape first means BigDecimal can never throw on what a person typed.
    if (!DECIMAL_NUMBER.matcher(percentText).matches()) {
      return unexpected(numberStart, "a percent such as 99.9");
    }
    BigDecimal percent = new BigDecimal(percentText);
    if (!Availability.isPercent(percent)) {
      return Result.error(new TopologyError.PercentOutOfRange(percentText));
    }
    return Result.success(
        new Topology.Single(new Component(name, Availability.ofPercent(percent))));
  }
```

- `Character.isDigit(peek()) || peek() == '.'` loops while the next character is a digit or a dot, moving forward. This collects the text of the number without judging its shape. `Character.isDigit` also accepts digits of other scripts, such as Arabic-Indic digits; the regex below rejects those.
- `expression.substring(numberStart, position)` cuts out the text from the start up to, but not including, `position`.
- The regex check comes before `new BigDecimal(percentText)` because `BigDecimal` throws `NumberFormatException` on text such as `9.9.9` or an empty string. After the shape check passes, the constructor cannot throw. The comment states this. For `chain(a=99.9, b=)` the number is empty, the regex fails, and the error is `expected a percent such as 99.9 at position 16`, which I ran.
- `PercentOutOfRange` is a separate error type because the text had the right shape and the wrong size, like `101`. `Availability.isPercent(percent)` reports that.
- `Availability.ofPercent(percent)` can no longer throw, because the check just passed.
- `new Topology.Single(new Component(name, ...))` builds the leaf.

**Lines 122 to 131: the end check and the error helper**

```java
  private Result<Topology, TopologyError> requireEndOfInput(Topology topology) {
    skipSpaces();
    return position == expression.length()
        ? Result.success(topology)
        : unexpected(position, "the end of the expression");
  }

  private static Result<Topology, TopologyError> unexpected(int position, String expected) {
    return Result.error(new TopologyError.Unexpected(position, expected));
  }
```

- `requireEndOfInput(Topology topology)` takes the topology that `readTopology` produced. After spaces, `position == expression.length()` is true when every character was consumed.
- The `? :` operator again: success with the same topology, or an error at the current position.
- `private static` `unexpected(...)` is a helper that builds the `Unexpected` error value so that the code above stays short. It is `static` because it needs no parser state. Its parameter `position` shadows the field of the same name inside the method, which is fine here because the method only uses the argument it was given.

**Lines 133 to 161: the four character helpers**

```java
  private String readWord() {
    int wordStart = position;
    while (isWordCharacter(peek())) {
      position++;
    }
    return expression.substring(wordStart, position);
  }

  private boolean consumeIf(char expected) {
    if (peek() != expected) {
      return false;
    }
    position++;
    return true;
  }

  private char peek() {
    return position < expression.length() ? expression.charAt(position) : END_OF_INPUT;
  }

  private void skipSpaces() {
    while (Character.isWhitespace(peek())) {
      position++;
    }
  }

  private static boolean isWordCharacter(char character) {
    return Character.isLetterOrDigit(character) || character == '-' || character == '_';
  }
```

- `readWord` marks the start, advances while `isWordCharacter(peek())`, and returns the cut text. It may return an empty string.
- `consumeIf(char expected)` is the one place that both tests and advances. If the next character is not the expected one, it returns false and changes nothing. If it is, it moves `position` one step and returns true.
- `peek()` returns the character at `position` without moving, or `END_OF_INPUT` when the position is at or after the end. Using the NUL character avoids a special case for "end of text" in every caller: NUL is neither a letter, a digit, a space, nor any character the grammar uses, so every loop stops there naturally. `expression.charAt(position)` would throw `StringIndexOutOfBoundsException` at the end, so the check must come first.
- `skipSpaces` loops while `Character.isWhitespace(peek())`. At the end of input, NUL is not whitespace, so the loop ends.
- `isWordCharacter` is true for a letter or digit (`Character.isLetterOrDigit`), a hyphen or an underscore. So `load-balancer` and `db_primary` are valid names.

#### The position trace

For the text `chain(a=99.9, b=99.5)` (21 characters, indexes 0 to 20):

| Step | Method | `position` after | What happened |
|---|---|---|---|
| 1 | `parse` | 0 | Not blank, 21 is at most 2000. New parser. |
| 2 | `readTopology(0)` | 5 | `readWord` consumed `chain`. `peek()` is `(`. |
| 3 | `readGroup` | 6 | `position++` stepped over `(`. |
| 4 | `readTopology(1)` | 7 | Word `a`. `peek()` is `=`. |
| 5 | `readComponent` | 12 | `consumeIf('=')` moved to 8, digits consumed to 12. Text `99.9`. |
| 6 | `readGroup` loop | 13 | `consumeIf(',')` true. |
| 7 | `readTopology(1)` | 15 | `skipSpaces` moved to 14, word `b` at 14, position 15. |
| 8 | `readComponent` | 20 | `=` consumed, text `99.5` read. |
| 9 | `readGroup` | 21 | `consumeIf(',')` false, `consumeIf(')')` true. Chain built. |
| 10 | `requireEndOfInput` | 21 | Position equals length 21. Success. |

### `AvailabilityReport.java`

**Lines 1 to 18**

```java
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
```

- `Locale` controls number and text conventions per country. `Collectors` builds results out of streams. A **stream** is a pipeline over a collection (see `java.md`).
- `Duration.ofHours(1).toMinutes()` is 60, a `long`, stored in a `double` constant for later division.

**Lines 20 to 40: `describe`**

```java
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
```

- The same sealed `switch` as the calculator, again with no default: if a fourth topology shape is added, this stops compiling too.
- For a single part, the description is its name.
- For a chain: `chain.parts().stream()` opens a stream over the list. `.map(AvailabilityReport::describe)` turns each part into its description by calling `describe` recursively, using a method reference. `.collect(Collectors.joining(", ", "chain(", ")"))` joins the texts with a comma and a space between them, a prefix `chain(` in front and a suffix `)` behind. For `chain(a, b)` it returns `chain(a, b)`.
- The parallel case is the same with a different prefix. Recursion makes nested topologies print as nested text, for example `chain(browser, load-balancer, parallel(seatwave, seatwave), database)`.

**Lines 42 to 62: `render`**

```java
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
```

- `StringBuilder` builds text in memory without making a new `String` per piece. `append` adds text and returns the same builder, so calls are chained.
- `'\n'` is the one-character line break. `report.append("...\n")` ends the line.
- `topology instanceof Topology.Chain chain` tests the type and names the value in one step. This is **pattern matching for `instanceof`**. The parts section is printed only for a chain, because a single part or a parallel group has no "parts of the chain".
- It evaluates the whole topology once, prints the percent, then calls `appendDowntime`.
- The last two lines print the fixed assumption text. The line break is written inside the string, so the text ends the line.
- `report.toString()` returns the finished text.

**Lines 64 to 77: `appendParts`**

```java
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
```

- For each part it computes its availability and prints one row: `String.format(Locale.ROOT, "  %-44s %s%n", describe(part), percent)`. **`String.format`** fills a template. `%-44s` means "a string, left-aligned, padded with spaces to width 44". `%s` is any value as text. `%n` is the platform's line break. `Locale.ROOT` fixes the locale so that numbers and text do not change with the computer's language settings.
- The weakest part is found by `weakestPart` and printed with its percent. The output looks like `Weakest part: gateway (99.9 %)`.

**Lines 79 to 97: `appendDowntime`**

```java
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
```

- It computes minutes per month: `Downtime.allowedBy` gives a `Duration`, and `Downtime.inMinutes` turns that into a number of minutes with decimals.
- `%,.2f` formats a `double` with a thousands separator and two decimals: 2894.7116 becomes `2,894.71`. `%,d` formats a whole number with a separator: `43,200`. With `Locale.ROOT`, the separator is always a comma and the decimal mark is always a dot.
- `Downtime.MONTH_OF_30_DAYS.toMinutes()` is 43,200. It is printed next to the budget so the reader sees the base.
- The last line prints the year budget and the same figure in hours, `minutesPerYear / MINUTES_PER_HOUR`.
- Check with the three 99.9 percent chain: month 129.47, year 1,575.22 minutes, which is 26.25 hours (1,575.22 / 60). The README shows the same.

### `AvailabilityCli.java`

**Lines 1 to 16**

```java
package io.plainround.seatwave.targets.availability;

import com.github.sviperll.result4j.Result;

/**
 * Command-line entry point of the availability calculator.
 *
 * <p>It reads one topology expression, prints the report and exits with 0. Bad input prints one
 * line that says what is wrong and exits with 2.
 */
public final class AvailabilityCli {

  // Exit status 2 is the shell convention for "the command was used incorrectly".
  private static final int EXIT_BAD_INPUT = 2;

  private AvailabilityCli() {}
```

- A **CLI** is a command-line interface: a program you start from a terminal. The class has a private constructor and only static members. It is the only class of this package that prints or ends the program.
- A **process exit status** is a small number a program hands back to the shell when it ends. 0 means success. A non-zero number means failure. By shell convention, 2 means the command was used incorrectly.

**Lines 18 to 38: `main`**

```java
  /**
   * Runs the calculator.
   *
   * @param arguments the topology expression; when a shell or a build tool splits it at spaces, the
   *     pieces are joined again
   */
  public static void main(String[] arguments) {
    if (arguments.length == 0) {
      System.err.println("usage:   availability <topology>");
      System.err.println("example: availability 'chain(a=99.9, parallel(b=99.5, b=99.5))'");
      System.exit(EXIT_BAD_INPUT);
    }
    switch (TopologyParser.parse(String.join(" ", arguments))) {
      case Result.Success<Topology, TopologyError>(Topology topology) ->
          System.out.print(AvailabilityReport.render(topology));
      case Result.Error<Topology, TopologyError>(TopologyError error) -> {
        System.err.println("invalid topology: " + explain(error));
        System.exit(EXIT_BAD_INPUT);
      }
    }
  }
```

- `public static void main(String[] arguments)` is the fixed entry point. The JVM calls it. `String[] arguments` is the list of words typed after the program name. `void` means it returns nothing.
- `arguments.length == 0` means no argument was given. It prints usage on `System.err`, the **standard error** stream, which is meant for messages about problems. `System.out` is the standard output stream for results. Then `System.exit(EXIT_BAD_INPUT)` ends the program with status 2.
- `String.join(" ", arguments)` glues the words back together with a space. A shell splits `chain(a=99.9, b=99.5)` at spaces into two arguments, so joining restores the text.
- The `switch` is a statement matching `Result.Success<Topology, TopologyError>(Topology topology)` and `Result.Error<...>(TopologyError error)`. On success, `System.out.print(AvailabilityReport.render(topology))` prints the report. `print` (not `println`) is used because the report already ends with a line break. On error the program prints `invalid topology: ` plus the explanation to standard error, then exits with 2.
- When `main` ends normally the exit status is 0, which is why the success branch has no `exit` call.
- After `System.exit` the JVM stops, so nothing below it runs. This is why the lab and CLI classes are the only ones that call it.

**Lines 40 to 54: `explain`**

```java
  private static String explain(TopologyError error) {
    return switch (error) {
      case TopologyError.EmptyExpression _ -> "the expression is empty";
      case TopologyError.ExpressionTooLong tooLong ->
          "the expression has " + tooLong.length() + " characters; the limit is " + tooLong.limit();
      case TopologyError.NestingTooDeep tooDeep ->
          "groups are nested deeper than " + tooDeep.limit() + " levels";
      case TopologyError.Unexpected unexpected ->
          "expected " + unexpected.expected() + " at position " + unexpected.position();
      case TopologyError.UnknownGroup unknown ->
          "unknown group '" + unknown.keyword() + "'; use chain(...) or parallel(...)";
      case TopologyError.PercentOutOfRange outOfRange ->
          "a percent must be between 0 and 100, but was " + outOfRange.percentText();
    };
  }
}
```

- Another switch expression over the sealed `TopologyError`, with no default. Each case turns a reason into a sentence.
- `case TopologyError.EmptyExpression _ ->` uses the **unnamed pattern** `_`. The record has no fields and the branch does not need the value, so the code does not name it. Unnamed variables are final in Java 22. The other branches name the value (`tooLong`, `tooDeep`, `unexpected`, `unknown`, `outOfRange`) because they read its fields.
- `tooLong.length()` and `tooLong.limit()` are the **accessor** methods the record created. Each is named after its field.
- Strings are joined with `+`. A `String` followed by `+` and an `int` produces text.
- The Javadoc line `* @param arguments ...` continues on the next line as part of the same sentence.

## Try it yourself

Run these in `seatwave-targets/` (the folder that holds `gradlew`). `./gradlew -q` runs a Gradle task with Gradle's own messages hidden. The first run may download Gradle, which takes a minute.

1. A chain of three parts:

```
./gradlew -q availability --args='chain(gateway=99.9, seatwave=99.9, payments=99.9)'
```

Look for `End-to-end availability: 99.7003 %` and `Downtime budget: 129.47 minutes per 30-day month`. Check it by hand: 0.999 times 0.999 times 0.999 is 0.997002999.

2. A parallel group (no "Parts of the chain" section, because it is not a chain):

```
./gradlew -q availability --args='parallel(app=99.5, app=99.5)'
```

I got `Topology: parallel(app, app)`, `End-to-end availability: 99.9975 %` and `1.08 minutes per 30-day month`. Check it by hand: 0.005 times 0.005 is 0.000025.

3. A parse error with a position:

```
./gradlew -q availability --args='chain(a=99.9'
```

I got `invalid topology: expected ',' or ')' at position 12`, and Gradle ended with `finished with non-zero exit value 2`.

4. An unknown group:

```
./gradlew -q availability --args='foo(a=1)'
```

I got `invalid topology: unknown group 'foo'; use chain(...) or parallel(...)`.

5. Use the classes by hand in the Java shell. `jshell` is a program that ships with the JDK. It evaluates one Java line at a time.

```
./gradlew -q compileJava
jshell --class-path build/classes/java/main:$HOME/.gradle/caches/modules-2/files-2.1/com.github.sviperll/result4j/1.2.0/1d73de9cf73a3a26f53be521c0cfdaf8cebdd96a/result4j-1.2.0.jar
```

Then type `import io.plainround.seatwave.targets.availability.*;` and `System.out.println(TopologyParser.parse("chain(a=101)"));`. I got `Error[error=PercentOutOfRange[percentText=101]]`. Type `/exit` to leave. (The long hash in the path is where Gradle stored the library on my machine; run `find ~/.gradle -name 'result4j-1.2.0.jar'` to find yours.)

## Mistakes people make

- **Using `<` and `>` to reject bad numbers and letting NaN through.** This code uses the negated range test (`Availability.java`, line 37).
- **Dividing a `double` by 100 to turn a percent into a fraction.** The result can be off in the last digits. The code moves the decimal point in `BigDecimal` first.
- **Adding availabilities.** A chain multiplies. Adding two 99.9 percent parts to 199.8 is meaningless.
- **Multiplying the availabilities of a parallel group.** That is the chain rule. The group multiplies the failure shares and subtracts from 1.
- **Forgetting the end-of-input check.** Without `requireEndOfInput`, text after a valid expression would be silently ignored.
- **Letting a parser recurse without a limit.** The limits (2,000 characters, 16 nesting levels) turn a possible stack overflow into an error value.
- **Trusting independence.** Two copies in the same data center share power and network. The report says so in its last two lines.

## Check yourself

1. Why does `Availability` use `!(fraction >= 0.0 && fraction <= 1.0)` and not `fraction < 0.0 || fraction > 1.0`? Answer: NaN fails every comparison. The second form is false for NaN, so NaN would be accepted. The negated test rejects it.
2. Two parts of 99 percent each are in a chain. What is the end-to-end availability? Answer: 0.99 times 0.99 is 0.9801, so 98.01 percent.
3. Two copies of 99 percent are in parallel. What is the group's availability? Answer: each fails 0.01. Both fail 0.0001. The group works 1 minus 0.0001, which is 99.99 percent.
4. After `readWord` finishes, what does `peek()` return for the text `chain(`? Answer: `(`, the character at `position`, without moving.
5. Why does the sealed `Topology` have no `default` branch in its switches? Answer: the compiler knows the full list, checks that every case is handled, and refuses to compile when a new shape is added until every switch handles it.

## Further reading

- The Java Language Specification and the Java SE 25 API documentation, https://docs.oracle.com/en/java/javase/25/docs/api/
- `java.lang.Record`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/Record.html
- `java.math.BigDecimal`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/math/BigDecimal.html
- `java.time.Duration`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/time/Duration.html
- Pattern matching for `switch` (JDK 25 language guide), https://docs.oracle.com/en/java/javase/25/language/pattern-matching.html
- Sealed classes (JDK 25 language guide), https://docs.oracle.com/en/java/javase/25/language/sealed-classes-and-interfaces.html
- `java.util.regex.Pattern`, https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/util/regex/Pattern.html
