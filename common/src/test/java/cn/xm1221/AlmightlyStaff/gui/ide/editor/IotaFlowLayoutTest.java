package cn.xm1221.AlmightlyStaff.gui.ide.editor;

import cn.xm1221.AlmightlyStaff.gui.ide.model.WorkspaceState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;

class IotaFlowLayoutTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        cn.xm1221.AlmightlyStaff.MinecraftBootstrap.ensure();
    }

    @Test
    void laysOutTightlyLikeListIotaWithManualSpacesAndBreaks() {
        List<IotaFlowLayout.FlowItem> items = List.of(
            new IotaFlowLayout.FlowItem(0, "意识之精思", 0xFFFFFF, false, false, false),
            new IotaFlowLayout.FlowItem(1, "{", 0xFFD700, true, false, false),
            new IotaFlowLayout.FlowItem(2, "爆破", 0xFFFFFF, false, false, false),
            new IotaFlowLayout.FlowItem(3, "}", 0xFFD700, false, true, false),
            new IotaFlowLayout.FlowItem(4, "1.0", 0xFFFFFF, false, false, true),
            new IotaFlowLayout.FlowItem(5, "瞬移", 0xFFFFFF, false, false, false)
        );
        IotaFlowLayout.Result res = IotaFlowLayout.layout(
            items,
            item -> item.label().length() * 6,
            item -> 6,
            240, 14, 14, 4,
            Set.of(2, 5),
            Map.of(1, 1) // 索引 1 前手动加 1 个空格 (+4px)
        );

        assertEquals(6, res.tokens().size());
        assertEquals(7, res.anchors().size());

        // 图案之间紧贴（仅第 1 个前加了 1 个 4px 手动空格）
        IotaFlowLayout.PlacedToken t0 = res.tokens().get(0);
        IotaFlowLayout.PlacedToken t1 = res.tokens().get(1);
        assertEquals(0, t0.row());
        assertEquals(0, t1.row());
        assertEquals(t0.x() + t0.dispW() + 4, t1.x());

        // 手动换行后的 '{' 块内部自动缩进 14px
        IotaFlowLayout.PlacedToken t2 = res.tokens().get(2);
        assertEquals(1, t2.row());
        assertEquals(14, t2.x());

        // 换行后退回到第 2 行
        assertEquals(2, res.tokens().get(5).row());

        // 命中测试
        assertEquals(2, res.tokenAt(t2.x() + 2, t2.y() + 2));
    }

    @Test
    void manualBreakSplitsLine() {
        List<IotaFlowLayout.FlowItem> items = List.of(
            new IotaFlowLayout.FlowItem(0, "A", 0xFFFFFF, false, false, false),
            new IotaFlowLayout.FlowItem(1, "B", 0xFFFFFF, false, false, false)
        );
        IotaFlowLayout.Result noBreak = IotaFlowLayout.layout(items, i -> 20, 200, 14, 12, Set.of());
        assertEquals(0, noBreak.tokens().get(1).row());
        // 零强制间距：紧贴前一项宽度
        assertEquals(20, noBreak.tokens().get(1).x());

        IotaFlowLayout.Result withBreak = IotaFlowLayout.layout(items, i -> 20, 200, 14, 12, Set.of(1));
        assertEquals(1, withBreak.tokens().get(1).row());

        // 首行与末尾换行测试
        IotaFlowLayout.Result leadAndTrail = IotaFlowLayout.layout(
            items, i -> 20, i -> 0, 200, 14, 12, 4, Set.of(0, 2), Map.of());
        assertEquals(1, leadAndTrail.tokens().get(0).row());
        assertEquals(2, leadAndTrail.anchors().get(2).row());
        assertEquals(3, leadAndTrail.totalRows());
    }

    @Test
    void formattingShiftsOnInsertAndRemove() {
        WorkspaceState ws = WorkspaceState.createDefault();
        ws.setLineBreaksForPage(1, Set.of(1, 3));
        ws.setSpacesForPage(1, Map.of(2, 1, 3, 2));

        // 在索引 1 处插入 2 个元素：原 1 留在 1（光标当前位置之后插入的新元素从该行开始），>1 的 2,3 顺移为 4,5
        assertTrue(ws.shiftFormattingOnInsert(1, 1, 2));
        assertEquals(Set.of(1, 5), ws.lineBreaksForPage(1));
        assertEquals(Map.of(4, 1, 5, 2), ws.spacesForPage(1));

        // 删除索引 2（即 1 之后的元素）：4,5 回移为 3,4
        assertTrue(ws.shiftFormattingOnRemove(1, Set.of(2), 4));
        assertEquals(Set.of(1, 4), ws.lineBreaksForPage(1));
        assertEquals(Map.of(3, 1, 4, 2), ws.spacesForPage(1));
    }
}
