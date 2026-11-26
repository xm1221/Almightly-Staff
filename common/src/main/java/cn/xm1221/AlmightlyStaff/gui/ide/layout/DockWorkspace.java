package cn.xm1221.AlmightlyStaff.gui.ide.layout;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.gui.ide.model.WorkspaceState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 工作区自由停靠与浮动协调器：
 * <ul>
 *   <li>管理递归 {@link DockNode} 槽位渲染、多 Tab 标签栏与拖拽撕裂浮动</li>
 *   <li>管理分割条拖拽与双击 50/50 恢复</li>
 *   <li>管理停靠窗口的工作区内最大化、最小化到工具条、拖回停靠区预览与落位</li>
 * </ul>
 */
public final class DockWorkspace {
    public static final int HEADER_H = 20;

    public record WindowDef(
        String id,
        Component title,
        IdeTheme.Icon icon,
        IdeComponent content,
        int minW,
        int minH,
        boolean closable
    ) {
        public WindowDef withTitle(Component nextTitle) {
            return new WindowDef(id, nextTitle, icon, content, minW, minH, closable);
        }
    }

    private record TabHit(DockNode.LeafNode leaf, String windowId, Bounds tabBounds,
                          @Nullable Bounds closeBounds, Component tooltip) {
    }

    private record ActionHit(@Nullable DockNode.LeafNode leaf, String windowId, String action, Bounds bounds, Component tooltip) {
    }

    private final WindowManager windowManager;
    private final Runnable onLayoutChanged;
    private final Consumer<String> onWindowClosed;

    private final Map<String, WindowDef> registry = new LinkedHashMap<>();
    private final Set<String> minimized = new HashSet<>();
    private final Map<String, Bounds> lastFloatingBounds = new HashMap<>();

    private DockNode root = new DockNode.LeafNode("slot_main", List.of("iota_text"));
    private Bounds dockBounds = Bounds.EMPTY;
    private final SplitterDragState splitterDrag = new SplitterDragState();

    @Nullable
    private String maximizedDockedWindowId = null;
    @Nullable
    private String focusedWindowId = "iota_text";

    @Nullable
    private String pendingTearWindowId = null;
    private double tearStartX, tearStartY;
    @Nullable
    private DockNode.DropTarget activeDropPreview = null;

    private long lastSplitterClickMs = 0L;
    @Nullable
    private String lastSplitterId = null;
    private long lastHeaderClickMs = 0L;
    @Nullable
    private String lastHeaderWindowId = null;

    private final List<TabHit> tabHits = new ArrayList<>();
    private final List<ActionHit> actionHits = new ArrayList<>();

    public DockWorkspace(WindowManager windowManager, Runnable onLayoutChanged, Consumer<String> onWindowClosed) {
        this.windowManager = windowManager;
        this.onLayoutChanged = onLayoutChanged;
        this.onWindowClosed = onWindowClosed;
    }

    public void registerWindow(WindowDef def) {
        registry.put(def.id(), def);
    }

    public void updateTitle(String id, Component title) {
        WindowDef def = registry.get(id);
        if (def != null) {
            registry.put(id, def.withTitle(title));
        }
    }

    public void unregisterWindow(String id) {
        registry.remove(id);
        minimized.remove(id);
        if (id.equals(maximizedDockedWindowId)) {
            maximizedDockedWindowId = null;
        }
        root = DockNode.removeWindow(root, id);
        windowManager.remove(id);
        layout(dockBounds);
    }

    @Nullable
    public WindowDef windowDef(String id) {
        return registry.get(id);
    }

    public DockNode root() {
        return root;
    }

    public Bounds dockBounds() {
        return dockBounds;
    }

    public void setRoot(DockNode next) {
        this.root = next != null ? next : new DockNode.LeafNode("slot_main", List.of("iota_text"));
        this.maximizedDockedWindowId = null;
        this.activeDropPreview = null;
    }

    public boolean isOpen(String id) {
        return root.findLeafContainingWindow(id) != null || windowManager.find(id) != null;
    }

    public boolean isMinimized(String id) {
        return minimized.contains(id);
    }

    @Nullable
    public String focusedWindowId() {
        return focusedWindowId;
    }

    public boolean hasMaximizedDocked() {
        return maximizedDockedWindowId != null;
    }

