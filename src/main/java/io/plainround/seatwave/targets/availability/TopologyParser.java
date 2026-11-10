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

  private Result<Topology, TopologyError> requireEndOfInput(Topology topology) {
    skipSpaces();
    return position == expression.length()
        ? Result.success(topology)
        : unexpected(position, "the end of the expression");
  }

  private static Result<Topology, TopologyError> unexpected(int position, String expected) {
    return Result.error(new TopologyError.Unexpected(position, expected));
  }

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
}
