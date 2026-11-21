package cn.xm1221.AlmightlyStaff.gui.ide.core;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 贴图驱动的 IDE 视觉主题（9-slice 面板/按钮/标签页 + 可着色 8x8 像素图标图集）。
 * 图集路径：{@code assets/almightly_staff/textures/gui/ide_widgets.png}（256x256）。
 */
public final class IdeTheme {
    private IdeTheme() {
    }

    public static final ResourceLocation WIDGETS_TEX =
        ResourceLocation.fromNamespaceAndPath("almightly_staff", "textures/gui/ide_widgets.png");

    // ---- 色板（用于文字、代码高亮及贴图缺失兜底） ----
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

    public enum NineSlice {
        ROOT(0, 0, 32, 32, 3),
        PANEL(32, 0, 32, 32, 3),
        FLOAT_INACTIVE(64, 0, 32, 32, 3),
        FLOAT_ACTIVE(96, 0, 32, 32, 3),
        HEADER(128, 0, 32, 32, 3),
        INPUT(160, 0, 32, 32, 3),
        POPUP(192, 0, 32, 32, 3),
        DOCK_PREVIEW(224, 0, 32, 32, 3),
        BTN_NORMAL(0, 32, 32, 18, 3),
        BTN_HOVER(32, 32, 32, 18, 3),
        BTN_ACTIVE(64, 32, 32, 18, 3),
        BTN_DISABLED(96, 32, 32, 18, 3),
        TAB_ACTIVE(128, 32, 32, 20, 3),
        TAB_INACTIVE(160, 32, 32, 20, 3),
        SEALED_BANNER(192, 32, 32, 20, 3);

        final int u, v, uw, vh, slice;

        NineSlice(int u, int v, int uw, int vh, int slice) {
            this.u = u;
            this.v = v;
            this.uw = uw;
            this.vh = vh;
            this.slice = slice;
        }
    }

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
        PLAY_CAST,
        MAXIMIZE_BOX,
        RESTORE_BOX,
        MINIMIZE_BAR,
        HEX_GRID_ICON(0, 80, 14),
        HEX_PARSE_ICON,
        PLUS_ICON,
        COPY_ICON,
        TRASH_ICON,
        MORE_DOTS,
        CHECK_APPLY,
        CENTER_RESET,
        CATALOG_LIST,
        STAFF_LOGO(0, 80, 14);

        final int u;
        final int v;
        final int size;

        Icon() {
            this.u = ordinal() * 10;
            this.v = 64;
            this.size = 8;
        }

        Icon(int u, int v, int size) {
            this.u = u;
            this.v = v;
            this.size = size;
        }

