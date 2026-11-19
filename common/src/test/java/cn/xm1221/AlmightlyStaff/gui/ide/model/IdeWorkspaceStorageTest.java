package cn.xm1221.AlmightlyStaff.gui.ide.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdeWorkspaceStorageTest {

    @Test
    void roundTripSaveAndLoad(@TempDir Path tempDir) {
        IdeWorkspaceStorage storage = new IdeWorkspaceStorage(tempDir);
        WorkspaceState state = WorkspaceState.createDefault();
        state.layout.leftRatio = 0.25f;
        state.layout.bottomMode = "grid";
        state.folders.add(new WorkspaceState.VirtualFolder("combat", "Combat", false, List.of(1, 3)));
        state.openTabs.add(new WorkspaceState.TabBookmark("spell", 1, "", 0));

        storage.save("abc-123", state);
        WorkspaceState loaded = storage.load("abc-123");

        assertEquals(0.25f, loaded.layout.leftRatio, 0.0001f);
        assertEquals("grid", loaded.layout.bottomMode);
        assertEquals(1, loaded.folders.size());
        assertEquals("Combat", loaded.folders.get(0).name);
        assertEquals(List.of(1, 3), loaded.folders.get(0).pageIndices);
        assertEquals(1, loaded.openTabs.size());
    }

    @Test
    void corruptedJsonFallsBackToDefault(@TempDir Path tempDir) throws Exception {
        IdeWorkspaceStorage storage = new IdeWorkspaceStorage(tempDir);
        Path target = storage.fileFor("corrupt-id");
        Files.createDirectories(target.getParent());
        Files.writeString(target, "{ broken json !!!", StandardCharsets.UTF_8);

        WorkspaceState loaded = storage.load("corrupt-id");
        assertEquals(1, loaded.version);
        assertEquals("split", loaded.layout.bottomMode);
        assertTrue(loaded.folders.isEmpty());
    }

    @Test
    void sanitizePreventsPathTraversal(@TempDir Path tempDir) {
        IdeWorkspaceStorage storage = new IdeWorkspaceStorage(tempDir);
        Path resolved = storage.fileFor("../../etc/passwd");
        assertEquals(tempDir, resolved.getParent());
        assertFalse(resolved.getFileName().toString().contains("/"));
        assertFalse(resolved.getFileName().toString().contains("\\"));
    }

    @Test
    void sanitizeAndRemapFolders() {
        WorkspaceState state = WorkspaceState.createDefault();
        state.folders.add(new WorkspaceState.VirtualFolder("f1", "Folder 1", false, List.of(1, 2, 99)));
        state.folders.add(new WorkspaceState.VirtualFolder("f2", "Folder 2", true, List.of(2, 4)));

        state.sanitize(Set.of(1, 2, 4));
        assertEquals(List.of(1, 2), state.folders.get(0).pageIndices);
        assertEquals(List.of(4), state.folders.get(1).pageIndices); // page 2 deduplicated

        state.movePageToFolder(2, "f2");
        assertEquals(List.of(1), state.folders.get(0).pageIndices);
        assertEquals(List.of(4, 2), state.folders.get(1).pageIndices);

        state.remapPageIndices(Map.of(1, 3, 2, 1));
        assertEquals(List.of(3), state.folders.get(0).pageIndices);
        assertEquals(List.of(4, 1), state.folders.get(1).pageIndices);

        state.removeFolderKeepPages("f1");
        assertEquals(1, state.folders.size());
        assertEquals("f2", state.folders.get(0).id);
    }
}
