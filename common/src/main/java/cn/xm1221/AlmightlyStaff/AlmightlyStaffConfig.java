package cn.xm1221.AlmightlyStaff;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Loaded from config/almightly_staff.json. Missing file is created with the current defaults. */
public final class AlmightlyStaffConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static AlmightlyStaffConfig instance = new AlmightlyStaffConfig();

    public boolean mediaRegenEnabled = true;
    public int mediaRegenIntervalTicks = 260;
    /** Dust restored each interval. 0 restores nothing. */
    public int mediaRegenAmountDust = 20;

    public static AlmightlyStaffConfig get() {
        return instance;
    }

    public static void load() {
        Path path = Platform.getConfigFolder().resolve("almightly_staff.json");
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    AlmightlyStaffConfig loaded = GSON.fromJson(reader, AlmightlyStaffConfig.class);
                    if (loaded != null) instance = loaded;
                }
            }
            instance.mediaRegenIntervalTicks = Math.max(1, instance.mediaRegenIntervalTicks);
            instance.mediaRegenAmountDust = Math.max(0, instance.mediaRegenAmountDust);
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException ignored) {
            instance.mediaRegenIntervalTicks = Math.max(1, instance.mediaRegenIntervalTicks);
            instance.mediaRegenAmountDust = Math.max(0, instance.mediaRegenAmountDust);
        }
    }
}
