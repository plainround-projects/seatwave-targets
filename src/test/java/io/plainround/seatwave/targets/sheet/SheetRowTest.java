package io.plainround.seatwave.targets.sheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SheetRowTest {

  @Test
  void returnsTheCellOfAColumn() {
    SheetRow row = SheetFixtures.completeRow();

    assertThat(row.cell(SheetColumn.OPERATION)).isEqualTo("List events");
    assertThat(row.cell(SheetColumn.REASON)).isEqualTo("It is the first page a fan sees");
  }

  @Test
  void hasEveryColumnOnlyWithEightCells() {
    assertThat(SheetFixtures.completeRow().hasEveryColumn()).isTrue();
    assertThat(new SheetRow(1, List.of("only", "two")).hasEveryColumn()).isFalse();
    assertThat(new SheetRow(1, List.of()).hasEveryColumn()).isFalse();
  }

  @Test
  void refusesToReadACellOfARowThatLacksColumns() {
    SheetRow shortRow = new SheetRow(1, List.of("only", "two"));

    assertThatThrownBy(() -> shortRow.cell(SheetColumn.REASON))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("line 1");
  }

  @Test
  void copiesTheCellsSoLaterChangesDoNotReachTheRow() {
    List<String> cells = new ArrayList<>(SheetFixtures.completeCells());
    SheetRow row = new SheetRow(1, cells);

    cells.set(0, "changed");

    assertThat(row.cell(SheetColumn.OPERATION)).isEqualTo("List events");
  }

  @Test
  void rejectsNullCellsAndANullColumn() {
    assertThatNullPointerException().isThrownBy(() -> new SheetRow(1, null));
    assertThatNullPointerException().isThrownBy(() -> SheetFixtures.completeRow().cell(null));
  }
}
