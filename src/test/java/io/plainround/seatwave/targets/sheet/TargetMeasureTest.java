package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class TargetMeasureTest {

  @Test
  void stripsEveryField() {
    TargetMeasure measure = new TargetMeasure(" Response time ", " p99 ", " 1 s ", " Month ");

    assertThat(measure.metric()).isEqualTo("Response time");
    assertThat(measure.percentileOrRate()).isEqualTo("p99");
    assertThat(measure.threshold()).isEqualTo("1 s");
    assertThat(measure.window()).isEqualTo("Month");
  }

  @Test
  void cannotBeBuiltWithoutAMetric() {
    assertThatThrownBy(() -> new TargetMeasure("", "p99", "1 s", "Month"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MissingField[column=METRIC]");
  }

  @Test
  void cannotBeBuiltWithoutAPercentileOrRate() {
    assertThatThrownBy(() -> new TargetMeasure("Metric", "n/a", "1 s", "Month"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MissingField[column=PERCENTILE]");
  }

  @Test
  void cannotBeBuiltWithAnUnknownPercentile() {
    assertThatThrownBy(() -> new TargetMeasure("Metric", "average", "1 s", "Month"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("NotAllowed");
  }

  @Test
  void cannotBeBuiltWithAThresholdThatHasNoNumber() {
    assertThatThrownBy(() -> new TargetMeasure("Metric", "p99", "fast", "Month"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ThresholdWithoutNumber");
  }

  @Test
  void cannotBeBuiltWithoutAWindow() {
    assertThatThrownBy(() -> new TargetMeasure("Metric", "p99", "1 s", "?"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("MissingField[column=WINDOW]");
  }

  @Test
  void rejectsNullFields() {
    assertThatNullPointerException().isThrownBy(() -> new TargetMeasure(null, "p99", "1 s", "M"));
    assertThatNullPointerException().isThrownBy(() -> new TargetMeasure("m", null, "1 s", "M"));
    assertThatNullPointerException().isThrownBy(() -> new TargetMeasure("m", "p99", null, "M"));
    assertThatNullPointerException().isThrownBy(() -> new TargetMeasure("m", "p99", "1 s", null));
  }

  @Test
  void reportsNoProblemsForACompleteMeasure() {
    assertThat(TargetMeasure.problemsIn("Metric", "p99", "1 s", "Month")).isEmpty();
  }

  @Test
  void reportsEveryProblemOfAnEmptyMeasureAtOnce() {
    assertThat(TargetMeasure.problemsIn("", "", "", ""))
        .containsExactly(
            new RowProblem.MissingField(SheetColumn.METRIC),
            new RowProblem.MissingField(SheetColumn.PERCENTILE),
            new RowProblem.ThresholdWithoutNumber(""),
            new RowProblem.MissingField(SheetColumn.WINDOW));
  }

  @Test
  void listsTheRateKindsInAlphabeticalOrder() {
    List<String> rateKinds = TargetMeasure.rateKinds();

    assertThat(rateKinds).isSorted().contains("rate", "count", "peak");
  }
}
