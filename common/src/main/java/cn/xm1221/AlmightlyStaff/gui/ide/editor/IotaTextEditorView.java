package cn.xm1221.AlmightlyStaff.gui.ide.editor;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.mod.HexConfig;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.ContextMenuOverlay;
import cn.xm1221.AlmightlyStaff.gui.ide.core.FocusManager;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.gui.ide.model.IdeSession;
import cn.xm1221.AlmightlyStaff.gui.ide.model.SpellDocument;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 主法术视图：Iota 文本流编辑器（替代 18×18 格子网）。
 * <ul>
 *   <li>顶部内嵌法术页 Tab 栏（支持多页切换、* 未保存星标、中键/大热区关闭）</li>
 *   <li>紧邻主视图的法术命名框，随时查看与修改页名</li>
 *   <li>正文以 {@code IotaType.getDisplay(tag)} + {@code IotaType.getColor(tag)} 按代码流排版（支持缩进、手动换行、拖拽选区、点选光标）</li>
 * </ul>
 */
public final class IotaTextEditorView implements IdeComponent {
    public interface Callbacks {
        void openContextMenu(int mx, int my, List<ContextMenuOverlay.MenuItem> items);

        void onEnsureHexGridOpen();

        void onEnsureCastCollectOpen();

        void onOpenPopupDraw(int pageIndex, int slotIndex, boolean insertAfter);

        void onActivePageChanged(@Nullable SpellDocument doc);

        void onDocumentMutated(SpellDocument doc, String source);

        void onSetCastPage(int pageIndex);
    }

    public static final class PageTabState {
        public final int pageIndex;
        public final Set<Integer> selectedIndices = new LinkedHashSet<>();
        public int selectionAnchor = -1;
        public int cursorIndex = 0;
        public int scrollY = 0;

        public PageTabState(int pageIndex, int initialCursor) {
            this.pageIndex = pageIndex;
            this.cursorIndex = Math.max(0, initialCursor);
        }
    }

    private static final int PAGE_TAB_H = 20;
    private static final int NAME_BAR_H = 22;
    private static final int SEALED_H = 15;
    private static final int ROW_H = 14;
    private static final int INDENT_PX = 14;

    private final IdeSession session;
    private final FocusManager focusManager;
    private final Callbacks callbacks;

    private final List<PageTabState> openTabs = new ArrayList<>();
    private int activeTabIndex = -1;

    private Bounds bounds = Bounds.EMPTY;
    private boolean nameFocused = false;
    private String nameBuffer = "";
    private final TextFieldHelper nameHelper;
    private boolean draggingSelection = false;
    private int dragAnchorToken = -1;

    @Nullable
    private IotaFlowLayout.Result lastLayout;
    private final List<IotaFlowLayout.FlowItem> lastItems = new ArrayList<>();

    public IotaTextEditorView(IdeSession session, FocusManager focusManager, Callbacks callbacks) {
        this.session = session;
        this.focusManager = focusManager;
        this.callbacks = callbacks;
        this.nameHelper = new TextFieldHelper(
            () -> nameBuffer,
            s -> nameBuffer = s,
            TextFieldHelper.createClipboardGetter(Minecraft.getInstance()),
            TextFieldHelper.createClipboardSetter(Minecraft.getInstance()),
            s -> s.length() <= 80
        );
    }

    public List<PageTabState> openTabs() {
        return openTabs;
    }

    @Nullable
    public PageTabState activeTabState() {
        if (activeTabIndex < 0 || activeTabIndex >= openTabs.size()) return null;
        return openTabs.get(activeTabIndex);
    }

    @Nullable
    public SpellDocument activeDocument() {
        PageTabState st = activeTabState();
        return st == null ? null : session.document(st.pageIndex);
    }

    public int activeCursorIndex() {
        PageTabState st = activeTabState();
        SpellDocument doc = activeDocument();
        if (st == null || doc == null) return 0;
        return Math.max(0, Math.min(st.cursorIndex, doc.size()));
    }

