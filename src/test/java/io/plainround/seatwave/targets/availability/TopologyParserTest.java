package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TopologyParserTest {

  // The number of parts in the very long chain; each part is about 8 characters, so it fits.
  private static final int PARTS_IN_A_LONG_CHAIN = 150;

  @Test
  void parsesASingleComponent() {
    assertThat(ParseResults.topologyOf("gateway=99.9"))
        .isEqualTo(Topologies.part("gateway", "99.9"));
  }

  @Test
  void parsesAChain() {
    assertThat(ParseResults.topologyOf("chain(gateway=99.9, payments=99.5)"))
        .isEqualTo(
            Topologies.chain(
                Topologies.part("gateway", "99.9"), Topologies.part("payments", "99.5")));
  }

  @Test
  void parsesAParallelGroup() {
    assertThat(ParseResults.topologyOf("parallel(seatwave=99.9, seatwave=99.9)"))
        .isEqualTo(
            Topologies.parallel(
                Topologies.part("seatwave", "99.9"), Topologies.part("seatwave", "99.9")));
  }

  @Test
  void parsesAGroupNestedInAChain() {
    String expression =
        "chain(gateway=99.9, parallel(seatwave=99.9, seatwave=99.9), payments=99.9)";

    assertThat(ParseResults.topologyOf(expression))
        .isEqualTo(
            Topologies.chain(
                Topologies.part("gateway", "99.9"),
                Topologies.parallel(
                    Topologies.part("seatwave", "99.9"), Topologies.part("seatwave", "99.9")),
                Topologies.part("payments", "99.9")));
  }

  @Test
  void parsesAChainWithOnePart() {
    assertThat(ParseResults.topologyOf("chain(gateway=99.9)"))
        .isEqualTo(Topologies.chain(Topologies.part("gateway", "99.9")));
  }

  @Test
  void ignoresSpacesAndTabsBetweenTokens() {
    String spaced = " chain ( gateway = 99.9 ,\tparallel ( a = 99.5 , a = 99.5 ) ) ";
    String tight = "chain(gateway=99.9,parallel(a=99.5,a=99.5))";

    assertThat(ParseResults.topologyOf(spaced)).isEqualTo(ParseResults.topologyOf(tight));
  }

  @Test
  void acceptsPercentsAtBothEndsOfTheRange() {
    assertThat(ParseResults.topologyOf("a=0")).isEqualTo(Topologies.part("a", "0"));
    assertThat(ParseResults.topologyOf("a=100")).isEqualTo(Topologies.part("a", "100"));
  }

  @Test
  void acceptsNamesWithHyphensUnderscoresAndDigits() {
    assertThat(ParseResults.topologyOf("load-balancer_2=99.999"))
        .isEqualTo(Topologies.part("load-balancer_2", "99.999"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "   ", "\t\n"})
  void rejectsEmptyAndBlankText(String blankExpression) {
    assertThat(ParseResults.errorOf(blankExpression))
        .isEqualTo(new TopologyError.EmptyExpression());
  }

  @Test
  void rejectsTextLongerThanTheLimit() {
    String tooLong = "a".repeat(TopologyParser.MAX_EXPRESSION_LENGTH + 1);

    assertThat(ParseResults.errorOf(tooLong))
        .isEqualTo(
            new TopologyError.ExpressionTooLong(
                tooLong.length(), TopologyParser.MAX_EXPRESSION_LENGTH));
  }

  @Test
  void acceptsTextOfExactlyTheLimitInsteadOfReportingItAsTooLong() {
    String atLimit = "a".repeat(TopologyParser.MAX_EXPRESSION_LENGTH);

    assertThat(ParseResults.errorOf(atLimit))
        .isNotInstanceOf(TopologyError.ExpressionTooLong.class);
  }

  @Test
  void rejectsGroupsNestedDeeperThanTheLimit() {
    String tooDeep = nestedChains(TopologyParser.MAX_NESTING_DEPTH + 1);

    assertThat(ParseResults.errorOf(tooDeep))
        .isEqualTo(new TopologyError.NestingTooDeep(TopologyParser.MAX_NESTING_DEPTH));
  }

  @Test
  void acceptsGroupsNestedExactlyAsDeepAsTheLimit() {
    assertThat(ParseResults.topologyOf(nestedChains(TopologyParser.MAX_NESTING_DEPTH)))
        .isInstanceOf(Topology.Chain.class);
  }

  @Test
  void rejectsAnUnknownGroupKeyword() {
    assertThat(ParseResults.errorOf("ring(a=99.9)"))
        .isEqualTo(new TopologyError.UnknownGroup("ring"));
  }

  @Test
  void rejectsKeywordsWrittenInCapitalLetters() {
    assertThat(ParseResults.errorOf("Chain(a=99.9)"))
        .isEqualTo(new TopologyError.UnknownGroup("Chain"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"chain(a=101)", "a=100.0001", "chain(a=99.9, b=1000)"})
  void rejectsAPercentAbove100(String expression) {
    assertThat(ParseResults.errorOf(expression))
        .isInstanceOf(TopologyError.PercentOutOfRange.class);
  }

  @Test
  void reportsThePercentTextAsTyped() {
    assertThat(ParseResults.errorOf("chain(a=101)"))
        .isEqualTo(new TopologyError.PercentOutOfRange("101"));
  }

  @Test
  void rejectsAChainThatIsNeverClosed() {
    String unclosed = "chain(a=99.9";

    assertThat(ParseResults.errorOf(unclosed))
        .isEqualTo(new TopologyError.Unexpected(unclosed.length(), "',' or ')'"));
  }

  @Test
  void rejectsAPercentWithTwoDecimalPoints() {
    // The number starts at index 8, right after "chain(a=".
    assertThat(ParseResults.errorOf("chain(a=9.9.9)"))
        .isEqualTo(new TopologyError.Unexpected(8, "a percent such as 99.9"));
  }

  @Test
  void rejectsAChainWithNoParts() {
    // Index 6 is the closing parenthesis, where a name was expected.
    assertThat(ParseResults.errorOf("chain()"))
        .isEqualTo(new TopologyError.Unexpected(6, "a name"));
  }

  @Test
  void rejectsANameWithNoPercent() {
    String nameOnly = "seatwave";

    assertThat(ParseResults.errorOf(nameOnly))
        .isEqualTo(new TopologyError.Unexpected(nameOnly.length(), "'=' or '('"));
  }

  @Test
  void rejectsTextAfterTheTopology() {
    // The word "extra" starts at index 7, after "a=99.9 ".
    assertThat(ParseResults.errorOf("a=99.9 extra"))
        .isEqualTo(new TopologyError.Unexpected(7, "the end of the expression"));
  }

  @Test
  void rejectsANegativePercent() {
    // The minus sign sits at index 2, where digits were expected.
    assertThat(ParseResults.errorOf("a=-1"))
        .isEqualTo(new TopologyError.Unexpected(2, "a percent such as 99.9"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"a=", "a=.", "a=99.", "a=.5", "a=1e2", "a=ninety"})
  void rejectsAMalformedPercent(String expression) {
    assertThat(ParseResults.errorOf(expression)).isInstanceOf(TopologyError.Unexpected.class);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"chain(a=99.9,)", "chain(,a=99.9)", "chain(a=99.9 b=99.9)", "=99.9", "(a=99.9)"})
  void rejectsMalformedSeparatorsAndMissingNames(String expression) {
    assertThat(ParseResults.errorOf(expression)).isInstanceOf(TopologyError.Unexpected.class);
  }

  @Test
  void rejectsTrailingCharactersAfterAClosedGroup() {
    assertThat(ParseResults.errorOf("chain(a=99.9))"))
        .isEqualTo(new TopologyError.Unexpected(13, "the end of the expression"));
  }

  @Test
  void parsesAVeryLongValidChain() {
    StringBuilder expression = new StringBuilder("chain(");
    for (int partIndex = 0; partIndex < PARTS_IN_A_LONG_CHAIN; partIndex++) {
      expression.append(partIndex == 0 ? "" : ",").append("p").append(partIndex).append("=99.9");
    }
    expression.append(')');

    Topology topology = ParseResults.topologyOf(expression.toString());

    assertThat(topology).isInstanceOf(Topology.Chain.class);
    assertThat(((Topology.Chain) topology).parts()).hasSize(PARTS_IN_A_LONG_CHAIN);
  }

  @Test
  void rejectsANullExpression() {
    assertThatNullPointerException()
        .isThrownBy(() -> TopologyParser.parse(null))
        .withMessageContaining("expression");
  }

  private static String nestedChains(int levels) {
    return "chain(".repeat(levels) + "a=99.9" + ")".repeat(levels);
  }
}
