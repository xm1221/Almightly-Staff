package cn.xm1221.AlmightlyStaff.gui.ide.layout;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WindowManagerTest {

    private static IdeComponent counterComponent(AtomicInteger clicks) {
        return new IdeComponent() {
            private Bounds b = Bounds.EMPTY;
            @Override public Bounds bounds() { return b; }
            @Override public void layout(Bounds bounds) { b = bounds; }
            @Override public void render(GuiGraphics g, int mx, int my, float pt) { }
            @Override public boolean mouseClicked(double mx, double my, int btn) {
                clicks.incrementAndGet();
                return true;
            }
        };
    }

    @Test
    void overlappingWindowsClickTopAndBringToFront() {
        WindowManager wm = new WindowManager();
        AtomicInteger aClicks = new AtomicInteger();
        AtomicInteger bClicks = new AtomicInteger();

        FloatingWindow winA = new FloatingWindow("a", Component.literal("A"),
            counterComponent(aClicks), new Bounds(50, 50, 200, 150), 180, 120, null, null);
        FloatingWindow winB = new FloatingWindow("b", Component.literal("B"),
            counterComponent(bClicks), new Bounds(100, 80, 200, 150), 180, 120, null, null);

        wm.add(winA);
        wm.add(winB);
        assertSame(winB, wm.activeWindow());

        // 点在重叠区 (120, 100)：应命中 B，不穿透 A
        assertTrue(wm.mouseClicked(120, 100, 0));
        assertEquals(1, bClicks.get());
        assertEquals(0, aClicks.get());

        // 点在仅属于 A 的内容区 (60, 80)：命中 A 并将其置顶（50..70 是标题栏）
        assertTrue(wm.mouseClicked(60, 80, 0));
        assertEquals(1, aClicks.get());
        assertSame(winA, wm.activeWindow());
        assertSame(winA, wm.windows().get(1));

        // 点在窗口外：返回 false 并清空 activeWindow
        assertFalse(wm.mouseClicked(10, 10, 0));
    }
}
