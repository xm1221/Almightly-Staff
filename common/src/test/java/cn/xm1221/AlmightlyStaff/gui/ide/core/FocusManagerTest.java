package cn.xm1221.AlmightlyStaff.gui.ide.core;

import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;

class FocusManagerTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        cn.xm1221.AlmightlyStaff.MinecraftBootstrap.ensure();
    }


    private static IdeComponent stub(String name, List<String> events) {
        return new IdeComponent() {
            private Bounds b = Bounds.EMPTY;
            @Override public Bounds bounds() { return b; }
            @Override public void layout(Bounds bounds) { b = bounds; }
            @Override public void render(GuiGraphics g, int mx, int my, float pt) { }
            @Override public void onFocusGained() { events.add(name + "+"); }
            @Override public void onFocusLost() { events.add(name + "-"); }
        };
    }

    @Test
    void switchingFocusNotifiesOldOnceAndNoDuplicates() {
        List<String> events = new ArrayList<>();
        FocusManager fm = new FocusManager(() -> events.add("switch"));
        IdeComponent a = stub("A", events);
        IdeComponent b = stub("B", events);

        fm.focus(a);
        fm.focus(a); // same component -> no-op
        assertTrue(fm.isFocused(a));
        assertEquals(List.of("switch", "A+"), events);

        fm.focus(b);
        assertFalse(fm.isFocused(a));
        assertTrue(fm.isFocused(b));
        assertEquals(List.of("switch", "A+", "A-", "switch", "B+"), events);

        fm.clear();
        assertNull(fm.focused());
        assertEquals(List.of("switch", "A+", "A-", "switch", "B+", "B-", "switch"), events);
    }
}
