package cn.xm1221.AlmightlyStaff.gui.ide.model;

import net.minecraft.nbt.CompoundTag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 统一文档模型：以当前书页的 {@code List<CompoundTag>} 作为唯一数据源。
 * 遵循 hex-gui-writer 约定：客户端不反序列化非图案 Iota，始终持有原始 NBT 标签。
 */
public final class SpellDocument {
    public static final int MAX_UNDO = 64;

    private final int pageIndex;
    private String name;
    private boolean sealed;
    private final List<CompoundTag> iotas = new ArrayList<>();
    private boolean dirty;
    private final ArrayDeque<List<CompoundTag>> undoStack = new ArrayDeque<>();
    private final ArrayDeque<List<CompoundTag>> redoStack = new ArrayDeque<>();
    private final List<BiConsumer<SpellDocument, String>> listeners = new ArrayList<>();

    public SpellDocument(int pageIndex, String name, List<CompoundTag> initialIotas, boolean sealed) {
        this.pageIndex = pageIndex;
        this.name = name == null ? "" : name;
        this.sealed = sealed;
        if (initialIotas != null) {
            for (CompoundTag t : initialIotas) {
                if (t != null) this.iotas.add(t.copy());
            }
        }
    }

    public int pageIndex() {
        return pageIndex;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name == null ? "" : name;
    }

    public boolean isSealed() {
        return sealed;
    }

    public void setSealed(boolean sealed) {
        this.sealed = sealed;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markSaved() {
        this.dirty = false;
    }

    public int size() {
        return iotas.size();
    }

    public List<CompoundTag> iotasView() {
        return Collections.unmodifiableList(iotas);
    }

    public List<CompoundTag> copyIotas() {
        List<CompoundTag> copy = new ArrayList<>(iotas.size());
        for (CompoundTag t : iotas) {
            if (t != null) copy.add(t.copy());
        }
        return copy;
    }

    public void addListener(BiConsumer<SpellDocument, String> listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(BiConsumer<SpellDocument, String> listener) {
        listeners.remove(listener);
    }

    private void notifyChanged(String source) {
        for (BiConsumer<SpellDocument, String> l : List.copyOf(listeners)) {
            l.accept(this, source);
        }
    }

    private void pushUndo() {
        undoStack.addFirst(copyIotas());
        while (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
        redoStack.clear();
    }

    public boolean canUndo() {
        return !sealed && !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !sealed && !redoStack.isEmpty();
    }

    public boolean insertAt(int index, List<CompoundTag> tags, String source) {
        if (sealed || tags == null || tags.isEmpty()) return false;
        int pos = Math.max(0, Math.min(index, iotas.size()));
        List<CompoundTag> clean = new ArrayList<>(tags.size());
        for (CompoundTag t : tags) {
            if (t != null) clean.add(t.copy());
        }
        if (clean.isEmpty()) return false;
        pushUndo();
        iotas.addAll(pos, clean);
        dirty = true;
        notifyChanged(source);
        return true;
    }

    public boolean replaceAt(int index, CompoundTag tag, String source) {
        if (sealed || tag == null) return false;
        if (index < 0 || index >= iotas.size()) {
            return insertAt(iotas.size(), List.of(tag), source);
        }
        pushUndo();
        iotas.set(index, tag.copy());
        dirty = true;
        notifyChanged(source);
        return true;
    }

    public boolean removeIndices(Collection<Integer> indices, String source) {
        if (sealed || indices == null || indices.isEmpty()) return false;
        List<Integer> sorted = new ArrayList<>(new HashSet<>(indices));
        sorted.removeIf(i -> i == null || i < 0 || i >= iotas.size());
        if (sorted.isEmpty()) return false;
        sorted.sort(Collections.reverseOrder());
        pushUndo();
        for (int idx : sorted) {
            iotas.remove(idx);
        }
        dirty = true;
        notifyChanged(source);
        return true;
    }

    /**
     * 把选中格整块移动到 dropIndex 之前，返回移动后新起始索引（未改变返回 -1）。
     */
    public int moveBlock(Collection<Integer> indices, int dropIndex, String source) {
        if (sealed || indices == null || indices.isEmpty()) return -1;
        List<Integer> sorted = new ArrayList<>(new HashSet<>(indices));
        sorted.removeIf(i -> i == null || i < 0 || i >= iotas.size());
        if (sorted.isEmpty()) return -1;
        Collections.sort(sorted);
        Set<Integer> selectedSet = new HashSet<>(sorted);

        List<CompoundTag> moved = new ArrayList<>(sorted.size());
        for (int idx : sorted) {
            moved.add(iotas.get(idx).copy());
        }

        int insertAt = 0;
        int boundedDrop = Math.max(0, Math.min(dropIndex, iotas.size()));
        for (int i = 0; i < boundedDrop; i++) {
            if (!selectedSet.contains(i)) {
                insertAt++;
            }
        }

        List<CompoundTag> before = copyIotas();
        List<Integer> desc = new ArrayList<>(sorted);
        desc.sort(Collections.reverseOrder());
        for (int idx : desc) {
            iotas.remove(idx);
        }
        insertAt = Math.max(0, Math.min(insertAt, iotas.size()));
        iotas.addAll(insertAt, moved);

        if (sameSequence(before, iotas)) {
            return -1;
        }
        undoStack.addFirst(before);
        while (undoStack.size() > MAX_UNDO) {
            undoStack.removeLast();
        }
        redoStack.clear();
        dirty = true;
        notifyChanged(source);
        return insertAt;
    }

    public boolean replaceAll(List<CompoundTag> newTags, String source, boolean markDirty) {
        if (sealed) return false;
        List<CompoundTag> clean = new ArrayList<>();
        if (newTags != null) {
            for (CompoundTag t : newTags) {
                if (t != null) clean.add(t.copy());
            }
        }
        if (sameSequence(iotas, clean)) return false;
        pushUndo();
        iotas.clear();
        iotas.addAll(clean);
        if (markDirty) dirty = true;
        notifyChanged(source);
        return true;
    }

    public boolean undo() {
        if (!canUndo()) return false;
        redoStack.addFirst(copyIotas());
        List<CompoundTag> prev = undoStack.removeFirst();
        iotas.clear();
        iotas.addAll(prev);
        dirty = true;
        notifyChanged("undo");
        return true;
    }

    public boolean redo() {
        if (!canRedo()) return false;
        undoStack.addFirst(copyIotas());
        List<CompoundTag> next = redoStack.removeFirst();
        iotas.clear();
        iotas.addAll(next);
        dirty = true;
        notifyChanged("redo");
        return true;
    }

    public void applyServerSnapshot(String newName, List<CompoundTag> tags, boolean newSealed, boolean clearHistory) {
        this.name = newName == null ? "" : newName;
        this.sealed = newSealed;
        this.iotas.clear();
        if (tags != null) {
            for (CompoundTag t : tags) {
                if (t != null) this.iotas.add(t.copy());
            }
        }
        this.dirty = false;
        if (clearHistory) {
            undoStack.clear();
            redoStack.clear();
        }
        notifyChanged("sync");
    }

    public static boolean sameSequence(List<CompoundTag> a, List<CompoundTag> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!a.get(i).equals(b.get(i))) return false;
        }
        return true;
    }
}
