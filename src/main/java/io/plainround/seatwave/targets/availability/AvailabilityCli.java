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
