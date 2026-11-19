package cn.xm1221.AlmightlyStaff.gui.ide.model;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellDocumentTest {

    private static CompoundTag tag(String id) {
        CompoundTag t = new CompoundTag();
        t.putString("id", id);
        return t;
    }

    private static List<String> ids(SpellDocument doc) {
        List<String> out = new ArrayList<>();
        for (CompoundTag t : doc.iotasView()) {
            out.add(t.getString("id"));
        }
        return out;
    }

    @Test
    void insertMoveUndoRedoAndSharedObserver() {
        SpellDocument doc = new SpellDocument(1, "Test", List.of(tag("A"), tag("B"), tag("C")), false);
        List<String> events = new ArrayList<>();
        doc.addListener((d, src) -> events.add(src + ":" + d.size()));

        assertTrue(doc.insertAt(1, List.of(tag("X")), "grid"));
        assertEquals(List.of("A", "X", "B", "C"), ids(doc));
        assertTrue(doc.isDirty());

        int newPos = doc.moveBlock(List.of(1, 2), 4, "sequence");
        assertEquals(2, newPos);
        assertEquals(List.of("A", "C", "X", "B"), ids(doc));

        assertTrue(doc.undo());
        assertEquals(List.of("A", "X", "B", "C"), ids(doc));
        assertTrue(doc.undo());
        assertEquals(List.of("A", "B", "C"), ids(doc));
        assertTrue(doc.redo());
        assertEquals(List.of("A", "X", "B", "C"), ids(doc));

        assertEquals(List.of("grid:4", "sequence:4", "undo:4", "undo:3", "redo:4"), events);
    }

    @Test
    void sealedDocumentRejectsAllContentMutations() {
        SpellDocument doc = new SpellDocument(2, "Sealed", List.of(tag("A")), true);
        assertFalse(doc.insertAt(0, List.of(tag("B")), "grid"));
        assertFalse(doc.replaceAt(0, tag("B"), "grid"));
        assertFalse(doc.removeIndices(List.of(0), "sequence"));
        assertEquals(-1, doc.moveBlock(List.of(0), 1, "sequence"));
        assertFalse(doc.replaceAll(List.of(), "parse", true));
        assertEquals(List.of("A"), ids(doc));
        assertFalse(doc.isDirty());
    }
}
