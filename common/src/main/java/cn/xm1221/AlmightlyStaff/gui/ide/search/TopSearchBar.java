package cn.xm1221.AlmightlyStaff.gui.ide.search;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.FocusManager;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.spell.PatternRef;
import cn.xm1221.AlmightlyStaff.spell.StaffHex;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 顶部 26px 固定工具栏 + 全局 Search Everywhere（Ctrl+K / 双击 Shift 唤醒，最高 Z-order 悬浮结果窗）。
 */
public final class TopSearchBar implements IdeComponent {
    public static final int HEIGHT = 26;

    public record ActionButton(String id, IdeTheme.Icon icon, Component label, boolean primary, Runnable onClick) {
    }

    private final SearchIndex index;
    private final FocusManager focusManager;
    private final Runnable rebuildIndexCallback;
    private final List<ActionButton> actions = new ArrayList<>();

    private Bounds bounds = Bounds.EMPTY;
    private Bounds searchBoxBounds = Bounds.EMPTY;
    private final List<Bounds> actionBounds = new ArrayList<>();

    private String query = "";
    private final TextFieldHelper helper;
    private List<SearchIndex.Match> results = List.of();
    private int selectedIdx = 0;
    private boolean popupOpen = false;
    private long lastShiftReleaseMs = 0L;

    public TopSearchBar(SearchIndex index, FocusManager focusManager, Runnable rebuildIndexCallback) {
        this.index = index;
        this.focusManager = focusManager;
        this.rebuildIndexCallback = rebuildIndexCallback;
        this.helper = new TextFieldHelper(
            () -> query,
            s -> {
                query = s;
                refreshResults();
            },
            TextFieldHelper.createClipboardGetter(Minecraft.getInstance()),
            TextFieldHelper.createClipboardSetter(Minecraft.getInstance()),
            s -> s.length() <= 100
        );
    }

    public void addAction(ActionButton button) {
        actions.add(button);
    }

    public boolean isPopupOpen() {
        return popupOpen;
    }

    public void openSearch() {
        rebuildIndexCallback.run();
        focusManager.focus(this);
        popupOpen = true;
        refreshResults();
        helper.selectAll();
    }

    public void closeSearch() {
        popupOpen = false;
        if (focusManager.isFocused(this)) {
            focusManager.clear();
        }
    }

    /** 追踪双击 Shift（300ms 内连续两次松开 Shift 且无输入焦点冲突）。 */
    public boolean onShiftKeyEvent(int keyCode, int action) {
        if (keyCode != GLFW.GLFW_KEY_LEFT_SHIFT && keyCode != GLFW.GLFW_KEY_RIGHT_SHIFT) return false;
        if (action == GLFW.GLFW_RELEASE) {
            long now = System.currentTimeMillis();
            if (now - lastShiftReleaseMs <= 300L) {
                lastShiftReleaseMs = 0L;
                openSearch();
                return true;
            }
            lastShiftReleaseMs = now;
        }
        return false;
    }

