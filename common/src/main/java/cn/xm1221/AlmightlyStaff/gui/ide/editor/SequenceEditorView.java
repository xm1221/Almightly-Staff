package cn.xm1221.AlmightlyStaff.gui.ide.editor;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.ContextMenuOverlay;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.gui.ide.model.IdeSession;
import cn.xm1221.AlmightlyStaff.gui.ide.model.SpellDocument;
import cn.xm1221.AlmightlyStaff.spell.PatternCatalogEntry;
import cn.xm1221.AlmightlyStaff.spell.PatternRef;
import cn.xm1221.AlmightlyStaff.spell.StaffHex;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 书页 Iota 序列可视化网格视图（迁自 StaffLibScreen 右栏）。
 * 多个分屏 Tab 打开同一页时共享 {@link SpellDocument}，各自持有独立滚动、光标与选区。
 */
public final class SequenceEditorView implements IdeComponent {
    public interface Callbacks {
        IdeSession session();

        void openContextMenu(int mx, int my, List<ContextMenuOverlay.MenuItem> items);

        void openDrawModal(int pageIndex, int slotIndex, boolean insertAfter);

        void openCastCollectModal(int pageIndex, int insertAt);

        void onDocumentMutated(SpellDocument doc, String source);
    }

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 4;
    private static final int STEP = SLOT_SIZE + SLOT_GAP;
    private static final int HEADER_H = 18;
    private static final int SEALED_H = 14;
    private static final int DRAG_THRESHOLD = 4;

    private final TabItem.SpellTab tab;
    private final Callbacks callbacks;
    private Bounds bounds = Bounds.EMPTY;

    private boolean pendingDrag;
    private boolean activeDrag;
    private int dragSlot = -1;
    private double dragStartX, dragStartY, dragMouseX, dragMouseY;

