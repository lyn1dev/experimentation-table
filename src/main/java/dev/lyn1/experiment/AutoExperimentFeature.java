package dev.lyn1.experiment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Tick-driven Auto Experiment handler, ported from the Lyn1-client Auto
 * Enchanting module. Solves Experimentation Table games (Chronomatron,
 * Ultrasequencer, Superpairs) with humanized Gaussian timing, spatial
 * cursor distance modeling, and reaction delays.
 */
public final class AutoExperimentFeature {
    private static final ExperimentSolver SOLVER = new ExperimentSolver();
    private static String lastTitle = "";
    private static String lastControlItem = "";
    private static int lastSlotClicked = 49;
    private static long nextAllowedClickMillis = 0L;
    private static int consecutivePairClicks = 0;
    private static boolean wasEnabled = true;

    private static final int BASE_HUMAN_DELAY_MS = 200;

    @FunctionalInterface
    private interface ScreenAccessor {
        Screen getScreen(Minecraft client);
    }

    private static final ScreenAccessor SCREEN_ACCESSOR = initScreenAccessor();

    private AutoExperimentFeature() {
    }

    public static void tick() {
        boolean enabled = ExperimentConfig.enabled();
        if (enabled != wasEnabled) {
            wasEnabled = enabled;
            reset();
        }
        Minecraft client = Minecraft.getInstance();
        if (!enabled || client == null) {
            return;
        }
        Screen currentScreen = SCREEN_ACCESSOR.getScreen(client);
        if (!(currentScreen instanceof ContainerScreen screen)) {
            reset();
            return;
        }
        String title = screen.getTitle().getString();
        if (title.startsWith("Superpairs (") && !ExperimentConfig.superpairs()) {
            reset();
            return;
        }
        ExperimentSolver.Mode mode = SOLVER.select(title);
        if (mode == ExperimentSolver.Mode.NONE) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();

        if (!title.equals(lastTitle)) {
            lastTitle = title;
            lastControlItem = "";
            lastSlotClicked = 49;
            consecutivePairClicks = 0;
            // Human takes ~300-500ms to orient upon opening / transitioning screens
            nextAllowedClickMillis = now + ThreadLocalRandom.current().nextLong(300, 500);
        }

        ChestMenu menu = screen.getMenu();
        List<ExperimentSolver.Cell> cells = snapshot(menu);

        // Detect control item (slot 49) transition from glowstone to clock (round start reaction)
        String currentControl = getControlItem(cells);
        if ("minecraft:glowstone".equals(lastControlItem) && "minecraft:clock".equals(currentControl)) {
            nextAllowedClickMillis = Math.max(nextAllowedClickMillis, now + generateReactionDelay());
        }
        lastControlItem = currentControl;

        if (now < nextAllowedClickMillis) {
            return;
        }

        OptionalInt click = SOLVER.nextClick(cells);
        if (click.isPresent() && clickContainerSlot(menu.containerId, click.getAsInt())) {
            int slot = click.getAsInt();
            boolean isNewPair = (mode == ExperimentSolver.Mode.SUPERPAIRS && (consecutivePairClicks % 2 == 0));
            long nextDelay = generateInterClickDelay(lastSlotClicked, slot, isNewPair);
            nextAllowedClickMillis = now + nextDelay;
            lastSlotClicked = slot;
            consecutivePairClicks++;
        }
    }

    public static void onWorldChanged() {
        reset();
    }

    private static void reset() {
        SOLVER.select("");
        lastTitle = "";
        lastControlItem = "";
        lastSlotClicked = 49;
        nextAllowedClickMillis = 0L;
        consecutivePairClicks = 0;
    }

    /**
     * Simulates human visual reaction time when the round changes from
     * memorization (glowstone) to input phase (clock).
     */
    private static long generateReactionDelay() {
        double gaussian = ThreadLocalRandom.current().nextGaussian();
        long delay = Math.round(330 + gaussian * 40); // Mean: 330ms, StdDev: 40ms
        return Math.clamp(delay, 250, 480);
    }