        public int size() {
            return size;
        }
    }

    private static Boolean cachedHasTex;

    public static boolean hasTexture() {
        if (cachedHasTex != null) return cachedHasTex;
        try {
            cachedHasTex = Minecraft.getInstance().getResourceManager().getResource(WIDGETS_TEX).isPresent();
        } catch (Throwable t) {
            cachedHasTex = false;
        }
        return cachedHasTex;
    }


    /**
     * 用 9 个普通 blit 拼出九宫格（替代 1.21.1 已移除的 GuiGraphics#blitNineSliced）。
     * 源图块 style.uw x style.vh 的四周各切 style.slice 像素作为边框/角。
     */
    private static void blitNineSlice(GuiGraphics g, int x, int y, int w, int h, NineSlice style) {
        final int s = style.slice;
        final int uw = style.uw;
        final int vh = style.vh;
        final int u = style.u;
        final int v = style.v;
        final int sw = uw - s * 2;
        final int sh = vh - s * 2;
        final int dw = w - s * 2;
        final int dh = h - s * 2;
        if (dw < 0 || dh < 0 || sw <= 0 || sh <= 0) {
            // 太小：直接拉伸整块，避免负尺寸
            g.blit(WIDGETS_TEX, x, y, w, h, u, v, uw, vh, 256, 256);
            return;
        }
        // 四角
        g.blit(WIDGETS_TEX, x, y, s, s, u, v, s, s, 256, 256);
        g.blit(WIDGETS_TEX, x + w - s, y, s, s, u + uw - s, v, s, s, 256, 256);
        g.blit(WIDGETS_TEX, x, y + h - s, s, s, u, v + vh - s, s, s, 256, 256);
        g.blit(WIDGETS_TEX, x + w - s, y + h - s, s, s, u + uw - s, v + vh - s, s, s, 256, 256);
        // 四边
        g.blit(WIDGETS_TEX, x + s, y, dw, s, u + s, v, sw, s, 256, 256);
        g.blit(WIDGETS_TEX, x + s, y + h - s, dw, s, u + s, v + vh - s, sw, s, 256, 256);
        g.blit(WIDGETS_TEX, x, y + s, s, dh, u, v + s, s, sh, 256, 256);
        g.blit(WIDGETS_TEX, x + w - s, y + s, s, dh, u + uw - s, v + s, s, sh, 256, 256);
        // 中心
        g.blit(WIDGETS_TEX, x + s, y + s, dw, dh, u + s, v + s, sw, sh, 256, 256);
    }

    public static void drawNineSlice(GuiGraphics g, Bounds b, NineSlice style) {
        if (b == null || b.isEmpty()) return;
        drawNineSlice(g, b.x(), b.y(), b.w(), b.h(), style);
    }

    public static void drawNineSlice(GuiGraphics g, int x, int y, int w, int h, NineSlice style) {
        if (w <= 0 || h <= 0) return;
        if (hasTexture()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            // 1.21.1 移除了 GuiGraphics.blitNineSliced(ResourceLocation,...)（只剩需要
            // GuiSpriteScaling.NineSlice 的 blitNineSlicedSprite，且图集必须是 sprite），
            // 这里用普通 blit 手工拼九宫格，行为与原来一致。
            blitNineSlice(g, x, y, w, h, style);
            return;
        }
        int fill = switch (style) {
            case ROOT, TAB_ACTIVE -> BG_ROOT;
            case PANEL, FLOAT_INACTIVE, FLOAT_ACTIVE, TAB_INACTIVE -> BG_PANEL;
            case HEADER, BTN_NORMAL -> BG_ELEVATED;
            case INPUT -> 0xFF11151B;
            case POPUP -> 0xF018111F;
            case DOCK_PREVIEW -> 0x449C6ADE;
            case BTN_HOVER -> BG_HOVER;
            case BTN_ACTIVE -> BG_ACTIVE;
            case BTN_DISABLED -> 0xFF1A1E24;
            case SEALED_BANNER -> GOLD_SEAL_FILL;
        };
        int border = switch (style) {
            case FLOAT_ACTIVE, POPUP, BTN_ACTIVE -> ACCENT;
            case DOCK_PREVIEW -> ACCENT_LIGHT;
            case SEALED_BANNER -> GOLD_SEAL;
            default -> BORDER;
        };
        g.fill(x, y, x + w, y + h, fill);
        drawBorder(g, x, y, w, h, border);
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

    /** 从 256x256 图集绘制可着色像素图标（大尺寸图标自动以 8x8 锚点为中心对齐）。 */
    public static void drawIcon(GuiGraphics g, Icon icon, int x, int y, int color) {
        if (hasTexture()) {
            float a = ((color >>> 24) & 0xFF) / 255.0F;
            if (a <= 0.01F) a = 1.0F;
            float r = ((color >>> 16) & 0xFF) / 255.0F;
            float gr = ((color >>> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            int off = (icon.size - 8) / 2;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(r, gr, b, a);
            g.blit(WIDGETS_TEX, x - off, y - off, icon.u, icon.v, icon.size, icon.size);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            return;
        }
        g.fill(x + 1, y + 1, x + 7, y + 7, color);
    }
}
