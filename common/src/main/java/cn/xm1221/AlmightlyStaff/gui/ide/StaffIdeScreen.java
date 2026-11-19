package cn.xm1221.AlmightlyStaff.gui.ide;

import at.petrak.hexcasting.api.casting.eval.ExecutionClientView;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffConfig;
import cn.xm1221.AlmightlyStaff.gui.StaffCastScreen;
import cn.xm1221.AlmightlyStaff.gui.StaffDrawScreen;
import cn.xm1221.AlmightlyStaff.gui.StaffLibScreen;
import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.ContextMenuOverlay;
import cn.xm1221.AlmightlyStaff.gui.ide.core.FocusManager;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.gui.ide.core.ToastOverlay;
import cn.xm1221.AlmightlyStaff.gui.ide.editor.EditorArea;
import cn.xm1221.AlmightlyStaff.gui.ide.editor.SequenceEditorView;
import cn.xm1221.AlmightlyStaff.gui.ide.editor.TabItem;
import cn.xm1221.AlmightlyStaff.gui.ide.host.EmbeddedScreenHost;
import cn.xm1221.AlmightlyStaff.gui.ide.host.MarkdownNotePanel;
import cn.xm1221.AlmightlyStaff.gui.ide.host.PatchouliHostBridge;
import cn.xm1221.AlmightlyStaff.gui.ide.layout.DockNode;
import cn.xm1221.AlmightlyStaff.gui.ide.layout.FloatingWindow;
import cn.xm1221.AlmightlyStaff.gui.ide.layout.SplitterDragState;
import cn.xm1221.AlmightlyStaff.gui.ide.layout.ToolWindowStripe;
import cn.xm1221.AlmightlyStaff.gui.ide.layout.WindowManager;
import cn.xm1221.AlmightlyStaff.gui.ide.model.IdeSession;
import cn.xm1221.AlmightlyStaff.gui.ide.model.IdeWorkspaceStorage;
import cn.xm1221.AlmightlyStaff.gui.ide.model.SpellDocument;
import cn.xm1221.AlmightlyStaff.gui.ide.model.WorkspaceState;
import cn.xm1221.AlmightlyStaff.gui.ide.search.SearchIndex;
import cn.xm1221.AlmightlyStaff.gui.ide.search.TopSearchBar;
import cn.xm1221.AlmightlyStaff.gui.ide.tool.BottomToolDock;
import cn.xm1221.AlmightlyStaff.gui.ide.tool.HexGridPanel;
import cn.xm1221.AlmightlyStaff.gui.ide.tool.HexParseEditorPanel;
import cn.xm1221.AlmightlyStaff.gui.ide.tool.PageTreePanel;
import cn.xm1221.AlmightlyStaff.gui.ide.tool.PatternCatalogPanel;
import cn.xm1221.AlmightlyStaff.network.ArchitecturyNetworkBridge;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import cn.xm1221.AlmightlyStaff.network.StaffSyncReceiver;
import cn.xm1221.AlmightlyStaff.spell.PatternCatalogEntry;
import cn.xm1221.AlmightlyStaff.spell.PatternRef;
import cn.xm1221.AlmightlyStaff.spell.StaffHex;
import dev.architectury.platform.Platform;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 万法之杖 IDEA 风格主编辑器界面（1.20.1 Common Core）。
 */
public final class StaffIdeScreen extends Screen implements StaffSyncReceiver, IdeSession.Listener {
    private static final int STATUSBAR_H = 18;

    private final ToastOverlay toast = new ToastOverlay();
    private final ContextMenuOverlay contextMenu = new ContextMenuOverlay();
    private final FocusManager focusManager = new FocusManager(() -> this.setFocused(null));
    private final IdeSession session = new IdeSession(
        ArchitecturyNetworkBridge.INSTANCE, IdeWorkspaceStorage.defaultInstance(), toast);

    private final SearchIndex searchIndex = new SearchIndex();
    private final TopSearchBar topSearchBar;
    private final ToolWindowStripe leftStripe = new ToolWindowStripe();
    private final PageTreePanel treePanel;
    private final EditorArea editorArea;
    private final HexGridPanel hexGridPanel;
    private final HexParseEditorPanel hexParsePanel;
    private final BottomToolDock bottomDock;
    private final PatternCatalogPanel catalogPanel;
    private final WindowManager windowManager = new WindowManager();

