package io.plainround.seatwave.targets.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ComponentTest {

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
  void rejectsABlankName(String blankName) {
    assertThatThrownBy(() -> new Component(blankName, Availability.ALWAYS_UP))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("needs a name");
  }

  @Test
  void rejectsANullName() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Component(null, Availability.ALWAYS_UP))
        .withMessageContaining("name");
  }

  @Test
  void rejectsANullAvailability() {
    assertThatNullPointerException()
        .isThrownBy(() -> new Component("gateway", null))
        .withMessageContaining("availability");
  }

  @Test
  void keepsTheNameAndTheAvailabilityItWasGiven() {
    Component component = new Component("gateway", Availability.ALWAYS_UP);

    assertThat(component.name()).isEqualTo("gateway");
    assertThat(component.availability()).isEqualTo(Availability.ALWAYS_UP);
  }
}
