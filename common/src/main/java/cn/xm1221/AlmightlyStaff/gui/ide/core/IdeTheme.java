package cn.xm1221.AlmightlyStaff.gui.ide.core;

import cn.xm1221.AlmightlyStaff.client.AlmightlyStaffClientConfig;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * 贴图与客户端配置驱动的 IDE 视觉主题（9-slice 面板/按钮/标签页 + 主题色板 + 可调边框粗细）。
 * 图集路径：{@code assets/almightly_staff/textures/gui/ide_widgets.png}（256x256）。
 */
public final class IdeTheme {
    private IdeTheme() {
    }

    public static final ResourceLocation WIDGETS_TEX =
        new ResourceLocation("almightly_staff", "textures/gui/ide_widgets.png");

    public enum ThemePreset {
        AMETHYST("amethyst",
            0xFF14181E, 0xFF1D2229, 0xFF252B34, 0xFF303744, 0xAA493662,
            0x331E252C, 0x332A3138, 0xFF11151B, 0xF018111F, 0xFF101318,
            0xFF1A1E24, 0x449C6ADE, 0xAA11161B, 0xAA315E5B,
            0xFF38404B, 0xFF4E5766, 0xFF9C6ADE, 0xFFD7B5FF, 0x339C6ADE,
            0xFFE2E6EA, 0xFF8A93A0, 0x66777777, 0xFFFFFFFF,
            0xFF8CE99A, 0xFFE8A82E, 0x33E8A82E, 0xFFFFD9A0, 0xFFE05555, 0xFF548AF7, 0xFF5CC8FF),
        OBSIDIAN("obsidian",
            0xFF0B0D11, 0xFF12151B, 0xFF1A1E26, 0xFF242A35, 0xAA263B5E,
            0x33141820, 0x331D232E, 0xFF090B0F, 0xF010141C, 0xFF080A0D,
            0xFF13171E, 0x445E9BFF, 0xAA0C0F14, 0xAA244968,
            0xFF2C3340, 0xFF404A5C, 0xFF5E9BFF, 0xFFA6C9FF, 0x335E9BFF,
            0xFFE8ECF2, 0xFF8792A4, 0x66778090, 0xFFFFFFFF,
            0xFF7CE8A6, 0xFFE8A82E, 0x33E8A82E, 0xFFFFDDA6, 0xFFE85D5D, 0xFF66A3FF, 0xFF6CD4FF),
        MIDNIGHT("midnight",
            0xFF0F1522, 0xFF161E2E, 0xFF1E283D, 0xFF27344E, 0xAA24486E,
            0x33182234, 0x33223047, 0xFF0C121D, 0xF0121928, 0xFF0B101A,
            0xFF151C2B, 0x444EA8DE, 0xAA0D1320, 0xAA22536E,
            0xFF32415E, 0xFF485B80, 0xFF4EA8DE, 0xFF96DAFF, 0x334EA8DE,
            0xFFE4EDF7, 0xFF8BA0BC, 0x667C8FA8, 0xFFFFFFFF,
            0xFF7BE8B0, 0xFFEAB338, 0x33EAB338, 0xFFFFE0A6, 0xFFEB636B, 0xFF69B4FF, 0xFF6CE5FF),
        EMERALD("emerald",
            0xFF0E1714, 0xFF14211C, 0xFF1C2C26, 0xFF253A32, 0xAA1F4D3C,
            0x3315241E, 0x331F322A, 0xFF0B1310, 0xF0101D18, 0xFF09100E,
            0xFF131E1A, 0x443ECF8E, 0xAA0B1411, 0xAA235E52,
            0xFF2E473E, 0xFF426659, 0xFF3ECF8E, 0xFF8BEAC0, 0x333ECF8E,
            0xFFE2EFEA, 0xFF85A396, 0x66738F83, 0xFFFFFFFF,
            0xFF8EF0A8, 0xFFE8B038, 0x33E8B038, 0xFFFFE0A3, 0xFFE56464, 0xFF5AA8F7, 0xFF64E2D6),
        CRIMSON("crimson",
            0xFF171114, 0xFF21181C, 0xFF2B2025, 0xFF382930, 0xAA5E2938,
            0x3324191E, 0x33312329, 0xFF120D0F, 0xF01D1418, 0xFF110C0E,
            0xFF1E161A, 0x44E05D74, 0xAA130E10, 0xAA5A3248,
            0xFF4A363F, 0xFF634855, 0xFFE05D74, 0xFFFF9CAE, 0x33E05D74,
            0xFFF0E4E7, 0xFFA38B92, 0x668F7980, 0xFFFFFFFF,
            0xFF86E39C, 0xFFEBA838, 0x33EBA838, 0xFFFFDEA3, 0xFFFF5C5C, 0xFF6D9BF7, 0xFF6FD0F0),
        PARCHMENT("parchment",
            0xFFEFE7D6, 0xFFE5DBC5, 0xFFDACFB5, 0xFFCFC1A3, 0x998E6BAE,
            0x44DDD2BB, 0x44D2C5AA, 0xFFF7F1E3, 0xF5EFE6D2, 0xFFF4EDE0,
            0xFFD6CCB4, 0x447B4B94, 0xAA9B8E76, 0x883D8B82,
            0xFFB3A384, 0xFF8E7E62, 0xFF7B4B94, 0xFF5D2D78, 0x337B4B94,
            0xFF28211C, 0xFF63574A, 0x8884796B, 0xFF28211C,
            0xFF267D42, 0xFFB87812, 0x33C88418, 0xFF7A4C05, 0xFFC23636, 0xFF2D66C8, 0xFF196E94);