    public SequenceEditorView(TabItem.SpellTab tab, Callbacks callbacks) {
        this.tab = tab;
        this.callbacks = callbacks;
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public void layout(Bounds bounds) {
        this.bounds = bounds;
    }

    private record GridLayout(int startX, int startY, int columns, int rowsPerView, int totalRows, int gridTop, int gridBottom) {
    }

    private GridLayout computeLayout() {
        SpellDocument doc = tab.document();
        boolean sealed = doc.isSealed();
        int top = bounds.y() + HEADER_H + (sealed ? SEALED_H : 0);
        int bottom = bounds.bottom() - 4;
        int contentRight = bounds.right() - IdeTheme.SCROLLBAR_WIDTH;
        int usableW = Math.max(SLOT_SIZE, contentRight - bounds.x() - 12);
        int cols = Math.max(1, usableW / STEP);
        int rowsPerView = Math.max(1, (bottom - top - 4) / STEP);
        int totalRows = doc.size() / cols + 1;
        int gridW = cols * SLOT_SIZE + (cols - 1) * SLOT_GAP;
        int startX = bounds.x() + Math.max(6, (contentRight - bounds.x() - gridW) / 2);
        return new GridLayout(startX, top + 4, cols, rowsPerView, totalRows, top, bottom);
    }

    private int slotAt(double mx, double my) {
        if (!bounds.contains(mx, my)) return -1;
        GridLayout gl = computeLayout();
        if (my < gl.gridTop() || my >= gl.gridBottom()) return -1;
        double lx = mx - gl.startX();
        double ly = my - gl.startY();
        if (lx < 0 || ly < 0) return -1;
        int col = (int) (lx / STEP);
        int rowInView = (int) (ly / STEP);
        if (col >= gl.columns() || rowInView >= gl.rowsPerView()) return -1;
        if (lx - col * STEP >= SLOT_SIZE || ly - rowInView * STEP >= SLOT_SIZE) return -1;
        int row = rowInView + tab.scrollRow();
        if (row >= gl.totalRows()) return -1;
        return row * gl.columns() + col;
    }

    public int insertionIndexAt(double mx, double my) {
        SpellDocument doc = tab.document();
        GridLayout gl = computeLayout();
        int slot = slotAt(mx, my);
        if (slot < 0) {
            return my >= gl.startY() ? doc.size() : 0;
        }
        int col = slot % gl.columns();
        double lx = mx - gl.startX() - col * STEP;
        boolean rightHalf = lx >= SLOT_SIZE / 2.0;
        int base = Math.min(slot, doc.size());
        return rightHalf ? Math.min(doc.size(), base + 1) : base;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (bounds.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        SpellDocument doc = tab.document();
        boolean sealed = doc.isSealed();
        List<CompoundTag> iotas = doc.iotasView();
        GridLayout gl = computeLayout();
        int maxScroll = Math.max(0, gl.totalRows() - gl.rowsPerView());
        tab.setScrollRow(Mth.clamp(tab.scrollRow(), 0, maxScroll));

        // 顶部状态小条（页号/施放标记/节点数/光标位）
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.y() + HEADER_H, IdeTheme.BG_PANEL);
        g.fill(bounds.x(), bounds.y() + HEADER_H - 1, bounds.right(), bounds.y() + HEADER_H, IdeTheme.BORDER);
        boolean isCast = doc.pageIndex() == callbacks.session().castPage();
        String title = (isCast ? "> " : "") + "#" + doc.pageIndex()
            + (doc.name().isEmpty() ? "" : " " + doc.name());
        g.drawString(font, IdeTheme.trimText(font, title, Math.max(40, bounds.w() - 120)),
            bounds.x() + 6, bounds.y() + 5, isCast ? IdeTheme.GREEN_CAST : IdeTheme.TEXT_MAIN, false);
        String countInfo = Component.translatable("almightly_staff.ide.slot_count",
            iotas.size(), tab.cursorIndex()).getString();
        g.drawString(font, countInfo, Math.max(bounds.x() + 8, bounds.right() - font.width(countInfo) - 8),
            bounds.y() + 5, IdeTheme.TEXT_MUTED, false);

        if (sealed) {
            int sy = bounds.y() + HEADER_H;
            g.fill(bounds.x(), sy, bounds.right() - IdeTheme.SCROLLBAR_WIDTH, sy + SEALED_H - 1, 0x55E8A82E);
            IdeTheme.drawIcon(g, IdeTheme.Icon.PADLOCK, bounds.x() + 5, sy + 3, IdeTheme.GOLD_SEAL);
            g.drawString(font, Component.translatable("almightly_staff.gui.sealed_readonly"),
                bounds.x() + 16, sy + 3, IdeTheme.GOLD_SEAL_TEXT, false);
        }

        int hoverSlot = slotAt(mouseX, mouseY);
        int contentRight = bounds.right() - IdeTheme.SCROLLBAR_WIDTH;
        try (IdeScissor.Scope sc = IdeScissor.push(g, bounds.x(), gl.gridTop(),
            Math.max(0, contentRight - bounds.x()), Math.max(0, gl.gridBottom() - gl.gridTop()))) {
            for (int rowInView = 0; rowInView < gl.rowsPerView(); rowInView++) {
                int row = rowInView + tab.scrollRow();
                if (row >= gl.totalRows()) break;
                for (int col = 0; col < gl.columns(); col++) {
                    int idx = row * gl.columns() + col;
                    int x = gl.startX() + col * STEP;
                    int y = gl.startY() + rowInView * STEP;
                    boolean occupied = idx < iotas.size();
                    boolean selected = occupied && tab.selectedSlots().contains(idx);
                    boolean hover = idx == hoverSlot;
                    if (occupied) {
                        drawSlotFrame(g, x, y, selected, hover);
                        renderSlotIota(g, iotas.get(idx), x + 1, y + 1, SLOT_SIZE - 2);
                    } else if (!sealed) {
                        int border = hover ? IdeTheme.ACCENT_LIGHT : 0xFF3A414A;
                        g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, border);
                        g.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1,
                            hover ? 0xFF2C2C35 : 0xFF20252B);
                        if (hover) {
                            g.drawCenteredString(font, "+", x + SLOT_SIZE / 2, y + SLOT_SIZE / 2 - 4, IdeTheme.ACCENT);
                        }
                    }
                }
            }

            // 光标竖线（指示网格/HexParse/搜索/笔记插入位置）
            if (!sealed) {
                int cur = tab.cursorIndex();
                int cRow = cur / gl.columns();
                int cCol = cur % gl.columns();
                int cViewRow = cRow - tab.scrollRow();
                if (cViewRow >= 0 && cViewRow < gl.rowsPerView()) {
                    int cx = gl.startX() + cCol * STEP - 2;
                    int cy = gl.startY() + cViewRow * STEP;
                    g.fill(cx, cy, cx + 2, cy + SLOT_SIZE, IdeTheme.ACCENT_LIGHT);
                }
            }

            // 拖拽插入线
            if (activeDrag && !sealed) {
                int dropIdx = insertionIndexAt(dragMouseX, dragMouseY);
                int dRow = dropIdx / gl.columns() - tab.scrollRow();
                int dCol = dropIdx % gl.columns();
                if (dRow >= 0 && dRow < gl.rowsPerView()) {
                    int dx = gl.startX() + dCol * STEP - 2;
                    int dy = gl.startY() + dRow * STEP;
                    g.fill(dx, dy, dx + 2, dy + SLOT_SIZE, IdeTheme.GOLD_SEAL);
                }
            }
        }

