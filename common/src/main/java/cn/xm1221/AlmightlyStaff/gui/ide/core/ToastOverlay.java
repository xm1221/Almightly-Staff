package cn.xm1221.AlmightlyStaff.gui.ide.core;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 右下角短时状态提示条（Toast）。
 */
public final class ToastOverlay {
    private String message = "";
    private int ticksRemaining;
    private int accentColor = IdeTheme.ACCENT;

    public void show(String msg) {
        show(msg, IdeTheme.ACCENT, 55);
    }

    public void showError(String msg) {
        show(msg, IdeTheme.RED_ERROR, 75);
    }

    public void show(String msg, int color, int ticks) {
        this.message = msg == null ? "" : msg;
        this.accentColor = color;
        this.ticksRemaining = this.message.isEmpty() ? 0 : Math.max(10, ticks);
    }

    public void tick() {
        if (ticksRemaining > 0) {
            ticksRemaining--;
        }
    }

    public void render(GuiGraphics g, Font font, int screenW, int screenH) {
        if (ticksRemaining <= 0 || message.isEmpty()) return;
        int maxW = Math.min(280, Math.max(80, screenW - 24));
        String txt = IdeTheme.trimText(font, message, maxW - 14);
        int w = Math.max(60, font.width(txt) + 14);
        int h = 18;
        int x = screenW - w - 10;
        int y = screenH - h - 24;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 680.0F);
        try {
            IdeTheme.drawNineSlice(g, x, y, w, h, IdeTheme.NineSlice.POPUP);
            IdeTheme.drawBorder(g, x, y, w, h, accentColor);
            g.drawString(font, txt, x + 7, y + 5, IdeTheme.TEXT_MAIN, false);
        } finally {
            ps.popPose();
        }
    }
}
