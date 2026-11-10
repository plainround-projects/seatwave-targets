package io.plainround.seatwave.targets.sheet;

import java.util.Arrays;
import java.util.List;

/**
 * The columns of the requirements sheet, in the order they appear in the Markdown table.
 *
 * <p>The order of the constants is the order of the cells in a row.
 */
public enum SheetColumn {
  OPERATION("Operation"),
  ATTRIBUTE("Attribute"),
  METRIC("Metric"),
  PERCENTILE("Percentile"),
  THRESHOLD("Threshold"),
  WINDOW("Window"),
  OWNER("Owner"),
  REASON("Reason");

  private final String heading;

  SheetColumn(String heading) {
    this.heading = heading;
  }

  /**
   * Returns the text of the column in the header row.
   *
   * @return the heading, for example "Threshold"
   */
  public String heading() {
    return heading;
  }

  /**
   * Lists the headings of a valid sheet.
   *
   * @return the eight headings in table order
   */
  public static List<String> headings() {
    return Arrays.stream(values()).map(SheetColumn::heading).toList();
  }
}
