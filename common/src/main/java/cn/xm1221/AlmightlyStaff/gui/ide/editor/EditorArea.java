package cn.xm1221.AlmightlyStaff.gui.ide.editor;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.ContextMenuOverlay;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.gui.ide.layout.DockNode;
import cn.xm1221.AlmightlyStaff.gui.ide.layout.SplitterDragState;
import cn.xm1221.AlmightlyStaff.gui.ide.model.SpellDocument;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 编辑器主区域：支持单组、左右分屏或上下分屏两个 {@link TabGroup} 对照编辑。
 */
public final class EditorArea implements IdeComponent, TabGroup.Host {
    public interface Callbacks {
        void onSetCastPage(int pageIndex);

        void openContextMenu(int mx, int my, List<ContextMenuOverlay.MenuItem> items);

        TabItem cloneTabForSplit(TabItem source);

        void onActiveDocumentChanged(@Nullable SpellDocument doc);
    }

    private final Callbacks callbacks;
    private final TabGroup primary;
    private final TabGroup secondary;
    private final DockNode.LeafNode primaryLeaf = new DockNode.LeafNode("editor_primary");
    private final DockNode.LeafNode secondaryLeaf = new DockNode.LeafNode("editor_secondary", true);
    private DockNode.SplitNode splitRoot;
    private final SplitterDragState splitterDrag = new SplitterDragState();
    private TabGroup focusedGroup;
    private Bounds bounds = Bounds.EMPTY;
    private boolean splitVertical = false;

    public EditorArea(Callbacks callbacks) {
        this.callbacks = callbacks;
        this.primary = new TabGroup("primary", this);
        this.secondary = new TabGroup("secondary", this);
        this.focusedGroup = primary;
        this.primary.setFocusedGroup(true);
        this.splitRoot = new DockNode.SplitNode("editor_split", DockNode.Axis.HORIZONTAL, 0.50f,
            120, 120, primaryLeaf, secondaryLeaf);
    }

    public TabGroup primary() {
        return primary;
    }

    public TabGroup secondary() {
        return secondary;
    }

    public TabGroup focusedGroup() {
        return focusedGroup;
    }

    public boolean isSplitOpen() {
        return !secondaryLeaf.isCollapsed();
    }

    public float splitRatio() {
        return splitRoot.ratio();
    }

    public void setSplitState(boolean open, float ratio, boolean vertical) {
        this.splitVertical = vertical;
        this.splitRoot = new DockNode.SplitNode("editor_split",
            vertical ? DockNode.Axis.VERTICAL : DockNode.Axis.HORIZONTAL,
            ratio, 120, 120, primaryLeaf, secondaryLeaf);
        secondaryLeaf.setCollapsed(!open);
        if (!open) {
            setFocusedGroup(primary);
        }
        layout(bounds);
    }

    public void toggleSplit(Supplier<TabItem> fallbackSupplier) {
        if (isSplitOpen()) {
            setSplitState(false, splitRoot.ratio(), splitVertical);
            return;
        }
        if (secondary.activeTab() == null) {
            if (primary.activeTab() != null) {
                secondary.openOrFocus(callbacks.cloneTabForSplit(primary.activeTab()));
            } else if (fallbackSupplier != null) {
                TabItem item = fallbackSupplier.get();
                if (item != null) secondary.openOrFocus(item);
            }
        }
        setSplitState(true, splitRoot.ratio(), false);
        setFocusedGroup(secondary);
    }

    public void setFocusedGroup(TabGroup group) {
        this.focusedGroup = group == secondary && isSplitOpen() ? secondary : primary;
        primary.setFocusedGroup(this.focusedGroup == primary);
        secondary.setFocusedGroup(this.focusedGroup == secondary);
        callbacks.onActiveDocumentChanged(activeSpellDocument());
    }

    public void openInFocused(TabItem item) {
        focusedGroup.openOrFocus(item);
        callbacks.onActiveDocumentChanged(activeSpellDocument());
    }

    public void openInSplit(TabItem item, boolean vertical) {
        setSplitState(true, splitRoot.ratio(), vertical);
        secondary.openOrFocus(item);
        setFocusedGroup(secondary);
    }

    public void closeSpellPageEverywhere(int pageIndex) {
        String id = "spell:" + pageIndex;
        primary.closeByTabId(id);
        secondary.closeByTabId(id);
        callbacks.onActiveDocumentChanged(activeSpellDocument());
    }

