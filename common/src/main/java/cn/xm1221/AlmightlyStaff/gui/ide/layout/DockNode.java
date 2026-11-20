package cn.xm1221.AlmightlyStaff.gui.ide.layout;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.model.WorkspaceState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 递归二叉树自由停靠布局引擎（纯 Java，支持槽内多 Tab、任意边缘拆分、空槽剪枝、双击均分与停靠预览）。
 */
public sealed interface DockNode permits DockNode.LeafNode, DockNode.SplitNode {
    int SPLITTER_SIZE = 5;
    int MIN_SLOT_W = 100;
    int MIN_SLOT_H = 72;

    enum Axis {
        HORIZONTAL,
        VERTICAL
    }

    enum DropZone {
        LEFT,
        RIGHT,
        TOP,
        BOTTOM,
        CENTER
    }

    record DropTarget(LeafNode slot, DropZone zone, Bounds previewBounds) {
    }

    String id();

    Bounds bounds();

    boolean isEffectivelyCollapsed();

    void layout(Bounds area);

    @Nullable
    SplitNode hitTestSplitter(double mx, double my);

    void collectSplitters(List<SplitNode> out);

    void collectLeaves(List<LeafNode> out);

    @Nullable
    LeafNode findLeaf(String id);

    @Nullable
    LeafNode findLeafContainingWindow(String windowId);

    WorkspaceState.DockLayoutDto toDto();

    static DockNode fromDto(@Nullable WorkspaceState.DockLayoutDto dto) {
        if (dto == null) {
            return new LeafNode("slot_main", List.of("iota_text"));
        }
        if ("split".equals(dto.kind) && dto.first != null && dto.second != null) {
            Axis ax = "V".equals(dto.axis) ? Axis.VERTICAL : Axis.HORIZONTAL;
            return new SplitNode(
                dto.id == null || dto.id.isBlank() ? "split" : dto.id,
                ax, dto.ratio, MIN_SLOT_W, MIN_SLOT_H,
                fromDto(dto.first), fromDto(dto.second)
            );
        }
        LeafNode leaf = new LeafNode(dto.id == null || dto.id.isBlank() ? "slot" : dto.id, dto.tabs);
        leaf.setActiveTabIndex(dto.activeTab);
        return leaf;
    }

    @Nullable
    static DropTarget computeDropTarget(DockNode root, double mx, double my, int tabBarHeight) {
        if (root == null || !root.bounds().contains(mx, my)) return null;
        List<LeafNode> leaves = new ArrayList<>();
        root.collectLeaves(leaves);
        for (LeafNode leaf : leaves) {
            Bounds b = leaf.bounds();
            if (!b.contains(mx, my)) continue;
            if (my < b.y() + tabBarHeight) {
                return new DropTarget(leaf, DropZone.CENTER, b);
            }
            int edgeX = Math.max(18, Math.min(44, b.w() / 4));
            int edgeY = Math.max(16, Math.min(40, b.h() / 4));
            if (mx - b.x() < edgeX) {
                return new DropTarget(leaf, DropZone.LEFT, new Bounds(b.x(), b.y(), b.w() / 2, b.h()));
            }
            if (b.right() - mx < edgeX) {
                return new DropTarget(leaf, DropZone.RIGHT, new Bounds(b.x() + b.w() / 2, b.y(), b.w() - b.w() / 2, b.h()));
            }
            if (my - b.y() < edgeY) {
                return new DropTarget(leaf, DropZone.TOP, new Bounds(b.x(), b.y(), b.w(), b.h() / 2));
            }
            if (b.bottom() - my < edgeY) {
                return new DropTarget(leaf, DropZone.BOTTOM, new Bounds(b.x(), b.y() + b.h() / 2, b.w(), b.h() - b.h() / 2));
            }
            return new DropTarget(leaf, DropZone.CENTER, b);
        }
        return null;
    }

