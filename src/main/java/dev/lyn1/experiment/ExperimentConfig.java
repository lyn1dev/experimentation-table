package dev.lyn1.experiment;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;

/** Enabled and feature settings for the Auto Experiment feature. */
public final class ExperimentConfig {
    private static final boolean DEFAULT_ENABLED = true;
    private static final boolean DEFAULT_SUPERPAIRS = true;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("lyn1-experiment.json");

    private static boolean enabled = DEFAULT_ENABLED;
    private static boolean superpairs = DEFAULT_SUPERPAIRS;

    private ExperimentConfig() {
    }

    private record Data(Boolean enabled, Boolean superpairs) {
    }

    public static void load() {
        if (!Files.isRegularFile(CONFIG_PATH)) return;
        try {
            Data data = GSON.fromJson(Files.readString(CONFIG_PATH, StandardCharsets.UTF_8), Data.class);
            if (data == null) return;
            if (data.enabled != null) enabled = data.enabled;
            if (data.superpairs != null) superpairs = data.superpairs;
        } catch (IOException | JsonParseException e) {
            // Corrupt config: fall back to defaults.
        }
    }

    private static void save() {
        try {
            Path tmp = CONFIG_PATH.resolveSibling(CONFIG_PATH.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(new Data(enabled, superpairs)), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, CONFIG_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            // Best-effort persistence; settings still apply for this session.
        }
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        save();
    }

    public static boolean superpairs() {
        return superpairs;
    }

    public static void setSuperpairs(boolean value) {
        superpairs = value;
        save();
    }
}
