package cn.xm1221.AlmightlyStaff.client;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.gui.ide.layout.FloatingWindow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientConfigAndSnapTest {

    @AfterEach
    void resetThemeState() {
        AlmightlyStaffClientConfig.get().resetDefaults();
        IdeTheme.applyConfig(AlmightlyStaffClientConfig.get());
    }

    @Test
    void configRoundTripAndClamping(@TempDir Path tempDir) throws IOException {
        Path cfgPath = tempDir.resolve("almightly_staff_client.json");

        AlmightlyStaffClientConfig cfg = AlmightlyStaffClientConfig.get();
        cfg.setTheme(IdeTheme.ThemePreset.PARCHMENT);
        cfg.setWindowSnapDistance(18);
        cfg.setBorderThickness(3);
        AlmightlyStaffClientConfig.saveTo(cfgPath);

        assertTrue(Files.exists(cfgPath));

        // Reset in-memory state then reload from disk
        cfg.resetDefaults();
        IdeTheme.applyConfig(cfg);
        assertEquals(IdeTheme.ThemePreset.AMETHYST, IdeTheme.activePreset());
        assertEquals(1, IdeTheme.borderThickness());

        AlmightlyStaffClientConfig.loadFrom(cfgPath);
        AlmightlyStaffClientConfig reloaded = AlmightlyStaffClientConfig.get();
        assertEquals(IdeTheme.ThemePreset.PARCHMENT, reloaded.resolvedTheme());
        assertEquals(18, reloaded.windowSnapDistance());
        assertEquals(3, reloaded.borderThickness());
        assertEquals(IdeTheme.ThemePreset.PARCHMENT, IdeTheme.activePreset());
        assertEquals(3, IdeTheme.borderThickness());
        assertEquals(3, IdeTheme.contentInset());
        assertNotEquals(IdeTheme.ThemePreset.AMETHYST.previewBg(), IdeTheme.BG_PANEL);

        // Out-of-range & invalid theme values in JSON should be clamped cleanly
        Files.writeString(cfgPath, """
            {
              "windowSnapDistance": 999,
              "theme": "nonexistent_theme",
              "borderThickness": -4
            }
            """);
        AlmightlyStaffClientConfig.loadFrom(cfgPath);
        assertEquals(AlmightlyStaffClientConfig.MAX_SNAP_DISTANCE, AlmightlyStaffClientConfig.get().windowSnapDistance());
        assertEquals(AlmightlyStaffClientConfig.MIN_BORDER_THICKNESS, AlmightlyStaffClientConfig.get().borderThickness());
        assertEquals(IdeTheme.ThemePreset.AMETHYST, AlmightlyStaffClientConfig.get().resolvedTheme());
        assertEquals(0, IdeTheme.borderThickness());
        assertEquals(1, IdeTheme.contentInset());

        // Case-insensitive theme id should resolve properly
        Files.writeString(cfgPath, """
            {
              "windowSnapDistance": -10,
              "theme": "EMERALD",
              "borderThickness": 42
            }
            """);
        AlmightlyStaffClientConfig.loadFrom(cfgPath);
        assertEquals(0, AlmightlyStaffClientConfig.get().windowSnapDistance());
        assertEquals(IdeTheme.ThemePreset.EMERALD, AlmightlyStaffClientConfig.get().resolvedTheme());
        assertEquals(AlmightlyStaffClientConfig.MAX_BORDER_THICKNESS, AlmightlyStaffClientConfig.get().borderThickness());
    }

    @Test
    void snapDistanceControlsWorkAreaAndNeighborSnapping() {
        Bounds workArea = new Bounds(20, 20, 400, 300);
        Bounds neighbor = new Bounds(220, 60, 100, 100);

        // Within default 8px of workArea left edge -> snaps to x=20
        Bounds snappedWork = FloatingWindow.snapMoveBounds(26, 80, 120, 90, workArea, List.of(), 8);
        assertEquals(20, snappedWork.x());
        assertEquals(80, snappedWork.y());

        // With snapDistance=0 -> no snapping even 1px away
        Bounds unsnapped = FloatingWindow.snapMoveBounds(21, 80, 120, 90, workArea, List.of(neighbor), 0);
        assertEquals(21, unsnapped.x());
        assertEquals(80, unsnapped.y());

        // Large custom snap distance (16px) snaps right edge (90 + 120 = 210) to neighbor.x (220)
        Bounds largeSnap = FloatingWindow.snapMoveBounds(88, 65, 120, 90, workArea, List.of(neighbor), 16);
        assertEquals(100, largeSnap.x()); // 100 + 120 == 220 (neighbor.x)
        assertEquals(60, largeSnap.y());  // aligned to neighbor.y (60) within 5px

        // Same coordinates with tight snap distance (3px) stay unsnapped
        Bounds tightSnap = FloatingWindow.snapMoveBounds(88, 65, 120, 90, workArea, List.of(neighbor), 3);
        assertEquals(88, tightSnap.x());
        assertEquals(65, tightSnap.y());
    }
}
