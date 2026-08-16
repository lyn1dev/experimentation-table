package dev.lyn1.experiment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExperimentSolverTest {
    private static ExperimentSolver.Cell cell(int slot, String itemId, int count, boolean foil, String name) {
        return new ExperimentSolver.Cell(slot, itemId, count, foil, name, itemId.isEmpty());
    }

    private static ExperimentSolver.Cell control(String itemId) {
        return cell(49, itemId, 1, false, "Control");
    }

    @Test
    void selectsTheRightGameModeFromTheTitle() {
        ExperimentSolver solver = new ExperimentSolver();
        assertEquals(ExperimentSolver.Mode.CHRONOMATRON, solver.select("Chronomatron (Best of 5)"));
        assertEquals(ExperimentSolver.Mode.ULTRASEQUENCER, solver.select("Ultrasequencer (Best of 3)"));
        assertEquals(ExperimentSolver.Mode.SUPERPAIRS, solver.select("Superpairs (1/5)"));
        assertEquals(ExperimentSolver.Mode.NONE, solver.select(""));
        // "Sta" excludes the stats screens of the previous game.
        assertEquals(ExperimentSolver.Mode.NONE, solver.select("Chronomatron Stats"));
        assertEquals(ExperimentSolver.Mode.NONE, solver.select("Ultrasequencer Stats"));
    }

    @Test
    void chronomatronRecordsFoilNotesAndReplaysThemInOrder() {
        ExperimentSolver solver = new ExperimentSolver();
        solver.select("Chronomatron (Best of 5)");
        long now = 1_000L;

        // Preparation: glowstone control, nothing to click.
        List<ExperimentSolver.Cell> prep = List.of(control("minecraft:glowstone"), cell(20, "minecraft:red_dye", 1, true, ""));
        assertTrue(solver.nextClick(prep, now, 0L, 200L).isEmpty());

        // Round 1: clock control reveals a foil note at slot 20 -> record and replay it.
        List<ExperimentSolver.Cell> round1 = List.of(control("minecraft:clock"), cell(20, "minecraft:red_dye", 1, true, ""));
        assertEquals(20, solver.nextClick(round1, now, 0L, 200L).orElseThrow());
        // Replay done; nothing else to click this round.
        assertTrue(solver.nextClick(round1, now + 300L, now, 200L).isEmpty());

        // Round 2: glowstone latches off, a new foil note at slot 25 -> replay 20, then 25.
        assertTrue(solver.nextClick(prep, now + 600L, now + 300L, 200L).isEmpty());
        List<ExperimentSolver.Cell> round2 = List.of(
            control("minecraft:clock"),
            cell(20, "minecraft:red_dye", 1, false, ""),
            cell(25, "minecraft:red_dye", 1, true, "")
        );
        assertEquals(20, solver.nextClick(round2, now + 900L, now + 600L, 200L).orElseThrow());
        assertEquals(25, solver.nextClick(round2, now + 1_200L, now + 900L, 200L).orElseThrow());
    }

    @Test
    void ultrasequencerOrdersRevealedNotesByCountAndReplaysThem() {
        ExperimentSolver solver = new ExperimentSolver();
        solver.select("Ultrasequencer (Best of 3)");
        long now = 1_000L;

        // Reveal: glowstone control; notes sorted by count (count 1 -> index 0).
        List<ExperimentSolver.Cell> reveal = List.of(
            control("minecraft:glowstone"),
            cell(12, "minecraft:lime_dye", 1, false, ""),
            cell(30, "minecraft:lime_dye", 2, false, ""),
            cell(40, "minecraft:lime_dye", 3, false, ""),
            cell(22, "minecraft:lime_dye", 4, false, "")
        );
        assertTrue(solver.nextClick(reveal, now, 0L, 200L).isEmpty());

        // Replay: clock control clicks 12, then 30, then 40, then 22.
        List<ExperimentSolver.Cell> replay = List.of(control("minecraft:clock"));
        assertEquals(12, solver.nextClick(replay, now + 300L, now, 200L).orElseThrow());
        assertEquals(30, solver.nextClick(replay, now + 600L, now + 300L, 200L).orElseThrow());
        assertEquals(40, solver.nextClick(replay, now + 900L, now + 600L, 200L).orElseThrow());
        assertEquals(22, solver.nextClick(replay, now + 1_200L, now + 900L, 200L).orElseThrow());
        assertTrue(solver.nextClick(replay, now + 1_500L, now + 1_200L, 200L).isEmpty());
    }

    @Test
    void superpairsClicksMatchedPairsInOrder() {
        ExperimentSolver solver = new ExperimentSolver();
        solver.select("Superpairs (1/5)");
        long now = 1_000L;

        List<ExperimentSolver.Cell> cells = List.of(
            cell(11, "minecraft:magenta_dye", 1, false, "Magenta Dye"),
            cell(22, "minecraft:magenta_dye", 1, false, "Magenta Dye"),
            cell(33, "minecraft:orange_dye", 1, false, "Orange Dye"),
            cell(44, "minecraft:orange_dye", 1, false, "Orange Dye")
        );
        assertEquals(11, solver.nextClick(cells, now, 0L, 200L).orElseThrow());
        assertEquals(22, solver.nextClick(cells, now + 300L, now, 200L).orElseThrow());
        assertEquals(33, solver.nextClick(cells, now + 600L, now + 300L, 200L).orElseThrow());
        assertEquals(44, solver.nextClick(cells, now + 900L, now + 600L, 200L).orElseThrow());
        assertTrue(solver.nextClick(cells, now + 1_200L, now + 900L, 200L).isEmpty());
    }

    @Test
    void superpairsIgnoresGlassControlsAndUnnamedItems() {
        ExperimentSolver solver = new ExperimentSolver();
        solver.select("Superpairs (1/5)");

        List<ExperimentSolver.Cell> cells = List.of(
            cell(10, "minecraft:gray_stained_glass_pane", 1, false, ""),
            cell(20, "minecraft:gray_stained_glass_pane", 1, false, ""),
            cell(30, "minecraft:black_dye", 1, false, "Cover"),
            cell(31, "minecraft:black_dye", 1, false, "Cover"),
            cell(40, "minecraft:red_dye", 1, false, "")
        );
        assertTrue(solver.nextClick(cells, 1_000L, 0L, 200L).isEmpty());
    }

    @Test
    void respectsTheClickDelay() {
        ExperimentSolver solver = new ExperimentSolver();
        solver.select("Superpairs (1/5)");
        List<ExperimentSolver.Cell> cells = List.of(
            cell(11, "minecraft:magenta_dye", 1, false, "Magenta Dye"),
            cell(22, "minecraft:magenta_dye", 1, false, "Magenta Dye")
        );
        // Within the delay window: no click.
        assertTrue(solver.nextClick(cells, 1_000L, 950L, 200L).isEmpty());
        // After the delay: click.
        assertEquals(11, solver.nextClick(cells, 1_200L, 950L, 200L).orElseThrow());
    }
}