        final String id;
        final int bgRoot, bgPanel, bgElevated, bgHover, bgActive;
        final int bgRowEven, bgRowOdd, bgInput, bgPopup, bgCanvas;
        final int bgBtnDisabled, dockPreviewFill, scrollbarTrack, selectionFill;
        final int border, borderStrong, accent, accentLight, accentSoft;
        final int textMain, textMuted, textDisabled, textOnCanvas;
        final int greenCast, goldSeal, goldSealFill, goldSealText, redError, blueHint, cyanNote;

        ThemePreset(
            String id,
            int bgRoot, int bgPanel, int bgElevated, int bgHover, int bgActive,
            int bgRowEven, int bgRowOdd, int bgInput, int bgPopup, int bgCanvas,
            int bgBtnDisabled, int dockPreviewFill, int scrollbarTrack, int selectionFill,
            int border, int borderStrong, int accent, int accentLight, int accentSoft,
            int textMain, int textMuted, int textDisabled, int textOnCanvas,
            int greenCast, int goldSeal, int goldSealFill, int goldSealText, int redError, int blueHint, int cyanNote
        ) {
            this.id = id;
            this.bgRoot = bgRoot;
            this.bgPanel = bgPanel;
            this.bgElevated = bgElevated;
            this.bgHover = bgHover;
            this.bgActive = bgActive;
            this.bgRowEven = bgRowEven;
            this.bgRowOdd = bgRowOdd;
            this.bgInput = bgInput;
            this.bgPopup = bgPopup;
            this.bgCanvas = bgCanvas;
            this.bgBtnDisabled = bgBtnDisabled;
            this.dockPreviewFill = dockPreviewFill;
            this.scrollbarTrack = scrollbarTrack;
            this.selectionFill = selectionFill;
            this.border = border;
            this.borderStrong = borderStrong;
            this.accent = accent;
            this.accentLight = accentLight;
            this.accentSoft = accentSoft;
            this.textMain = textMain;
            this.textMuted = textMuted;
            this.textDisabled = textDisabled;
            this.textOnCanvas = textOnCanvas;
            this.greenCast = greenCast;
            this.goldSeal = goldSeal;
            this.goldSealFill = goldSealFill;
            this.goldSealText = goldSealText;
            this.redError = redError;
            this.blueHint = blueHint;
            this.cyanNote = cyanNote;
        }

        public String id() {
            return id;
        }

        public Component displayName() {
            return Component.translatable("almightly_staff.config.theme." + id);
        }

        public int previewBg() {
            return bgPanel;
        }

        public int previewAccent() {
            return accent;
        }

        public int previewBorder() {
            return borderStrong;
        }

