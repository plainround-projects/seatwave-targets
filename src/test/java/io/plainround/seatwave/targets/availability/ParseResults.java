package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.fail;

import com.github.sviperll.result4j.Result;

/** Opens the result of the parser, so a test states only what it expects. */
final class ParseResults {

  private ParseResults() {}

  static Topology topologyOf(String expression) {
    return switch (TopologyParser.parse(expression)) {
      case Result.Success<Topology, TopologyError>(Topology topology) -> topology;
      case Result.Error<Topology, TopologyError>(TopologyError error) ->
          fail("expected a topology but got " + error);
    };
  }

  static TopologyError errorOf(String expression) {
    return switch (TopologyParser.parse(expression)) {
      case Result.Success<Topology, TopologyError>(Topology topology) ->
          fail("expected an error but got " + topology);
      case Result.Error<Topology, TopologyError>(TopologyError error) -> error;
    };
  }
}
