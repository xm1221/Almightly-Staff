package cn.xm1221.AlmightlyStaff.gui.ide.core;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * IDE 视觉主题常量与整数像素绘制工具（延续 StaffLibScreen 深板岩 + 紫水晶风格）。
 * 严禁依赖系统 Emoji，图标全部像素绘制。
 */
public final class IdeTheme {
    private IdeTheme() {
    }

    // ---- 色板 ----
    public static final int BG_ROOT = 0xFF14181E;
    public static final int BG_PANEL = 0xFF1D2229;
    public static final int BG_ELEVATED = 0xFF252B34;
    public static final int BG_HOVER = 0xFF303744;
    public static final int BG_ACTIVE = 0xAA493662;
    public static final int BG_ROW_EVEN = 0x331E252C;
    public static final int BG_ROW_ODD = 0x332A3138;

    public static final int BORDER = 0xFF38404B;
    public static final int BORDER_STRONG = 0xFF4E5766;
    public static final int ACCENT = 0xFF9C6ADE;
    public static final int ACCENT_LIGHT = 0xFFD7B5FF;
    public static final int ACCENT_SOFT = 0x339C6ADE;

    public static final int TEXT_MAIN = 0xFFE2E6EA;
    public static final int TEXT_MUTED = 0xFF8A93A0;
    public static final int TEXT_DISABLED = 0x66777777;

    public static final int GREEN_CAST = 0xFF8CE99A;
    public static final int GOLD_SEAL = 0xFFE8A82E;
    public static final int GOLD_SEAL_FILL = 0x33E8A82E;
    public static final int GOLD_SEAL_TEXT = 0xFFFFD9A0;
    public static final int RED_ERROR = 0xFFE05555;
    public static final int BLUE_HINT = 0xFF548AF7;
    public static final int CYAN_NOTE = 0xFF5CC8FF;

    public static final int SCROLLBAR_WIDTH = 6;
    public static final int MIN_THUMB_HEIGHT = 16;

    public enum Icon {
        FOLDER_CLOSED,
        FOLDER_OPEN,
        SPELL_STAR,
        NOTE_DOC,
        BOOK_EXTERNAL,
        PADLOCK,
        SPLIT_VIEW,
        FLOAT_WIN,
        DOCK_BACK,
        CLOSE_X,
        SEARCH_GLASS,
        SAVE_DISK,
        PLAY_CAST
    }

    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void drawBorder(GuiGraphics g, Bounds b, int color) {
        drawBorder(g, b.x(), b.y(), b.w(), b.h(), color);
    }

    public static void drawPanel(GuiGraphics g, Bounds b, int fillColor, int borderColor) {
        if (b.isEmpty()) return;
        g.fill(b.x(), b.y(), b.right(), b.bottom(), fillColor);
        drawBorder(g, b, borderColor);
    }

    public static void drawScrollbar(GuiGraphics g, int trackX, int y, int h, int totalItems, int visibleItems, int scroll) {
        if (h <= 0) return;
        g.fill(trackX, y, trackX + SCROLLBAR_WIDTH, y + h, 0xAA11161B);
        int maxScroll = Math.max(0, totalItems - visibleItems);
        if (maxScroll <= 0) return;
        int thumb = Math.max(MIN_THUMB_HEIGHT, h * Math.max(1, visibleItems) / Math.max(1, totalItems));
        thumb = Math.min(h, thumb);
        int ty = y + (int) ((long) scroll * (h - thumb) / Math.max(1, maxScroll));
        g.fill(trackX + 1, ty, trackX + SCROLLBAR_WIDTH - 1, ty + thumb, ACCENT);
    }

    public static String trimText(Font font, String text, int maxWidth) {
        if (text == null || maxWidth <= 0) return "";
        if (font.width(text) <= maxWidth) return text;
        String ellipsis = "..";
        int ew = font.width(ellipsis);
        if (maxWidth <= ew) return "";
        String s = text;
        while (s.length() > 1 && font.width(s) + ew > maxWidth) {
            s = s.substring(0, s.length() - 1);
        }
        return s + ellipsis;
    }

