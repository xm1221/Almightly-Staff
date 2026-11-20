package cn.xm1221.AlmightlyStaff.gui.ide.model;

import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import cn.xm1221.AlmightlyStaff.gui.ide.core.ToastOverlay;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import cn.xm1221.AlmightlyStaff.network.StaffNetworkBridge;
import cn.xm1221.AlmightlyStaff.spell.PatternCatalogEntry;
import cn.xm1221.AlmightlyStaff.spell.PatternRef;
import cn.xm1221.AlmightlyStaff.spell.StaffHex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 法杖 IDE 会话中心：维护服务端同步页表、UUID 侧车工作区、剪贴板与图案目录缓存。
 */
public final class IdeSession {
    public interface Listener {
        default void onFullSync(int focusPage) {
        }

        default void onPagesUpdated(int focusPage, List<Integer> removed) {
        }

        default void onGreatSpellLearned(PatternRef ref) {
        }

        default void onParseToCodeResult(int requestId, int pageIndex, String code, String error) {
        }

        default void onParseToIotasResult(int requestId, int pageIndex, boolean ok,
                                          List<CompoundTag> tags, String error, int errorLine) {
        }
    }

    private final StaffNetworkBridge network;
    private final IdeWorkspaceStorage storage;
    private final ToastOverlay toast;
    private final TreeMap<Integer, SpellDocument> documents = new TreeMap<>();
    private final List<ModNetworking.PageData> pendingChunks = new ArrayList<>();
    private final Map<String, PatternRef> learnedPerWorld = new HashMap<>();
    private final List<CompoundTag> clipboard = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();

    private List<PatternCatalogEntry> catalog = new ArrayList<>();
    private final Set<String> knownIdentifiers = new HashSet<>();
    private int castPage = 1;
    private String staffUuid = "";
    private WorkspaceState workspace = WorkspaceState.createDefault();
    private boolean workspaceLoaded;

    // 卓越法术待回包上下文
    @Nullable
    private String pendingGreatAction;
    private int pendingGreatPage;
    private int pendingGreatInsertIndex = -1;

    // 导出 / 复制 HexParse 代码请求回调 (requestId -> Consumer<String>)
    private int utilityReqSeq = 700000;
    private final Map<Integer, java.util.function.Consumer<String>> codeActionCallbacks = new HashMap<>();

    public IdeSession(StaffNetworkBridge network, IdeWorkspaceStorage storage, ToastOverlay toast) {
        this.network = network;
        this.storage = storage;
        this.toast = toast;
        reloadCatalog();
    }

    public StaffNetworkBridge network() {
        return network;
    }

