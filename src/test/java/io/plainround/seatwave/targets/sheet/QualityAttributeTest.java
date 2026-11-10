package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class QualityAttributeTest {

  @ParameterizedTest
  @EnumSource(QualityAttribute.class)
  void findsEveryAttributeByItsOwnLabel(QualityAttribute attribute) {
    assertThat(QualityAttribute.fromLabel(attribute.label())).contains(attribute);
  }

  @Test
  void ignoresCase() {
    assertThat(QualityAttribute.fromLabel("aVAILABILITY")).contains(QualityAttribute.AVAILABILITY);
  }

  @Test
  void ignoresSurroundingSpaces() {
    assertThat(QualityAttribute.fromLabel("  Latency ")).contains(QualityAttribute.LATENCY);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "Speed", "Late", "Latency and more"})
  void returnsEmptyForAnUnknownLabel(String unknownLabel) {
    assertThat(QualityAttribute.fromLabel(unknownLabel)).isEmpty();
  }

  @Test
  void rejectsNullText() {
    assertThatNullPointerException().isThrownBy(() -> QualityAttribute.fromLabel(null));
  }
}
