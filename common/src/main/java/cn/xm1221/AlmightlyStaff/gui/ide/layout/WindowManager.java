package cn.xm1221.AlmightlyStaff.gui.ide.layout;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Z-order 浮动窗口管理器：底到顶渲染、顶到底命中、点击置顶、贴边吸附与工作区钳位。
 */
public final class WindowManager {
    private final List<FloatingWindow> windows = new ArrayList<>();
    @Nullable
    private FloatingWindow activeWindow;

    public List<FloatingWindow> windows() {
        return List.copyOf(windows);
    }

    @Nullable
    public FloatingWindow activeWindow() {
        return activeWindow;
    }

    @Nullable
    public FloatingWindow find(String id) {
        for (FloatingWindow w : windows) {
            if (w.id().equals(id)) return w;
        }
        return null;
    }

    public void add(FloatingWindow window) {
        if (window == null) return;
        windows.removeIf(w -> w.id().equals(window.id()));
        windows.add(window);
        activeWindow = window;
    }

    public void remove(String id) {
        windows.removeIf(w -> w.id().equals(id));
        if (activeWindow != null && activeWindow.id().equals(id)) {
            activeWindow = windows.isEmpty() ? null : windows.get(windows.size() - 1);
        }
    }

    public void bringToFront(FloatingWindow window) {
        if (window == null || !windows.contains(window)) return;
        windows.remove(window);
        windows.add(window);
        activeWindow = window;
    }

    public void clearFocus() {
        activeWindow = null;
    }

    public void clampAll(Bounds workArea) {
        for (FloatingWindow w : windows) {
            if (w.isMaximized()) {
                w.setBounds(workArea, workArea.right(), workArea.bottom(), workArea.y());
            } else {
                w.setBounds(w.bounds(), workArea.right(), workArea.bottom(), workArea.y());
            }
        }
    }

    public void tick() {
        for (FloatingWindow w : List.copyOf(windows)) {
            w.content().tick();
        }
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < windows.size(); i++) {
            FloatingWindow w = windows.get(i);
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(0.0F, 0.0F, 120.0F + i * 25.0F);
            try {
                w.render(g, font, mouseX, mouseY, partialTick, w == activeWindow);
            } finally {
                ps.popPose();
            }
        }
    }

    public boolean mouseClicked(double mx, double my, int button, Bounds workArea) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            FloatingWindow w = windows.get(i);
            if (w.contains(mx, my)) {
                bringToFront(w);
                return w.mouseClicked(mx, my, button, workArea);
            }
        }
        activeWindow = null;
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy, Bounds workArea) {
        if (activeWindow != null) {
            return activeWindow.mouseDragged(mx, my, button, dx, dy, workArea, windows);
        }
        return false;
    }

    public boolean mouseReleased(double mx, double my, int button) {
        if (activeWindow != null) {
            return activeWindow.mouseReleased(mx, my, button);
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            FloatingWindow w = windows.get(i);
            if (w.contains(mx, my)) {
                return w.mouseScrolled(mx, my, delta);
            }
        }
        return false;
    }

    public void mouseMoved(double mx, double my) {
        if (activeWindow != null) {
            activeWindow.content().mouseMoved(mx, my);
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (activeWindow != null) {
            return activeWindow.content().keyPressed(keyCode, scanCode, modifiers);
        }
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (activeWindow != null) {
            return activeWindow.content().charTyped(codePoint, modifiers);
        }
        return false;
    }
}
