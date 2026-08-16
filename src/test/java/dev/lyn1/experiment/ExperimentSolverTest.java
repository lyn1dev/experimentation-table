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

        // Preparation: glowstone control, nothing to click.
        List<ExperimentSolver.Cell> prep = List.of(control("minecraft:glowstone"), cell(20, "minecraft:red_dye", 1, true, ""));
        assertTrue(solver.nextClick(prep).isEmpty());

        // Round 1: clock control reveals a foil note at slot 20 -> record and replay it.
        List<ExperimentSolver.Cell> round1 = List.of(control("minecraft:clock"), cell(20, "minecraft:red_dye", 1, true, ""));
        assertEquals(20, solver.nextClick(round1).orElseThrow());
        // Replay done; nothing else to click this round.
        assertTrue(solver.nextClick(round1).isEmpty());

        // Round 2: glowstone latches off, a new foil note at slot 25 -> replay 20, then 25.
        assertTrue(solver.nextClick(prep).isEmpty());
        List<ExperimentSolver.Cell> round2 = List.of(
            control("minecraft:clock"),
            cell(20, "minecraft:red_dye", 1, false, ""),
            cell(25, "minecraft:red_dye", 1, true, "")
        );
        assertEquals(20, solver.nextClick(round2).orElseThrow());
        assertEquals(25, solver.nextClick(round2).orElseThrow());
    }

    @Test
    void ultrasequencerOrdersRevealedNotesByCountAndReplaysThem() {
        ExperimentSolver solver = new ExperimentSolver();
        solver.select("Ultrasequencer (Best of 3)");

        // Reveal: glowstone control; notes sorted by count (count 1 -> index 0).
        List<ExperimentSolver.Cell> reveal = List.of(
            control("minecraft:glowstone"),
            cell(12, "minecraft:lime_dye", 1, false, ""),
            cell(30, "minecraft:lime_dye", 2, false, ""),
            cell(40, "minecraft:lime_dye", 3, false, ""),
            cell(22, "minecraft:lime_dye", 4, false, "")
        );
        assertTrue(solver.nextClick(reveal).isEmpty());

        // Replay: clock control clicks 12, then 30, then 40, then 22.
        List<ExperimentSolver.Cell> replay = List.of(control("minecraft:clock"));
        assertEquals(12, solver.nextClick(replay).orElseThrow());
        assertEquals(30, solver.nextClick(replay).orElseThrow());
        assertEquals(40, solver.nextClick(replay).orElseThrow());
        assertEquals(22, solver.nextClick(replay).orElseThrow());
        assertTrue(solver.nextClick(replay).isEmpty());
    }

    @Test
    void superpairsClicksMatchedPairsInOrder() {
        ExperimentSolver solver = new ExperimentSolver();
        solver.select("Superpairs (1/5)");

        List<ExperimentSolver.Cell> cells = List.of(
            cell(11, "minecraft:magenta_dye", 1, false, "Magenta Dye"),
            cell(22, "minecraft:magenta_dye", 1, false, "Magenta Dye"),
            cell(33, "minecraft:orange_dye", 1, false, "Orange Dye"),
            cell(44, "minecraft:orange_dye", 1, false, "Orange Dye")
        );
        assertEquals(11, solver.nextClick(cells).orElseThrow());
        assertEquals(22, solver.nextClick(cells).orElseThrow());
        assertEquals(33, solver.nextClick(cells).orElseThrow());
        assertEquals(44, solver.nextClick(cells).orElseThrow());
        assertTrue(solver.nextClick(cells).isEmpty());
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
        assertTrue(solver.nextClick(cells).isEmpty());
    }
}
