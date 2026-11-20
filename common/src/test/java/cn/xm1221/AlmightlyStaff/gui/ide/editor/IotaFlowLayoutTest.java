package cn.xm1221.AlmightlyStaff.gui.ide.editor;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    }
}