    /**
     * Calculates grid Euclidean distance between two inventory slots.
     */
    private static double slotDistance(int s1, int s2) {
        if (s1 < 0 || s2 < 0) return 1.5;
        int x1 = s1 % 9, y1 = s1 / 9;
        int x2 = s2 % 9, y2 = s2 / 9;
        return Math.hypot(x2 - x1, y2 - y1);
    }

    /**
     * Computes a natural human inter-click delay incorporating:
     * 1. Base human clicking speed (~200ms)
     * 2. Physical cursor travel time based on 2D slot distance
     * 3. Gaussian variance (jitter)
     * 4. Contextual hesitation (finding new pairs or recalling note sequences)
     */
    private static long generateInterClickDelay(int fromSlot, int toSlot, boolean isNewPair) {
        double dist = slotDistance(fromSlot, toSlot);
        double speed = ThreadLocalRandom.current().nextDouble(16.0, 24.0); // ms per grid unit
        long travelTime = Math.round(dist * speed);

        double gaussian = ThreadLocalRandom.current().nextGaussian();
        long jitter = Math.round(gaussian * 25);
        jitter = Math.clamp(jitter, -45, 65);

        long hesitation = 0;
        if (isNewPair) {
            // Visual search hesitation to locate the next matching pair
            hesitation = ThreadLocalRandom.current().nextLong(160, 290);
        } else if (ThreadLocalRandom.current().nextDouble() < 0.10) {
            // 10% chance of natural micro-hesitation during long sequences
            hesitation = ThreadLocalRandom.current().nextLong(45, 95);
        }

        long total = BASE_HUMAN_DELAY_MS + travelTime + jitter + hesitation;
        return Math.max(120, total);
    }

    private static String getControlItem(List<ExperimentSolver.Cell> cells) {
        for (ExperimentSolver.Cell cell : cells) {
            if (cell.slot() == 49) {
                return cell.itemId();
            }
        }
        return "";
    }

    private static boolean clickContainerSlot(int containerId, int slot) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gameMode == null) {
            return false;
        }
        client.gameMode.handleContainerInput(containerId, slot, 0, ContainerInput.PICKUP, client.player);
        return true;
    }

    private static ScreenAccessor initScreenAccessor() {
        // Option 1: Gui.screen() method (Minecraft 26.2+)
        try {
            Method method = Gui.class.getMethod("screen");
            method.setAccessible(true);
            return client -> {
                try {
                    return client.gui != null ? (Screen) method.invoke(client.gui) : null;
                } catch (Throwable t) {
                    return null;
                }
            };
        } catch (Throwable ignored) {
        }

        // Option 2: Gui.screen field (Minecraft 26.2+ fallback)
        try {
            Field field = Gui.class.getDeclaredField("screen");
            field.setAccessible(true);
            return client -> {
                try {
                    return client.gui != null ? (Screen) field.get(client.gui) : null;
                } catch (Throwable t) {
                    return null;
                }
            };
        } catch (Throwable ignored) {
        }

        // Option 3: Minecraft.screen field (Minecraft 26.1.2)
        try {
            Field field = Minecraft.class.getDeclaredField("screen");
            field.setAccessible(true);
            return client -> {
                try {
                    return (Screen) field.get(client);
                } catch (Throwable t) {
                    return null;
                }
            };
        } catch (Throwable ignored) {
        }

        // Option 4: Scan Minecraft fields for any Screen type instance (Minecraft 26.1.2 fallback)
        try {
            for (Field field : Minecraft.class.getDeclaredFields()) {
                if (Screen.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    return client -> {
                        try {
                            return (Screen) field.get(client);
                        } catch (Throwable t) {
                            return null;
                        }
                    };
                }
            }
        } catch (Throwable ignored) {
        }

        return client -> null;
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