    public void unmaximizeDocked() {
        if (maximizedDockedWindowId != null) {
            maximizedDockedWindowId = null;
            layout(dockBounds);
        }
    }

    public void applyWorkspace(WorkspaceState ws) {
        windowManager.windows().forEach(w -> windowManager.remove(w.id()));
        minimized.clear();
        maximizedDockedWindowId = null;
        lastFloatingBounds.clear();

        DockNode loaded = DockNode.fromDto(ws.dockTree);
        // 清理未注册的废弃窗口 ID
        List<DockNode.LeafNode> leaves = new ArrayList<>();
        loaded.collectLeaves(leaves);
        for (DockNode.LeafNode leaf : leaves) {
            for (String w : List.copyOf(leaf.windows())) {
                if (!registry.containsKey(w)) {
                    leaf.removeWindow(w);
                }
            }
        }
        // 确保主工作区至少有 iota_text
        if (loaded.findLeafContainingWindow("iota_text") == null && ws.windowPlacements.get("iota_text") == null) {
            List<DockNode.LeafNode> rem = new ArrayList<>();
            loaded.collectLeaves(rem);
            if (!rem.isEmpty()) {
                rem.get(0).addWindow("iota_text");
            }
        }
        this.root = DockNode.removeWindow(loaded, "__nonexistent__");

        for (Map.Entry<String, WorkspaceState.WindowPlacement> entry : ws.windowPlacements.entrySet()) {
            String wid = entry.getKey();
            WorkspaceState.WindowPlacement pl = entry.getValue();
            if (!registry.containsKey(wid) || pl == null) continue;
            Bounds fb = new Bounds(pl.x, pl.y, Math.max(180, pl.w), Math.max(120, pl.h));
            lastFloatingBounds.put(wid, fb);
            if (pl.floating) {
                root = DockNode.removeWindow(root, wid);
                spawnFloating(wid, fb, false, 0, 0);
            } else if (pl.minimized) {
                root = DockNode.removeWindow(root, wid);
                minimized.add(wid);
            }
        }
        layout(dockBounds);
    }

    public void saveToWorkspace(WorkspaceState ws) {
        ws.dockTree = root.toDto();
        ws.windowPlacements.clear();
        for (WindowDef def : registry.values()) {
            String id = def.id();
            FloatingWindow fw = windowManager.find(id);
            if (fw != null) {
                Bounds b = fw.bounds();
                lastFloatingBounds.put(id, b);
                ws.windowPlacements.put(id, new WorkspaceState.WindowPlacement(true, false, b.x(), b.y(), b.w(), b.h()));
            } else if (minimized.contains(id)) {
                Bounds b = lastFloatingBounds.getOrDefault(id, new Bounds(80, 60, Math.max(240, def.minW()), Math.max(180, def.minH())));
                ws.windowPlacements.put(id, new WorkspaceState.WindowPlacement(false, true, b.x(), b.y(), b.w(), b.h()));
            }
        }
    }

    public void openOrFocus(String windowId) {
        WindowDef def = registry.get(windowId);
        if (def == null) return;
        FloatingWindow fw = windowManager.find(windowId);
        if (fw != null) {
            windowManager.bringToFront(fw);
            return;
        }
        DockNode.LeafNode leaf = root.findLeafContainingWindow(windowId);
        if (leaf != null) {
            leaf.focusWindow(windowId);
            if (maximizedDockedWindowId != null && !maximizedDockedWindowId.equals(windowId)) {
                maximizedDockedWindowId = null;
            }
            layout(dockBounds);
            onLayoutChanged.run();
            return;
        }
        // 从最小化或未打开状态打开：优先恢复为停靠（若曾经是浮动且有位置则放回默认停靠槽，侧栏点击总是快速可用）
        minimized.remove(windowId);
        maximizedDockedWindowId = null;
        DockNode.DropZone defaultZone = defaultDockZone(windowId);
        List<DockNode.LeafNode> leaves = new ArrayList<>();
        root.collectLeaves(leaves);
        if (leaves.isEmpty()) {
            root = new DockNode.LeafNode("slot_main", List.of(windowId));
        } else if (leaves.size() == 1 && leaves.get(0).windows().isEmpty()) {
            leaves.get(0).addWindow(windowId);
        } else {
            DockNode.LeafNode target = defaultZone == DockNode.DropZone.LEFT
                ? leaves.get(0)
                : leaves.get(leaves.size() - 1);
            if (defaultZone == DockNode.DropZone.CENTER) {
                target.addWindow(windowId);
            } else {
                root = DockNode.dockWindow(root, windowId, target, defaultZone);
            }
        }
        layout(dockBounds);
        onLayoutChanged.run();
    }