    @Nullable
    public TabItem.SpellTab activeSpellTab() {
        if (focusedGroup.activeTab() instanceof TabItem.SpellTab st) return st;
        if (primary.activeTab() instanceof TabItem.SpellTab st) return st;
        if (isSplitOpen() && secondary.activeTab() instanceof TabItem.SpellTab st) return st;
        return null;
    }

    @Nullable
    public SpellDocument activeSpellDocument() {
        TabItem.SpellTab st = activeSpellTab();
        return st == null ? null : st.document();
    }

    @Nullable
    public SequenceEditorView findSequenceViewAt(double mx, double my) {
        if (isSplitOpen() && secondary.bounds().contains(mx, my)
            && secondary.activeTab() instanceof TabItem.SpellTab st) {
            return st.view();
        }
        if (primary.bounds().contains(mx, my)
            && primary.activeTab() instanceof TabItem.SpellTab st) {
            return st.view();
        }
        return null;
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public void layout(Bounds bounds) {
        this.bounds = bounds;
        splitRoot.layout(bounds);
        primary.layout(primaryLeaf.bounds());
        secondary.layout(secondaryLeaf.bounds());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (bounds.isEmpty()) return;
        primary.render(g, mouseX, mouseY, partialTick);
        if (isSplitOpen()) {
            secondary.render(g, mouseX, mouseY, partialTick);
            Bounds sb = splitRoot.splitterBounds();
            if (!sb.isEmpty()) {
                boolean active = splitterDrag.isDragging() || sb.contains(mouseX, mouseY);
                g.fill(sb.x(), sb.y(), sb.right(), sb.bottom(), IdeTheme.BORDER);
                if (active) {
                    if (splitRoot.axis() == DockNode.Axis.HORIZONTAL) {
                        int cx = sb.x() + sb.w() / 2;
                        g.fill(cx, sb.y(), cx + 1, sb.bottom(), IdeTheme.ACCENT);
                    } else {
                        int cy = sb.y() + sb.h() / 2;
                        g.fill(sb.x(), cy, sb.right(), cy + 1, IdeTheme.ACCENT);
                    }
                }
            }
        }
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        primary.renderOverlay(g, mouseX, mouseY, partialTick);
        if (isSplitOpen()) {
            secondary.renderOverlay(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        if (button == 0 && isSplitOpen() && splitterDrag.beginDrag(splitRoot, mx, my)) {
            return true;
        }
        if (isSplitOpen() && secondary.bounds().contains(mx, my)) {
            return secondary.mouseClicked(mx, my, button);
        }
        return primary.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (splitterDrag.drag(splitRoot, bounds, mx, my)) {
            primary.layout(primaryLeaf.bounds());
            secondary.layout(secondaryLeaf.bounds());
            return true;
        }
        if (focusedGroup.mouseDragged(mx, my, button, dx, dy)) return true;
        TabGroup other = focusedGroup == primary ? secondary : primary;
        return isSplitOpen() && other.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (splitterDrag.endDrag()) return true;
        boolean a = primary.mouseReleased(mx, my, button);
        boolean b = isSplitOpen() && secondary.mouseReleased(mx, my, button);
        return a || b;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!bounds.contains(mx, my)) return false;
        if (isSplitOpen() && secondary.bounds().contains(mx, my)) {
            return secondary.mouseScrolled(mx, my, delta);
        }
        return primary.mouseScrolled(mx, my, delta);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        primary.mouseMoved(mx, my);
        if (isSplitOpen()) secondary.mouseMoved(mx, my);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return focusedGroup.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return focusedGroup.charTyped(codePoint, modifiers);
    }

    @Override
    public void tick() {
        primary.tick();
        if (isSplitOpen()) secondary.tick();
    }

    @Override
    public void onGroupFocused(TabGroup group) {
        setFocusedGroup(group);
    }

    @Override
    public void onSplitRequested(TabGroup source, TabItem item, boolean vertical) {
        TabItem clone = callbacks.cloneTabForSplit(item);
        openInSplit(clone, vertical);
    }

    @Override
    public void onTabClosed(TabGroup group, TabItem item) {
        if (group == secondary && secondary.tabs().isEmpty()) {
            setSplitState(false, splitRoot.ratio(), splitVertical);
        }
        callbacks.onActiveDocumentChanged(activeSpellDocument());
    }

    @Override
    public void onSetCastPage(int pageIndex) {
        callbacks.onSetCastPage(pageIndex);
    }

    @Override
    public void openContextMenu(int mx, int my, List<ContextMenuOverlay.MenuItem> items) {
        callbacks.openContextMenu(mx, my, items);
    }
}
