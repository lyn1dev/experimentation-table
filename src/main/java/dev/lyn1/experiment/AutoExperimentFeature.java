package dev.lyn1.experiment;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Tick-driven Auto Experiment handler, ported from the Lyn1-client Auto
 * Enchanting module. Solves Experimentation Table games (Chronomatron,
 * Ultrasequencer, Superpairs) by reading the open container and clicking the
 * required slots via the PICKUP interaction (Left/Right click).
 */
public final class AutoExperimentFeature {
    private static final ExperimentSolver SOLVER = new ExperimentSolver();
    private static String lastTitle = "";
    private static long lastClickMillis = 0L;
    private static boolean wasEnabled = true;

    private AutoExperimentFeature() {
    }

    public static void tick() {
        boolean enabled = ExperimentConfig.enabled();
        if (enabled != wasEnabled) {
            wasEnabled = enabled;
            reset();
        }
        Minecraft client = Minecraft.getInstance();
        if (!enabled) {
            return;
        }
        if (!(client.gui.screen() instanceof ContainerScreen screen)) {
            SOLVER.select("");
            return;
        }
        String title = screen.getTitle().getString();
        if (title.startsWith("Superpairs (") && !ExperimentConfig.superpairs()) {
            SOLVER.select("");
            return;
        }
        ExperimentSolver.Mode mode = SOLVER.select(title);
        if (mode == ExperimentSolver.Mode.NONE) {
            return;
        }
        if (!title.equals(lastTitle)) {
            lastTitle = title;
            lastClickMillis = 0L;
        }
        ChestMenu menu = screen.getMenu();
        List<ExperimentSolver.Cell> cells = snapshot(menu);
        long now = System.currentTimeMillis();
        OptionalInt click = SOLVER.nextClick(cells, now, lastClickMillis, ExperimentConfig.delayMs());
        if (click.isPresent() && clickContainerSlot(menu.containerId, click.getAsInt())) {
            lastClickMillis = now;
        }
    }

    public static void onWorldChanged() {
        reset();
    }

    private static void reset() {
        SOLVER.select("");
        lastTitle = "";
        lastClickMillis = 0L;
    }

    private static boolean clickContainerSlot(int containerId, int slot) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) {
            return false;
        }
        int button = ThreadLocalRandom.current().nextInt(100) < 4 ? 1 : 0;
        client.gameMode.handleContainerInput(containerId, slot, button, ContainerInput.PICKUP, client.player);
        return true;
    }

    private static List<ExperimentSolver.Cell> snapshot(ChestMenu menu) {
        ArrayList<ExperimentSolver.Cell> cells = new ArrayList<>(menu.slots.size());
        for (Slot slot : menu.slots) {
            ItemStack stack = slot.getItem();
            boolean empty = stack == null || stack.isEmpty();
            cells.add(new ExperimentSolver.Cell(
                slot.index,
                empty ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
                empty ? 0 : stack.getCount(),
                !empty && stack.hasFoil(),
                empty ? "" : stack.getHoverName().getString(),
                empty
            ));
        }
        return List.copyOf(cells);
    }
}
