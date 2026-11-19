package cn.xm1221.AlmightlyStaff.gui.ide.layout;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import org.jetbrains.annotations.Nullable;

/**
 * 分割条拖拽状态：拖拽过程只修改 {@link DockNode.SplitNode#updateRatioFromPointer} 并重新 layout，
 * 绝不触发 {@code Screen.init()}。
 */
public final class SplitterDragState {
    @Nullable
    private DockNode.SplitNode dragging;

    public boolean isDragging() {
        return dragging != null;
    }

    @Nullable
    public DockNode.SplitNode draggingNode() {
        return dragging;
    }

    public boolean beginDrag(DockNode root, double mx, double my) {
        if (root == null) return false;
        DockNode.SplitNode hit = root.hitTestSplitter(mx, my);
        if (hit != null) {
            dragging = hit;
            return true;
        }
        return false;
    }

    public boolean drag(DockNode root, Bounds rootBounds, double mx, double my) {
        if (dragging == null || root == null) return false;
        dragging.updateRatioFromPointer(mx, my);
        root.layout(rootBounds);
        return true;
    }

    public boolean endDrag() {
        if (dragging == null) return false;
        dragging = null;
        return true;
    }
}
