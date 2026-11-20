package cn.xm1221.AlmightlyStaff.gui.ide.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 读写按法杖 UUID 隔离的侧车工作区 JSON（{@code config/almightly_staff/ide/<uuid>.json}）。
 * 不依赖 MC 客户端类，可直接走 JUnit 单测。
 */
public final class IdeWorkspaceStorage {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path baseDir;

    public IdeWorkspaceStorage(Path baseDir) {
        this.baseDir = baseDir;
    }

    public static IdeWorkspaceStorage defaultInstance() {
        return new IdeWorkspaceStorage(Platform.getConfigFolder().resolve("almightly_staff").resolve("ide"));
    }

    /** 清洗 UUID，防止路径穿越或非法文件名字符。 */
    public static String sanitizeId(String staffUuid) {
        if (staffUuid == null || staffUuid.isBlank()) {
            return "default";
        }
        String cleaned = staffUuid.replaceAll("[^a-zA-Z0-9_-]", "_");
        return cleaned.isEmpty() ? "default" : cleaned;
    }

    public Path fileFor(String staffUuid) {
        return baseDir.resolve(sanitizeId(staffUuid) + ".json");
    }

    public WorkspaceState load(String staffUuid) {
        Path path = fileFor(staffUuid);
        if (!Files.exists(path)) {
            return WorkspaceState.createDefault();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            WorkspaceState loaded = GSON.fromJson(reader, WorkspaceState.class);
            if (loaded == null) {
                return WorkspaceState.createDefault();
            }
            loaded.sanitize(null);
            return loaded;
        } catch (Exception ignored) {
            return WorkspaceState.createDefault();
        }
    }

    public void save(String staffUuid, WorkspaceState state) {
        if (state == null) {
            return;
        }
        state.sanitize(null);
        Path target = fileFor(staffUuid);
        Path tmp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try {
            Files.createDirectories(baseDir);
            try (Writer writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(state, writer);
            }
            try {
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored2) {
            }
        }
    }
}