    private void refreshResults() {
        results = index.search(query, 10);
        selectedIdx = 0;
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public void layout(Bounds bounds) {
        this.bounds = bounds;
        Font font = Minecraft.getInstance().font;
        actionBounds.clear();

        int rightX = bounds.right() - 6;
        List<Bounds> reversed = new ArrayList<>();
        for (int i = actions.size() - 1; i >= 0; i--) {
            ActionButton ab = actions.get(i);
            int w = font.width(ab.label()) + 20;
            int bx = rightX - w;
            reversed.add(new Bounds(bx, bounds.y() + 4, w, 18));
            rightX = bx - 4;
        }
        for (int i = reversed.size() - 1; i >= 0; i--) {
            actionBounds.add(reversed.get(i));
        }

        int brandW = 112;
        int sx = bounds.x() + brandW;
        int maxRight = Math.max(sx + 80, rightX - 6);
        int sw = Math.min(340, Math.max(80, maxRight - sx));
        searchBoxBounds = new Bounds(sx, bounds.y() + 4, sw, 18);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (bounds.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), IdeTheme.BG_PANEL);
        g.fill(bounds.x(), bounds.bottom() - 1, bounds.right(), bounds.bottom(), IdeTheme.BORDER);

        try (IdeScissor.Scope sc = IdeScissor.push(g, bounds)) {
            // Brand
            g.fill(bounds.x() + 8, bounds.y() + 8, bounds.x() + 16, bounds.y() + 16, IdeTheme.ACCENT);
            g.fill(bounds.x() + 10, bounds.y() + 10, bounds.x() + 14, bounds.y() + 14, IdeTheme.ACCENT_LIGHT);
            g.drawString(font, Component.translatable("almightly_staff.ide.brand"),
                bounds.x() + 20, bounds.y() + 9, IdeTheme.TEXT_MAIN, false);

            // Search Input
            boolean focused = focusManager.isFocused(this);
            g.fill(searchBoxBounds.x(), searchBoxBounds.y(), searchBoxBounds.right(), searchBoxBounds.bottom(), IdeTheme.BG_ROOT);
            IdeTheme.drawBorder(g, searchBoxBounds, focused ? IdeTheme.ACCENT : IdeTheme.BORDER_STRONG);
            IdeTheme.drawIcon(g, IdeTheme.Icon.SEARCH_GLASS, searchBoxBounds.x() + 5, searchBoxBounds.y() + 5, IdeTheme.TEXT_MUTED);

            try (IdeScissor.Scope s2 = IdeScissor.push(g, searchBoxBounds.inset(15, 2, 34, 2))) {
                String shown = query.isEmpty() && !focused
                    ? Component.translatable("almightly_staff.ide.search_placeholder").getString()
                    : query;
                int col = query.isEmpty() && !focused ? IdeTheme.TEXT_MUTED : IdeTheme.TEXT_MAIN;
                g.drawString(font, shown, searchBoxBounds.x() + 16, searchBoxBounds.y() + 5, col, false);
                if (focused) {
                    int cur = Math.min(helper.getCursorPos(), query.length());
                    int cx = searchBoxBounds.x() + 16 + font.width(query.substring(0, cur));
                    g.fill(cx, searchBoxBounds.y() + 3, cx + 1, searchBoxBounds.bottom() - 3, IdeTheme.ACCENT_LIGHT);
                }
            }
            g.drawString(font, "Ctrl+K", searchBoxBounds.right() - 33, searchBoxBounds.y() + 5, IdeTheme.TEXT_MUTED, false);

            // Action Buttons
            for (int i = 0; i < actions.size() && i < actionBounds.size(); i++) {
                ActionButton ab = actions.get(i);
                Bounds abBounds = actionBounds.get(i);
                boolean hov = abBounds.contains(mouseX, mouseY);
                int fill = ab.primary()
                    ? (hov ? 0x669C6ADE : IdeTheme.ACCENT_SOFT)
                    : (hov ? IdeTheme.BG_HOVER : IdeTheme.BG_ELEVATED);
                int border = ab.primary() ? IdeTheme.ACCENT : IdeTheme.BORDER_STRONG;
                g.fill(abBounds.x(), abBounds.y(), abBounds.right(), abBounds.bottom(), fill);
                IdeTheme.drawBorder(g, abBounds, border);
                IdeTheme.drawIcon(g, ab.icon(), abBounds.x() + 5, abBounds.y() + 5,
                    ab.primary() ? IdeTheme.ACCENT_LIGHT : IdeTheme.TEXT_MAIN);
                g.drawString(font, ab.label(), abBounds.x() + 16, abBounds.y() + 5, IdeTheme.TEXT_MAIN, false);
            }
        }
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!popupOpen || !focusManager.isFocused(this)) return;
        Font font = Minecraft.getInstance().font;
        int popX = searchBoxBounds.x();
        int popY = searchBoxBounds.bottom() + 2;
        int popW = Math.max(260, searchBoxBounds.w());
        int rowH = 20;
        int count = Math.max(1, results.size());
        int popH = count * rowH + 4;

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 660.0F);
        try {
            g.fill(popX - 1, popY - 1, popX + popW + 1, popY + popH + 1, 0xEE080A0D);
            g.fill(popX, popY, popX + popW, popY + popH, IdeTheme.BG_PANEL);
            IdeTheme.drawBorder(g, popX, popY, popW, popH, IdeTheme.ACCENT);

            if (results.isEmpty()) {
                g.drawString(font, Component.translatable("almightly_staff.ide.search_empty"),
                    popX + 8, popY + 6, IdeTheme.TEXT_MUTED, false);
                return;
            }
            for (int i = 0; i < results.size(); i++) {
                SearchIndex.Entry e = results.get(i).entry();
                int ry = popY + 2 + i * rowH;
                boolean sel = i == selectedIdx;
                boolean hov = mouseX >= popX && mouseX < popX + popW && mouseY >= ry && mouseY < ry + rowH;
                if (sel || hov) {
                    g.fill(popX + 1, ry, popX + popW - 1, ry + rowH, IdeTheme.BG_ACTIVE);
                }
                int textX = popX + 8;
                if (e.kind() == SearchIndex.Kind.PATTERN && !e.patternSig().isBlank()) {
                    StaffHex.renderPattern(new PatternRef(e.patternActionId(), e.patternDir(), e.patternSig()),
                        ps, popX + 4, ry + 2, 14);
                    textX = popX + 24;
                }
                String tag = kindLabel(e.kind());
                int tagW = font.width(tag) + 8;
                g.fill(popX + popW - tagW - 6, ry + 3, popX + popW - 6, ry + rowH - 3, IdeTheme.BG_ELEVATED);
                g.drawString(font, tag, popX + popW - tagW - 2, ry + 6, IdeTheme.TEXT_MUTED, false);

                int availW = Math.max(20, popW - tagW - (textX - popX) - 14);
                g.drawString(font, IdeTheme.trimText(font, e.title(), availW), textX, ry + 6, IdeTheme.TEXT_MAIN, false);
            }
        } finally {
            ps.popPose();
        }
    }

    private static String kindLabel(SearchIndex.Kind k) {
        return switch (k) {
            case PAGE -> Component.translatable("almightly_staff.ide.kind_page").getString();
            case PATTERN -> Component.translatable("almightly_staff.ide.kind_pattern").getString();
            case NOTE -> Component.translatable("almightly_staff.ide.kind_note").getString();
            case COMMAND -> Component.translatable("almightly_staff.ide.kind_command").getString();
        };
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (popupOpen && focusManager.isFocused(this)) {
            int popX = searchBoxBounds.x();
            int popY = searchBoxBounds.bottom() + 2;
            int popW = Math.max(260, searchBoxBounds.w());
            int rowH = 20;
            int popH = Math.max(1, results.size()) * rowH + 4;
            if (mx >= popX && mx < popX + popW && my >= popY && my < popY + popH) {
                int idx = (int) ((my - popY - 2) / rowH);
                if (idx >= 0 && idx < results.size()) {
                    executeMatch(results.get(idx).entry());
                }
                return true;
            }
        }
        if (!bounds.contains(mx, my)) {
            if (popupOpen) closeSearch();
            return false;
        }
        if (searchBoxBounds.contains(mx, my)) {
            openSearch();
            return true;
        }
        if (button == 0) {
            for (int i = 0; i < actionBounds.size() && i < actions.size(); i++) {
                if (actionBounds.get(i).contains(mx, my)) {
                    actions.get(i).onClick().run();
                    return true;
                }
            }
        }
        return true;
    }

    private void executeMatch(SearchIndex.Entry entry) {
        closeSearch();
        if (entry != null && entry.action() != null) {
            entry.action().run();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focusManager.isFocused(this)) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeSearch();
            return true;
        }
        if (popupOpen && !results.isEmpty()) {
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                selectedIdx = (selectedIdx + 1) % results.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                selectedIdx = (selectedIdx - 1 + results.size()) % results.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                executeMatch(results.get(Mth.clamp(selectedIdx, 0, results.size() - 1)).entry());
                return true;
            }
        }
        return helper.keyPressed(keyCode);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!focusManager.isFocused(this)) return false;
        popupOpen = true;
        return helper.charTyped(codePoint);
    }

    @Override
    public void onFocusLost() {
        popupOpen = false;
    }
}
