package cn.xm1221.AlmightlyStaff.gui.ide.tool;

import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import cn.xm1221.AlmightlyStaff.gui.StaffBookData;
import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.FocusManager;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.gui.ide.model.IdeSession;
import cn.xm1221.AlmightlyStaff.gui.ide.model.SpellDocument;
import cn.xm1221.AlmightlyStaff.spell.PatternCatalogEntry;
import cn.xm1221.AlmightlyStaff.spell.PatternRef;
import cn.xm1221.AlmightlyStaff.spell.StaffHex;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 右侧图案目录面板（迁自 StaffLibScreen 中栏）：过滤搜索、StaffBookData 详情浮窗、双击插入与拖拽到序列。
 */
public final class PatternCatalogPanel implements IdeComponent {
    private static final int HEADER_H = 20;
    private static final int SEARCH_H = 18;
    private static final int ROW_H = 18;
    private static final long DOUBLE_CLICK_MS = 280L;

    private final IdeSession session;
    private final FocusManager focusManager;
    private final Supplier<SpellDocument> activeDocSupplier;
    private final Consumer<PatternCatalogEntry> onInsertPattern;
    private final BiConsumer<PatternCatalogEntry, double[]> onDropPattern;

    private Bounds bounds = Bounds.EMPTY;
    private String query = "";
    private final TextFieldHelper searchHelper;
    private List<PatternCatalogEntry> filtered = new ArrayList<>();
    private int scroll;
    private int selectedIndex = -1;
    private long lastClickMs;
    private int lastClickRow = -1;

    private boolean pendingDrag;
    private boolean activeDrag;
    private int dragIndex = -1;
    private double dragStartX, dragStartY, dragMouseX, dragMouseY;

    public PatternCatalogPanel(IdeSession session, FocusManager focusManager,
                               Supplier<SpellDocument> activeDocSupplier,
                               Consumer<PatternCatalogEntry> onInsertPattern,
                               BiConsumer<PatternCatalogEntry, double[]> onDropPattern) {
        this.session = session;
        this.focusManager = focusManager;
        this.activeDocSupplier = activeDocSupplier;
        this.onInsertPattern = onInsertPattern;
        this.onDropPattern = onDropPattern;
        this.searchHelper = new TextFieldHelper(
            () -> query,
            s -> {
                query = s;
                refreshFilter();
            },
            TextFieldHelper.createClipboardGetter(Minecraft.getInstance()),
            TextFieldHelper.createClipboardSetter(Minecraft.getInstance()),
            s -> s.length() <= 64
        );
        refreshFilter();
    }

    public void refreshFilter() {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<PatternCatalogEntry> next = new ArrayList<>();
        for (PatternCatalogEntry e : session.catalog()) {
            if (q.isEmpty()
                || e.displayName().getString().toLowerCase(Locale.ROOT).contains(q)
                || e.pattern().actionId().toLowerCase(Locale.ROOT).contains(q)
                || e.pattern().signature().contains(q)) {
                next.add(e);
            }
        }
        filtered = next;
        if (selectedIndex >= filtered.size()) selectedIndex = -1;
    }

    public void focusAction(String actionId) {
        if (actionId == null || actionId.isEmpty()) return;
        query = "";
        refreshFilter();
        for (int i = 0; i < filtered.size(); i++) {
            if (actionId.equals(filtered.get(i).pattern().actionId())) {
                selectedIndex = i;
                int visible = Math.max(1, listBounds().h() / ROW_H);
                if (i < scroll) scroll = i;
                else if (i >= scroll + visible) scroll = i - visible + 1;
                return;
            }
        }
    }

    private Bounds searchBounds() {
        return new Bounds(bounds.x() + 4, bounds.y() + 4, Math.max(0, bounds.w() - 8), SEARCH_H);
    }

