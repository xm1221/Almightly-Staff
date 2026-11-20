package cn.xm1221.AlmightlyStaff.gui.ide.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceTreeSyncTest {

    @Test
    void folderSanitizeDropsDeletedPagesAndPreservesPagesOnFolderRemove() {
        WorkspaceState ws = WorkspaceState.createDefault();
        ws.folders.add(new WorkspaceState.VirtualFolder("combat", "Combat", false, List.of(1, 2, 3)));
        ws.folders.add(new WorkspaceState.VirtualFolder("util", "Utility", true, List.of(4)));

        // 假设服务端删除了第 2 页
        ws.sanitize(Set.of(1, 3, 4));
        assertEquals(List.of(1, 3), ws.folders.get(0).pageIndices);
        assertEquals(List.of(4), ws.folders.get(1).pageIndices);

        // 跨文件夹移动第 3 页
        ws.movePageToFolder(3, "util");
        assertEquals(List.of(1), ws.folders.get(0).pageIndices);
        assertEquals(List.of(4, 3), ws.folders.get(1).pageIndices);

        // 移回未分类
        ws.movePageToFolder(1, null);
        assertTrue(ws.folders.get(0).pageIndices.isEmpty());

        // 删除 util 文件夹不丢失页（只是回到未分类）
        ws.removeFolderKeepPages("util");
        assertEquals(1, ws.folders.size());
    }
}