    /** 将 windowId 从旧位置移出后停靠到 target 槽位的指定区域，返回更新后的根节点。 */
    static DockNode dockWindow(DockNode root, String windowId, LeafNode target, DropZone zone) {
        if (zone == DropZone.CENTER) {
            if (!target.windows().contains(windowId)) {
                root = removeWindow(root, windowId);
                LeafNode freshTarget = root.findLeaf(target.id());
                if (freshTarget != null) {
                    freshTarget.addWindow(windowId);
                    return root;
                }
            } else {
                target.focusWindow(windowId);
                return root;
            }
        }
        root = removeWindow(root, windowId);
        LeafNode freshTarget = root.findLeaf(target.id());
        if (freshTarget == null) {
            List<LeafNode> all = new ArrayList<>();
            root.collectLeaves(all);
            if (all.isEmpty()) {
                return new LeafNode("slot_main", List.of(windowId));
            }
            freshTarget = all.get(0);
        }
        LeafNode newLeaf = new LeafNode("slot_" + Long.toHexString(System.nanoTime()), List.of(windowId));
        Axis axis = (zone == DropZone.LEFT || zone == DropZone.RIGHT) ? Axis.HORIZONTAL : Axis.VERTICAL;
        boolean newFirst = (zone == DropZone.LEFT || zone == DropZone.TOP);
        SplitNode replacement = new SplitNode(
            "split_" + Long.toHexString(System.nanoTime()),
            axis,
            newFirst ? 0.38f : 0.62f,
            MIN_SLOT_W, MIN_SLOT_H,
            newFirst ? newLeaf : freshTarget,
            newFirst ? freshTarget : newLeaf
        );
        return replaceNode(root, freshTarget, replacement);
    }

    static DockNode removeWindow(DockNode root, String windowId) {
        List<LeafNode> leaves = new ArrayList<>();
        root.collectLeaves(leaves);
        for (LeafNode leaf : leaves) {
            leaf.removeWindow(windowId);
        }
        DockNode pruned = pruneEmpty(root);
        return pruned == null ? new LeafNode("slot_main") : pruned;
    }

    private static @Nullable DockNode pruneEmpty(DockNode node) {
        if (node instanceof LeafNode leaf) {
            return leaf.windows().isEmpty() ? null : leaf;
        }
        if (node instanceof SplitNode split) {
            DockNode a = pruneEmpty(split.first());
            DockNode b = pruneEmpty(split.second());
            if (a == null && b == null) return null;
            if (a == null) return b;
            if (b == null) return a;
            split.setFirst(a);
            split.setSecond(b);
            return split;
        }
        return node;
    }

    private static DockNode replaceNode(DockNode current, DockNode target, DockNode replacement) {
        if (current == target) return replacement;
        if (current instanceof SplitNode split) {
            split.setFirst(replaceNode(split.first(), target, replacement));
            split.setSecond(replaceNode(split.second(), target, replacement));
        }
        return current;
    }

    final class LeafNode implements DockNode {
        private final String id;
        private Bounds bounds = Bounds.EMPTY;
        private boolean collapsed;
        private final List<String> windows = new ArrayList<>();
        private int activeTabIndex = 0;

        public LeafNode(String id) {
            this(id, false);
        }

        public LeafNode(String id, boolean collapsed) {
            this.id = id;
            this.collapsed = collapsed;
        }

        public LeafNode(String id, List<String> initialWindows) {
            this.id = id;
            if (initialWindows != null) {
                for (String w : initialWindows) {
                    if (w != null && !w.isBlank() && !this.windows.contains(w)) {
                        this.windows.add(w);
                    }
                }
            }
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Bounds bounds() {
            return bounds;
        }

        public List<String> windows() {
            return Collections.unmodifiableList(windows);
        }

        public int activeTabIndex() {
            return windows.isEmpty() ? -1 : Math.max(0, Math.min(activeTabIndex, windows.size() - 1));
        }

        public void setActiveTabIndex(int idx) {
            this.activeTabIndex = windows.isEmpty() ? 0 : Math.max(0, Math.min(idx, windows.size() - 1));
        }

        @Nullable
        public String activeWindowId() {
            int idx = activeTabIndex();
            return idx >= 0 && idx < windows.size() ? windows.get(idx) : null;
        }

        public void addWindow(String windowId) {
            if (windowId == null || windowId.isBlank()) return;
            int idx = windows.indexOf(windowId);
            if (idx >= 0) {
                activeTabIndex = idx;
                return;
            }
            windows.add(windowId);
            activeTabIndex = windows.size() - 1;
        }

        public void focusWindow(String windowId) {
            int idx = windows.indexOf(windowId);
            if (idx >= 0) activeTabIndex = idx;
        }

        public boolean removeWindow(String windowId) {
            int idx = windows.indexOf(windowId);
            if (idx < 0) return false;
            windows.remove(idx);
            if (activeTabIndex >= windows.size()) {
                activeTabIndex = Math.max(0, windows.size() - 1);
            }
            return true;
        }

        public boolean isCollapsed() {
            return collapsed;
        }

        public void setCollapsed(boolean collapsed) {
            this.collapsed = collapsed;
        }

        @Override
        public boolean isEffectivelyCollapsed() {
            return collapsed;
        }

        @Override
        public void layout(Bounds area) {
            this.bounds = collapsed || area == null ? Bounds.EMPTY : area;
        }

        @Override
        public @Nullable SplitNode hitTestSplitter(double mx, double my) {
            return null;
        }

        @Override
        public void collectSplitters(List<SplitNode> out) {
        }

        @Override
        public void collectLeaves(List<LeafNode> out) {
            if (!collapsed) {
                out.add(this);
            }
        }

        @Override
        public @Nullable LeafNode findLeaf(String targetId) {
            return id.equals(targetId) ? this : null;
        }

        @Override
        public @Nullable LeafNode findLeafContainingWindow(String windowId) {
            return windows.contains(windowId) ? this : null;
        }

        @Override
        public WorkspaceState.DockLayoutDto toDto() {
            WorkspaceState.DockLayoutDto dto = new WorkspaceState.DockLayoutDto();
            dto.kind = "leaf";
            dto.id = id;
            dto.tabs = new ArrayList<>(windows);
            dto.activeTab = activeTabIndex();
            return dto;
        }
    }

