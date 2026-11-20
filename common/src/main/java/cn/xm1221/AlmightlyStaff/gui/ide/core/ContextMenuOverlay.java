package cn.xm1221.AlmightlyStaff.gui.ide.core;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 最高 Z-order 通用右键上下文菜单（遵循 StaffLibScreen B17：打开时吞掉首次点击与 ESC）。
 */
public final class ContextMenuOverlay {
    public record MenuItem(Component label, boolean enabled, Runnable action) {
    }

    private static final int ITEM_HEIGHT = 16;
    private static final int MIN_WIDTH = 104;

    private final List<MenuItem> items = new ArrayList<>();
    private boolean open;
    private int x, y, width, height;

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
        items.clear();
    }

    public void open(int mx, int my, int screenW, int screenH, Font font, List<MenuItem> entries) {
        items.clear();
        if (entries == null || entries.isEmpty()) {
            open = false;
            return;
        }
        items.addAll(entries);
        int w = MIN_WIDTH;
        for (MenuItem item : items) {
            w = Math.max(w, font.width(item.label()) + 16);
        }
        width = Math.min(w, Math.max(60, screenW - 8));
        height = items.size() * ITEM_HEIGHT + 2;
        x = Math.max(2, Math.min(mx, screenW - width - 2));
        y = Math.max(2, Math.min(my, screenH - height - 2));
        open = true;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY) {
        if (!open) return;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 700.0F);
        try {
            g.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xEE080A0D);
            IdeTheme.drawNineSlice(g, x, y, width, height, IdeTheme.NineSlice.POPUP);
            for (int i = 0; i < items.size(); i++) {
                MenuItem item = items.get(i);
                int iy = y + 1 + i * ITEM_HEIGHT;
                boolean hover = mouseX >= x && mouseX < x + width && mouseY >= iy && mouseY < iy + ITEM_HEIGHT;
                if (hover && item.enabled()) {
                    g.fill(x + 1, iy, x + width - 1, iy + ITEM_HEIGHT, IdeTheme.BG_ACTIVE);
                }
                int color = item.enabled() ? IdeTheme.TEXT_MAIN : IdeTheme.TEXT_DISABLED;
                IdeTheme.drawClippedString(g, font, item.label(), x + 6, iy + 4, width - 12, color);
            }
        } finally {
            ps.popPose();
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!open) return false;
        if (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height) {
            int idx = (int) ((mouseY - y - 1) / ITEM_HEIGHT);
            MenuItem chosen = (idx >= 0 && idx < items.size()) ? items.get(idx) : null;
            close();
            if (chosen != null && chosen.enabled() && chosen.action() != null) {
                chosen.action().run();
            }
            return true;
        }
        close();
        return true; // 吞掉关闭菜单的那次点击
    }

    public boolean keyPressed(int keyCode) {
        if (!open) return false;
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }
}