    private final DockNode.LeafNode treeLeaf = new DockNode.LeafNode("tree");
    private final DockNode.LeafNode editorLeaf = new DockNode.LeafNode("editor");
    private final DockNode.LeafNode bottomLeaf = new DockNode.LeafNode("bottom");
    private final DockNode.LeafNode catalogLeaf = new DockNode.LeafNode("catalog");

    private DockNode.SplitNode centerColSplit;
    private DockNode.SplitNode rightSplit;
    private DockNode.SplitNode rootSplit;
    private final SplitterDragState splitterDrag = new SplitterDragState();
    private Bounds dockBounds = Bounds.EMPTY;

    private boolean syncRequested = false;
    private boolean workspaceApplied = false;
    private int floatingCounter = 0;

    public StaffIdeScreen() {
        super(Component.translatable("almightly_staff.ide.title"));
        this.session.addListener(this);

        this.topSearchBar = new TopSearchBar(searchIndex, focusManager, this::rebuildSearchIndex);
        this.editorArea = new EditorArea(new EditorCallbacksImpl());
        this.hexGridPanel = new HexGridPanel(session, tags -> insertTagsIntoActiveEditor(tags, "grid"));
        this.hexParsePanel = new HexParseEditorPanel(session, focusManager,
            () -> {
                SpellDocument doc = editorArea.activeSpellDocument();
                if (doc != null) session.saveDocument(doc);
            });
        this.bottomDock = new BottomToolDock(hexGridPanel, hexParsePanel, 0.42f, "split");
        this.treePanel = new PageTreePanel(session, focusManager, new TreeCallbacksImpl());
        this.catalogPanel = new PatternCatalogPanel(session, focusManager,
            editorArea::activeSpellDocument,
            this::insertCatalogEntryAtCursor,
            this::dropCatalogEntry);

        buildDockTree(WorkspaceState.createDefault());
        buildStripeButtons();
        buildTopActions();
    }

    private void buildDockTree(WorkspaceState ws) {
        WorkspaceState.LayoutState l = ws.layout;
        treeLeaf.setCollapsed(l.leftCollapsed);
        bottomLeaf.setCollapsed(l.bottomCollapsed);
        catalogLeaf.setCollapsed(l.catalogCollapsed);

        centerColSplit = new DockNode.SplitNode("center_col", DockNode.Axis.VERTICAL,
            1.0f - l.bottomRatio, 100, 100, editorLeaf, bottomLeaf);
        rightSplit = new DockNode.SplitNode("right_area", DockNode.Axis.HORIZONTAL,
            1.0f - l.catalogRatio, 180, 120, centerColSplit, catalogLeaf);
        rootSplit = new DockNode.SplitNode("root", DockNode.Axis.HORIZONTAL,
            l.leftRatio, 110, 220, treeLeaf, rightSplit);
        bottomDock.setMode(l.bottomMode);
    }

    private void buildStripeButtons() {
        leftStripe.addButton(new ToolWindowStripe.StripeButton(
            "tree", IdeTheme.Icon.FOLDER_CLOSED,
            Component.translatable("almightly_staff.ide.toggle_tree"),
            () -> !treeLeaf.isCollapsed(),
            () -> toggleLeaf(treeLeaf)
        ));
        leftStripe.addButton(new ToolWindowStripe.StripeButton(
            "bottom", IdeTheme.Icon.SPLIT_VIEW,
            Component.translatable("almightly_staff.ide.toggle_bottom"),
            () -> !bottomLeaf.isCollapsed(),
            () -> toggleLeaf(bottomLeaf)
        ));
        leftStripe.addButton(new ToolWindowStripe.StripeButton(
            "catalog", IdeTheme.Icon.SPELL_STAR,
            Component.translatable("almightly_staff.ide.toggle_catalog"),
            () -> !catalogLeaf.isCollapsed(),
            () -> toggleLeaf(catalogLeaf)
        ));
    }

    private void buildTopActions() {
        topSearchBar.addAction(new TopSearchBar.ActionButton(
            "split", IdeTheme.Icon.SPLIT_VIEW,
            Component.translatable("almightly_staff.ide.btn_split"), false,
            () -> editorArea.toggleSplit(this::defaultFallbackTab)
        ));
        topSearchBar.addAction(new TopSearchBar.ActionButton(
            "float_note", IdeTheme.Icon.FLOAT_WIN,
            Component.translatable("almightly_staff.ide.btn_float_note"), false,
            this::openFloatingNote
        ));
        if (Platform.isModLoaded("patchouli")) {
            topSearchBar.addAction(new TopSearchBar.ActionButton(
                "patchouli", IdeTheme.Icon.BOOK_EXTERNAL,
                Component.translatable("almightly_staff.ide.btn_patchouli"), false,
                this::openPatchouliInTab
            ));
        }
        topSearchBar.addAction(new TopSearchBar.ActionButton(
            "reset", IdeTheme.Icon.DOCK_BACK,
            Component.translatable("almightly_staff.ide.btn_reset_layout"), false,
            this::resetLayout
        ));
        topSearchBar.addAction(new TopSearchBar.ActionButton(
            "save", IdeTheme.Icon.SAVE_DISK,
            Component.translatable("almightly_staff.ide.btn_save"), true,
            this::saveAll
        ));
    }

