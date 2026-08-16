package dev.lyn1.experiment;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.ItemEnchantments;

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

        // Detect control item (slot 49) transition from preview item to clock (round start reaction)
        String currentControl = getControlItem(cells);
        boolean wasNotClock = !lastControlItem.isEmpty() && !lastControlItem.contains("clock");
        boolean isNowClock = currentControl.contains("clock");
        if (wasNotClock && isNowClock) {
            nextAllowedClickMillis = Math.max(nextAllowedClickMillis, now + generateReactionDelay());
        }
        lastControlItem = currentControl;

        // Process solver every tick to ensure continuous real-time observation of note sequences
        OptionalInt click = SOLVER.nextClick(cells);

        // Click execution is gated by humanized timing & ping compensation
        if (click.isPresent()) {
            if (now < nextAllowedClickMillis) {
                return;
            }
            int slot = click.getAsInt();
            if (clickContainerSlot(menu.containerId, slot)) {
                SOLVER.onSlotClicked(slot);
                boolean isNewPair = (mode == ExperimentSolver.Mode.SUPERPAIRS && (consecutivePairClicks % 2 == 0));
                long nextDelay = generateInterClickDelay(lastSlotClicked, slot, isNewPair);
                nextAllowedClickMillis = now + nextDelay;
                lastSlotClicked = slot;
                consecutivePairClicks++;
            }
        }
    }

    private static volatile int measuredPingMs = -1;
    private static volatile long lastPingSampleTime = 0;

    public static void onWorldChanged() {
        measuredPingMs = -1;
        lastPingSampleTime = 0;
        reset();
        refreshPingAsync(true);
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
     * Retrieves the real client-to-server latency in milliseconds.
     * Uses live socket RTT measurements to bypass Hypixel's fake tablist ping (which reports 1 ms).
     * Returns -1 if not connected to a server or in singleplayer.
     */
    public static int getClientPing() {
        refreshPingAsync(false);
        if (measuredPingMs >= 0) {
            return measuredPingMs;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.getConnection() == null) {
            return -1;
        }
        PlayerInfo info = client.getConnection().getPlayerInfo(client.player.getUUID());
        if (info != null && info.getLatency() > 1) {
            return info.getLatency();
        }
        return -1;
    }

    public static void refreshPingAsync(boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now - lastPingSampleTime < 3_000L) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return;
        }
        Connection connection = client.getConnection().getConnection();
        if (connection == null || connection.isMemoryConnection()) {
            measuredPingMs = 0;
            lastPingSampleTime = now;
            return;
        }
        SocketAddress address = connection.getRemoteAddress();
        if (!(address instanceof InetSocketAddress inetAddr)) {
            return;
        }

        lastPingSampleTime = now;
        CompletableFuture.runAsync(() -> {
            try {
                long start = System.nanoTime();
                try (Socket socket = new Socket()) {
                    InetAddress host = inetAddr.getAddress();
                    SocketAddress target = host != null
                        ? new InetSocketAddress(host, inetAddr.getPort())
                        : new InetSocketAddress(inetAddr.getHostString(), inetAddr.getPort());
                    socket.connect(target, 1500);
                }
                long rtt = (System.nanoTime() - start) / 1_000_000L;
                measuredPingMs = (int) Math.max(1, rtt);
            } catch (Throwable ignored) {
            }
        });
    }

    /**
     * Calculates the effective ping buffer in milliseconds:
     * max(configuredBaselinePing, detectedPing)
     */
    private static long getPingDelayBuffer() {
        int detected = getClientPing();
        int base = ExperimentConfig.basePingMs();
        return Math.max(base, Math.max(0, detected));
    }

    /**
     * Simulates human visual reaction time when the round changes from
     * memorization (glowstone) to input phase (clock), adjusted for network latency.
     */
    private static long generateReactionDelay() {
        double gaussian = ThreadLocalRandom.current().nextGaussian();
        long delay = Math.round(330 + gaussian * 40); // Mean: 330ms, StdDev: 40ms
        long reaction = Math.clamp(delay, 250, 480);
        return reaction + (getPingDelayBuffer() / 2);
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
     * 5. Ping compensation to prevent clicking faster than server packet processing
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

        long pingCompensation = getPingDelayBuffer();
        long total = BASE_HUMAN_DELAY_MS + travelTime + jitter + hesitation + pingCompensation;
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

    private static boolean isItemFoil(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (stack.hasFoil() || stack.isEnchanted()) {
            return true;
        }
        Boolean glint = stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
        if (Boolean.TRUE.equals(glint)) {
            return true;
        }
        ItemEnchantments enchs = stack.get(DataComponents.ENCHANTMENTS);
        if (enchs != null && !enchs.isEmpty()) {
            return true;
        }
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored != null && !stored.isEmpty()) {
            return true;
        }
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null && !customData.isEmpty()) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("ench") || tag.contains("Enchantments") || tag.contains("glint") || tag.contains("glowing") || tag.contains("enchanted")) {
                return true;
            }
        }
        return false;
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
                !empty && isItemFoil(stack),
                empty ? "" : stack.getHoverName().getString(),
                empty
            ));
        }
        return List.copyOf(cells);
    }
}