        IdeTheme.drawScrollbar(g, contentRight, gl.gridTop(), Math.max(0, gl.gridBottom() - gl.gridTop()),
            gl.totalRows(), gl.rowsPerView(), tab.scrollRow());
        if (sealed) {
            IdeTheme.drawBorder(g, bounds.x(), gl.gridTop(), bounds.w(), Math.max(0, gl.gridBottom() - gl.gridTop()),
                IdeTheme.GOLD_SEAL);
        }
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        List<CompoundTag> iotas = tab.document().iotasView();
        if (activeDrag && dragSlot >= 0 && dragSlot < iotas.size()) {
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(0.0F, 0.0F, 500.0F);
            try {
                int x = (int) dragMouseX;
                int y = (int) dragMouseY;
                g.fill(x - 2, y - 2, x + SLOT_SIZE + 2, y + SLOT_SIZE + 2, 0xCC20252B);
                renderSlotIota(g, iotas.get(dragSlot), x, y, SLOT_SIZE);
            } finally {
                ps.popPose();
            }
            return;
        }
        int hover = slotAt(mouseX, mouseY);
        if (hover >= 0 && hover < iotas.size()) {
            CompoundTag tag = iotas.get(hover);
            Component tip = hoverName(tag);
            g.renderTooltip(font, tip != null ? tip : StaffHex.displayOfTag(tag), mouseX, mouseY);
        }
    }

    private static void drawSlotFrame(GuiGraphics g, int x, int y, boolean selected, boolean hover) {
        int border = selected ? 0xFFFFCA72 : hover ? IdeTheme.ACCENT_LIGHT : 0xFF4B535C;
        int fill = selected ? 0xFF4E3D2C : hover ? 0xFF303039 : 0xFF20252B;
        g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, border);
        g.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, fill);
        g.fill(x + 2, y + 2, x + SLOT_SIZE - 2, y + 3, 0x553B424A);
    }

    public static void renderSlotIota(GuiGraphics g, CompoundTag tag, int x, int y, int size) {
        if (StaffHex.isPatternTag(tag)) {
            try {
                Iota iota = StaffHex.deserializeIota(tag);
                if (iota instanceof PatternIota pi) {
                    PoseStack ps = g.pose();
                    ps.pushPose();
                    PatternRef ref = StaffHex.refFor(pi.getPattern());
                    StaffHex.renderPattern(ref, ps, x, y, (int) (size / 16.0f * 14.0f));
                    ps.popPose();
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        int color = StaffHex.colorOfTag(tag) | 0xFF000000;
        int cx = x + size / 2;
        int cy = y + size / 2;
        int half = Math.max(3, size / 4);
        g.fill(cx - half, cy - half, cx + half + 1, cy + half + 1, color);
        g.fill(cx - half + 1, cy - half + 1, cx + half, cy + half, 0x66FFFFFF);
    }

    @Nullable
    private static Component hoverName(CompoundTag tag) {
        if (!StaffHex.isPatternTag(tag)) return null;
        try {
            Iota i = StaffHex.deserializeIota(tag);
            if (i instanceof PatternIota pi) {
                return StaffHex.patternDisplayName(StaffHex.refFor(pi.getPattern()));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        SpellDocument doc = tab.document();
        int slot = slotAt(mx, my);
        if (button == 0) {
            if (slot < 0) {
                if (my >= computeLayout().gridTop()) {
                    tab.selectedSlots().clear();
                    tab.setCursorIndex(doc.size());
                }
                return true;
            }
            if (slot >= doc.size()) {
                tab.selectedSlots().clear();
                tab.setCursorIndex(doc.size());
                if (!doc.isSealed()) {
                    callbacks.openDrawModal(doc.pageIndex(), -1, false);
                }
                return true;
            }
            if (Screen.hasShiftDown() && tab.selectionAnchor() >= 0) {
                tab.selectedSlots().clear();
                int lo = Math.min(tab.selectionAnchor(), slot);
                int hi = Math.max(tab.selectionAnchor(), slot);
                for (int i = lo; i <= hi && i < doc.size(); i++) tab.selectedSlots().add(i);
            } else if (Screen.hasControlDown()) {
                if (!tab.selectedSlots().remove(slot)) tab.selectedSlots().add(slot);
                tab.setSelectionAnchor(slot);
            } else {
                tab.selectedSlots().clear();
                tab.selectedSlots().add(slot);
                tab.setSelectionAnchor(slot);
            }
            tab.setCursorIndex(slot + 1);
            pendingDrag = !doc.isSealed();
            dragSlot = slot;
            dragStartX = mx;
            dragStartY = my;
            dragMouseX = mx;
            dragMouseY = my;
            return true;
        }
        if (button == 1 && slot >= 0) {
            if (!Screen.hasControlDown() && !Screen.hasShiftDown()) {
                tab.selectedSlots().clear();
                if (slot < doc.size()) tab.selectedSlots().add(slot);
                tab.setSelectionAnchor(slot);
            }
            tab.setCursorIndex(Math.min(slot, doc.size()));
            openMenuAt((int) mx, (int) my, slot);
            return true;
        }
        return true;
    }

    private void openMenuAt(int mx, int my, int slot) {
        SpellDocument doc = tab.document();
        boolean editable = !doc.isSealed();
        boolean hasSel = !tab.selectedSlots().isEmpty();
        boolean hasClip = !callbacks.session().clipboard().isEmpty();
        List<ContextMenuOverlay.MenuItem> items = List.of(
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.remove"), editable && hasSel, this::removeSelected),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.copy_pattern"), hasSel, this::copySelected),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.paste_pattern"), editable && hasClip, this::pasteAtSelection),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.redraw"), editable && hasSel,
                () -> callbacks.openDrawModal(doc.pageIndex(), slot, false)),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.draw_backward"), editable,
                () -> callbacks.openDrawModal(doc.pageIndex(), slot, true)),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.cast_collect"), editable,
                () -> callbacks.openCastCollectModal(doc.pageIndex(), Math.min(slot, doc.size())))
        );
        callbacks.openContextMenu(mx, my, items);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0) return false;
        if (pendingDrag && Math.hypot(mx - dragStartX, my - dragStartY) >= DRAG_THRESHOLD) {
            pendingDrag = false;
            activeDrag = true;
        }
        if (activeDrag) {
            dragMouseX = mx;
            dragMouseY = my;
            GridLayout gl = computeLayout();
            int max = Math.max(0, gl.totalRows() - gl.rowsPerView());
            if (my < gl.gridTop() + STEP && my >= bounds.y()) {
                tab.setScrollRow(Mth.clamp(tab.scrollRow() - 1, 0, max));
            } else if (my > gl.gridBottom() - STEP && my < bounds.bottom()) {
                tab.setScrollRow(Mth.clamp(tab.scrollRow() + 1, 0, max));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0) return false;
        boolean wasActive = activeDrag;
        pendingDrag = false;
        activeDrag = false;
        if (wasActive && bounds.contains(mx, my)) {
            int dropIdx = insertionIndexAt(mx, my);
            int movedPos = tab.document().moveBlock(tab.selectedSlots(), dropIdx, "sequence");
            if (movedPos >= 0) {
                int count = tab.selectedSlots().size();
                tab.selectedSlots().clear();
                for (int i = 0; i < count; i++) {
                    tab.selectedSlots().add(movedPos + i);
                }
                tab.setSelectionAnchor(movedPos);
                tab.setCursorIndex(movedPos + count);
                callbacks.onDocumentMutated(tab.document(), "sequence");
            }
            return true;
        }
        return wasActive;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!bounds.contains(mx, my)) return false;
        GridLayout gl = computeLayout();
        int max = Math.max(0, gl.totalRows() - gl.rowsPerView());
        int step = (int) Math.signum(delta);
        tab.setScrollRow(Mth.clamp(tab.scrollRow() - step, 0, max));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        SpellDocument doc = tab.document();
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
            pasteAtSelection();
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
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!tab.selectedSlots().isEmpty()) {
                removeSelected();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE && tab.cursorIndex() > 0 && !doc.isSealed()) {
                int target = tab.cursorIndex() - 1;
                if (doc.removeIndices(List.of(target), "sequence")) {
                    tab.setCursorIndex(target);
                    callbacks.onDocumentMutated(doc, "sequence");
                }
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            tab.setCursorIndex(tab.cursorIndex() - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            tab.setCursorIndex(tab.cursorIndex() + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            tab.setCursorIndex(0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_END) {
            tab.setCursorIndex(doc.size());
            return true;
        }
        return false;
    }

    public void copySelected() {
        List<Integer> sorted = new ArrayList<>(tab.selectedSlots());
        Collections.sort(sorted);
        List<CompoundTag> iotas = tab.document().iotasView();
        List<CompoundTag> copied = new ArrayList<>();
        for (int idx : sorted) {
            if (idx >= 0 && idx < iotas.size()) {
                copied.add(iotas.get(idx).copy());
            }
        }
        if (!copied.isEmpty()) {
            callbacks.session().setClipboard(copied);
        }
    }

    public void removeSelected() {
        SpellDocument doc = tab.document();
        if (doc.isSealed() || tab.selectedSlots().isEmpty()) return;
        int minSel = Collections.min(tab.selectedSlots());
        if (doc.removeIndices(tab.selectedSlots(), "sequence")) {
            tab.selectedSlots().clear();
            tab.setSelectionAnchor(-1);
            tab.setCursorIndex(minSel);
            callbacks.onDocumentMutated(doc, "sequence");
        }
    }

    public void pasteAtSelection() {
        SpellDocument doc = tab.document();
        List<CompoundTag> clip = callbacks.session().clipboard();
        if (doc.isSealed() || clip.isEmpty()) return;
        int insertAt = tab.cursorIndex();
        if (!tab.selectedSlots().isEmpty()) {
            insertAt = Collections.min(tab.selectedSlots());
            doc.removeIndices(tab.selectedSlots(), "sequence");
        }
        if (doc.insertAt(insertAt, clip, "sequence")) {
            tab.selectedSlots().clear();
            for (int i = 0; i < clip.size(); i++) {
                tab.selectedSlots().add(insertAt + i);
            }
            tab.setSelectionAnchor(insertAt);
            tab.setCursorIndex(insertAt + clip.size());
            callbacks.onDocumentMutated(doc, "sequence");
        }
    }

    public void insertTagsAtCursor(List<CompoundTag> tags, String source) {
        SpellDocument doc = tab.document();
        if (doc.isSealed() || tags == null || tags.isEmpty()) return;
        int at = tab.cursorIndex();
        if (doc.insertAt(at, tags, source)) {
            tab.selectedSlots().clear();
            for (int i = 0; i < tags.size(); i++) {
                tab.selectedSlots().add(at + i);
            }
            tab.setSelectionAnchor(at);
            tab.setCursorIndex(at + tags.size());
            callbacks.onDocumentMutated(doc, source);
        }
    }

    public void acceptCatalogDrop(PatternCatalogEntry entry, double mx, double my) {
        SpellDocument doc = tab.document();
        if (doc.isSealed() || entry == null) return;
        int target = insertionIndexAt(mx, my);
        PatternRef ref = entry.pattern();
        if (ref.signature().isBlank()) {
            callbacks.session().requestGreatSpell(ref.actionId(), doc.pageIndex(), target);
            return;
        }
        CompoundTag tag = StaffHex.serializeIota(new PatternIota(StaffHex.patternFor(ref)));
        if (doc.insertAt(target, List.of(tag), "catalog")) {
            tab.selectedSlots().clear();
            tab.selectedSlots().add(target);
            tab.setSelectionAnchor(target);
            tab.setCursorIndex(target + 1);
            callbacks.onDocumentMutated(doc, "catalog");
        }
    }
}
