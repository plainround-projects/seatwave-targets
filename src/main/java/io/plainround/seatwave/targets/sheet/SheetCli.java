package io.plainround.seatwave.targets.sheet;

import com.github.sviperll.result4j.Result;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Command-line entry point of the sheet validator.
 *
 * <p>It reads one Markdown file, prints the verdict and exits with 0 when the sheet is accepted, 1
 * when it is rejected and 2 when the file cannot be used at all.
 */
public final class SheetCli {

  private static final int EXIT_SHEET_REJECTED = 1;
  // Exit status 2 is the shell convention for "the command was used incorrectly".
  private static final int EXIT_BAD_INPUT = 2;
  // A requirements sheet is a page or two of text. One mebibyte is far above any real sheet and
  // keeps a wrong path, such as a video file, from being read into memory.
  private static final long MAX_SHEET_BYTES = 1L << 20;

  private SheetCli() {}

  /**
   * Runs the validator.
   *
   * @param arguments exactly one argument: the path of the sheet
   */
  public static void main(String[] arguments) {
    if (arguments.length != 1) {
      System.err.println("usage: validateSheet <requirements-sheet.md>");
      System.exit(EXIT_BAD_INPUT);
    }
    Path sheetFile = Path.of(arguments[0]);
    List<String> lines = readLinesOrExit(sheetFile);
    switch (MarkdownSheetReader.read(lines).flatMap(SheetValidator::validate)) {
      case Result.Success<List<Target>, SheetError>(List<Target> targets) ->
          System.out.print(SheetReport.accepted(targets));
      case Result.Error<List<Target>, SheetError>(SheetError error) -> {
        System.out.print(SheetReport.rejected(error));
        System.exit(EXIT_SHEET_REJECTED);
      }
    }
  }

  private static List<String> readLinesOrExit(Path sheetFile) {
    try {
      if (!Files.isRegularFile(sheetFile)) {
        System.err.println("not a file: " + sheetFile);
        System.exit(EXIT_BAD_INPUT);
      }
      if (Files.size(sheetFile) > MAX_SHEET_BYTES) {
        System.err.println("larger than " + MAX_SHEET_BYTES + " bytes: " + sheetFile);
        System.exit(EXIT_BAD_INPUT);
      }
      return Files.readAllLines(sheetFile, StandardCharsets.UTF_8);
    } catch (IOException unreadable) {
      // The file exists but cannot be read as UTF-8 text. Nothing here can repair that.
      System.err.println("cannot read " + sheetFile + ": " + unreadable.getMessage());
      System.exit(EXIT_BAD_INPUT);
      throw new IllegalStateException("unreachable after exit", unreadable);
    }
  }
}
