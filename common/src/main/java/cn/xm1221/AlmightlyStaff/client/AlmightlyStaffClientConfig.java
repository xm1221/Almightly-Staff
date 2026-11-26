package cn.xm1221.AlmightlyStaff.client;

import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.architectury.platform.Platform;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 万法之杖客户端外观与窗口配置，保存在 {@code config/almightly_staff_client.json}。
 */
public final class AlmightlyStaffClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String FILE_NAME = "almightly_staff_client.json";
    public static final int DEFAULT_SNAP_DISTANCE = 8;
    public static final int MIN_SNAP_DISTANCE = 0;
    public static final int MAX_SNAP_DISTANCE = 32;
    public static final int DEFAULT_BORDER_THICKNESS = 1;
    public static final int MIN_BORDER_THICKNESS = 0;
    public static final int MAX_BORDER_THICKNESS = 4;

    private static AlmightlyStaffClientConfig instance = new AlmightlyStaffClientConfig();

    /** 窗口移动/缩放时吸附工作区或其它浮动窗口边缘的像素阈值（0 表示关闭吸附）。 */
    public int windowSnapDistance = DEFAULT_SNAP_DISTANCE;
    /** IDE 与相关界面的整体配色主题标识。 */
    public String theme = IdeTheme.ThemePreset.AMETHYST.id();
    /** 面板、浮动窗与控件边框粗细（0..4 px）。 */
    public int borderThickness = DEFAULT_BORDER_THICKNESS;

    public static AlmightlyStaffClientConfig get() {
        return instance;
    }

    public int windowSnapDistance() {
        return clampSnapDistance(windowSnapDistance);
    }

    public int borderThickness() {
        return clampBorderThickness(borderThickness);
    }

    public IdeTheme.ThemePreset resolvedTheme() {
        return IdeTheme.ThemePreset.fromId(theme);
    }

    public void setWindowSnapDistance(int snapDistance) {
        this.windowSnapDistance = clampSnapDistance(snapDistance);
    }

    public void setBorderThickness(int borderThickness) {
        this.borderThickness = clampBorderThickness(borderThickness);
    }

    public void setTheme(IdeTheme.ThemePreset preset) {
        this.theme = (preset != null ? preset : IdeTheme.ThemePreset.AMETHYST).id();
    }

    public void resetDefaults() {
        this.windowSnapDistance = DEFAULT_SNAP_DISTANCE;
        this.theme = IdeTheme.ThemePreset.AMETHYST.id();
        this.borderThickness = DEFAULT_BORDER_THICKNESS;
    }

    public void sanitize() {
        this.windowSnapDistance = clampSnapDistance(this.windowSnapDistance);
        this.borderThickness = clampBorderThickness(this.borderThickness);
        this.theme = resolvedTheme().id();
    }

    public static int clampSnapDistance(int value) {
        return Math.max(MIN_SNAP_DISTANCE, Math.min(MAX_SNAP_DISTANCE, value));
    }

    public static int clampBorderThickness(int value) {
        return Math.max(MIN_BORDER_THICKNESS, Math.min(MAX_BORDER_THICKNESS, value));
    }

    public static void load() {
        loadFrom(defaultPath());
    }

    public static void loadFrom(Path path) {
        AlmightlyStaffClientConfig target = new AlmightlyStaffClientConfig();
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            if (Files.exists(path)) {
                try (Reader reader = Files.newBufferedReader(path)) {
                    AlmightlyStaffClientConfig parsed = GSON.fromJson(reader, AlmightlyStaffClientConfig.class);
                    if (parsed != null) {
                        target = parsed;
                    }
                }
            }
            target.sanitize();
            instance = target;
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException ignored) {
            target.sanitize();
            instance = target;
        }
        IdeTheme.applyConfig(instance);
    }

    public static void save() {
        saveTo(defaultPath());
    }

    public static void saveTo(Path path) {
        instance.sanitize();
        IdeTheme.applyConfig(instance);
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public static Path defaultPath() {
        return Platform.getConfigFolder().resolve(FILE_NAME);
    }
}
