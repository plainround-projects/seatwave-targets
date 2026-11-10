package io.plainround.seatwave.targets.sheet;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** The rule for when a cell of the sheet counts as empty. */
final class CellText {

  // Words people write when they do not know yet. Each one is a missing field, not a value.
  private static final Set<String> PLACEHOLDERS =
      Set.of("tbd", "todo", "n/a", "na", "none", "unknown", "?", "-");

  private CellText() {}

  /**
   * Tells whether a cell holds no real value.
   *
   * @param cellText the text of the cell
   * @return true when the text is blank or is a placeholder such as "TBD"
   */
  static boolean isMissing(String cellText) {
    Objects.requireNonNull(cellText, "cellText");
    String normalized = cellText.strip().toLowerCase(Locale.ROOT);
    return normalized.isEmpty() || PLACEHOLDERS.contains(normalized);
  }
}