    public void openOrFocusPage(int pageIndex) {
        SpellDocument doc = session.document(pageIndex);
        if (doc == null) return;
        commitNameEdit();
        for (int i = 0; i < openTabs.size(); i++) {
            if (openTabs.get(i).pageIndex == pageIndex) {
                activeTabIndex = i;
                syncNameBuffer();
                callbacks.onActivePageChanged(doc);
                return;
            }
        }
        openTabs.add(new PageTabState(pageIndex, doc.size()));
        activeTabIndex = openTabs.size() - 1;
        syncNameBuffer();
        callbacks.onActivePageChanged(doc);
    }

    public void closePage(int pageIndex) {
        for (int i = 0; i < openTabs.size(); i++) {
            if (openTabs.get(i).pageIndex == pageIndex) {
                if (i == activeTabIndex) commitNameEdit();
                openTabs.remove(i);
                if (openTabs.isEmpty()) {
                    activeTabIndex = -1;
                } else if (activeTabIndex >= openTabs.size()) {
                    activeTabIndex = openTabs.size() - 1;
                }
                syncNameBuffer();
                callbacks.onActivePageChanged(activeDocument());
                return;
            }
        }
    }

    private void syncNameBuffer() {
        SpellDocument doc = activeDocument();
        nameBuffer = doc == null ? "" : doc.name();
        nameHelper.setCursorPos(nameBuffer.length(), false);
    }

    public void commitNameEdit() {
        if (!nameFocused) return;
        nameFocused = false;
        SpellDocument doc = activeDocument();
        if (doc != null) {
            session.renamePage(doc.pageIndex(), nameBuffer);
        }
    }

    public void insertTagsAtCursor(List<CompoundTag> tags, String source) {
        SpellDocument doc = activeDocument();
        PageTabState st = activeTabState();
        if (doc == null || st == null || doc.isSealed() || tags == null || tags.isEmpty()) return;
        int at = activeCursorIndex();
        if (doc.insertAt(at, tags, source)) {
            st.selectedIndices.clear();
            for (int i = 0; i < tags.size(); i++) st.selectedIndices.add(at + i);
            st.selectionAnchor = at;
            st.cursorIndex = at + tags.size();
            callbacks.onDocumentMutated(doc, source);
        }
    }

    private Bounds tabBarBounds() {
        return new Bounds(bounds.x(), bounds.y(), bounds.w(), Math.min(PAGE_TAB_H, bounds.h()));
    }

    private Bounds nameBarBounds() {
        return new Bounds(bounds.x(), bounds.y() + PAGE_TAB_H, bounds.w(), Math.min(NAME_BAR_H, Math.max(0, bounds.h() - PAGE_TAB_H)));
    }

    private Bounds nameInputBounds() {
        Bounds nb = nameBarBounds();
        int w = Math.min(200, Math.max(70, nb.w() - 140));
        return new Bounds(nb.x() + 34, nb.y() + 3, w, 16);
    }

    private Bounds bodyBounds() {
        SpellDocument doc = activeDocument();
        int top = PAGE_TAB_H + NAME_BAR_H + (doc != null && doc.isSealed() ? SEALED_H : 0);
        return bounds.inset(0, top, 0, 0);
    }