    public void toggleFromStripe(String windowId) {
        WindowDef def = registry.get(windowId);
        if (def == null) return;
        FloatingWindow fw = windowManager.find(windowId);
        if (fw != null) {
            if (windowManager.activeWindow() == fw) {
                minimizeWindow(windowId);
            } else {
                windowManager.bringToFront(fw);
            }
            return;
        }
        DockNode.LeafNode leaf = root.findLeafContainingWindow(windowId);
        if (leaf != null) {
            if (!windowId.equals(leaf.activeWindowId())) {
                leaf.focusWindow(windowId);
                layout(dockBounds);
                onLayoutChanged.run();
            } else {
                minimizeWindow(windowId);
            }
            return;
        }
        openOrFocus(windowId);
    }

    public void minimizeWindow(String windowId) {
        FloatingWindow fw = windowManager.find(windowId);
        if (fw != null) {
            lastFloatingBounds.put(windowId, fw.bounds());
            windowManager.remove(windowId);
        }
        if (windowId.equals(maximizedDockedWindowId)) {
            maximizedDockedWindowId = null;
        }
        root = DockNode.removeWindow(root, windowId);
        minimized.add(windowId);
        layout(dockBounds);
        onLayoutChanged.run();
    }

    public void closeWindow(String windowId) {
        WindowDef def = registry.get(windowId);
        if (def != null && !def.closable()) {
            minimizeWindow(windowId);
            return;
        }
        FloatingWindow fw = windowManager.find(windowId);
        if (fw != null) {
            lastFloatingBounds.put(windowId, fw.bounds());
            windowManager.remove(windowId);
        }
        if (windowId.equals(maximizedDockedWindowId)) {
            maximizedDockedWindowId = null;
        }
        root = DockNode.removeWindow(root, windowId);
        minimized.remove(windowId);
        layout(dockBounds);
        onWindowClosed.accept(windowId);
        onLayoutChanged.run();
    }

    public void floatWindow(String windowId, @Nullable Bounds overrideBounds, boolean startDrag, double mx, double my) {
        WindowDef def = registry.get(windowId);
        if (def == null) return;
        minimized.remove(windowId);
        if (windowId.equals(maximizedDockedWindowId)) {
            maximizedDockedWindowId = null;
        }
        root = DockNode.removeWindow(root, windowId);

        Bounds fb = overrideBounds;
        if (fb == null) {
            Bounds prev = lastFloatingBounds.get(windowId);
            if (prev != null && !prev.isEmpty()) {
                fb = prev;
            } else {
                int w = Math.max(def.minW(), Math.min(360, Math.max(220, dockBounds.w() / 2)));
                int h = Math.max(def.minH(), Math.min(260, Math.max(160, dockBounds.h() * 2 / 3)));
                int fx = dockBounds.x() + Math.max(16, (dockBounds.w() - w) / 2);
                int fy = dockBounds.y() + Math.max(16, (dockBounds.h() - h) / 2);
                fb = new Bounds(fx, fy, w, h);
            }
        }
        spawnFloating(windowId, fb, startDrag, mx, my);
        layout(dockBounds);
        onLayoutChanged.run();
    }

    private void spawnFloating(String windowId, Bounds fb, boolean startDrag, double mx, double my) {
        WindowDef def = registry.get(windowId);
        if (def == null) return;
        FloatingWindow fw = new FloatingWindow(
            windowId, def.title(), def.icon(), windowId.startsWith("note:"),
            def.content(), fb, def.minW(), def.minH(),
            () -> minimizeWindow(windowId),
            () -> closeWindow(windowId)
        );
        windowManager.add(fw);
        windowManager.clampAll(dockBounds);
        if (startDrag) {
            fw.beginExternalDrag(mx, my);
        }
    }

