package cn.xm1221.AlmightlyStaff.gui.ide.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 客户端侧车工作区持久化模型（按法杖 UUID 存在 {@code config/almightly_staff/ide/<uuid>.json}）。
 * 保存自由停靠树、浮动窗位置/最小化状态、虚拟目录树以及各页手动换行排版标记。
 */
public final class WorkspaceState {
    public int version = 2;
    public LayoutState layout = new LayoutState();
    public DockLayoutDto dockTree = null;
    public Map<String, WindowPlacement> windowPlacements = new HashMap<>();
    public List<VirtualFolder> folders = new ArrayList<>();
    public List<TabBookmark> openTabs = new ArrayList<>();
    public Map<Integer, List<Integer>> pageLineBreaks = new HashMap<>();
    public Map<Integer, Map<Integer, Integer>> pageSpaces = new HashMap<>();

    public static final class DockLayoutDto {
        public String kind = "leaf"; // "leaf" | "split"
        public String id = "";
        public String axis = "H"; // "H" | "V"
        public float ratio = 0.5f;
        public List<String> tabs = new ArrayList<>();
        public int activeTab = 0;
        public DockLayoutDto first;
        public DockLayoutDto second;
    }

    public static final class WindowPlacement {
        public boolean floating = false;
        public boolean minimized = false;
        public int x = 80, y = 40, w = 260, h = 200;

        public WindowPlacement() {
        }

        public WindowPlacement(boolean floating, boolean minimized, int x, int y, int w, int h) {
            this.floating = floating;
            this.minimized = minimized;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    public static final class LayoutState {
        public float leftRatio = 0.36f;
        public float catalogRatio = 0.24f;
        public float bottomRatio = 0.34f;
        public float bottomInnerRatio = 0.42f;
        public float editorSplitRatio = 0.50f;
        public boolean leftCollapsed = false;
        public boolean catalogCollapsed = false;
        public boolean bottomCollapsed = false;
        public boolean editorSplitOpen = false;
        public String bottomMode = "split";

        public void sanitize() {
            leftRatio = clamp(leftRatio, 0.12f, 0.65f, 0.36f);
            catalogRatio = clamp(catalogRatio, 0.14f, 0.45f, 0.24f);
            bottomRatio = clamp(bottomRatio, 0.16f, 0.65f, 0.34f);
            bottomInnerRatio = clamp(bottomInnerRatio, 0.20f, 0.80f, 0.42f);
            editorSplitRatio = clamp(editorSplitRatio, 0.20f, 0.80f, 0.50f);
        }

        private static float clamp(float val, float min, float max, float fallback) {
            if (Float.isNaN(val) || Float.isInfinite(val)) return fallback;
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
        public String kind = "spell";
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
        // 出厂布局：左边充足高度的 HexGrid 画板 (36%)，右边为主角 Iota 文本 (64%)
        DockLayoutDto left = new DockLayoutDto();
        left.kind = "leaf";
        left.id = "slot_left";
        left.tabs.add("hex_grid");

        DockLayoutDto right = new DockLayoutDto();
        right.kind = "leaf";
        right.id = "slot_main";
        right.tabs.add("iota_text");

        DockLayoutDto root = new DockLayoutDto();
        root.kind = "split";
        root.id = "root";
        root.axis = "H";
        root.ratio = 0.36f;
        root.first = left;
        root.second = right;
        state.dockTree = root;
        return state;
    }

    /** 清理无效字段与已删除页号引用，并保证每页最多只属于一个虚拟文件夹。 */
    public void sanitize(Set<Integer> validPages) {
        if (layout == null) layout = new LayoutState();
        layout.sanitize();
        if (windowPlacements == null) windowPlacements = new HashMap<>();
        if (folders == null) folders = new ArrayList<>();
        if (openTabs == null) openTabs = new ArrayList<>();
        if (pageLineBreaks == null) pageLineBreaks = new HashMap<>();
        if (pageSpaces == null) pageSpaces = new HashMap<>();

        Set<Integer> seen = new LinkedHashSet<>();
        List<VirtualFolder> cleanFolders = new ArrayList<>();
        for (VirtualFolder f : folders) {
            if (f == null || f.id == null || f.id.isBlank()) continue;
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

    public Set<Integer> lineBreaksForPage(int pageIndex) {
        List<Integer> list = pageLineBreaks.get(pageIndex);
        return list == null ? new LinkedHashSet<>() : new LinkedHashSet<>(list);
    }

    public void setLineBreaksForPage(int pageIndex, Set<Integer> breaks) {
        if (breaks == null || breaks.isEmpty()) {
            pageLineBreaks.remove(pageIndex);
        } else {
            pageLineBreaks.put(pageIndex, new ArrayList<>(breaks));
        }
    }

    public Map<Integer, Integer> spacesForPage(int pageIndex) {
        Map<Integer, Integer> map = pageSpaces.get(pageIndex);
        return map == null ? new HashMap<>() : new HashMap<>(map);
    }

    public void setSpacesForPage(int pageIndex, Map<Integer, Integer> spaces) {
        if (spaces == null || spaces.isEmpty()) {
            pageSpaces.remove(pageIndex);
        } else {
            Map<Integer, Integer> clean = new HashMap<>();
            for (Map.Entry<Integer, Integer> e : spaces.entrySet()) {
                if (e.getKey() != null && e.getKey() >= 0 && e.getValue() != null && e.getValue() > 0) {
                    clean.put(e.getKey(), e.getValue());
                }
            }
            if (clean.isEmpty()) pageSpaces.remove(pageIndex);
            else pageSpaces.put(pageIndex, clean);
        }
    }

    public void movePageToFolder(int pageIndex, String targetFolderId) {
        for (VirtualFolder f : folders) {
            f.pageIndices.removeIf(p -> p != null && p == pageIndex);
        }
        if (targetFolderId == null || targetFolderId.isBlank()) return;
        for (VirtualFolder f : folders) {
            if (targetFolderId.equals(f.id)) {
                if (!f.pageIndices.contains(pageIndex)) f.pageIndices.add(pageIndex);
                return;
            }
        }
    }

    public void remapPageIndices(Map<Integer, Integer> oldToNew) {
        if (oldToNew == null || oldToNew.isEmpty()) return;
        for (VirtualFolder f : folders) {
            List<Integer> updated = new ArrayList<>(f.pageIndices.size());
            for (Integer idx : f.pageIndices) {
                if (idx != null) updated.add(oldToNew.getOrDefault(idx, idx));
            }
            f.pageIndices = updated;
        }
        Map<Integer, List<Integer>> nextBreaks = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> e : pageLineBreaks.entrySet()) {
            nextBreaks.put(oldToNew.getOrDefault(e.getKey(), e.getKey()), e.getValue());
        }
        pageLineBreaks = nextBreaks;
        Map<Integer, Map<Integer, Integer>> nextSpaces = new HashMap<>();
        for (Map.Entry<Integer, Map<Integer, Integer>> e : pageSpaces.entrySet()) {
            nextSpaces.put(oldToNew.getOrDefault(e.getKey(), e.getKey()), e.getValue());
        }
        pageSpaces = nextSpaces;
    }

    public void removeFolderKeepPages(String folderId) {
        if (folderId == null) return;
        folders.removeIf(f -> folderId.equals(f.id));
    }
}
