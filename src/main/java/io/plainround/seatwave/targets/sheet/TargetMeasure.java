package io.plainround.seatwave.targets.sheet;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * How a target is measured: the metric, the percentile or rate, the threshold and the window.
 *
 * <p>A measure with a blank metric, no percentile or rate, a threshold without a number or no
 * window cannot be built. Immutable and safe to share between threads.
 *
 * @param metric what is measured and where, for example "Response time at the load balancer"
 * @param percentileOrRate a percentile such as "p99", or the kind of number, such as "rate"
 * @param threshold the line to beat, with its unit, for example "under 300 ms"
 * @param window the period over which the target is judged
 */
public record TargetMeasure(
    String metric, String percentileOrRate, String threshold, String window) {

  // A percentile such as p50, p95, p99 or p99.9.
  private static final Pattern PERCENTILE = Pattern.compile("p[0-9]{1,2}(\\.[0-9]+)?");
  // For targets that are not a percentile, the kind of number the threshold is.
  private static final Set<String> RATE_KINDS =
      Set.of("rate", "count", "peak", "mean", "max", "min", "ratio");
  private static final Pattern CONTAINS_A_DIGIT = Pattern.compile(".*[0-9].*");

  /**
   * Creates a measure.
   *
   * @throws NullPointerException if a field is null
   * @throws IllegalArgumentException if a field breaks one of the rules of {@link #problemsIn}
   */
  public TargetMeasure {
    List<RowProblem> problems = problemsIn(metric, percentileOrRate, threshold, window);
    if (!problems.isEmpty()) {
      throw new IllegalArgumentException("not a complete measure: " + problems);
    }
    metric = metric.strip();
    percentileOrRate = percentileOrRate.strip();
    threshold = threshold.strip();
    window = window.strip();
  }

  /**
   * Lists what stops four cells from being a complete measure.
   *
   * <p>The constructor and the sheet validator both call this method, so the rules exist once.
   *
   * @param metric the text of the Metric cell
   * @param percentileOrRate the text of the Percentile cell
   * @param threshold the text of the Threshold cell
   * @param window the text of the Window cell
   * @return every problem found, or an empty list when the four cells form a complete measure
   * @throws NullPointerException if a cell is null
   */
  public static List<RowProblem> problemsIn(
      String metric, String percentileOrRate, String threshold, String window) {
    Objects.requireNonNull(metric, "metric");
    Objects.requireNonNull(percentileOrRate, "percentileOrRate");
    Objects.requireNonNull(threshold, "threshold");
    Objects.requireNonNull(window, "window");
    List<RowProblem> problems = new ArrayList<>();
    if (CellText.isMissing(metric)) {
      problems.add(new RowProblem.MissingField(SheetColumn.METRIC));
    }
    if (CellText.isMissing(percentileOrRate)) {
      problems.add(new RowProblem.MissingField(SheetColumn.PERCENTILE));
    } else if (!isPercentileOrRate(percentileOrRate)) {
      problems.add(new RowProblem.NotAllowed(SheetColumn.PERCENTILE, percentileOrRate.strip()));
    }
    // "Fast" and "highly available" contain no digit, so nobody can test them.
    if (!CONTAINS_A_DIGIT.matcher(threshold).matches()) {
      problems.add(new RowProblem.ThresholdWithoutNumber(threshold.strip()));
    }
    // A number with no window cannot be measured: there is no start and no end.
    if (CellText.isMissing(window)) {
      problems.add(new RowProblem.MissingField(SheetColumn.WINDOW));
    }
    return List.copyOf(problems);
  }

  /**
   * Lists the words the Percentile cell accepts besides a percentile.
   *
   * @return the kinds in alphabetical order
   */
  public static List<String> rateKinds() {
    return RATE_KINDS.stream().sorted().toList();
  }

  private static boolean isPercentileOrRate(String cellText) {
    String normalized = cellText.strip().toLowerCase(Locale.ROOT);
    return PERCENTILE.matcher(normalized).matches() || RATE_KINDS.contains(normalized);
  }
}