    final class SplitNode implements DockNode {
        private final String id;
        private final Axis axis;
        private float ratio;
        private float lastRatio;
        private final int minFirstPx;
        private final int minSecondPx;
        private DockNode first;
        private DockNode second;
        private Bounds bounds = Bounds.EMPTY;
        private Bounds splitterBounds = Bounds.EMPTY;

        public SplitNode(String id, Axis axis, float ratio, int minFirstPx, int minSecondPx,
                         DockNode first, DockNode second) {
            this.id = id;
            this.axis = axis;
            this.ratio = clamp01(ratio);
            this.lastRatio = this.ratio;
            this.minFirstPx = Math.max(0, minFirstPx);
            this.minSecondPx = Math.max(0, minSecondPx);
            this.first = first;
            this.second = second;
        }

        @Override
        public String id() {
            return id;
        }

        public Axis axis() {
            return axis;
        }

        public float ratio() {
            return ratio;
        }

        public float lastRatio() {
            return lastRatio;
        }

        public void setRatio(float ratio) {
            this.ratio = clamp01(ratio);
            this.lastRatio = this.ratio;
        }

        public void resetHalfOrRestore() {
            if (Math.abs(this.ratio - 0.50f) < 0.02f && Math.abs(this.lastRatio - 0.50f) > 0.03f) {
                this.ratio = clamp01(this.lastRatio);
            } else {
                this.lastRatio = this.ratio;
                this.ratio = 0.50f;
            }
        }

        public DockNode first() {
            return first;
        }

        public DockNode second() {
            return second;
        }

        public void setFirst(DockNode first) {
            this.first = first;
        }

        public void setSecond(DockNode second) {
            this.second = second;
        }

        @Override
        public Bounds bounds() {
            return bounds;
        }

        public Bounds splitterBounds() {
            return splitterBounds;
        }

        @Override
        public boolean isEffectivelyCollapsed() {
            return first.isEffectivelyCollapsed() && second.isEffectivelyCollapsed();
        }

