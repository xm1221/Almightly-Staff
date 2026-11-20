package cn.xm1221.AlmightlyStaff.gui.ide.layout;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;

class DockLayoutTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        cn.xm1221.AlmightlyStaff.MinecraftBootstrap.ensure();
    }


    @Test
    void splitClampsMinPixelsAndRelayoutsOnDrag() {
        DockNode.LeafNode left = new DockNode.LeafNode("tree");
        DockNode.LeafNode right = new DockNode.LeafNode("editor");
        DockNode.SplitNode root = new DockNode.SplitNode(
            "root", DockNode.Axis.HORIZONTAL, 0.25f, 100, 180, left, right);

        Bounds area = new Bounds(20, 26, 405, 300);
        root.layout(area);
        assertEquals(100, left.bounds().w());
        assertEquals(300, right.bounds().w());
        assertEquals(5, root.splitterBounds().w());

        // 拖拽到极左，左侧不能小于 100px
        SplitterDragState drag = new SplitterDragState();
        assertTrue(drag.beginDrag(root, root.splitterBounds().x() + 2, 100));
        drag.drag(root, area, 30, 100);
        assertEquals(100, left.bounds().w());

        // 拖拽到极右，右侧不能小于 180px（总可用 400 -> 左侧最大 220）
        drag.drag(root, area, 410, 100);
        assertEquals(220, left.bounds().w());
        assertEquals(180, right.bounds().w());
        drag.endDrag();
    }

    @Test
    void collapseExpandsSiblingAndExtremeNarrowViewportNeverNegative() {
        DockNode.LeafNode top = new DockNode.LeafNode("main");
        DockNode.LeafNode bottom = new DockNode.LeafNode("bottom");
        DockNode.SplitNode split = new DockNode.SplitNode(
            "v", DockNode.Axis.VERTICAL, 0.70f, 120, 100, top, bottom);

        Bounds full = new Bounds(0, 0, 500, 405);
        split.layout(full);
        assertNotNull(split.hitTestSplitter(100, split.splitterBounds().y() + 2));

        bottom.setCollapsed(true);
        split.layout(full);
        assertTrue(bottom.bounds().isEmpty());
        assertEquals(full, top.bounds());
        assertTrue(split.splitterBounds().isEmpty());
        assertNull(split.hitTestSplitter(100, 280));

        // 极窄视口（小于 minFirst + minSecond）不抛异常、不出现负尺寸
        bottom.setCollapsed(false);
        split.layout(new Bounds(0, 0, 80, 60));
        assertTrue(top.bounds().h() >= 0);
        assertTrue(bottom.bounds().h() >= 0);
        assertSame(bottom, split.findLeaf("bottom"));
    }

    @Test
    void dropZonesSplitSlotAndPruneEmptyLeaves() {
        DockNode.LeafNode main = new DockNode.LeafNode("slot_main", java.util.List.of("iota_text"));
        Bounds area = new Bounds(0, 20, 600, 360);
        main.layout(area);

        DockNode.DropTarget leftDrop = DockNode.computeDropTarget(main, 12, 200, 20);
        assertNotNull(leftDrop);
        assertEquals(DockNode.DropZone.LEFT, leftDrop.zone());

        DockNode tree = DockNode.dockWindow(main, "hex_grid", leftDrop.slot(), leftDrop.zone());
        tree.layout(area);
        assertNotNull(tree.findLeafContainingWindow("hex_grid"));
        assertNotNull(tree.findLeafContainingWindow("iota_text"));

        // 停靠到中心成为同一槽位 Tab
        DockNode.LeafNode gridSlot = tree.findLeafContainingWindow("hex_grid");
        tree = DockNode.dockWindow(tree, "hex_parse", gridSlot, DockNode.DropZone.CENTER);
        assertEquals(java.util.List.of("hex_grid", "hex_parse"), gridSlot.windows());

        // 移除两个窗口后空槽自动剪枝，右侧主槽自动占满
        tree = DockNode.removeWindow(tree, "hex_grid");
        tree = DockNode.removeWindow(tree, "hex_parse");
        tree.layout(area);
        assertTrue(tree instanceof DockNode.LeafNode);
        assertEquals(area, tree.bounds());
    }
}