    private void toggleLeaf(DockNode.LeafNode leaf) {
        leaf.setCollapsed(!leaf.isCollapsed());
        layoutAll();
        persistLayoutState();
    }

    private void resetLayout() {
        WorkspaceState def = WorkspaceState.createDefault();
        buildDockTree(def);
        editorArea.setSplitState(false, 0.50f, false);
        layoutAll();
        persistLayoutState();
        toast.show(Component.translatable("almightly_staff.ide.layout_reset").getString());
    }

    private void saveAll() {
        persistLayoutState();
        session.saveAllDirty();
    }

    private void persistLayoutState() {
        WorkspaceState.LayoutState l = session.workspace().layout;
        l.leftRatio = rootSplit.ratio();
        l.catalogRatio = 1.0f - rightSplit.ratio();
        l.bottomRatio = 1.0f - centerColSplit.ratio();
        l.bottomInnerRatio = bottomDock.splitRatio();
        l.editorSplitRatio = editorArea.splitRatio();
        l.leftCollapsed = treeLeaf.isCollapsed();
        l.catalogCollapsed = catalogLeaf.isCollapsed();
        l.bottomCollapsed = bottomLeaf.isCollapsed();
        l.editorSplitOpen = editorArea.isSplitOpen();
        l.bottomMode = bottomDock.mode();

        session.workspace().openTabs.clear();
        for (TabItem t : editorArea.primary().tabs()) {
            if (t instanceof TabItem.SpellTab st) {
                session.workspace().openTabs.add(new WorkspaceState.TabBookmark("spell", st.document().pageIndex(), "", 0));
            }
        }
    }

    @Override
    protected void init() {
        layoutAll();
        windowManager.clampAll(width, height, TopSearchBar.HEIGHT);
        if (!syncRequested) {
            syncRequested = true;
            session.requestSync();
        }
    }

    private void layoutAll() {
        topSearchBar.layout(new Bounds(0, 0, width, TopSearchBar.HEIGHT));
        int bodyH = Math.max(0, height - TopSearchBar.HEIGHT - STATUSBAR_H);
        leftStripe.layout(new Bounds(0, TopSearchBar.HEIGHT, ToolWindowStripe.WIDTH, bodyH));
        dockBounds = new Bounds(ToolWindowStripe.WIDTH, TopSearchBar.HEIGHT,
            Math.max(0, width - ToolWindowStripe.WIDTH), bodyH);
        rootSplit.layout(dockBounds);
        treePanel.layout(treeLeaf.bounds());
        editorArea.layout(editorLeaf.bounds());
        bottomDock.layout(bottomLeaf.bounds());
        catalogPanel.layout(catalogLeaf.bounds());
    }

    // ==================== 同步与回调 ====================

    @Override
    public void onSyncChunk(List<ModNetworking.PageData> chunkPages, int castPage, int focusPage,
                            List<Integer> removed, boolean lastChunk, boolean incremental,
                            String status, String staffUuid) {
        session.onSyncChunk(chunkPages, castPage, focusPage, removed, lastChunk, incremental, status, staffUuid);
    }

    @Override
    public void onGreatSpellCheck(String actionId, boolean usable, String signature, String startDir) {
        session.onGreatSpellCheck(actionId, usable, signature, startDir);
        catalogPanel.refreshFilter();
    }

    @Override
    public void onIdeParseToCode(int requestId, int pageIndex, String code, String error) {
        session.onIdeParseToCode(requestId, pageIndex, code, error);
    }

    @Override
    public void onIdeParseToIotas(int requestId, int pageIndex, boolean ok,
                                  List<CompoundTag> iotaTags, String error, int errorLine) {
        session.onIdeParseToIotas(requestId, pageIndex, ok, iotaTags, error, errorLine);
    }

