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
                context.getSource().sendFeedback(Component.literal(
                    "[Lyn1-Experiment] Status: " + (ExperimentConfig.enabled() ? "ON" : "OFF")
                        + " | Click delay: " + ExperimentConfig.delayMs() + " ms"
                        + " | Superpairs: " + (ExperimentConfig.superpairs() ? "ON" : "OFF")
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
            .then(ClientCommands.literal("delay")
                .then(ClientCommands.argument("ms",
                        IntegerArgumentType.integer(ExperimentConfig.MIN_DELAY_MS, ExperimentConfig.MAX_DELAY_MS))
                    .executes(context -> {
                        int ms = IntegerArgumentType.getInteger(context, "ms");
                        ExperimentConfig.setDelayMs(ms);
                        context.getSource().sendFeedback(Component.literal(
                            "[Lyn1-Experiment] Click delay set to " + ms + " ms"
                        ).withStyle(ChatFormatting.GREEN));
                        return 1;
                    }))));
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