    private void rebuildFlowLayout(Font font) {
        SpellDocument doc = activeDocument();
        Bounds body = bodyBounds();
        lastItems.clear();
        if (doc == null || body.isEmpty()) {
            lastLayout = null;
            return;
        }
        List<CompoundTag> tags = doc.iotasView();
        boolean alwaysShowCommas = false;
        try {
            alwaysShowCommas = HexConfig.client() != null && HexConfig.client().alwaysShowListCommas();
        } catch (Throwable ignored) {
        }
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag tag = tags.get(i);
            boolean hasComma = false;
            if (i < tags.size() - 1) {
                boolean thisNotPat = IotaType.getTypeFromTag(tag) != HexIotaTypes.PATTERN;
                boolean nextNotPat = IotaType.getTypeFromTag(tags.get(i + 1)) != HexIotaTypes.PATTERN;
                hasComma = thisNotPat || nextNotPat || alwaysShowCommas;
            }
            lastItems.add(describeTag(i, tag, hasComma));
        }
        int usableW = Math.max(80, body.w() - IdeTheme.SCROLLBAR_WIDTH - 18);
        int commaW = font.width(", ");
        int spaceW = Math.max(1, font.width(" "));
        Set<Integer> breaks = session.workspace().lineBreaksForPage(doc.pageIndex());
        Map<Integer, Integer> spaces = session.workspace().spacesForPage(doc.pageIndex());
        lastLayout = IotaFlowLayout.layout(
            lastItems,
            item -> font.width(IotaType.getDisplay(tags.get(item.index()))),
            item -> commaW,
            usableW, ROW_H, INDENT_PX, spaceW, breaks, spaces
        );
    }

    private static IotaFlowLayout.FlowItem describeTag(int idx, CompoundTag tag, boolean hasComma) {
        boolean isPat = IotaType.getTypeFromTag(tag) == HexIotaTypes.PATTERN;
        boolean intro = false;
        boolean retro = false;
        if (isPat) {
            try {
                Iota iota = IotaType.deserialize(tag, null);
                if (iota instanceof PatternIota pi) {
                    String sig = pi.getPattern().anglesSignature();
                    intro = "qqq".equals(sig);
                    retro = "eee".equals(sig);
                }
            } catch (Exception ignored) {
            }
        }
        String label = IotaType.getDisplay(tag).getString();
        int color = IotaType.getColor(tag) | 0xFF000000;
        return new IotaFlowLayout.FlowItem(idx, label, color, intro, retro, hasComma);
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public void layout(Bounds bounds) {
        this.bounds = bounds;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (bounds.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        IdeTheme.drawNineSlice(g, bounds, IdeTheme.NineSlice.ROOT);

        // 1. 书页 Tab 栏
        Bounds tb = tabBarBounds();
        IdeTheme.drawNineSlice(g, tb, IdeTheme.NineSlice.HEADER);
        try (IdeScissor.Scope sc = IdeScissor.push(g, tb)) {
            int tx = tb.x() + 2;
            for (int i = 0; i < openTabs.size(); i++) {
                PageTabState st = openTabs.get(i);
                SpellDocument d = session.document(st.pageIndex);
                if (d == null) continue;
                boolean act = i == activeTabIndex;
                String lbl = (d.name().isEmpty()
                    ? Component.translatable("almightly_staff.gui.page_label", d.pageIndex()).getString()
                    : "#" + d.pageIndex() + " " + d.name()) + (d.isDirty() ? " *" : "");
                int w = Math.max(64, font.width(lbl) + 32);
                Bounds tabB = new Bounds(tx, tb.y() + 1, w, tb.h() - 1);
                IdeTheme.drawNineSlice(g, tabB, act ? IdeTheme.NineSlice.TAB_ACTIVE : IdeTheme.NineSlice.TAB_INACTIVE);
                IdeTheme.drawIcon(g, d.isSealed() ? IdeTheme.Icon.PADLOCK : IdeTheme.Icon.SPELL_STAR,
                    tx + 5, tb.y() + 6, d.isSealed() ? IdeTheme.GOLD_SEAL : (act ? IdeTheme.ACCENT_LIGHT : IdeTheme.TEXT_MUTED));
                g.drawString(font, lbl, tx + 15, tb.y() + 6,
                    d.isDirty() ? IdeTheme.GOLD_SEAL_TEXT : (act ? IdeTheme.TEXT_MAIN : IdeTheme.TEXT_MUTED), false);

                // 较大关闭热区 (12x12)
                int cx = tx + w - 15;
                int cy = tb.y() + 4;
                if (mouseX >= cx && mouseX < cx + 12 && mouseY >= cy && mouseY < cy + 12) {
                    g.fill(cx, cy, cx + 12, cy + 12, IdeTheme.BG_HOVER);
                }
                IdeTheme.drawIcon(g, IdeTheme.Icon.CLOSE_X, cx + 2, cy + 2, IdeTheme.TEXT_MUTED);
                tx += w + 1;
            }
        }

        SpellDocument doc = activeDocument();
        PageTabState st = activeTabState();
        if (doc == null || st == null) {
            g.drawCenteredString(font, Component.translatable("almightly_staff.ide.empty_editor"),
                bounds.x() + bounds.w() / 2, bounds.y() + Math.max(40, bounds.h() / 2), IdeTheme.TEXT_MUTED);
            return;
        }

        // 2. 紧邻主视图的法术命名框
        Bounds nb = nameBarBounds();
        IdeTheme.drawNineSlice(g, nb, IdeTheme.NineSlice.PANEL);
        try (IdeScissor.Scope sc = IdeScissor.push(g, nb)) {
            boolean isCast = doc.pageIndex() == session.castPage();
            String idxBadge = "#" + doc.pageIndex();
            if (isCast) {
                IdeTheme.drawIcon(g, IdeTheme.Icon.PLAY_CAST, nb.x() + 5, nb.y() + 7, IdeTheme.GREEN_CAST);
            }
            g.drawString(font, idxBadge, nb.x() + (isCast ? 15 : 6), nb.y() + 7,
                isCast ? IdeTheme.GREEN_CAST : IdeTheme.TEXT_MUTED, false);

            Bounds inB = nameInputBounds();
            IdeTheme.drawNineSlice(g, inB, IdeTheme.NineSlice.INPUT);
            if (nameFocused) IdeTheme.drawBorder(g, inB, IdeTheme.ACCENT);
            String shownName = nameFocused ? nameBuffer : doc.name();
            try (IdeScissor.Scope s2 = IdeScissor.push(g, inB.inset(2))) {
                String txt = shownName.isEmpty() && !nameFocused
                    ? Component.translatable("almightly_staff.gui.spell_name").getString()
                    : shownName;
                int col = shownName.isEmpty() && !nameFocused ? IdeTheme.TEXT_MUTED : IdeTheme.TEXT_MAIN;
                g.drawString(font, txt, inB.x() + 4, inB.y() + 4, col, false);
                if (nameFocused) {
                    int cur = Math.min(nameHelper.getCursorPos(), nameBuffer.length());
                    int cx = inB.x() + 4 + font.width(nameBuffer.substring(0, cur));
                    g.fill(cx, inB.y() + 2, cx + 1, inB.bottom() - 2, IdeTheme.ACCENT_LIGHT);
                }
            }

            String meta = Component.translatable("almightly_staff.ide.slot_count", doc.size(), activeCursorIndex()).getString();
            int metaW = font.width(meta);
            if (inB.right() + metaW + 12 <= nb.right()) {
                g.drawString(font, meta, nb.right() - metaW - 8, nb.y() + 7, IdeTheme.TEXT_MUTED, false);
            }
        }

        if (doc.isSealed()) {
            Bounds sb = new Bounds(bounds.x(), nb.bottom(), bounds.w(), SEALED_H);
            IdeTheme.drawNineSlice(g, sb, IdeTheme.NineSlice.SEALED_BANNER);
            IdeTheme.drawIcon(g, IdeTheme.Icon.PADLOCK, sb.x() + 6, sb.y() + 3, IdeTheme.GOLD_SEAL);
            g.drawString(font, Component.translatable("almightly_staff.gui.sealed_readonly"),
                sb.x() + 17, sb.y() + 4, IdeTheme.GOLD_SEAL_TEXT, false);
        }

        // 3. Iota 文本流正文
        Bounds body = bodyBounds();
        if (body.isEmpty()) return;
        rebuildFlowLayout(font);
        if (lastLayout == null) return;

        int padX = body.x() + 8;
        int padY = body.y() + 6;
        int contentH = lastLayout.totalRows() * ROW_H + 16;
        int maxScroll = Math.max(0, contentH - body.h());
        st.scrollY = Mth.clamp(st.scrollY, 0, maxScroll);

        int hoverToken = lastLayout.tokenAt(mouseX - padX, mouseY - padY + st.scrollY);
        int contentRight = body.right() - IdeTheme.SCROLLBAR_WIDTH;

        try (IdeScissor.Scope sc = IdeScissor.push(g, body.x(), body.y(), Math.max(0, contentRight - body.x()), body.h())) {
            for (IotaFlowLayout.PlacedToken tok : lastLayout.tokens()) {
                int rx = padX + tok.x();
                int ry = padY + tok.y() - st.scrollY;
                if (ry + tok.h() < body.y() || ry > body.bottom()) continue;

                boolean sel = st.selectedIndices.contains(tok.index());
                boolean hov = tok.index() == hoverToken;
                if (sel) {
                    g.fill(rx - 1, ry + 1, rx + tok.dispW() + 1, ry + tok.h() - 1, 0x88493662);
                } else if (hov) {
                    g.fill(rx - 1, ry + 1, rx + tok.dispW() + 1, ry + tok.h() - 1, 0x44303744);
                }

                Component disp = IotaType.getDisplay(doc.iotasView().get(tok.index()));
                if (sel) {
                    g.drawString(font, disp.getString(), rx, ry + 3, 0xFFFFFFFF, false);
                } else {
                    g.drawString(font, disp, rx, ry + 3, tok.color(), false);
                }

                if (tok.hasComma()) {
                    g.drawString(font, ", ", rx + tok.dispW(), ry + 3, IdeTheme.TEXT_MUTED, false);
                }
            }

            // 光标指示竖线
            if (!doc.isSealed()) {
                int cur = activeCursorIndex();
                for (IotaFlowLayout.CursorAnchor a : lastLayout.anchors()) {
                    if (a.cursorIndex() == cur) {
                        int cx = padX + a.x();
                        int cy = padY + a.y() - st.scrollY;
                        if (cy + a.h() >= body.y() && cy <= body.bottom()) {
                            g.fill(cx, cy + 1, cx + 2, cy + a.h() - 1, IdeTheme.ACCENT_LIGHT);
                        }
                        break;
                    }
                }
            }
        }
        IdeTheme.drawScrollbar(g, contentRight, body.y(), body.h(), contentH, body.h(), st.scrollY);
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        SpellDocument doc = activeDocument();
        PageTabState st = activeTabState();
        Bounds body = bodyBounds();
        if (doc == null || st == null || lastLayout == null || !body.contains(mouseX, mouseY)) return;
        int padX = body.x() + 8;
        int padY = body.y() + 6;
        int hover = lastLayout.tokenAt(mouseX - padX, mouseY - padY + st.scrollY);
        if (hover < 0 || hover >= doc.size()) return;
        CompoundTag tag = doc.iotasView().get(hover);
        g.renderTooltip(Minecraft.getInstance().font, IotaType.getDisplay(tag), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        Font font = Minecraft.getInstance().font;

        // 1. Page Tab Bar
        Bounds tb = tabBarBounds();
        if (tb.contains(mx, my)) {
            int tx = tb.x() + 2;
            for (int i = 0; i < openTabs.size(); i++) {
                PageTabState st = openTabs.get(i);
                SpellDocument d = session.document(st.pageIndex);
                if (d == null) continue;
                String lbl = (d.name().isEmpty()
                    ? Component.translatable("almightly_staff.gui.page_label", d.pageIndex()).getString()
                    : "#" + d.pageIndex() + " " + d.name()) + (d.isDirty() ? " *" : "");
                int w = Math.max(64, font.width(lbl) + 32);
                if (mx >= tx && mx < tx + w) {
                    int cx = tx + w - 15;
                    if ((button == 0 && mx >= cx && mx < cx + 12) || button == 2) {
                        closePage(st.pageIndex);
                        return true;
                    }
                    openOrFocusPage(st.pageIndex);
                    return true;
                }
                tx += w + 1;
            }
            return true;
        }

        // 2. Name Input
        if (nameInputBounds().contains(mx, my) && button == 0 && activeDocument() != null) {
            nameFocused = true;
            syncNameBuffer();
            focusManager.focus(this);
            nameHelper.selectAll();
            return true;
        }
        commitNameEdit();

        // 3. Text Flow Body
        focusManager.focus(this);
        SpellDocument doc = activeDocument();
        PageTabState st = activeTabState();
        Bounds body = bodyBounds();
        if (doc == null || st == null || !body.contains(mx, my)) return true;

        rebuildFlowLayout(font);
        if (lastLayout == null) return true;
        int padX = body.x() + 8;
        int padY = body.y() + 6;
        double lx = mx - padX;
        double ly = my - padY + st.scrollY;
        int hit = lastLayout.tokenAt(lx, ly);

        if (button == 0) {
            if (hit >= 0) {
                if (Screen.hasShiftDown() && st.selectionAnchor >= 0) {
                    st.selectedIndices.clear();
                    int lo = Math.min(st.selectionAnchor, hit);
                    int hi = Math.max(st.selectionAnchor, hit);
                    for (int i = lo; i <= hi && i < doc.size(); i++) st.selectedIndices.add(i);
                } else if (Screen.hasControlDown()) {
                    if (!st.selectedIndices.remove(hit)) st.selectedIndices.add(hit);
                    st.selectionAnchor = hit;
                } else {
                    st.selectedIndices.clear();
                    st.selectedIndices.add(hit);
                    st.selectionAnchor = hit;
                }
                st.cursorIndex = hit + 1;
                draggingSelection = true;
                dragAnchorToken = hit;
            } else {
                st.selectedIndices.clear();
                st.cursorIndex = lastLayout.cursorAt(lx, ly, ROW_H);
            }
            return true;
        }
        if (button == 1) {
            if (hit >= 0 && !st.selectedIndices.contains(hit)) {
                st.selectedIndices.clear();
                st.selectedIndices.add(hit);
                st.selectionAnchor = hit;
                st.cursorIndex = hit;
            } else if (hit < 0) {
                st.cursorIndex = lastLayout.cursorAt(lx, ly, ROW_H);
            }
            openContextMenu((int) mx, (int) my, doc, st, hit);
            return true;
        }
        return true;
    }

    private void openContextMenu(int mx, int my, SpellDocument doc, PageTabState st, int hit) {
        boolean editable = !doc.isSealed();
        boolean hasSel = !st.selectedIndices.isEmpty();
        boolean hasClip = !session.clipboard().isEmpty();
        int cur = activeCursorIndex();
        List<ContextMenuOverlay.MenuItem> items = List.of(
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.copy_pattern"), hasSel, this::copySelected),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.paste_pattern"), editable && hasClip, this::pasteAtCursor),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.remove"), editable && hasSel, this::removeSelected),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.draw_in_grid"), editable,
                callbacks::onEnsureHexGridOpen),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.toggle_newline"), editable,
                () -> toggleManualLineBreak(doc.pageIndex(), cur)),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.redraw"), editable && hit >= 0,
                () -> callbacks.onOpenPopupDraw(doc.pageIndex(), hit, false)),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.cast_collect"), editable,
                callbacks::onEnsureCastCollectOpen)
        );
        callbacks.openContextMenu(mx, my, items);
    }

    private void toggleManualLineBreak(int pageIndex, int cursorPos) {
        Set<Integer> breaks = session.workspace().lineBreaksForPage(pageIndex);
        if (!breaks.remove(cursorPos)) {
            breaks.add(cursorPos);
        }
        session.workspace().setLineBreaksForPage(pageIndex, breaks);
        session.saveWorkspace();
    }

    private void addManualSpace(int pageIndex, int cursorPos) {
        Map<Integer, Integer> spaces = session.workspace().spacesForPage(pageIndex);
        int curCount = Math.max(0, spaces.getOrDefault(cursorPos, 0));
        if (curCount < 16) {
            spaces.put(cursorPos, curCount + 1);
            session.workspace().setSpacesForPage(pageIndex, spaces);
            session.saveWorkspace();
        }
    }

    private boolean removeManualSpaceOrBreak(int pageIndex, int cursorPos) {
        Map<Integer, Integer> spaces = session.workspace().spacesForPage(pageIndex);
        int curCount = Math.max(0, spaces.getOrDefault(cursorPos, 0));
        if (curCount > 0) {
            if (curCount == 1) spaces.remove(cursorPos);
            else spaces.put(cursorPos, curCount - 1);
            session.workspace().setSpacesForPage(pageIndex, spaces);
            session.saveWorkspace();
            return true;
        }
        Set<Integer> breaks = session.workspace().lineBreaksForPage(pageIndex);
        if (breaks.remove(cursorPos)) {
            session.workspace().setLineBreaksForPage(pageIndex, breaks);
            session.saveWorkspace();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0 || !draggingSelection || lastLayout == null) return false;
        SpellDocument doc = activeDocument();
        PageTabState st = activeTabState();
        Bounds body = bodyBounds();
        if (doc == null || st == null || !body.contains(mx, my)) return false;
        int hit = lastLayout.tokenAt(mx - (body.x() + 8), my - (body.y() + 6) + st.scrollY);
        if (hit >= 0 && dragAnchorToken >= 0) {
            st.selectedIndices.clear();
            int lo = Math.min(dragAnchorToken, hit);
            int hi = Math.max(dragAnchorToken, hit);
            for (int i = lo; i <= hi && i < doc.size(); i++) st.selectedIndices.add(i);
            st.cursorIndex = hit >= dragAnchorToken ? hit + 1 : hit;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingSelection) {
            draggingSelection = false;
            dragAnchorToken = -1;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!bounds.contains(mx, my) || lastLayout == null) return false;
        PageTabState st = activeTabState();
        Bounds body = bodyBounds();
        if (st == null || !body.contains(mx, my)) return false;
        int contentH = lastLayout.totalRows() * ROW_H + 16;
        int max = Math.max(0, contentH - body.h());
        st.scrollY = Mth.clamp(st.scrollY - (int) Math.signum(delta) * ROW_H, 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitNameEdit();
                return true;
            }
            return nameHelper.keyPressed(keyCode);
        }
        SpellDocument doc = activeDocument();
        PageTabState st = activeTabState();
        if (doc == null || st == null) return false;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;

        if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
            st.selectedIndices.clear();
            for (int i = 0; i < doc.size(); i++) st.selectedIndices.add(i);
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
            copySelected();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) {
            copySelected();
            removeSelected();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
            pasteAtCursor();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
            boolean ok = shift ? doc.redo() : doc.undo();
            if (ok) callbacks.onDocumentMutated(doc, shift ? "redo" : "undo");
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Y) {
            if (doc.redo()) callbacks.onDocumentMutated(doc, "redo");
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && !doc.isSealed()) {
            toggleManualLineBreak(doc.pageIndex(), activeCursorIndex());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE && !doc.isSealed()) {
            addManualSpace(doc.pageIndex(), activeCursorIndex());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!st.selectedIndices.isEmpty()) {
                removeSelected();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !doc.isSealed()) {
                int cur = activeCursorIndex();
                if (removeManualSpaceOrBreak(doc.pageIndex(), cur)) {
                    return true;
                }
                if (cur > 0) {
                    int target = cur - 1;
                    if (doc.removeIndices(List.of(target), "text")) {
                        st.cursorIndex = target;
                        callbacks.onDocumentMutated(doc, "text");
                    }
                    return true;
                }
            }
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            st.cursorIndex = Math.max(0, activeCursorIndex() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            st.cursorIndex = Math.min(doc.size(), activeCursorIndex() + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            st.cursorIndex = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            st.cursorIndex = doc.size();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (nameFocused) {
            return nameHelper.charTyped(codePoint);
        }
        return false;
    }

    @Override
    public void onFocusLost() {
        commitNameEdit();
    }

    public void copySelected() {
        SpellDocument doc = activeDocument();
        PageTabState st = activeTabState();
        if (doc == null || st == null || st.selectedIndices.isEmpty()) return;
        List<Integer> sorted = new ArrayList<>(st.selectedIndices);
        Collections.sort(sorted);
        List<CompoundTag> copied = new ArrayList<>();
        for (int idx : sorted) {
            if (idx >= 0 && idx < doc.size()) copied.add(doc.iotasView().get(idx).copy());
        }
        if (!copied.isEmpty()) session.setClipboard(copied);
    }

    public void removeSelected() {
        SpellDocument doc = activeDocument();
        PageTabState st = activeTabState();
        if (doc == null || st == null || doc.isSealed() || st.selectedIndices.isEmpty()) return;
        int min = Collections.min(st.selectedIndices);
        if (doc.removeIndices(st.selectedIndices, "text")) {
            st.selectedIndices.clear();
            st.selectionAnchor = -1;
            st.cursorIndex = Math.min(min, doc.size());
            callbacks.onDocumentMutated(doc, "text");
        }
    }

    public void pasteAtCursor() {
        SpellDocument doc = activeDocument();
        PageTabState st = activeTabState();
        List<CompoundTag> clip = session.clipboard();
        if (doc == null || st == null || doc.isSealed() || clip.isEmpty()) return;
        int at = activeCursorIndex();
        if (!st.selectedIndices.isEmpty()) {
            at = Collections.min(st.selectedIndices);
            doc.removeIndices(st.selectedIndices, "text");
        }
        if (doc.insertAt(at, clip, "text")) {
            st.selectedIndices.clear();
            for (int i = 0; i < clip.size(); i++) st.selectedIndices.add(at + i);
            st.selectionAnchor = at;
            st.cursorIndex = at + clip.size();
            callbacks.onDocumentMutated(doc, "text");
        }
    }
}