    @Override
    public void onFullSync(int focusPage) {
        if (!workspaceApplied) {
            workspaceApplied = true;
            buildDockTree(session.workspace());
            if (session.workspace().layout.editorSplitOpen) {
                editorArea.setSplitState(true, session.workspace().layout.editorSplitRatio, false);
            }
            layoutAll();
            for (WorkspaceState.TabBookmark bm : session.workspace().openTabs) {
                if ("spell".equals(bm.kind) && session.document(bm.pageIndex) != null) {
                    openSpellPage(bm.pageIndex, false);
                }
            }
        }
        int target = focusPage > 0 ? focusPage
            : (editorArea.activeSpellDocument() != null ? editorArea.activeSpellDocument().pageIndex() : session.castPage());
        if (session.document(target) == null && !session.documents().isEmpty()) {
            target = session.documents().iterator().next().pageIndex();
        }
        if (target > 0 && session.document(target) != null && editorArea.primary().tabs().isEmpty()) {
            openSpellPage(target, false);
        } else if (focusPage > 0 && session.document(focusPage) != null) {
            openSpellPage(focusPage, false);
        }
        hexParsePanel.bindDocument(editorArea.activeSpellDocument(), true);
    }

    @Override
    public void onPagesUpdated(int focusPage, List<Integer> removed) {
        if (removed != null) {
            for (int r : removed) {
                editorArea.closeSpellPageEverywhere(r);
            }
        }
        if (focusPage > 0 && session.document(focusPage) != null) {
            openSpellPage(focusPage, false);
        }
        hexParsePanel.bindDocument(editorArea.activeSpellDocument(), false);
    }

    @Override
    public void onGreatSpellLearned(PatternRef ref) {
        catalogPanel.refreshFilter();
    }

    public void onSpellcastingServerUpdate(ExecutionClientView info, int index) {
        hexGridPanel.onServerUpdate(info, index);
    }

    // ==================== 业务操作 ====================

    private void openSpellPage(int pageIndex, boolean inSplit) {
        SpellDocument doc = session.document(pageIndex);
        if (doc == null) return;
        TabItem.SpellTab tab = new TabItem.SpellTab(doc, new SequenceCallbacksImpl());
        if (inSplit) {
            editorArea.openInSplit(tab, false);
        } else {
            editorArea.openInFocused(tab);
        }
        treePanel.setActivePage(pageIndex);
        hexParsePanel.bindDocument(doc, true);
    }

    @Nullable
    private TabItem defaultFallbackTab() {
        SpellDocument doc = editorArea.activeSpellDocument();
        if (doc == null && !session.documents().isEmpty()) {
            doc = session.documents().iterator().next();
        }
        if (doc == null) return null;
        return new TabItem.SpellTab(doc, new SequenceCallbacksImpl());
    }

    private void openNoteTab(PageTreePanel.NoteEntry note, boolean inSplit) {
        MarkdownNotePanel panel = new MarkdownNotePanel(
            session, note,
            this::insertPatternRefAtCursor,
            this::insertHexParseCodeSnippet
        );
        TabItem.NoteTab tab = new TabItem.NoteTab(note.id(), Component.literal(note.title()), panel);
        if (inSplit) {
            editorArea.openInSplit(tab, false);
        } else {
            editorArea.openInFocused(tab);
        }
    }

    private void openFloatingNote() {
        List<PageTreePanel.NoteEntry> notes = MarkdownNotePanel.loadAllNotes();
        if (notes.isEmpty()) return;
        PageTreePanel.NoteEntry note = notes.get(0);
        floatingCounter++;
        String winId = "note_" + floatingCounter;
        MarkdownNotePanel panel = new MarkdownNotePanel(
            session, note,
            this::insertPatternRefAtCursor,
            this::insertHexParseCodeSnippet
        );
        Bounds initB = new Bounds(Math.max(40, width / 2 - 140), 50 + (floatingCounter % 4) * 18, 280, 210);
        FloatingWindow fw = new FloatingWindow(
            winId, Component.literal(note.title()), panel, initB, 200, 140,
            () -> {
                windowManager.remove(winId);
                openNoteTab(note, false);
            },
            () -> windowManager.remove(winId)
        );
        windowManager.add(fw);
    }

