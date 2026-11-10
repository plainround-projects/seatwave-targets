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
}