        public ThemePreset next() {
            ThemePreset[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public static ThemePreset fromId(String id) {
            if (id != null) {
                String norm = id.trim().toLowerCase(Locale.ROOT);
                for (ThemePreset preset : values()) {
                    if (preset.id.equals(norm)) {
                        return preset;
                    }
                }
            }
            return AMETHYST;
        }
    }

    private static ThemePreset activePreset = ThemePreset.AMETHYST;
    private static int activeBorderThickness = AlmightlyStaffClientConfig.DEFAULT_BORDER_THICKNESS;

    // ---- 当前活动色板（非 final，随客户端主题配置实时刷新） ----
    public static int BG_ROOT = activePreset.bgRoot;
    public static int BG_PANEL = activePreset.bgPanel;
    public static int BG_ELEVATED = activePreset.bgElevated;
    public static int BG_HOVER = activePreset.bgHover;
    public static int BG_ACTIVE = activePreset.bgActive;
    public static int BG_ROW_EVEN = activePreset.bgRowEven;
    public static int BG_ROW_ODD = activePreset.bgRowOdd;
    public static int BG_INPUT = activePreset.bgInput;
    public static int BG_POPUP = activePreset.bgPopup;
    public static int BG_CANVAS = activePreset.bgCanvas;
    public static int BG_BTN_DISABLED = activePreset.bgBtnDisabled;
    public static int DOCK_PREVIEW_FILL = activePreset.dockPreviewFill;
    public static int SCROLLBAR_TRACK = activePreset.scrollbarTrack;
    public static int SELECTION_FILL = activePreset.selectionFill;

    public static int BORDER = activePreset.border;
    public static int BORDER_STRONG = activePreset.borderStrong;
    public static int ACCENT = activePreset.accent;
    public static int ACCENT_LIGHT = activePreset.accentLight;
    public static int ACCENT_SOFT = activePreset.accentSoft;

    public static int TEXT_MAIN = activePreset.textMain;
    public static int TEXT_MUTED = activePreset.textMuted;
    public static int TEXT_DISABLED = activePreset.textDisabled;
    public static int TEXT_ON_CANVAS = activePreset.textOnCanvas;

    public static int GREEN_CAST = activePreset.greenCast;
    public static int GOLD_SEAL = activePreset.goldSeal;
    public static int GOLD_SEAL_FILL = activePreset.goldSealFill;
    public static int GOLD_SEAL_TEXT = activePreset.goldSealText;
    public static int RED_ERROR = activePreset.redError;
    public static int BLUE_HINT = activePreset.blueHint;
    public static int CYAN_NOTE = activePreset.cyanNote;

    public static final int SCROLLBAR_WIDTH = 6;
    public static final int MIN_THUMB_HEIGHT = 16;

    public static void applyConfig(AlmightlyStaffClientConfig config) {
        if (config == null) {
            applyPreset(ThemePreset.AMETHYST, AlmightlyStaffClientConfig.DEFAULT_BORDER_THICKNESS);
            return;
        }
        applyPreset(config.resolvedTheme(), config.borderThickness());
    }

    public static void applyPreset(ThemePreset preset, int borderThickness) {
        ThemePreset p = preset != null ? preset : ThemePreset.AMETHYST;
        activePreset = p;
        activeBorderThickness = AlmightlyStaffClientConfig.clampBorderThickness(borderThickness);

        BG_ROOT = p.bgRoot;
        BG_PANEL = p.bgPanel;
        BG_ELEVATED = p.bgElevated;
        BG_HOVER = p.bgHover;
        BG_ACTIVE = p.bgActive;
        BG_ROW_EVEN = p.bgRowEven;
        BG_ROW_ODD = p.bgRowOdd;
        BG_INPUT = p.bgInput;
        BG_POPUP = p.bgPopup;
        BG_CANVAS = p.bgCanvas;
        BG_BTN_DISABLED = p.bgBtnDisabled;
        DOCK_PREVIEW_FILL = p.dockPreviewFill;
        SCROLLBAR_TRACK = p.scrollbarTrack;
        SELECTION_FILL = p.selectionFill;

        BORDER = p.border;
        BORDER_STRONG = p.borderStrong;
        ACCENT = p.accent;
        ACCENT_LIGHT = p.accentLight;
        ACCENT_SOFT = p.accentSoft;

        TEXT_MAIN = p.textMain;
        TEXT_MUTED = p.textMuted;
        TEXT_DISABLED = p.textDisabled;
        TEXT_ON_CANVAS = p.textOnCanvas;

        GREEN_CAST = p.greenCast;
        GOLD_SEAL = p.goldSeal;
        GOLD_SEAL_FILL = p.goldSealFill;
        GOLD_SEAL_TEXT = p.goldSealText;
        RED_ERROR = p.redError;
        BLUE_HINT = p.blueHint;
        CYAN_NOTE = p.cyanNote;
    }

    public static ThemePreset activePreset() {
        return activePreset;
    }

    public static int borderThickness() {
        return activeBorderThickness;
    }

    /** 面板/窗口内部内容安全内边距，避免加粗边框遮挡子组件。 */
    public static int contentInset() {
        return Math.max(1, activeBorderThickness);
    }

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

    public static void drawNineSlice(GuiGraphics g, Bounds b, NineSlice style) {
        if (b == null || b.isEmpty()) return;
        drawNineSlice(g, b.x(), b.y(), b.w(), b.h(), style);
    }

    public static void drawNineSlice(GuiGraphics g, int x, int y, int w, int h, NineSlice style) {
        if (w <= 0 || h <= 0) return;
        // 仅在默认主题且边框为 1px 时使用原始 9-slice 贴图，其余主题或边框粗细走可配置色板与边框绘制
        if (activePreset == ThemePreset.AMETHYST && activeBorderThickness == 1 && hasTexture()) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            g.blitNineSliced(WIDGETS_TEX, x, y, w, h, style.slice, style.uw, style.vh, style.u, style.v);
            return;
        }
        int fill = switch (style) {
            case ROOT, TAB_ACTIVE -> BG_ROOT;
            case PANEL, FLOAT_INACTIVE, FLOAT_ACTIVE, TAB_INACTIVE -> BG_PANEL;
            case HEADER, BTN_NORMAL -> BG_ELEVATED;
            case INPUT -> BG_INPUT;
            case POPUP -> BG_POPUP;
            case DOCK_PREVIEW -> DOCK_PREVIEW_FILL;
            case BTN_HOVER -> BG_HOVER;
            case BTN_ACTIVE -> BG_ACTIVE;
            case BTN_DISABLED -> BG_BTN_DISABLED;
            case SEALED_BANNER -> GOLD_SEAL_FILL;
        };
        int border = switch (style) {
            case FLOAT_ACTIVE, POPUP, BTN_ACTIVE -> ACCENT;
            case FLOAT_INACTIVE -> BORDER_STRONG;
            case DOCK_PREVIEW -> ACCENT_LIGHT;
            case SEALED_BANNER -> GOLD_SEAL;
            default -> BORDER;
        };
        g.fill(x, y, x + w, y + h, fill);
        drawBorder(g, x, y, w, h, border);
    }

    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color) {
        drawBorder(g, x, y, w, h, color, activeBorderThickness);
    }