    private static DockNode.DropZone defaultDockZone(String windowId) {
        return switch (windowId) {
            case "page_tree", "hex_grid" -> DockNode.DropZone.LEFT;
            case "hex_parse", "sequence_grid" -> DockNode.DropZone.BOTTOM;
            case "iota_text", "pattern_catalog", "cast_collect" -> DockNode.DropZone.RIGHT;
            default -> DockNode.DropZone.CENTER;
        };
    }

    public void layout(Bounds area) {
        this.dockBounds = area == null ? Bounds.EMPTY : area;
        if (maximizedDockedWindowId != null && root.findLeafContainingWindow(maximizedDockedWindowId) == null) {
            maximizedDockedWindowId = null;
        }
        int inset = IdeTheme.contentInset();
        if (maximizedDockedWindowId != null) {
            WindowDef def = registry.get(maximizedDockedWindowId);
            if (def != null && !dockBounds.isEmpty()) {
                def.content().layout(dockBounds.inset(inset, HEADER_H, inset, inset));
            }
            return;
        }
        root.layout(this.dockBounds);
        List<DockNode.LeafNode> leaves = new ArrayList<>();
        root.collectLeaves(leaves);
        for (DockNode.LeafNode leaf : leaves) {
            String activeId = leaf.activeWindowId();
            if (activeId == null) continue;
            WindowDef def = registry.get(activeId);
            if (def != null) {
                def.content().layout(leaf.bounds().inset(inset, HEADER_H, inset, inset));
            }
        }
    }

    public void tick() {
        List<DockNode.LeafNode> leaves = new ArrayList<>();
        root.collectLeaves(leaves);
        for (DockNode.LeafNode leaf : leaves) {
            String activeId = leaf.activeWindowId();
            if (activeId == null) continue;
            WindowDef def = registry.get(activeId);
            if (def != null) {
                def.content().tick();
            }
        }
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick) {
        tabHits.clear();
        actionHits.clear();
        if (dockBounds.isEmpty()) return;

        if (maximizedDockedWindowId != null) {
            WindowDef def = registry.get(maximizedDockedWindowId);
            if (def != null) {
                renderSlot(g, font, null, List.of(def.id()), 0, dockBounds, mouseX, mouseY, partialTick, true);
                return;
            }
            maximizedDockedWindowId = null;
        }

        List<DockNode.LeafNode> leaves = new ArrayList<>();
        root.collectLeaves(leaves);
        for (DockNode.LeafNode leaf : leaves) {
            Bounds b = leaf.bounds();
            if (b.isEmpty()) continue;
            renderSlot(g, font, leaf, leaf.windows(), leaf.activeTabIndex(), b, mouseX, mouseY, partialTick, false);
        }
        renderSplitters(g, mouseX, mouseY);
    }

