package io.plainround.seatwave.targets.sheet;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * What a target applies to: one of the four SeatWave operations, or all of them together.
 *
 * <p>The list is closed on purpose. A target on an operation that does not exist cannot be tested.
 */
public enum Operation {
  LIST_EVENTS("List events"),
  VIEW_SEATS("View seats"),
  HOLD_A_SEAT("Hold a seat"),
  CONFIRM_A_BOOKING("Confirm a booking"),
  ALL_OPERATIONS("All operations");

  private final String label;

  Operation(String label) {
    this.label = label;
  }

  /**
   * Returns the text used for the operation on the sheet.
   *
   * @return the label, for example "Hold a seat"
   */
  public String label() {
    return label;
  }

  /**
   * Finds the operation a cell names.
   *
   * @param cellText the text of the Operation cell
   * @return the operation, or empty when the text names none; upper and lower case are equal
   * @throws NullPointerException if the text is null
   */
  public static Optional<Operation> fromLabel(String cellText) {
    Objects.requireNonNull(cellText, "cellText");
    return Arrays.stream(values())
        .filter(operation -> operation.label.equalsIgnoreCase(cellText.strip()))
        .findFirst();
  }
}
