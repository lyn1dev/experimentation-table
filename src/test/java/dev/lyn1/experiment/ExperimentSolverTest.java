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
        assertEquals(ExperimentSolver.Mode.CHRONOMATRON, solver.select("Chronomatron (Metaphysical)"));
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
        solver.select("Chronomatron (Metaphysical)");

        // Round 1 Preview: Glowstone phase -> slot 20 flashes
        List<ExperimentSolver.Cell> r1Flash = List.of(control("minecraft:glowstone"), cell(20, "minecraft:red_dye", 1, true, ""));
        assertTrue(solver.nextClick(r1Flash).isEmpty());

        // Round 1 Input: Clock appears -> clicks [20]
        List<ExperimentSolver.Cell> r1Input = List.of(control("minecraft:clock"), cell(20, "minecraft:red_dye", 1, false, ""));
        assertEquals(20, solver.nextClick(r1Input).orElseThrow());
        solver.onSlotClicked(20);
        assertTrue(solver.nextClick(r1Input).isEmpty());

        // Round 2 Preview: Glowstone phase -> gap, flashes 20, gap, then flashes 31
        List<ExperimentSolver.Cell> r2Gap = List.of(control("minecraft:glowstone"), cell(20, "minecraft:red_dye", 1, false, ""), cell(31, "minecraft:blue_dye", 1, false, ""));
        assertTrue(solver.nextClick(r2Gap).isEmpty());

        List<ExperimentSolver.Cell> r2Flash1 = List.of(control("minecraft:glowstone"), cell(20, "minecraft:red_dye", 1, true, ""), cell(31, "minecraft:blue_dye", 1, false, ""));
        assertTrue(solver.nextClick(r2Flash1).isEmpty());

        assertTrue(solver.nextClick(r2Gap).isEmpty());

        List<ExperimentSolver.Cell> r2Flash2 = List.of(control("minecraft:glowstone"), cell(20, "minecraft:red_dye", 1, false, ""), cell(31, "minecraft:blue_dye", 1, true, ""));
        assertTrue(solver.nextClick(r2Flash2).isEmpty());

        // Round 2 Input: Clock appears -> replays [20, 31]
        List<ExperimentSolver.Cell> r2Input = List.of(control("minecraft:clock"), cell(20, "minecraft:red_dye", 1, false, ""), cell(31, "minecraft:blue_dye", 1, false, ""));
        assertEquals(20, solver.nextClick(r2Input).orElseThrow());
        solver.onSlotClicked(20);
        assertEquals(31, solver.nextClick(r2Input).orElseThrow());
        solver.onSlotClicked(31);
        assertTrue(solver.nextClick(r2Input).isEmpty());

        // Round 3 Preview: Glowstone phase -> gap, flashes 20, gap, 31, gap, 14
        assertTrue(solver.nextClick(r2Gap).isEmpty());

        List<ExperimentSolver.Cell> r3Flash1 = List.of(control("minecraft:glowstone"), cell(20, "minecraft:red_dye", 1, true, ""));
        assertTrue(solver.nextClick(r3Flash1).isEmpty());
        assertTrue(solver.nextClick(r2Gap).isEmpty());

        List<ExperimentSolver.Cell> r3Flash2 = List.of(control("minecraft:glowstone"), cell(31, "minecraft:blue_dye", 1, true, ""));
        assertTrue(solver.nextClick(r3Flash2).isEmpty());
        assertTrue(solver.nextClick(r2Gap).isEmpty());

        List<ExperimentSolver.Cell> r3Flash3 = List.of(control("minecraft:glowstone"), cell(14, "minecraft:lime_dye", 1, true, ""));
        assertTrue(solver.nextClick(r3Flash3).isEmpty());

        // Round 3 Input: Clock appears -> replays [20, 31, 14]
        List<ExperimentSolver.Cell> r3Input = List.of(control("minecraft:clock"));
        assertEquals(20, solver.nextClick(r3Input).orElseThrow());
        solver.onSlotClicked(20);
        assertEquals(31, solver.nextClick(r3Input).orElseThrow());
        solver.onSlotClicked(31);
        assertEquals(14, solver.nextClick(r3Input).orElseThrow());
        solver.onSlotClicked(14);
        assertTrue(solver.nextClick(r3Input).isEmpty());
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
        solver.onSlotClicked(12);
        assertEquals(30, solver.nextClick(replay).orElseThrow());
        solver.onSlotClicked(30);
        assertEquals(40, solver.nextClick(replay).orElseThrow());
        solver.onSlotClicked(40);
        assertEquals(22, solver.nextClick(replay).orElseThrow());
        solver.onSlotClicked(22);
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
        solver.onSlotClicked(11);
        assertEquals(22, solver.nextClick(cells).orElseThrow());
        solver.onSlotClicked(22);
        assertEquals(33, solver.nextClick(cells).orElseThrow());
        solver.onSlotClicked(33);
        assertEquals(44, solver.nextClick(cells).orElseThrow());
        solver.onSlotClicked(44);
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