    public ToastOverlay toast() {
        return toast;
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public void reloadCatalog() {
        catalog = StaffHex.loadCatalog();
        applyLearnedToCatalog();
        rebuildKnownIdentifiers();
    }

    private void rebuildKnownIdentifiers() {
        knownIdentifiers.clear();
        for (PatternCatalogEntry e : catalog) {
            String id = e.pattern().actionId().toLowerCase(Locale.ROOT);
            if (!id.isEmpty()) {
                knownIdentifiers.add(id);
                int colon = id.indexOf(':');
                if (colon >= 0) {
                    knownIdentifiers.add(id.substring(colon + 1));
                }
            }
            String disp = e.displayName().getString().toLowerCase(Locale.ROOT);
            if (!disp.isEmpty()) {
                knownIdentifiers.add(disp);
            }
        }
    }

    public Set<String> knownIdentifiers() {
        return Collections.unmodifiableSet(knownIdentifiers);
    }

    public List<PatternCatalogEntry> catalog() {
        return catalog;
    }

    public Collection<SpellDocument> documents() {
        return documents.values();
    }

    @Nullable
    public SpellDocument document(int pageIndex) {
        return documents.get(pageIndex);
    }

    public int castPage() {
        return castPage;
    }

    public String staffUuid() {
        return staffUuid;
    }

    public WorkspaceState workspace() {
        return workspace;
    }

    public List<CompoundTag> clipboard() {
        return clipboard;
    }

    public void setClipboard(List<CompoundTag> tags) {
        clipboard.clear();
        if (tags != null) {
            for (CompoundTag t : tags) {
                if (t != null) clipboard.add(t.copy());
            }
        }
    }

    public void requestSync() {
        network.requestFullSync();
    }

    public void onSyncChunk(List<ModNetworking.PageData> chunkPages, int castPage, int focusPage,
                            List<Integer> removed, boolean lastChunk, boolean incremental,
                            String status, String uuid) {
        if (uuid != null && !uuid.isEmpty()) {
            boolean uuidChanged = !uuid.equals(this.staffUuid);
            this.staffUuid = uuid;
            if (!workspaceLoaded || uuidChanged) {
                this.workspace = storage.load(uuid);
                this.workspaceLoaded = true;
            }
        }
        if (incremental) {
            onIncrementalSync(chunkPages, castPage, focusPage, removed, status);
            return;
        }
        pendingChunks.addAll(chunkPages);
        if (!lastChunk) return;
        List<ModNetworking.PageData> all = new ArrayList<>(pendingChunks);
        pendingChunks.clear();
        onFullSync(all, castPage, focusPage);
    }

    private void onFullSync(List<ModNetworking.PageData> all, int castPage, int focusPage) {
        this.castPage = castPage;
        Set<Integer> incomingIds = new HashSet<>();
        for (ModNetworking.PageData p : all) {
            incomingIds.add(p.pageIndex());
            SpellDocument doc = documents.get(p.pageIndex());
            if (doc == null) {
                documents.put(p.pageIndex(), new SpellDocument(p.pageIndex(), p.name(), p.iotas(), p.sealed()));
            } else {
                doc.applyServerSnapshot(p.name(), p.iotas(), p.sealed(), false);
            }
        }
        documents.keySet().removeIf(k -> !incomingIds.contains(k));
        workspace.sanitize(documents.keySet());
        for (Listener l : List.copyOf(listeners)) {
            l.onFullSync(focusPage);
        }
    }

    private void onIncrementalSync(List<ModNetworking.PageData> updated, int castPage, int focusPage,
                                   List<Integer> removed, String status) {
        this.castPage = castPage;
        List<Integer> removedList = removed == null ? List.of() : removed;
        for (int idx : removedList) {
            documents.remove(idx);
        }
        boolean rejected = status != null && !status.isEmpty();
        if (rejected) {
            toast.showError(Component.translatable("almightly_staff.gui." + status).getString());
        }
        for (ModNetworking.PageData p : updated) {
            SpellDocument doc = documents.get(p.pageIndex());
            if (doc == null) {
                documents.put(p.pageIndex(), new SpellDocument(p.pageIndex(), p.name(), p.iotas(), p.sealed()));
            } else if (rejected || !doc.isDirty()) {
                doc.applyServerSnapshot(p.name(), p.iotas(), p.sealed(), rejected);
            } else {
                doc.setName(p.name());
                doc.setSealed(p.sealed());
            }
        }
        workspace.sanitize(documents.keySet());
        for (Listener l : List.copyOf(listeners)) {
            l.onPagesUpdated(focusPage, removedList);
        }
    }

    public void saveDocument(SpellDocument doc) {
        if (doc == null || doc.isSealed()) return;
        network.sendPageWrite(doc.pageIndex(), doc.copyIotas());
        doc.markSaved();
    }

    public void saveAllDirty() {
        int count = 0;
        for (SpellDocument doc : documents.values()) {
            if (doc.isDirty() && !doc.isSealed()) {
                saveDocument(doc);
                count++;
            }
        }
        saveWorkspace();
        if (count > 0) {
            toast.show(Component.translatable("almightly_staff.ide.saved_pages", count).getString());
        } else {
            toast.show(Component.translatable("almightly_staff.ide.saved_layout").getString());
        }
    }

    public void saveWorkspace() {
        if (!staffUuid.isEmpty()) {
            workspace.sanitize(documents.keySet());
            storage.save(staffUuid, workspace);
        }
    }

    public void selectCastPage(int pageIndex) {
        if (documents.containsKey(pageIndex)) {
            network.sendPageOp(ModNetworking.OP_SELECT, pageIndex, 0, false, "");
        }
    }

    public void renamePage(int pageIndex, String newName) {
        SpellDocument doc = documents.get(pageIndex);
        if (doc == null) return;
        String clean = newName == null ? "" : newName.trim();
        if (clean.equals(doc.name())) return;
        doc.setName(clean);
        network.sendPageOp(ModNetworking.OP_RENAME, pageIndex, 0, false, clean);
    }

    public void createNewPage() {
        network.sendPageOp(ModNetworking.OP_NEW, 0, 0, false, "");
    }

    public void duplicatePage(int pageIndex, String copyName) {
        network.sendPageOp(ModNetworking.OP_DUPLICATE, pageIndex, 0, false, copyName == null ? "" : copyName);
    }

    public void deletePage(int pageIndex) {
        SpellDocument doc = documents.get(pageIndex);
        if (doc != null && doc.isSealed()) {
            toast.showError(Component.translatable("almightly_staff.gui.page_sealed").getString());
            return;
        }
        network.sendPageOp(ModNetworking.OP_DELETE, pageIndex, 0, false, "");
    }

    public void reorderPage(int fromIndex, int targetIndex, boolean after) {
        network.sendPageOp(ModNetworking.OP_REORDER, fromIndex, targetIndex, after, "");
    }

    public void requestGreatSpell(String actionId, int pageIndex, int insertIndex) {
        pendingGreatAction = actionId;
        pendingGreatPage = pageIndex;
        pendingGreatInsertIndex = insertIndex;
        network.checkGreatSpell(actionId, insertIndex);
    }

    public void onGreatSpellCheck(String actionId, boolean usable, String signature, String startDir) {
        String pending = pendingGreatAction;
        int page = pendingGreatPage;
        int insertAt = pendingGreatInsertIndex;
        pendingGreatAction = null;
        pendingGreatPage = 0;
        pendingGreatInsertIndex = -1;
        if (pending == null || !pending.equals(actionId)) return;
        if (!usable || signature == null || signature.isEmpty() || startDir == null || startDir.isEmpty()) {
            toast.showError(Component.translatable("almightly_staff.gui.great_spell_unusable").getString());
            return;
        }
        PatternRef learned = new PatternRef(actionId, startDir, signature);
        learnGreatSpell(learned);
        SpellDocument doc = documents.get(page);
        if (doc != null && !doc.isSealed()) {
            CompoundTag tag = IotaType.serialize(new PatternIota(StaffHex.patternFor(learned)));
            int pos = insertAt >= 0 ? Math.min(insertAt, doc.size()) : doc.size();
            doc.insertAt(pos, List.of(tag), "catalog");
            saveDocument(doc);
            toast.show(Component.translatable("almightly_staff.gui.great_spell_added").getString());
        }
    }

    public void learnGreatSpell(PatternRef ref) {
        if (ref == null || ref.actionId().isEmpty() || ref.signature().isBlank()) return;
        if (StaffHex.isPerWorldAction(ref.actionId())) {
            learnedPerWorld.put(ref.actionId(), ref);
            applyLearnedToCatalog();
            rebuildKnownIdentifiers();
            for (Listener l : List.copyOf(listeners)) {
                l.onGreatSpellLearned(ref);
            }
        }
    }

    private void applyLearnedToCatalog() {
        for (int i = 0; i < catalog.size(); i++) {
            PatternCatalogEntry entry = catalog.get(i);
            if (entry.kind() == PatternCatalogEntry.Kind.PER_WORLD) {
                PatternRef learned = learnedPerWorld.get(entry.pattern().actionId());
                if (learned != null && !learned.signature().isBlank()) {
                    catalog.set(i, new PatternCatalogEntry(learned, entry.displayName(), PatternCatalogEntry.Kind.NORMAL));
                }
            }
        }
    }

    public void requestCodeForAction(int pageIndex, java.util.function.Consumer<String> onCodeReady) {
        SpellDocument doc = documents.get(pageIndex);
        if (doc == null) return;
        if (dev.architectury.platform.Platform.isModLoaded("hexparse")) {
            int req = ++utilityReqSeq;
            codeActionCallbacks.put(req, onCodeReady);
            network.requestIdeParseToCode(req, pageIndex, doc.copyIotas());
        } else {
            onCodeReady.accept(cn.xm1221.AlmightlyStaff.gui.ide.tool.HexParseEditorPanel.formatTagsOffline(doc.iotasView()));
        }
    }

    public void onIdeParseToCode(int requestId, int pageIndex, String code, String error) {
        java.util.function.Consumer<String> cb = codeActionCallbacks.remove(requestId);
        if (cb != null) {
            if (error != null && !error.isEmpty()) {
                toast.showError(error);
            } else {
                cb.accept(code == null ? "" : code);
            }
            return;
        }
        for (Listener l : List.copyOf(listeners)) {
            l.onParseToCodeResult(requestId, pageIndex, code, error);
        }
    }

    public void onIdeParseToIotas(int requestId, int pageIndex, boolean ok,
                                  List<CompoundTag> tags, String error, int errorLine) {
        for (Listener l : List.copyOf(listeners)) {
            l.onParseToIotasResult(requestId, pageIndex, ok, tags, error, errorLine);
        }
    }
}
