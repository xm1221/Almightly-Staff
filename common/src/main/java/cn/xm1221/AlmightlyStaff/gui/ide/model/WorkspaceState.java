package cn.xm1221.AlmightlyStaff.gui.ide.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 客户端侧车工作区持久化模型（按法杖 UUID 存在 {@code config/almightly_staff/ide/<uuid>.json}）。
 * 纯 Java DTO，不依赖 Minecraft/Platform 类，方便单测。
 */
public final class WorkspaceState {
    public int version = 1;
    public LayoutState layout = new LayoutState();
    public List<VirtualFolder> folders = new ArrayList<>();
    public List<TabBookmark> openTabs = new ArrayList<>();

    public static final class LayoutState {
        public float leftRatio = 0.20f;
        public float catalogRatio = 0.24f;
        public float bottomRatio = 0.34f;
        public float bottomInnerRatio = 0.42f;
        public float editorSplitRatio = 0.50f;
        public boolean leftCollapsed = false;
        public boolean catalogCollapsed = false;
        public boolean bottomCollapsed = false;
        public boolean editorSplitOpen = false;
        public String bottomMode = "split"; // "split" | "grid" | "parse"

        public void sanitize() {
            leftRatio = clamp(leftRatio, 0.12f, 0.45f, 0.20f);
            catalogRatio = clamp(catalogRatio, 0.14f, 0.45f, 0.24f);
            bottomRatio = clamp(bottomRatio, 0.16f, 0.65f, 0.34f);
            bottomInnerRatio = clamp(bottomInnerRatio, 0.20f, 0.80f, 0.42f);
            editorSplitRatio = clamp(editorSplitRatio, 0.20f, 0.80f, 0.50f);
            if (!"split".equals(bottomMode) && !"grid".equals(bottomMode) && !"parse".equals(bottomMode)) {
                bottomMode = "split";
            }
        }

        private static float clamp(float val, float min, float max, float fallback) {
            if (Float.isNaN(val) || Float.isInfinite(val)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, val));
        }
    }

    public static final class VirtualFolder {
        public String id = "";
        public String name = "";
        public boolean collapsed = false;
        public List<Integer> pageIndices = new ArrayList<>();

        public VirtualFolder() {
        }

        public VirtualFolder(String id, String name, boolean collapsed, List<Integer> pageIndices) {
            this.id = id == null ? "" : id;
            this.name = name == null ? "" : name;
            this.collapsed = collapsed;
            this.pageIndices = pageIndices == null ? new ArrayList<>() : new ArrayList<>(pageIndices);
        }
    }

    public static final class TabBookmark {
        public String kind = "spell"; // "spell" | "note"
        public int pageIndex = 0;
        public String noteId = "";
        public int groupIndex = 0;

        public TabBookmark() {
        }

        public TabBookmark(String kind, int pageIndex, String noteId, int groupIndex) {
            this.kind = kind;
            this.pageIndex = pageIndex;
            this.noteId = noteId == null ? "" : noteId;
            this.groupIndex = groupIndex;
        }
    }

    public static WorkspaceState createDefault() {
        WorkspaceState state = new WorkspaceState();
        state.layout.sanitize();
        return state;
    }

    /** 清理无效字段与已删除页号引用，并保证每页最多只属于一个虚拟文件夹。 */
    public void sanitize(Set<Integer> validPages) {
        if (layout == null) {
            layout = new LayoutState();
        }
        layout.sanitize();
        if (folders == null) {
            folders = new ArrayList<>();
        }
        if (openTabs == null) {
            openTabs = new ArrayList<>();
        }
        Set<Integer> seen = new LinkedHashSet<>();
        List<VirtualFolder> cleanFolders = new ArrayList<>();
        for (VirtualFolder f : folders) {
            if (f == null || f.id == null || f.id.isBlank()) {
                continue;
            }
            List<Integer> cleanPages = new ArrayList<>();
            if (f.pageIndices != null) {
                for (Integer idx : f.pageIndices) {
                    if (idx != null && (validPages == null || validPages.contains(idx)) && seen.add(idx)) {
                        cleanPages.add(idx);
                    }
                }
            }
            cleanFolders.add(new VirtualFolder(f.id, f.name == null || f.name.isBlank() ? f.id : f.name,
                f.collapsed, cleanPages));
        }
        folders = cleanFolders;
    }

    /** 将指定页移动到目标文件夹（null 表示移回未分类）。 */
    public void movePageToFolder(int pageIndex, String targetFolderId) {
        for (VirtualFolder f : folders) {
            f.pageIndices.removeIf(p -> p != null && p == pageIndex);
        }
        if (targetFolderId == null || targetFolderId.isBlank()) {
            return;
        }
        for (VirtualFolder f : folders) {
            if (targetFolderId.equals(f.id)) {
                if (!f.pageIndices.contains(pageIndex)) {
                    f.pageIndices.add(pageIndex);
                }
                return;
            }
        }
    }

    /** 服务端重排页号映射更新（oldIndex -> newIndex）。 */
    public void remapPageIndices(java.util.Map<Integer, Integer> oldToNew) {
        if (oldToNew == null || oldToNew.isEmpty()) {
            return;
        }
        for (VirtualFolder f : folders) {
            List<Integer> updated = new ArrayList<>(f.pageIndices.size());
            for (Integer idx : f.pageIndices) {
                if (idx != null) {
                    updated.add(oldToNew.getOrDefault(idx, idx));
                }
            }
            f.pageIndices = updated;
        }
    }

    /** 删除文件夹但保留其内所有书页（自然回到未分类分组）。 */
    public void removeFolderKeepPages(String folderId) {
        if (folderId == null) {
            return;
        }
        folders.removeIf(f -> folderId.equals(f.id));
    }
}
