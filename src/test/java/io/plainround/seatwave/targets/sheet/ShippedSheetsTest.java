package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.github.sviperll.result4j.Result;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShippedSheetsTest {

  // The number of targets in each shipped sheet, counted by hand from its table.
  private static final int ARENA_TARGETS = 24;
  private static final int CLUB_TARGETS = 6;
  private static final int LAB_SOLUTION_TARGETS = 8;
  private static final int DRAFT_REJECTIONS = 4;

  @Test
  void acceptsTheArenaSheetWithItsTargets() throws IOException {
    assertThat(acceptedTargetsIn("arena-on-sale.md")).hasSize(ARENA_TARGETS);
  }

  @Test
  void acceptsTheClubSheetWithItsTargets() throws IOException {
    assertThat(acceptedTargetsIn("club.md")).hasSize(CLUB_TARGETS);
  }

  @Test
  void acceptsTheLabSolutionSheetWithItsTargets() throws IOException {
    assertThat(acceptedTargetsIn("lab1-solution.md")).hasSize(LAB_SOLUTION_TARGETS);
  }

  @Test
  void rejectsTheLabDraftWithNoAcceptedRowsAndFourRejections() throws IOException {
    switch (SheetFixtures.validatedSheet("lab1-draft.md")) {
      case Result.Success<List<Target>, SheetError>(List<Target> targets) ->
          fail("expected the draft to be rejected but got " + targets);
      case Result.Error<List<Target>, SheetError>(SheetError error) -> {
        assertThat(error).isInstanceOf(SheetError.RowsRejected.class);
        SheetError.RowsRejected rejected = (SheetError.RowsRejected) error;
        assertThat(rejected.acceptedCount()).isZero();
        assertThat(rejected.rejections()).hasSize(DRAFT_REJECTIONS);
      }
    }
  }

  private static List<Target> acceptedTargetsIn(String fileName) throws IOException {
    return switch (SheetFixtures.validatedSheet(fileName)) {
      case Result.Success<List<Target>, SheetError>(List<Target> targets) -> targets;
      case Result.Error<List<Target>, SheetError>(SheetError error) ->
          fail("expected " + fileName + " to be accepted but got " + error);
    };
  }
}
