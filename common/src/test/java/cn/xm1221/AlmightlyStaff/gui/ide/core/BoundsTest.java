package cn.xm1221.AlmightlyStaff.gui.ide.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundsTest {

    @Test
    void containsAndEdges() {
        Bounds b = new Bounds(10, 20, 100, 50);
        assertEquals(110, b.right());
        assertEquals(70, b.bottom());
        assertTrue(b.contains(10, 20));
        assertTrue(b.contains(109.9, 69.9));
        assertFalse(b.contains(110, 20));
        assertFalse(b.contains(10, 70));
    }

    @Test
    void insetAndIntersectClampToNonNegative() {
        Bounds a = new Bounds(0, 0, 50, 50);
        Bounds b = new Bounds(30, 40, 40, 40);
        Bounds inter = a.intersect(b);
        assertEquals(new Bounds(30, 40, 20, 10), inter);

        Bounds disjoint = a.intersect(new Bounds(60, 0, 10, 10));
        assertTrue(disjoint.isEmpty());

        Bounds overInset = a.inset(30);
        assertEquals(0, overInset.w());
        assertEquals(0, overInset.h());
        assertTrue(overInset.isEmpty());
    }
}