    private void openPatchouliInTab() {
        Screen bookScreen = PatchouliHostBridge.openHexBookCaptured();
        if (bookScreen == null) {
            toast.showError(Component.translatable("almightly_staff.ide.patchouli_missing").getString());
            return;
        }
        String tabId = "ext:patchouli";
        EmbeddedScreenHost host = new EmbeddedScreenHost(
            bookScreen,
            () -> editorArea.focusedGroup().closeByTabId(tabId),
            this::insertPatternRefAtCursor
        );
        TabItem.ExternalTab tab = new TabItem.ExternalTab("patchouli",
            Component.translatable("almightly_staff.ide.patchouli_book"), host);
        if (editorArea.isSplitOpen()) {
            editorArea.secondary().openOrFocus(tab);
            editorArea.setFocusedGroup(editorArea.secondary());
        } else {
            editorArea.openInFocused(tab);
        }
    }

    private void insertPatternRefAtCursor(PatternRef ref) {
        if (ref == null) return;
        if (ref.signature().isBlank()) {
            SpellDocument doc = editorArea.activeSpellDocument();
            if (doc != null) {
                session.requestGreatSpell(ref.actionId(), doc.pageIndex(),
                    editorArea.activeSpellTab() == null ? doc.size() : editorArea.activeSpellTab().cursorIndex());
            }
            return;
        }
        CompoundTag tag = IotaType.serialize(new PatternIota(StaffHex.patternFor(ref)));
        insertTagsIntoActiveEditor(List.of(tag), "note");
        toast.show(Component.translatable("almightly_staff.ide.inserted_pattern",
            StaffHex.patternDisplayName(ref) != null ? StaffHex.patternDisplayName(ref).getString() : ref.actionId()).getString());
    }

    private void insertHexParseCodeSnippet(String code) {
        SpellDocument doc = editorArea.activeSpellDocument();
        if (doc == null || doc.isSealed()) return;
        if (Platform.isModLoaded("hexparse")) {
            session.network().requestIdeParseToIotas(999999, doc.pageIndex(), code);
            toast.show(Component.translatable("almightly_staff.ide.applied_snippet").getString());
        } else {
            toast.showError(Component.translatable("almightly_staff.gui.hexparse_missing").getString());
        }
    }

    private void insertCatalogEntryAtCursor(PatternCatalogEntry entry) {
        SpellDocument doc = editorArea.activeSpellDocument();
        if (doc == null || doc.isSealed()) return;
        PatternRef ref = entry.pattern();
        if (ref.signature().isBlank()) {
            TabItem.SpellTab st = editorArea.activeSpellTab();
            session.requestGreatSpell(ref.actionId(), doc.pageIndex(), st == null ? doc.size() : st.cursorIndex());
            return;
        }
        CompoundTag tag = IotaType.serialize(new PatternIota(StaffHex.patternFor(ref)));
        insertTagsIntoActiveEditor(List.of(tag), "catalog");
    }

    private void dropCatalogEntry(PatternCatalogEntry entry, double[] xy) {
        SequenceEditorView seq = editorArea.findSequenceViewAt(xy[0], xy[1]);
        if (seq != null) {
            seq.acceptCatalogDrop(entry, xy[0], xy[1]);
        }
    }

    private void insertTagsIntoActiveEditor(List<CompoundTag> tags, String source) {
        TabItem.SpellTab st = editorArea.activeSpellTab();
        if (st == null) return;
        st.view().insertTagsAtCursor(tags, source);
    }

