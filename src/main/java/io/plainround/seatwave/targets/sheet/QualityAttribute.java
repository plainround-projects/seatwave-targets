package io.plainround.seatwave.targets.sheet;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * The dimension of "how well" that a target sets a level on.
 *
 * <p>The first seven are the quality attributes the module teaches. Security, privacy and
 * compliance are on the list because they are non-functional requirements too.
 */
public enum QualityAttribute {
  LATENCY("Latency"),
  THROUGHPUT("Throughput"),
  SCALABILITY("Scalability"),
  AVAILABILITY("Availability"),
  RELIABILITY("Reliability"),
  EFFICIENCY("Efficiency"),
  MAINTAINABILITY("Maintainability"),
  SECURITY("Security"),
  PRIVACY("Privacy"),
  COMPLIANCE("Compliance");

  private final String label;

  QualityAttribute(String label) {
    this.label = label;
  }

  /**
   * Returns the text used for the attribute on the sheet.
   *
   * @return the label, for example "Availability"
   */
  public String label() {
    return label;
  }

  /**
   * Finds the attribute a cell names.
   *
   * @param cellText the text of the Attribute cell
   * @return the attribute, or empty when the text names none; upper and lower case are equal
   * @throws NullPointerException if the text is null
   */
  public static Optional<QualityAttribute> fromLabel(String cellText) {
    Objects.requireNonNull(cellText, "cellText");
    return Arrays.stream(values())
        .filter(attribute -> attribute.label.equalsIgnoreCase(cellText.strip()))
        .findFirst();
  }
}