        @Override
        public void layout(Bounds area) {
            this.bounds = area == null ? Bounds.EMPTY : area;
            if (this.bounds.isEmpty()) {
                this.splitterBounds = Bounds.EMPTY;
                first.layout(Bounds.EMPTY);
                second.layout(Bounds.EMPTY);
                return;
            }
            boolean firstHidden = first.isEffectivelyCollapsed();
            boolean secondHidden = second.isEffectivelyCollapsed();
            if (firstHidden && secondHidden) {
                this.splitterBounds = Bounds.EMPTY;
                first.layout(Bounds.EMPTY);
                second.layout(Bounds.EMPTY);
                return;
            }
            if (firstHidden) {
                this.splitterBounds = Bounds.EMPTY;
                first.layout(Bounds.EMPTY);
                second.layout(this.bounds);
                return;
            }
            if (secondHidden) {
                this.splitterBounds = Bounds.EMPTY;
                first.layout(this.bounds);
                second.layout(Bounds.EMPTY);
                return;
            }

            int total = axis == Axis.HORIZONTAL ? this.bounds.w() : this.bounds.h();
            int split = Math.min(SPLITTER_SIZE, total);
            int avail = Math.max(0, total - split);
            int firstSize = computeFirstSize(avail, ratio, minFirstPx, minSecondPx);
            int secondSize = Math.max(0, avail - firstSize);

            if (axis == Axis.HORIZONTAL) {
                int x1 = this.bounds.x();
                int xSplit = x1 + firstSize;
                int x2 = xSplit + split;
                first.layout(new Bounds(x1, this.bounds.y(), firstSize, this.bounds.h()));
                this.splitterBounds = new Bounds(xSplit, this.bounds.y(), split, this.bounds.h());
                second.layout(new Bounds(x2, this.bounds.y(), secondSize, this.bounds.h()));
            } else {
                int y1 = this.bounds.y();
                int ySplit = y1 + firstSize;
                int y2 = ySplit + split;
                first.layout(new Bounds(this.bounds.x(), y1, this.bounds.w(), firstSize));
                this.splitterBounds = new Bounds(this.bounds.x(), ySplit, this.bounds.w(), split);
                second.layout(new Bounds(this.bounds.x(), y2, this.bounds.w(), secondSize));
            }
        }

        public void updateRatioFromPointer(double mx, double my) {
            if (bounds.isEmpty() || first.isEffectivelyCollapsed() || second.isEffectivelyCollapsed()) {
                return;
            }
            int total = axis == Axis.HORIZONTAL ? bounds.w() : bounds.h();
            int avail = Math.max(1, total - Math.min(SPLITTER_SIZE, total));
            double offset = axis == Axis.HORIZONTAL
                ? (mx - bounds.x() - SPLITTER_SIZE / 2.0)
                : (my - bounds.y() - SPLITTER_SIZE / 2.0);
            int clampedSize = computeFirstSize(avail, (float) (offset / avail), minFirstPx, minSecondPx);
            this.ratio = clamp01(clampedSize / (float) avail);
            this.lastRatio = this.ratio;
        }

        private static int computeFirstSize(int avail, float ratio, int minFirst, int minSecond) {
            if (avail <= 0) return 0;
            int raw = Math.round(avail * clamp01(ratio));
            if (minFirst + minSecond <= avail) {
                return Math.max(minFirst, Math.min(avail - minSecond, raw));
            }
            int sum = Math.max(1, minFirst + minSecond);
            return Math.max(0, Math.min(avail, (int) Math.round(avail * (minFirst / (double) sum))));
        }

        private static float clamp01(float v) {
            if (Float.isNaN(v) || Float.isInfinite(v)) return 0.5f;
            return Math.max(0.08f, Math.min(0.92f, v));
        }

        @Override
        public @Nullable SplitNode hitTestSplitter(double mx, double my) {
            if (!splitterBounds.isEmpty() && splitterBounds.contains(mx, my)) {
                return this;
            }
            SplitNode hit = first.hitTestSplitter(mx, my);
            return hit != null ? hit : second.hitTestSplitter(mx, my);
        }

        @Override
        public void collectSplitters(List<SplitNode> out) {
            if (!splitterBounds.isEmpty()) {
                out.add(this);
            }
            first.collectSplitters(out);
            second.collectSplitters(out);
        }

        @Override
        public void collectLeaves(List<LeafNode> out) {
            first.collectLeaves(out);
            second.collectLeaves(out);
        }

        @Override
        public @Nullable LeafNode findLeaf(String targetId) {
            if (id.equals(targetId)) return null;
            LeafNode hit = first.findLeaf(targetId);
            return hit != null ? hit : second.findLeaf(targetId);
        }

        @Override
        public @Nullable LeafNode findLeafContainingWindow(String windowId) {
            LeafNode hit = first.findLeafContainingWindow(windowId);
            return hit != null ? hit : second.findLeafContainingWindow(windowId);
        }

        @Override
        public WorkspaceState.DockLayoutDto toDto() {
            WorkspaceState.DockLayoutDto dto = new WorkspaceState.DockLayoutDto();
            dto.kind = "split";
            dto.id = id;
            dto.axis = axis == Axis.VERTICAL ? "V" : "H";
            dto.ratio = ratio;
            dto.first = first.toDto();
            dto.second = second.toDto();
            return dto;
        }
    }
}