    private void renderSlot(GuiGraphics g, Font font, @Nullable DockNode.LeafNode leaf,
                            List<String> windows, int activeIdx, Bounds slotBounds,
                            int mouseX, int mouseY, float partialTick, boolean isMaximized) {
        IdeTheme.drawNineSlice(g, slotBounds, IdeTheme.NineSlice.PANEL);
        Bounds headerB = new Bounds(slotBounds.x(), slotBounds.y(), slotBounds.w(), Math.min(HEADER_H, slotBounds.h()));
        IdeTheme.drawNineSlice(g, headerB, IdeTheme.NineSlice.HEADER);

        String activeId = activeIdx >= 0 && activeIdx < windows.size() ? windows.get(activeIdx) : null;
        WindowDef activeDef = activeId != null ? registry.get(activeId) : null;

        // 右侧槽位动作钮：[□/❐最大化] [⧉浮动] [×关闭]
        int ax = headerB.right() - 16;
        int ay = headerB.y() + 3;
        if (activeDef != null) {
            Bounds cb = new Bounds(ax, ay, 14, 14);
            boolean hov = cb.contains(mouseX, mouseY);
            if (hov) IdeTheme.drawNineSlice(g, cb, IdeTheme.NineSlice.BTN_HOVER);
            IdeTheme.drawIcon(g, IdeTheme.Icon.CLOSE_X, cb.x() + 3, cb.y() + 3, hov ? IdeTheme.RED_ERROR : IdeTheme.TEXT_MUTED);
            actionHits.add(new ActionHit(leaf, activeDef.id(), "close", cb,
                Component.translatable("almightly_staff.ide.close_window")));
            ax -= 16;
        }
        if (activeDef != null && !isMaximized) {
            Bounds fb = new Bounds(ax, ay, 14, 14);
            boolean hov = fb.contains(mouseX, mouseY);
            if (hov) IdeTheme.drawNineSlice(g, fb, IdeTheme.NineSlice.BTN_HOVER);
            IdeTheme.drawIcon(g, IdeTheme.Icon.FLOAT_WIN, fb.x() + 3, fb.y() + 3, hov ? IdeTheme.TEXT_MAIN : IdeTheme.TEXT_MUTED);
            actionHits.add(new ActionHit(leaf, activeDef.id(), "float", fb,
                Component.translatable("almightly_staff.ide.float_window")));
            ax -= 16;
        }
        if (activeDef != null) {
            Bounds mb = new Bounds(ax, ay, 14, 14);
            boolean hov = mb.contains(mouseX, mouseY);
            if (hov) IdeTheme.drawNineSlice(g, mb, IdeTheme.NineSlice.BTN_HOVER);
            IdeTheme.drawIcon(g, isMaximized ? IdeTheme.Icon.RESTORE_BOX : IdeTheme.Icon.MAXIMIZE_BOX,
                mb.x() + 3, mb.y() + 3, hov ? IdeTheme.TEXT_MAIN : IdeTheme.TEXT_MUTED);
            actionHits.add(new ActionHit(leaf, activeDef.id(), "maximize", mb,
                Component.translatable(isMaximized ? "almightly_staff.ide.restore_window" : "almightly_staff.ide.maximize")));
            ax -= 4;
        }

        // 左侧全图标标签页条（无文本窗口种类名，悬停显示完整名称；动态笔记/外部界面带 inline 关闭叉）
        int tabX = headerB.x() + 2;
        int tabMaxRight = Math.max(tabX, ax);
        try (IdeScissor.Scope sc = IdeScissor.push(g, headerB.x(), headerB.y(), Math.max(0, tabMaxRight - headerB.x()), headerB.h())) {
            for (int i = 0; i < windows.size(); i++) {
                String wid = windows.get(i);
                WindowDef def = registry.get(wid);
                if (def == null) continue;
                boolean isAct = (i == activeIdx);
                boolean showClose = def.closable() && leaf != null;
                boolean isNote = wid.startsWith("note:");
                int desiredW = isNote
                    ? Math.min(120, 18 + font.width(def.title().getString()) + (showClose ? 14 : 6))
                    : (showClose ? 30 : 20);
                int avail = tabMaxRight - tabX;
                if (avail < 18) break;
                int tw = Math.min(desiredW, avail);
                Bounds tb = new Bounds(tabX, headerB.y() + 1, tw, headerB.h() - 1);
                IdeTheme.drawNineSlice(g, tb, isAct ? IdeTheme.NineSlice.TAB_ACTIVE : IdeTheme.NineSlice.TAB_INACTIVE);
                if (isAct) {
                    g.fill(tb.x() + 1, tb.y(), tb.right() - 1, tb.y() + 2, IdeTheme.ACCENT);
                }
                int iconX = isNote ? tb.x() + 4 : tb.x() + (showClose ? 4 : Math.max(1, (tw - 8) / 2));
                IdeTheme.drawIcon(g, def.icon(), iconX, tb.y() + 5, isAct ? IdeTheme.ACCENT_LIGHT : IdeTheme.TEXT_MUTED);
                if (isNote) {
                    int textW = Math.max(4, tw - 16 - (showClose ? 14 : 4));
                    IdeTheme.drawClippedString(g, font, def.title(), tb.x() + 14, tb.y() + 5, textW,
                        isAct ? IdeTheme.TEXT_MAIN : IdeTheme.TEXT_MUTED);
                }

                Bounds closeB = null;
                if (showClose && tw >= 26) {
                    closeB = new Bounds(tb.right() - 12, tb.y() + 4, 10, 10);
                    boolean ch = closeB.contains(mouseX, mouseY);
                    if (ch || isAct) {
                        IdeTheme.drawIcon(g, IdeTheme.Icon.CLOSE_X, closeB.x() + 1, closeB.y() + 1,
                            ch ? IdeTheme.RED_ERROR : IdeTheme.TEXT_MUTED);
                    }
                }
                if (leaf != null) {
                    tabHits.add(new TabHit(leaf, wid, tb, closeB, def.title()));
                }
                tabX += tw + 1;
            }
        }

        if (activeDef != null) {
            activeDef.content().render(g, mouseX, mouseY, partialTick);
        }
        if (IdeTheme.borderThickness() > 1) {
            IdeTheme.drawBorder(g, slotBounds, IdeTheme.BORDER);
        }
    }