    private Bounds listBounds() {
        return bounds.inset(0, SEARCH_H + 8, 0, 2);
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public void layout(Bounds bounds) {
        this.bounds = bounds;
    }

    private int rowAt(double mx, double my) {
        Bounds lb = listBounds();
        if (!lb.contains(mx, my) || mx >= lb.right() - IdeTheme.SCROLLBAR_WIDTH) return -1;
        int row = scroll + (int) ((my - lb.y()) / ROW_H);
        return row >= 0 && row < filtered.size() ? row : -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (bounds.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        IdeTheme.drawNineSlice(g, bounds, IdeTheme.NineSlice.PANEL);

        // Search box (紧凑顶置搜索条)
        Bounds sb = new Bounds(bounds.x() + 4, bounds.y() + 4, Math.max(0, bounds.w() - 8), SEARCH_H);
        boolean focused = focusManager.isFocused(this);
        IdeTheme.drawNineSlice(g, sb, IdeTheme.NineSlice.INPUT);
        if (focused) IdeTheme.drawBorder(g, sb, IdeTheme.ACCENT);
        try (IdeScissor.Scope sc = IdeScissor.push(g, sb.inset(2))) {
            String shown = query.isEmpty() && !focused
                ? Component.translatable("almightly_staff.gui.search").getString()
                : query;
            int col = query.isEmpty() && !focused ? IdeTheme.TEXT_MUTED : IdeTheme.TEXT_MAIN;
            g.drawString(font, shown, sb.x() + 4, sb.y() + 5, col, false);
            if (focused) {
                int cur = Math.min(searchHelper.getCursorPos(), query.length());
                int cx = sb.x() + 4 + font.width(query.substring(0, cur));
                g.fill(cx, sb.y() + 3, cx + 1, sb.bottom() - 3, IdeTheme.ACCENT_LIGHT);
            }
        }

        // List
        Bounds lb = bounds.inset(0, SEARCH_H + 8, 0, 2);
        if (lb.isEmpty()) return;
        int visible = Math.max(1, lb.h() / ROW_H);
        int maxScroll = Math.max(0, filtered.size() - visible);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        int contentRight = lb.right() - IdeTheme.SCROLLBAR_WIDTH;
        try (IdeScissor.Scope sc = IdeScissor.push(g, lb.x() + 1, lb.y(), Math.max(0, contentRight - lb.x() - 1), lb.h())) {
            for (int i = 0; i < visible; i++) {
                int idx = i + scroll;
                if (idx >= filtered.size()) break;
                int y = lb.y() + i * ROW_H;
                boolean sel = idx == selectedIndex;
                boolean hov = mouseX >= lb.x() && mouseX < contentRight && mouseY >= y && mouseY < y + ROW_H;
                int fill = sel ? IdeTheme.SELECTION_FILL : hov ? IdeTheme.BG_HOVER : (i % 2 == 0 ? IdeTheme.BG_ROW_EVEN : IdeTheme.BG_ROW_ODD);
                g.fill(lb.x() + 2, y, contentRight - 1, y + ROW_H - 1, fill);
                PatternCatalogEntry entry = filtered.get(idx);
                int textCol = entry.kind() == PatternCatalogEntry.Kind.PER_WORLD ? IdeTheme.GOLD_SEAL_TEXT
                    : (sel ? IdeTheme.TEXT_ON_CANVAS : IdeTheme.TEXT_MAIN);
                IdeTheme.drawClippedString(g, font, entry.displayName(), lb.x() + 6, y + 5,
                    Math.max(10, contentRight - lb.x() - 10), textCol);
            }
        }
        IdeTheme.drawScrollbar(g, contentRight, lb.y(), lb.h(), filtered.size(), visible, scroll);
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        if (activeDrag && dragIndex >= 0 && dragIndex < filtered.size()) {
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(0.0F, 0.0F, 520.0F);
            try {
                int x = (int) dragMouseX;
                int y = (int) dragMouseY;
                String name = filtered.get(dragIndex).displayName().getString();
                g.fill(x - 2, y - 2, x + 76, y + 16, IdeTheme.SELECTION_FILL);
                g.drawString(font, IdeTheme.trimText(font, name, 70), x + 2, y + 3, IdeTheme.TEXT_ON_CANVAS, false);
            } finally {
                ps.popPose();
            }
            return;
        }
        int row = rowAt(mouseX, mouseY);
        if (row < 0) return;
        drawCatalogTooltip(g, font, filtered.get(row), mouseX, mouseY);
    }

    private void drawCatalogTooltip(GuiGraphics g, Font font, PatternCatalogEntry entry, int mx, int my) {
        PatternRef pattern = entry.pattern();
        String summary = patternSummary(pattern);
        String desc = patternDescription(pattern);
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 600.0F);
        try {
            int previewSize = 36;
            int tooltipW = 220;
            int textW = tooltipW - previewSize - 22;
            List<FormattedCharSequence> nameLines = font.split(entry.displayName(), textW);
            List<FormattedCharSequence> sumLines = summary.isEmpty() ? List.of()
                : font.split(Component.literal(summary).withStyle(ChatFormatting.GRAY), textW);
            List<FormattedCharSequence> descLines = desc.isEmpty() ? List.of()
                : font.split(Component.literal(desc).withStyle(ChatFormatting.GRAY), textW);
            int lineH = 10;
            int tooltipH = Math.max(previewSize + 14, 14 + (nameLines.size() + sumLines.size() + descLines.size()) * lineH);
            int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
            int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
            int tx = mx + 12;
            int ty = my + 12;
            if (tx + tooltipW > sw) tx = Math.max(4, mx - tooltipW - 12);
            if (ty + tooltipH > sh) ty = Math.max(4, sh - tooltipH - 6);
            IdeTheme.drawNineSlice(g, tx, ty, tooltipW, tooltipH, IdeTheme.NineSlice.POPUP);
            if (!pattern.signature().isBlank()) {
                StaffHex.renderPattern(pattern, ps, tx + 6, ty + 6, previewSize);
            }
            int textX = tx + previewSize + 12;
            int textY = ty + 6;
            for (var c : nameLines) { g.drawString(font, c, textX, textY, IdeTheme.TEXT_MAIN, false); textY += lineH; }
            for (var c : sumLines) { g.drawString(font, c, textX, textY, IdeTheme.TEXT_MUTED, false); textY += lineH; }
            for (var c : descLines) { g.drawString(font, c, textX, textY, IdeTheme.TEXT_MUTED, false); textY += lineH; }
        } finally {
            ps.popPose();
        }
    }

    private static String patternSummary(PatternRef pattern) {
        String id = pattern.actionId();
        if (id.isEmpty() || pattern.signature().isBlank()) return "";
        StaffBookData.Entry book = StaffBookData.get(id);
        return book != null ? book.args() : "";
    }

    private static String patternDescription(PatternRef pattern) {
        String id = pattern.actionId();
        if (id.isEmpty() || pattern.signature().isBlank()) return "";
        StaffBookData.Entry book = StaffBookData.get(id);
        if (book != null && !book.descKey().isEmpty()) {
            Language lang = Language.getInstance();
            String raw = lang.has(book.descKey()) ? lang.getOrDefault(book.descKey(), "") : book.descKey();
            return StaffBookData.clean(raw);
        }
        return "";
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        if (searchBounds().contains(mx, my)) {
            focusManager.focus(this);
            return true;
        }
        focusManager.focus(null);
        int row = rowAt(mx, my);
        if (button == 0 && row >= 0) {
            selectedIndex = row;
            long now = System.currentTimeMillis();
            boolean dbl = row == lastClickRow && now - lastClickMs <= DOUBLE_CLICK_MS;
            lastClickRow = row;
            lastClickMs = now;
            if (dbl) {
                onInsertPattern.accept(filtered.get(row));
            } else {
                pendingDrag = true;
                dragIndex = row;
                dragStartX = mx;
                dragStartY = my;
                dragMouseX = mx;
                dragMouseY = my;
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0) return false;
        if (pendingDrag && Math.hypot(mx - dragStartX, my - dragStartY) >= 4) {
            pendingDrag = false;
            activeDrag = true;
        }
        if (activeDrag) {
            dragMouseX = mx;
            dragMouseY = my;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0) return false;
        boolean wasActive = activeDrag;
        int idx = dragIndex;
        pendingDrag = false;
        activeDrag = false;
        dragIndex = -1;
        if (wasActive && idx >= 0 && idx < filtered.size()) {
            onDropPattern.accept(filtered.get(idx), new double[]{mx, my});
            return true;
        }
        return wasActive;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!bounds.contains(mx, my)) return false;
        int visible = Math.max(1, listBounds().h() / ROW_H);
        int max = Math.max(0, filtered.size() - visible);
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focusManager.isFocused(this)) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            focusManager.clear();
            return true;
        }
        return searchHelper.keyPressed(keyCode);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!focusManager.isFocused(this)) return false;
        return searchHelper.charTyped(codePoint);
    }
}
