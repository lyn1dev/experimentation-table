package dev.lyn1.experiment;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Queue;
import java.util.Set;

/**
 * Solves the three Experimentation Table games (Chronomatron, Ultrasequencer,
 * Superpairs). Ported verbatim from the Lyn1-client Auto Enchanting module.
 */
final class ExperimentSolver {
    enum Mode { NONE, CHRONOMATRON, ULTRASEQUENCER, SUPERPAIRS }

    record Cell(int slot, String itemId, int count, boolean foil, String name, boolean empty) {
    }

    private Mode mode = Mode.NONE;
    private final List<Integer> chronomatron = new ArrayList<>();
    private final List<Integer> chronomatronPreviewSequence = new ArrayList<>();
    private int lastFoilSlot = -1;
    private boolean wasClock = false;
    private int chronomatronClickIndex = 0;
    private final Map<Integer, Integer> ultrasequencer = new HashMap<>();
    private int ultrasequencerClickIndex = 0;
    private boolean ultrasequencerReady = false;
    private final Map<String, Integer> seenPairs = new HashMap<>();
    private final Queue<Integer> pairClicks = new ArrayDeque<>();
    private final Set<Integer> queuedPairSlots = new HashSet<>();

    Mode select(String title) {
        Mode selected;
        if (title.contains("Chronomatron") && !title.contains("Sta")) selected = Mode.CHRONOMATRON;
        else if (title.contains("Ultrasequencer") && !title.contains("Sta")) selected = Mode.ULTRASEQUENCER;
        else if (title.startsWith("Superpairs (")) selected = Mode.SUPERPAIRS;
        else selected = Mode.NONE;
        if (selected != mode) reset(selected);
        return mode;
    }

    OptionalInt nextClick(List<Cell> cells) {
        return switch (mode) {
            case CHRONOMATRON -> solveChronomatron(cells);
            case ULTRASEQUENCER -> solveUltrasequencer(cells);
            case SUPERPAIRS -> solveSuperpairs(cells);
            case NONE -> OptionalInt.empty();
        };
    }

    void onSlotClicked(int slot) {
        switch (mode) {
            case CHRONOMATRON -> {
                if (chronomatronClickIndex < chronomatron.size()) {
                    chronomatronClickIndex++;
                }
            }
            case ULTRASEQUENCER -> {
                ultrasequencerClickIndex++;
            }
            case SUPERPAIRS -> {
                pairClicks.poll();
            }
            case NONE -> {}
        }
    }

    int rememberedCount() {
        return switch (mode) {
            case CHRONOMATRON -> chronomatron.size();
            case ULTRASEQUENCER -> ultrasequencer.size();
            case SUPERPAIRS -> queuedPairSlots.size();
            case NONE -> 0;
        };
    }

    private OptionalInt solveChronomatron(List<Cell> cells) {
        Cell control = cell(cells, 49);
        if (control == null) return OptionalInt.empty();

        Cell foil = cells.stream()
            .filter(cell -> cell.slot() >= 10 && cell.slot() <= 43 && cell.foil())
            .findFirst()
            .orElse(null);

        boolean isClock = control.itemId().contains("clock");

        // Transition: Clock -> Glowstone (New round demonstration preview starting)
        if (wasClock && !isClock) {
            chronomatronPreviewSequence.clear();
            lastFoilSlot = foil != null ? foil.slot() : -1;
            chronomatronClickIndex = 0;
        }

        // Preview / demonstration phase (non-clock, e.g. glowstone)
        if (!isClock) {
            wasClock = false;
            chronomatronClickIndex = 0;
            if (foil != null) {
                if (foil.slot() != lastFoilSlot) {
                    lastFoilSlot = foil.slot();
                    chronomatronPreviewSequence.add(foil.slot());
                }
            } else {
                lastFoilSlot = -1;
            }
            return OptionalInt.empty();
        }

        // Transition: Glowstone -> Clock (Preview ended, player input starting)
        if (!wasClock && isClock) {
            wasClock = true;
            if (foil != null && foil.slot() != lastFoilSlot) {
                lastFoilSlot = foil.slot();
                chronomatronPreviewSequence.add(foil.slot());
            }
            if (!chronomatronPreviewSequence.isEmpty()) {
                chronomatron.clear();
                chronomatron.addAll(chronomatronPreviewSequence);
            }
            chronomatronClickIndex = 0;
            lastFoilSlot = foil != null ? foil.slot() : -1;
        }

        if (chronomatronClickIndex < chronomatron.size()) {
            return OptionalInt.of(chronomatron.get(chronomatronClickIndex));
        }
        return OptionalInt.empty();
    }

    private OptionalInt solveUltrasequencer(List<Cell> cells) {
        Cell control = cell(cells, 49);
        if (control == null) return OptionalInt.empty();
        if (control.itemId().contains("clock")) {
            ultrasequencerReady = false;
            if (ultrasequencer.containsKey(ultrasequencerClickIndex)) {
                return OptionalInt.of(ultrasequencer.get(ultrasequencerClickIndex));
            }
            return OptionalInt.empty();
        }
        if (!ultrasequencerReady) {
            ultrasequencer.clear();
            cells.stream()
                .filter(cell -> cell.slot() >= 9 && cell.slot() <= 44)
                .filter(ExperimentSolver::isSequenceItem)
                .sorted(Comparator.comparingInt(Cell::count))
                .forEach(cell -> ultrasequencer.put(cell.count() - 1, cell.slot()));
            ultrasequencerClickIndex = 0;
            if (!ultrasequencer.isEmpty()) {
                ultrasequencerReady = true;
            }
        }
        return OptionalInt.empty();
    }

    private OptionalInt solveSuperpairs(List<Cell> cells) {
        for (Cell cell : cells) {
            if (cell.slot() < 9 || cell.slot() > 44 || !isRevealedPair(cell) || queuedPairSlots.contains(cell.slot())) continue;
            String key = cell.itemId() + "|" + cell.name();
            Integer first = seenPairs.putIfAbsent(key, cell.slot());
            if (first != null && first != cell.slot() && !queuedPairSlots.contains(first)) {
                pairClicks.add(first);
                pairClicks.add(cell.slot());
                queuedPairSlots.add(first);
                queuedPairSlots.add(cell.slot());
                seenPairs.remove(key);
            }
        }
        Integer next = pairClicks.peek();
        return next == null ? OptionalInt.empty() : OptionalInt.of(next);
    }

    private void reset(Mode selected) {
        mode = selected;
        chronomatron.clear();
        chronomatronPreviewSequence.clear();
        lastFoilSlot = -1;
        wasClock = false;
        chronomatronClickIndex = 0;
        ultrasequencer.clear();
        ultrasequencerClickIndex = 0;
        ultrasequencerReady = false;
        seenPairs.clear();
        pairClicks.clear();
        queuedPairSlots.clear();
    }

    private static Cell cell(List<Cell> cells, int slot) {
        return cells.stream().filter(cell -> cell.slot() == slot).findFirst().orElse(null);
    }

    private static boolean isSequenceItem(Cell cell) {
        return cell.itemId().contains("_dye")
            || cell.itemId().equals("minecraft:lapis_lazuli")
            || cell.itemId().equals("minecraft:bone_meal");
    }

    private static boolean isRevealedPair(Cell cell) {
        if (cell.empty()) return false;
        String id = cell.itemId();
        return !id.contains("stained_glass")
            && !id.equals("minecraft:clock")
            && !id.equals("minecraft:glowstone")
            && !id.equals("minecraft:black_dye")
            && !cell.name().isBlank();
    }
}