    public static void drawClippedString(GuiGraphics g, Font font, Component text, int x, int y, int maxWidth, int color) {
        g.drawString(font, trimText(font, text.getString(), maxWidth), x, y, color, false);
    }

    /** 绘制 8x8 整数像素图标，绝不依赖系统 Emoji 字体。 */
    public static void drawIcon(GuiGraphics g, Icon icon, int x, int y, int color) {
        switch (icon) {
            case FOLDER_CLOSED -> {
                g.fill(x, y + 1, x + 4, y + 2, color);
                g.fill(x, y + 2, x + 8, y + 7, color);
                g.fill(x + 1, y + 3, x + 7, y + 6, BG_PANEL);
            }
            case FOLDER_OPEN -> {
                g.fill(x, y + 1, x + 4, y + 2, color);
                g.fill(x, y + 2, x + 8, y + 4, color);
                g.fill(x + 1, y + 4, x + 8, y + 7, color);
            }
            case SPELL_STAR -> {
                g.fill(x + 3, y, x + 5, y + 8, color);
                g.fill(x, y + 3, x + 8, y + 5, color);
                g.fill(x + 2, y + 2, x + 6, y + 6, color);
            }
            case NOTE_DOC -> {
                g.fill(x + 1, y, x + 7, y + 8, color);
                g.fill(x + 2, y + 1, x + 6, y + 7, BG_PANEL);
                g.fill(x + 3, y + 2, x + 6, y + 3, color);
                g.fill(x + 3, y + 4, x + 6, y + 5, color);
            }
            case BOOK_EXTERNAL -> {
                g.fill(x + 1, y + 1, x + 7, y + 7, color);
                g.fill(x + 2, y + 2, x + 4, y + 6, BG_PANEL);
                g.fill(x + 5, y + 2, x + 6, y + 6, BG_PANEL);
            }
            case PADLOCK -> {
                g.fill(x + 2, y, x + 6, y + 1, color);
                g.fill(x + 2, y + 1, x + 3, y + 4, color);
                g.fill(x + 5, y + 1, x + 6, y + 4, color);
                g.fill(x + 1, y + 4, x + 7, y + 8, color);
                g.fill(x + 3, y + 5, x + 5, y + 7, 0xFF2A2114);
            }
            case SPLIT_VIEW -> {
                drawBorder(g, x, y + 1, 8, 6, color);
                g.fill(x + 3, y + 1, x + 5, y + 7, color);
            }
            case FLOAT_WIN -> {
                drawBorder(g, x + 2, y, 6, 5, color);
                g.fill(x, y + 3, x + 5, y + 8, color);
                g.fill(x + 1, y + 4, x + 4, y + 7, BG_PANEL);
            }
            case DOCK_BACK -> {
                drawBorder(g, x, y + 1, 8, 6, color);
                g.fill(x + 1, y + 2, x + 3, y + 6, color);
            }
            case CLOSE_X -> {
                for (int i = 1; i <= 6; i++) {
                    g.fill(x + i, y + i, x + i + 1, y + i + 1, color);
                    g.fill(x + 7 - i, y + i, x + 8 - i, y + i + 1, color);
                }
            }
            case SEARCH_GLASS -> {
                drawBorder(g, x + 1, y + 1, 5, 5, color);
                g.fill(x + 5, y + 5, x + 6, y + 6, color);
                g.fill(x + 6, y + 6, x + 8, y + 8, color);
            }
            case SAVE_DISK -> {
                g.fill(x + 1, y + 1, x + 7, y + 7, color);
                g.fill(x + 2, y + 1, x + 6, y + 3, BG_PANEL);
                g.fill(x + 2, y + 4, x + 6, y + 7, BG_PANEL);
            }
            case PLAY_CAST -> {
                g.fill(x + 1, y + 1, x + 3, y + 7, color);
                g.fill(x + 3, y + 2, x + 5, y + 6, color);
                g.fill(x + 5, y + 3, x + 7, y + 5, color);
            }
        }
    }
}
