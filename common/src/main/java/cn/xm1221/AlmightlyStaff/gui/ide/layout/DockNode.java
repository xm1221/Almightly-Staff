package cn.xm1221.AlmightlyStaff.gui.ide.layout;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 递归二叉树停靠布局节点（纯 Java 逻辑，可直接走 JUnit 单测）。
 */
public sealed interface DockNode permits DockNode.LeafNode, DockNode.SplitNode {
    int SPLITTER_SIZE = 5;

    enum Axis {
        HORIZONTAL, // 左右分屏（分割条为竖线）
        VERTICAL    // 上下分屏（分割条为横线）
    }

    String id();

    Bounds bounds();

    boolean isEffectivelyCollapsed();

    void layout(Bounds area);

    @Nullable
    SplitNode hitTestSplitter(double mx, double my);

    void collectSplitters(List<SplitNode> out);

    @Nullable
    LeafNode findLeaf(String id);

    final class LeafNode implements DockNode {
        private final String id;
        private Bounds bounds = Bounds.EMPTY;
        private boolean collapsed;

        public LeafNode(String id) {
            this(id, false);
        }

        public LeafNode(String id, boolean collapsed) {
            this.id = id;
            this.collapsed = collapsed;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Bounds bounds() {
            return bounds;
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
        public @Nullable LeafNode findLeaf(String targetId) {
            return id.equals(targetId) ? this : null;
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

        public void restoreLastRatio() {
            this.ratio = clamp01(this.lastRatio);
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
            // 极窄视口退让：按最小尺寸权重分配，保证不出现负宽高
            int sum = Math.max(1, minFirst + minSecond);
            return Math.max(0, Math.min(avail, (int) Math.round(avail * (minFirst / (double) sum))));
        }

        private static float clamp01(float v) {
            if (Float.isNaN(v) || Float.isInfinite(v)) return 0.5f;
            return Math.max(0.05f, Math.min(0.95f, v));
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
        public @Nullable LeafNode findLeaf(String targetId) {
            if (id.equals(targetId)) {
                return null;
            }
            LeafNode hit = first.findLeaf(targetId);
            return hit != null ? hit : second.findLeaf(targetId);
        }
    }
}