    private void rebuildSearchIndex() {
        searchIndex.clear();
        for (SpellDocument doc : session.documents()) {
            String title = doc.name().isEmpty()
                ? Component.translatable("almightly_staff.gui.page_label", doc.pageIndex()).getString()
                : "#" + doc.pageIndex() + " " + doc.name();
            int pid = doc.pageIndex();
            searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.PAGE, "page:" + pid, title,
                "#" + pid, title + " " + pid, pid, "", "", "", () -> openSpellPage(pid, false)));
        }
        for (PatternCatalogEntry entry : session.catalog()) {
            PatternRef ref = entry.pattern();
            String cn = entry.displayName().getString();
            searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.PATTERN, ref.actionId(),
                cn + " (" + ref.actionId() + ")", ref.signature(),
                cn + " " + ref.actionId() + " " + ref.signature(),
                0, ref.actionId(), ref.startDirection(), ref.signature(),
                () -> insertCatalogEntryAtCursor(entry)));
        }
        for (PageTreePanel.NoteEntry note : MarkdownNotePanel.loadAllNotes()) {
            searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.NOTE, note.id(), note.title(),
                note.id(), note.title() + " " + note.id(), 0, "", "", "",
                () -> openNoteTab(note, false)));
        }
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:split",
            Component.translatable("almightly_staff.ide.btn_split").getString(), "Split Editor",
            "split 分屏", 0, "", "", "", () -> editorArea.toggleSplit(this::defaultFallbackTab)));
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:save",
            Component.translatable("almightly_staff.ide.btn_save").getString(), "Ctrl+S",
            "save 保存", 0, "", "", "", this::saveAll));
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:reset",
            Component.translatable("almightly_staff.ide.btn_reset_layout").getString(), "Reset Layout",
            "reset layout 重置布局", 0, "", "", "", this::resetLayout));
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:float",
            Component.translatable("almightly_staff.ide.btn_float_note").getString(), "Floating Note",
            "float note 浮动笔记", 0, "", "", "", this::openFloatingNote));
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:legacy",
            Component.translatable("almightly_staff.ide.switch_legacy").getString(), "Legacy UI",
            "legacy classic 经典界面", 0, "", "", "", () -> {
                AlmightlyStaffConfig.get().useIdeScreen = false;
                if (minecraft != null) minecraft.setScreen(new StaffLibScreen());
            }));
    }

    // ==================== 渲染与事件 ====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        g.fill(0, 0, width, height, IdeTheme.BG_ROOT);

        topSearchBar.render(g, mouseX, mouseY, partialTick);
        leftStripe.render(g, mouseX, mouseY, partialTick);
        if (!treeLeaf.isCollapsed()) treePanel.render(g, mouseX, mouseY, partialTick);
        editorArea.render(g, mouseX, mouseY, partialTick);
        if (!bottomLeaf.isCollapsed()) bottomDock.render(g, mouseX, mouseY, partialTick);
        if (!catalogLeaf.isCollapsed()) catalogPanel.render(g, mouseX, mouseY, partialTick);

        renderSplitters(g, mouseX, mouseY);
        renderStatusBar(g);

        windowManager.render(g, font, mouseX, mouseY, partialTick);

        // Overlays
        treePanel.renderOverlay(g, mouseX, mouseY, partialTick);
        editorArea.renderOverlay(g, mouseX, mouseY, partialTick);
        bottomDock.renderOverlay(g, mouseX, mouseY, partialTick);
        catalogPanel.renderOverlay(g, mouseX, mouseY, partialTick);
        leftStripe.renderOverlay(g, mouseX, mouseY, partialTick);
        topSearchBar.renderOverlay(g, mouseX, mouseY, partialTick);
        contextMenu.render(g, font, mouseX, mouseY);
        toast.render(g, font, width, height);
    }

    private void renderSplitters(GuiGraphics g, int mx, int my) {
        List<DockNode.SplitNode> splits = new ArrayList<>();
        rootSplit.collectSplitters(splits);
        for (DockNode.SplitNode s : splits) {
            Bounds sb = s.splitterBounds();
            if (sb.isEmpty()) continue;
            boolean hot = splitterDrag.draggingNode() == s || sb.contains(mx, my);
            g.fill(sb.x(), sb.y(), sb.right(), sb.bottom(), IdeTheme.BORDER);
            if (hot) {
                if (s.axis() == DockNode.Axis.HORIZONTAL) {
                    int cx = sb.x() + sb.w() / 2;
                    g.fill(cx, sb.y(), cx + 1, sb.bottom(), IdeTheme.ACCENT);
                } else {
                    int cy = sb.y() + sb.h() / 2;
                    g.fill(sb.x(), cy, sb.right(), cy + 1, IdeTheme.ACCENT);
                }
            }
        }
    }

    private void renderStatusBar(GuiGraphics g) {
        int y = height - STATUSBAR_H;
        g.fill(0, y, width, height, IdeTheme.BG_PANEL);
        g.fill(0, y, width, y + 1, IdeTheme.BORDER);
        try (IdeScissor.Scope sc = IdeScissor.push(g, 0, y, width, STATUSBAR_H)) {
            SpellDocument doc = editorArea.activeSpellDocument();
            String left = doc == null
                ? Component.translatable("almightly_staff.ide.status_ready").getString()
                : "#" + doc.pageIndex() + " " + (doc.name().isEmpty() ? "" : doc.name())
                + (doc.isDirty() ? " *" : "");
            g.drawString(font, left, 8, y + 5, IdeTheme.TEXT_MUTED, false);
            String right = Component.translatable("almightly_staff.gui.cast_hint").getString();
            g.drawString(font, right, Math.max(120, width - font.width(right) - 8), y + 5, IdeTheme.TEXT_MUTED, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (contextMenu.mouseClicked(mx, my, button)) return true;
        if (topSearchBar.isPopupOpen() && topSearchBar.mouseClicked(mx, my, button)) return true;
        if (windowManager.mouseClicked(mx, my, button)) {
            focusManager.clear();
            return true;
        }
        if (topSearchBar.bounds().contains(mx, my)) {
            return topSearchBar.mouseClicked(mx, my, button);
        }
        if (button == 0 && splitterDrag.beginDrag(rootSplit, mx, my)) {
            return true;
        }
        if (leftStripe.bounds().contains(mx, my)) {
            return leftStripe.mouseClicked(mx, my, button);
        }
        if (!treeLeaf.isCollapsed() && treePanel.bounds().contains(mx, my)) {
            return treePanel.mouseClicked(mx, my, button);
        }
        if (!catalogLeaf.isCollapsed() && catalogPanel.bounds().contains(mx, my)) {
            return catalogPanel.mouseClicked(mx, my, button);
        }
        if (!bottomLeaf.isCollapsed() && bottomDock.bounds().contains(mx, my)) {
            if (!hexParsePanel.bounds().contains(mx, my)) {
                focusManager.clear();
            }
            return bottomDock.mouseClicked(mx, my, button);
        }
        if (editorArea.bounds().contains(mx, my)) {
            focusManager.clear();
            return editorArea.mouseClicked(mx, my, button);
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (windowManager.mouseDragged(mx, my, button, dx, dy, width, height - STATUSBAR_H, TopSearchBar.HEIGHT)) {
            return true;
        }
        if (splitterDrag.drag(rootSplit, dockBounds, mx, my)) {
            treePanel.layout(treeLeaf.bounds());
            editorArea.layout(editorLeaf.bounds());
            bottomDock.layout(bottomLeaf.bounds());
            catalogPanel.layout(catalogLeaf.bounds());
            return true;
        }
        if (!treeLeaf.isCollapsed() && treePanel.mouseDragged(mx, my, button, dx, dy)) return true;
        if (!catalogLeaf.isCollapsed() && catalogPanel.mouseDragged(mx, my, button, dx, dy)) return true;
        if (!bottomLeaf.isCollapsed() && bottomDock.mouseDragged(mx, my, button, dx, dy)) return true;
        if (editorArea.mouseDragged(mx, my, button, dx, dy)) return true;
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (windowManager.mouseReleased(mx, my, button)) return true;
        if (splitterDrag.endDrag()) {
            persistLayoutState();
            return true;
        }
        boolean handled = false;
        if (!treeLeaf.isCollapsed()) handled |= treePanel.mouseReleased(mx, my, button);
        if (!catalogLeaf.isCollapsed()) handled |= catalogPanel.mouseReleased(mx, my, button);
        if (!bottomLeaf.isCollapsed()) handled |= bottomDock.mouseReleased(mx, my, button);
        handled |= editorArea.mouseReleased(mx, my, button);
        return handled || super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (windowManager.mouseScrolled(mx, my, delta)) return true;
        if (!treeLeaf.isCollapsed() && treePanel.mouseScrolled(mx, my, delta)) return true;
        if (!catalogLeaf.isCollapsed() && catalogPanel.mouseScrolled(mx, my, delta)) return true;
        if (!bottomLeaf.isCollapsed() && bottomDock.mouseScrolled(mx, my, delta)) return true;
        if (editorArea.mouseScrolled(mx, my, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        windowManager.mouseMoved(mx, my);
        if (!bottomLeaf.isCollapsed()) bottomDock.mouseMoved(mx, my);
        editorArea.mouseMoved(mx, my);
        super.mouseMoved(mx, my);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (contextMenu.keyPressed(keyCode)) return true;

        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_K) {
            topSearchBar.openSearch();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_S) {
            saveAll();
            return true;
        }

        IdeComponent focused = focusManager.focused();
        if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (windowManager.activeWindow() != null && windowManager.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (editorArea.focusedGroup().activeTab() instanceof TabItem.ExternalTab ext
                && ext.view().keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            onClose();
            return true;
        }

        if (editorArea.keyPressed(keyCode, scanCode, modifiers)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (focusManager.focused() == null && topSearchBar.onShiftKeyEvent(keyCode, GLFW.GLFW_RELEASE)) {
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        IdeComponent focused = focusManager.focused();
        if (focused != null && focused.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (windowManager.activeWindow() != null && windowManager.charTyped(codePoint, modifiers)) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public void tick() {
        super.tick();
        toast.tick();
        windowManager.tick();
        treePanel.tick();
        editorArea.tick();
        if (!bottomLeaf.isCollapsed()) bottomDock.tick();
    }

    @Override
    public void onClose() {
        hexGridPanel.dispose();
        persistLayoutState();
        session.saveAllDirty();
        super.onClose();
    }

    @Override
    public void removed() {
        hexGridPanel.dispose();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 内部回调适配器 ====================

    private final class EditorCallbacksImpl implements EditorArea.Callbacks {
        @Override
        public void onSetCastPage(int pageIndex) {
            session.selectCastPage(pageIndex);
        }

        @Override
        public void openContextMenu(int mx, int my, List<ContextMenuOverlay.MenuItem> items) {
            contextMenu.open(mx, my, width, height, font, items);
        }

        @Override
        public TabItem cloneTabForSplit(TabItem source) {
            if (source instanceof TabItem.SpellTab st) {
                return new TabItem.SpellTab(st.document(), new SequenceCallbacksImpl());
            }
            return source;
        }

        @Override
        public void onActiveDocumentChanged(@Nullable SpellDocument doc) {
            if (doc != null) {
                treePanel.setActivePage(doc.pageIndex());
            }
            hexParsePanel.bindDocument(doc, true);
        }
    }

    private final class SequenceCallbacksImpl implements SequenceEditorView.Callbacks {
        @Override
        public IdeSession session() {
            return session;
        }

        @Override
        public void openContextMenu(int mx, int my, List<ContextMenuOverlay.MenuItem> items) {
            contextMenu.open(mx, my, width, height, font, items);
        }

        @Override
        public void openDrawModal(int pageIndex, int slotIndex, boolean insertAfter) {
            SpellDocument doc = session.document(pageIndex);
            if (doc == null || doc.isSealed() || minecraft == null) return;
            minecraft.setScreen(new StaffDrawScreen(StaffIdeScreen.this, ref -> {
                session.learnGreatSpell(ref);
                CompoundTag tag = IotaType.serialize(new PatternIota(StaffHex.patternFor(ref)));
                if (insertAfter) {
                    int pos = Math.max(0, Math.min(slotIndex + 1, doc.size()));
                    doc.insertAt(pos, List.of(tag), "sequence");
                } else if (slotIndex >= 0 && slotIndex < doc.size()) {
                    doc.replaceAt(slotIndex, tag, "sequence");
                } else {
                    doc.insertAt(doc.size(), List.of(tag), "sequence");
                }
                onDocumentMutated(doc, "sequence");
            }));
        }

        @Override
        public void openCastCollectModal(int pageIndex, int insertAt) {
            SpellDocument doc = session.document(pageIndex);
            if (doc == null || doc.isSealed() || minecraft == null) return;
            session.network().clearCastStack();
            minecraft.setScreen(new StaffCastScreen(StaffIdeScreen.this, collected -> {
                if (collected != null && !collected.isEmpty() && !doc.isSealed()) {
                    doc.insertAt(insertAt, collected, "sequence");
                    onDocumentMutated(doc, "sequence");
                    toast.show(Component.translatable("almightly_staff.gui.stack_collected", collected.size()).getString());
                }
            }));
        }

        @Override
        public void onDocumentMutated(SpellDocument doc, String source) {
            session.saveDocument(doc);
            hexParsePanel.onDocumentChanged(doc, source);
        }
    }

    private final class TreeCallbacksImpl implements PageTreePanel.Callbacks {
        @Override
        public void onOpenSpellPage(int pageIndex, boolean split) {
            openSpellPage(pageIndex, split);
        }

        @Override
        public void onOpenNote(PageTreePanel.NoteEntry note, boolean split) {
            openNoteTab(note, split);
        }

        @Override
        public void onOpenExternalPatchouli() {
            openPatchouliInTab();
        }

        @Override
        public void onCopyPageAsHexParse(int pageIndex) {
            SpellDocument doc = session.document(pageIndex);
            if (doc == null || minecraft == null) return;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < doc.iotasView().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(IotaType.getDisplay(doc.iotasView().get(i)).getString());
            }
            minecraft.keyboardHandler.setClipboard(sb.toString());
            toast.show(Component.translatable("almightly_staff.ide.copied_clipboard").getString());
        }

        @Override
        public void openContextMenu(int mx, int my, List<ContextMenuOverlay.MenuItem> items) {
            contextMenu.open(mx, my, width, height, font, items);
        }

        @Override
        public List<PageTreePanel.NoteEntry> collectNotes() {
            return MarkdownNotePanel.loadAllNotes();
        }
    }
}