    private void renderSplitters(GuiGraphics g, int mx, int my) {
        List<DockNode.SplitNode> splits = new ArrayList<>();
        root.collectSplitters(splits);
        int accentW = Math.max(1, IdeTheme.borderThickness());
        for (DockNode.SplitNode s : splits) {
            Bounds sb = s.splitterBounds();
            if (sb.isEmpty()) continue;
            boolean hot = splitterDrag.draggingNode() == s || sb.contains(mx, my);
            g.fill(sb.x(), sb.y(), sb.right(), sb.bottom(), IdeTheme.BORDER);
            if (hot) {
                if (s.axis() == DockNode.Axis.HORIZONTAL) {
                    int w = Math.min(sb.w(), accentW);
                    int cx = sb.x() + Math.max(0, (sb.w() - w) / 2);
                    g.fill(cx, sb.y(), cx + w, sb.bottom(), IdeTheme.ACCENT);
                } else {
                    int h = Math.min(sb.h(), accentW);
                    int cy = sb.y() + Math.max(0, (sb.h() - h) / 2);
                    g.fill(sb.x(), cy, sb.right(), cy + h, IdeTheme.ACCENT);
                }
            }
        }
    }

    public void renderDropPreview(GuiGraphics g) {
        if (activeDropPreview == null) return;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 95.0F);
        try {
            IdeTheme.drawNineSlice(g, activeDropPreview.previewBounds(), IdeTheme.NineSlice.DOCK_PREVIEW);
        } finally {
            ps.popPose();
        }
    }

    public void renderOverlay(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick) {
        for (ActionHit hit : actionHits) {
            if (hit.bounds().contains(mouseX, mouseY)) {
                g.renderTooltip(font, hit.tooltip(), mouseX, mouseY);
                return;
            }
        }
        for (TabHit hit : tabHits) {
            if (hit.tabBounds().contains(mouseX, mouseY)) {
                g.renderTooltip(font, hit.tooltip(), mouseX, mouseY);
                return;
            }
        }
        if (maximizedDockedWindowId != null) {
            WindowDef def = registry.get(maximizedDockedWindowId);
            if (def != null) def.content().renderOverlay(g, mouseX, mouseY, partialTick);
            return;
        }
        List<DockNode.LeafNode> leaves = new ArrayList<>();
        root.collectLeaves(leaves);
        for (DockNode.LeafNode leaf : leaves) {
            String activeId = leaf.activeWindowId();
            if (activeId == null) continue;
            WindowDef def = registry.get(activeId);
            if (def != null) {
                def.content().renderOverlay(g, mouseX, mouseY, partialTick);
            }
        }
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (!dockBounds.contains(mx, my)) return false;

        if (maximizedDockedWindowId == null && button == 0) {
            DockNode.SplitNode hitSplit = root.hitTestSplitter(mx, my);
            if (hitSplit != null) {
                long now = System.currentTimeMillis();
                if (hitSplit.id().equals(lastSplitterId) && now - lastSplitterClickMs <= 280L) {
                    lastSplitterClickMs = 0L;
                    hitSplit.resetHalfOrRestore();
                    layout(dockBounds);
                    onLayoutChanged.run();
                    return true;
                }
                lastSplitterId = hitSplit.id();
                lastSplitterClickMs = now;
                splitterDrag.beginDrag(root, mx, my);
                return true;
            }
        }

        for (ActionHit hit : actionHits) {
            if (button == 0 && hit.bounds().contains(mx, my)) {
                switch (hit.action()) {
                    case "close" -> closeWindow(hit.windowId());
                    case "float" -> floatWindow(hit.windowId(), null, false, mx, my);
                    case "maximize" -> {
                        maximizedDockedWindowId = hit.windowId().equals(maximizedDockedWindowId) ? null : hit.windowId();
                        layout(dockBounds);
                    }
                    default -> {
                    }
                }
                return true;
            }
        }

        for (TabHit hit : tabHits) {
            if (hit.closeBounds() != null && button == 0 && hit.closeBounds().contains(mx, my)) {
                closeWindow(hit.windowId());
                return true;
            }
            if (hit.tabBounds().contains(mx, my)) {
                if (button == 2) {
                    closeWindow(hit.windowId());
                    return true;
                }
                if (button == 0) {
                    hit.leaf().focusWindow(hit.windowId());
                    layout(dockBounds);
                    long now = System.currentTimeMillis();
                    if (hit.windowId().equals(lastHeaderWindowId) && now - lastHeaderClickMs <= 280L) {
                        lastHeaderClickMs = 0L;
                        maximizedDockedWindowId = hit.windowId().equals(maximizedDockedWindowId) ? null : hit.windowId();
                        layout(dockBounds);
                    } else {
                        lastHeaderClickMs = now;
                        lastHeaderWindowId = hit.windowId();
                        pendingTearWindowId = hit.windowId();
                        tearStartX = mx;
                        tearStartY = my;
                    }
                    return true;
                }
            }
        }

        // 双击槽位空白标题栏也切换最大化
        if (button == 0) {
            if (maximizedDockedWindowId != null && my < dockBounds.y() + HEADER_H) {
                long now = System.currentTimeMillis();
                if (maximizedDockedWindowId.equals(lastHeaderWindowId) && now - lastHeaderClickMs <= 280L) {
                    lastHeaderClickMs = 0L;
                    maximizedDockedWindowId = null;
                    layout(dockBounds);
                    return true;
                }
                lastHeaderClickMs = now;
                lastHeaderWindowId = maximizedDockedWindowId;
                return true;
            }
            if (maximizedDockedWindowId == null) {
                List<DockNode.LeafNode> leaves = new ArrayList<>();
                root.collectLeaves(leaves);
                for (DockNode.LeafNode leaf : leaves) {
                    Bounds b = leaf.bounds();
                    if (b.contains(mx, my) && my < b.y() + HEADER_H) {
                        String aid = leaf.activeWindowId();
                        if (aid != null) {
                            long now = System.currentTimeMillis();
                            if (aid.equals(lastHeaderWindowId) && now - lastHeaderClickMs <= 280L) {
                                lastHeaderClickMs = 0L;
                                maximizedDockedWindowId = aid;
                                layout(dockBounds);
                                return true;
                            }
                            lastHeaderClickMs = now;
                            lastHeaderWindowId = aid;
                            pendingTearWindowId = aid;
                            tearStartX = mx;
                            tearStartY = my;
                        }
                        return true;
                    }
                }
            }
        }

        if (maximizedDockedWindowId != null) {
            WindowDef def = registry.get(maximizedDockedWindowId);
            return def != null && def.content().mouseClicked(mx, my, button);
        }

        List<DockNode.LeafNode> leaves = new ArrayList<>();
        root.collectLeaves(leaves);
        for (DockNode.LeafNode leaf : leaves) {
            if (leaf.bounds().contains(mx, my)) {
                String aid = leaf.activeWindowId();
                if (aid != null) {
                    focusedWindowId = aid;
                    WindowDef def = registry.get(aid);
                    if (def != null && def.content().mouseClicked(mx, my, button)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (splitterDrag.drag(root, dockBounds, mx, my)) {
            layout(dockBounds);
            return true;
        }
        if (pendingTearWindowId != null && Math.hypot(mx - tearStartX, my - tearStartY) > 6.0) {
            String wid = pendingTearWindowId;
            pendingTearWindowId = null;
            WindowDef def = registry.get(wid);
            int w = def != null ? Math.max(def.minW(), 280) : 280;
            int h = def != null ? Math.max(def.minH(), 200) : 200;
            Bounds tornBounds = new Bounds((int) mx - Math.min(80, w / 3), (int) my - 10, w, h);
            floatWindow(wid, tornBounds, true, mx, my);
            return true;
        }
        if (maximizedDockedWindowId != null) {
            WindowDef def = registry.get(maximizedDockedWindowId);
            return def != null && def.content().mouseDragged(mx, my, button, dx, dy);
        }
        boolean handled = false;
        List<DockNode.LeafNode> leaves = new ArrayList<>();
        root.collectLeaves(leaves);
        for (DockNode.LeafNode leaf : leaves) {
            String aid = leaf.activeWindowId();
            if (aid == null) continue;
            WindowDef def = registry.get(aid);
            if (def != null) {
                handled |= def.content().mouseDragged(mx, my, button, dx, dy);
            }
        }
        return handled;
    }

    public void updateFloatingDragPreview(double mx, double my) {
        FloatingWindow active = windowManager.activeWindow();
        if (active != null && active.isMoving() && maximizedDockedWindowId == null && dockBounds.contains(mx, my)) {
            activeDropPreview = DockNode.computeDropTarget(root, mx, my, HEADER_H);
        } else {
            activeDropPreview = null;
        }
    }

    public void onFloatingDragReleased(double mx, double my) {
        DockNode.DropTarget target = activeDropPreview;
        activeDropPreview = null;
        FloatingWindow active = windowManager.activeWindow();
        if (target != null && active != null && registry.containsKey(active.id())) {
            lastFloatingBounds.put(active.id(), active.bounds());
            windowManager.remove(active.id());
            root = DockNode.dockWindow(root, active.id(), target.slot(), target.zone());
            layout(dockBounds);
            onLayoutChanged.run();
        } else if (active != null && registry.containsKey(active.id())) {
            lastFloatingBounds.put(active.id(), active.bounds());
            onLayoutChanged.run();
        }
    }

    public boolean mouseReleased(double mx, double my, int button) {
        pendingTearWindowId = null;
        if (splitterDrag.endDrag()) {
            onLayoutChanged.run();
            return true;
        }
        boolean handled = false;
        if (maximizedDockedWindowId != null) {
            WindowDef def = registry.get(maximizedDockedWindowId);
            if (def != null) handled |= def.content().mouseReleased(mx, my, button);
            return handled;
        }
        List<DockNode.LeafNode> leaves = new ArrayList<>();
        root.collectLeaves(leaves);
        for (DockNode.LeafNode leaf : leaves) {
            String aid = leaf.activeWindowId();
            if (aid == null) continue;
            WindowDef def = registry.get(aid);
            if (def != null) handled |= def.content().mouseReleased(mx, my, button);
        }
        return handled;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (maximizedDockedWindowId != null) {
            WindowDef def = registry.get(maximizedDockedWindowId);
            return def != null && def.content().mouseScrolled(mx, my, delta);
        }
        List<DockNode.LeafNode> leaves = new ArrayList<>();
        root.collectLeaves(leaves);
        for (DockNode.LeafNode leaf : leaves) {
            if (leaf.bounds().contains(mx, my)) {
                String aid = leaf.activeWindowId();
                if (aid != null) {
                    WindowDef def = registry.get(aid);
                    if (def != null && def.content().mouseScrolled(mx, my, delta)) return true;
                }
            }
        }
        return false;
    }

    public void mouseMoved(double mx, double my) {
        if (maximizedDockedWindowId != null) {
            WindowDef def = registry.get(maximizedDockedWindowId);
            if (def != null) def.content().mouseMoved(mx, my);
            return;
        }
        List<DockNode.LeafNode> leaves = new ArrayList<>();
        root.collectLeaves(leaves);
        for (DockNode.LeafNode leaf : leaves) {
            String aid = leaf.activeWindowId();
            if (aid == null) continue;
            WindowDef def = registry.get(aid);
            if (def != null) def.content().mouseMoved(mx, my);
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (maximizedDockedWindowId != null) {
            WindowDef def = registry.get(maximizedDockedWindowId);
            return def != null && def.content().keyPressed(keyCode, scanCode, modifiers);
        }
        if (focusedWindowId != null && root.findLeafContainingWindow(focusedWindowId) != null) {
            DockNode.LeafNode fl = root.findLeafContainingWindow(focusedWindowId);
            if (fl != null && focusedWindowId.equals(fl.activeWindowId())) {
                WindowDef fdef = registry.get(focusedWindowId);
                if (fdef != null && fdef.content().keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
        }
        List<DockNode.LeafNode> leaves = new ArrayList<>();
        root.collectLeaves(leaves);
        for (DockNode.LeafNode leaf : leaves) {
            String aid = leaf.activeWindowId();
            if (aid == null || aid.equals(focusedWindowId)) continue;
            WindowDef def = registry.get(aid);
            if (def != null && def.content().keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }
}
