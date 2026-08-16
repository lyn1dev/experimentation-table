package dev.lyn1.experiment;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

/** Lyn1-Experiment: Auto Experiment (Experimentation Table solver) mod. */
public final class Lyn1ExperimentMod implements ClientModInitializer {
    public static final String MOD_ID = "lyn1-experiment";

    @Override
    public void onInitializeClient() {
        ExperimentConfig.load();
        ClientTickEvents.END_CLIENT_TICK.register(client -> AutoExperimentFeature.tick());
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> AutoExperimentFeature.onWorldChanged());
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> registerCommands(dispatcher));
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("experiment")
            .executes(context -> {
                int currentPing = AutoExperimentFeature.getClientPing();
                String pingStr = currentPing >= 0 ? (currentPing + " ms") : "N/A";
                context.getSource().sendFeedback(Component.literal(
                    "[Lyn1-Experiment] Status: " + (ExperimentConfig.enabled() ? "ON" : "OFF")
                        + " | Superpairs: " + (ExperimentConfig.superpairs() ? "ON" : "OFF")
                        + " | Baseline Ping: " + ExperimentConfig.basePingMs() + " ms (Current: " + pingStr + ")"
                ).withStyle(ChatFormatting.AQUA));
                return 1;
            })
            .then(ClientCommands.literal("on")
                .executes(context -> setEnabled(context.getSource(), true)))
            .then(ClientCommands.literal("off")
                .executes(context -> setEnabled(context.getSource(), false)))
            .then(ClientCommands.literal("superpairs")
                .then(ClientCommands.literal("on")
                    .executes(context -> setSuperpairsEnabled(context.getSource(), true)))
                .then(ClientCommands.literal("off")
                    .executes(context -> setSuperpairsEnabled(context.getSource(), false))))
            .then(ClientCommands.literal("ping")
                .executes(context -> showPing(context.getSource()))
                .then(ClientCommands.argument("ms",
                        IntegerArgumentType.integer(ExperimentConfig.MIN_BASE_PING_MS, ExperimentConfig.MAX_BASE_PING_MS))
                    .executes(context -> {
                        int ms = IntegerArgumentType.getInteger(context, "ms");
                        ExperimentConfig.setBasePingMs(ms);
                        context.getSource().sendFeedback(Component.literal(
                            "[Lyn1-Experiment] Baseline ping delay set to " + ms + " ms"
                        ).withStyle(ChatFormatting.GREEN));
                        return 1;
                    }))));
    }

    private static int showPing(FabricClientCommandSource source) {
        AutoExperimentFeature.refreshPingAsync(true);
        int currentPing = AutoExperimentFeature.getClientPing();
        String pingStr = currentPing >= 0 ? (currentPing + " ms") : "N/A (Not connected to server)";
        int baseline = ExperimentConfig.basePingMs();
        int effective = Math.max(baseline, Math.max(0, currentPing));
        source.sendFeedback(Component.literal(
            "[Lyn1-Experiment] Current ping: " + pingStr
                + " | Configured baseline: " + baseline + " ms"
                + " | Effective delay buffer: " + effective + " ms"
        ).withStyle(ChatFormatting.YELLOW));
        return 1;
    }

    private static int setEnabled(FabricClientCommandSource source, boolean value) {
        ExperimentConfig.setEnabled(value);
        source.sendFeedback(Component.literal(
            "[Lyn1-Experiment] Auto Experiment " + (value ? "enabled" : "disabled")
        ).withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int setSuperpairsEnabled(FabricClientCommandSource source, boolean value) {
        ExperimentConfig.setSuperpairs(value);
        source.sendFeedback(Component.literal(
            "[Lyn1-Experiment] Superpairs auto-matching " + (value ? "enabled" : "disabled")
        ).withStyle(ChatFormatting.GREEN));
        return 1;
    }
}