    public static void drawBorder(GuiGraphics g, int x, int y, int w, int h, int color, int thickness) {
        if (w <= 0 || h <= 0 || thickness <= 0) return;
        int t = Math.min(thickness, Math.min(w, h) / 2);
        if (t <= 0) t = 1;
        g.fill(x, y, x + w, y + t, color);
        g.fill(x, y + h - t, x + w, y + h, color);
        if (h > t * 2) {
            g.fill(x, y + t, x + t, y + h - t, color);
            g.fill(x + w - t, y + t, x + w, y + h - t, color);
        }
    }

    public static void drawBorder(GuiGraphics g, Bounds b, int color) {
        if (b == null || b.isEmpty()) return;
        drawBorder(g, b.x(), b.y(), b.w(), b.h(), color);
    }

    public static void drawPanel(GuiGraphics g, Bounds b, int fillColor, int borderColor) {
        if (b == null || b.isEmpty()) return;
        g.fill(b.x(), b.y(), b.right(), b.bottom(), fillColor);
        drawBorder(g, b, borderColor);
    }

    public static void drawScrollbar(GuiGraphics g, int trackX, int y, int h, int totalItems, int visibleItems, int scroll) {
        if (h <= 0) return;
        g.fill(trackX, y, trackX + SCROLLBAR_WIDTH, y + h, SCROLLBAR_TRACK);
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
