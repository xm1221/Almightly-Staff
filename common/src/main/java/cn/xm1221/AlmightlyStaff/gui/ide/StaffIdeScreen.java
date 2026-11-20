package cn.xm1221.AlmightlyStaff.gui.ide;

import at.petrak.hexcasting.api.casting.eval.ExecutionClientView;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffConfig;
import cn.xm1221.AlmightlyStaff.gui.StaffDrawScreen;
import cn.xm1221.AlmightlyStaff.gui.StaffLibScreen;
import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.ContextMenuOverlay;
import cn.xm1221.AlmightlyStaff.gui.ide.core.FocusManager;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.gui.ide.core.ToastOverlay;
import cn.xm1221.AlmightlyStaff.gui.ide.editor.IotaTextEditorView;
import cn.xm1221.AlmightlyStaff.gui.ide.editor.SequenceEditorView;
import cn.xm1221.AlmightlyStaff.gui.ide.editor.TabItem;
import cn.xm1221.AlmightlyStaff.gui.ide.host.EmbeddedScreenHost;
import cn.xm1221.AlmightlyStaff.gui.ide.host.MarkdownNotePanel;
import cn.xm1221.AlmightlyStaff.gui.ide.host.PatchouliHostBridge;
import cn.xm1221.AlmightlyStaff.gui.ide.layout.DockWorkspace;
import cn.xm1221.AlmightlyStaff.gui.ide.layout.FloatingWindow;
import cn.xm1221.AlmightlyStaff.gui.ide.layout.ToolWindowStripe;
import cn.xm1221.AlmightlyStaff.gui.ide.layout.WindowManager;
import cn.xm1221.AlmightlyStaff.gui.ide.model.IdeSession;
import cn.xm1221.AlmightlyStaff.gui.ide.model.IdeWorkspaceStorage;
import cn.xm1221.AlmightlyStaff.gui.ide.model.SpellDocument;
import cn.xm1221.AlmightlyStaff.gui.ide.model.WorkspaceState;
import cn.xm1221.AlmightlyStaff.gui.ide.search.SearchIndex;
import cn.xm1221.AlmightlyStaff.gui.ide.search.TopSearchBar;
import cn.xm1221.AlmightlyStaff.gui.ide.tool.CastCollectPanel;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 万法之杖现代停靠式法术 IDE 主界面（Minecraft 1.20.1 Common）。
 */
public final class StaffIdeScreen extends Screen implements StaffSyncReceiver, IdeSession.Listener {
    private static final int STATUSBAR_H = 18;
    private static final int SNIPPET_REQ_BASE = 888000;

    private final ToastOverlay toast = new ToastOverlay();
    private final ContextMenuOverlay contextMenu = new ContextMenuOverlay();
    private final FocusManager focusManager = new FocusManager(() -> this.setFocused(null));
    private final IdeSession session = new IdeSession(
        ArchitecturyNetworkBridge.INSTANCE, IdeWorkspaceStorage.defaultInstance(), toast);

    private final SearchIndex searchIndex = new SearchIndex();
    private final TopSearchBar topSearchBar;
    private final ToolWindowStripe leftStripe = new ToolWindowStripe(false);
    private final ToolWindowStripe rightStripe = new ToolWindowStripe(true);
    private final WindowManager windowManager = new WindowManager();
    private final DockWorkspace dockWorkspace;

    private final IotaTextEditorView iotaTextView;
    private final SequenceGridHost sequenceGridHost = new SequenceGridHost();
    private final PageTreePanel treePanel;
    private final HexGridPanel hexGridPanel;
    private final HexParseEditorPanel hexParsePanel;
    private final PatternCatalogPanel catalogPanel;
    private final CastCollectPanel castCollectPanel;

    private boolean syncRequested = false;
    private boolean workspaceApplied = false;
    private int snippetReqSeq = SNIPPET_REQ_BASE;
    private int pendingSnippetReq = -1;
    private int pendingSnippetPage = -1;

    public StaffIdeScreen() {
        super(Component.translatable("almightly_staff.ide.title"));
        this.session.addListener(this);

        this.dockWorkspace = new DockWorkspace(windowManager, this::persistLayoutState, this::onDynamicWindowClosed);
        this.topSearchBar = new TopSearchBar(searchIndex, focusManager, this::rebuildSearchIndex);
        this.iotaTextView = new IotaTextEditorView(session, focusManager, new TextViewCallbacks());
        this.hexGridPanel = new HexGridPanel(session, tags -> iotaTextView.insertTagsAtCursor(tags, "grid"));
        this.hexParsePanel = new HexParseEditorPanel(session, focusManager,
            () -> {
                SpellDocument doc = iotaTextView.activeDocument();
                if (doc != null) session.saveDocument(doc);
            });
        this.castCollectPanel = new CastCollectPanel(session, collected -> {
            if (collected == null || collected.isEmpty()) return;
            iotaTextView.insertTagsAtCursor(collected, "cast");
            toast.show(Component.translatable("almightly_staff.gui.stack_collected", collected.size()).getString());
        });
        this.treePanel = new PageTreePanel(session, focusManager, new TreeCallbacksImpl());
        this.catalogPanel = new PatternCatalogPanel(session, focusManager,
            iotaTextView::activeDocument,
            this::insertCatalogEntryAtCursor,
            (entry, xy) -> {
                if (iotaTextView.bounds().contains(xy[0], xy[1])) {
                    insertCatalogEntryAtCursor(entry);
                } else {
                    sequenceGridHost.acceptCatalogDrop(entry, xy[0], xy[1]);
                }
            });

        registerWindows();
        buildStripeButtons();
        buildTopActions();
        dockWorkspace.applyWorkspace(WorkspaceState.createDefault());
    }

    private void registerWindows() {
        dockWorkspace.registerWindow(new DockWorkspace.WindowDef(
            "iota_text",
            Component.translatable("almightly_staff.ide.win_iota_text"),
            IdeTheme.Icon.SPELL_STAR,
            iotaTextView, 220, 140, false
        ));
        dockWorkspace.registerWindow(new DockWorkspace.WindowDef(
            "hex_grid",
            Component.translatable("almightly_staff.ide.win_hex_grid"),
            IdeTheme.Icon.HEX_GRID_ICON,
            hexGridPanel, 180, 140, false
        ));
        dockWorkspace.registerWindow(new DockWorkspace.WindowDef(
            "page_tree",
            Component.translatable("almightly_staff.ide.win_page_tree"),
            IdeTheme.Icon.FOLDER_CLOSED,
            treePanel, 160, 140, false
        ));
        dockWorkspace.registerWindow(new DockWorkspace.WindowDef(
            "hex_parse",
            Component.translatable("almightly_staff.ide.win_hex_parse"),
            IdeTheme.Icon.HEX_PARSE_ICON,
            hexParsePanel, 220, 130, false
        ));
        dockWorkspace.registerWindow(new DockWorkspace.WindowDef(
            "pattern_catalog",
            Component.translatable("almightly_staff.ide.win_catalog"),
            IdeTheme.Icon.SPELL_STAR,
            catalogPanel, 180, 140, false
        ));
        dockWorkspace.registerWindow(new DockWorkspace.WindowDef(
            "cast_collect",
            Component.translatable("almightly_staff.ide.win_cast_collect"),
            IdeTheme.Icon.PLAY_CAST,
            castCollectPanel, 240, 160, false
        ));
        dockWorkspace.registerWindow(new DockWorkspace.WindowDef(
            "sequence_grid",
            Component.translatable("almightly_staff.ide.win_sequence_grid"),
            IdeTheme.Icon.SPLIT_VIEW,
            sequenceGridHost, 180, 130, true
        ));
    }

    private void buildStripeButtons() {
        leftStripe.addButton(stripeButton("page_tree", IdeTheme.Icon.FOLDER_CLOSED, "almightly_staff.ide.win_page_tree"));
        leftStripe.addButton(stripeButton("hex_grid", IdeTheme.Icon.HEX_GRID_ICON, "almightly_staff.ide.win_hex_grid"));
        leftStripe.addButton(stripeButton("hex_parse", IdeTheme.Icon.HEX_PARSE_ICON, "almightly_staff.ide.win_hex_parse"));

        rightStripe.addButton(stripeButton("pattern_catalog", IdeTheme.Icon.SPELL_STAR, "almightly_staff.ide.win_catalog"));
        rightStripe.addButton(stripeButton("cast_collect", IdeTheme.Icon.PLAY_CAST, "almightly_staff.ide.win_cast_collect"));
        rightStripe.addButton(stripeButton("sequence_grid", IdeTheme.Icon.SPLIT_VIEW, "almightly_staff.ide.win_sequence_grid"));
    }

    private ToolWindowStripe.StripeButton stripeButton(String winId, IdeTheme.Icon icon, String langKey) {
        return new ToolWindowStripe.StripeButton(
            winId, icon, Component.translatable(langKey),
            () -> dockWorkspace.isOpen(winId),
            () -> dockWorkspace.isMinimized(winId),
            () -> dockWorkspace.toggleFromStripe(winId)
        );
    }

    private void buildTopActions() {
        topSearchBar.addAction(new TopSearchBar.ActionButton(
            "save", IdeTheme.Icon.SAVE_DISK,
            Component.translatable("almightly_staff.ide.btn_save"),
            this::saveAll
        ));
        topSearchBar.addAction(new TopSearchBar.ActionButton(
            "note", IdeTheme.Icon.NOTE_DOC,
            Component.translatable("almightly_staff.ide.btn_float_note"),
            this::openDefaultNote
        ));
        if (Platform.isModLoaded("patchouli")) {
            topSearchBar.addAction(new TopSearchBar.ActionButton(
                "patchouli", IdeTheme.Icon.BOOK_EXTERNAL,
                Component.translatable("almightly_staff.ide.btn_patchouli"),
                this::openPatchouliWindow
            ));
        }
        topSearchBar.addAction(new TopSearchBar.ActionButton(
            "more", IdeTheme.Icon.MORE_DOTS,
            Component.translatable("almightly_staff.ide.overflow_menu"),
            this::openOverflowMenu
        ));
    }

    private void openOverflowMenu() {
        List<ContextMenuOverlay.MenuItem> items = new ArrayList<>();
        items.add(new ContextMenuOverlay.MenuItem(
            Component.translatable("almightly_staff.ide.open_sequence_grid"), true,
            () -> dockWorkspace.openOrFocus("sequence_grid")
        ));
        items.add(new ContextMenuOverlay.MenuItem(
            Component.translatable("almightly_staff.ide.btn_reset_layout"), true,
            this::resetLayout
        ));
        items.add(new ContextMenuOverlay.MenuItem(
            Component.translatable("almightly_staff.ide.switch_legacy"), true,
            this::switchToLegacyUi
        ));
        contextMenu.open(Math.max(4, width - 150), TopSearchBar.HEIGHT + 2, width, height, font, items);
    }

    private void switchToLegacyUi() {
        iotaTextView.commitNameEdit();
        persistLayoutState();
        session.saveAllDirty();
        AlmightlyStaffConfig.get().useIdeScreen = false;
        AlmightlyStaffConfig.save();
        if (minecraft != null) {
            minecraft.setScreen(new StaffLibScreen());
        }
    }

    private void resetLayout() {
        WorkspaceState def = WorkspaceState.createDefault();
        dockWorkspace.applyWorkspace(def);
        persistLayoutState();
        toast.show(Component.translatable("almightly_staff.ide.layout_reset").getString());
    }

    private void saveAll() {
        iotaTextView.commitNameEdit();
        persistLayoutState();
        session.saveAllDirty();
    }

    private void persistLayoutState() {
        dockWorkspace.saveToWorkspace(session.workspace());
        session.workspace().openTabs.clear();
        for (IotaTextEditorView.PageTabState st : iotaTextView.openTabs()) {
            session.workspace().openTabs.add(new WorkspaceState.TabBookmark("spell", st.pageIndex, "", 0));
        }
    }

    private void onDynamicWindowClosed(String windowId) {
        if (windowId.startsWith("note:") || windowId.startsWith("ext:")) {
            dockWorkspace.unregisterWindow(windowId);
        }
    }

    @Override
    protected void init() {
        layoutAll();
        windowManager.clampAll(dockWorkspace.dockBounds());
        if (!syncRequested) {
            syncRequested = true;
            session.requestSync();
        }
    }

    private void layoutAll() {
        topSearchBar.layout(new Bounds(0, 0, width, TopSearchBar.HEIGHT));
        int bodyH = Math.max(0, height - TopSearchBar.HEIGHT - STATUSBAR_H);
        leftStripe.layout(new Bounds(0, TopSearchBar.HEIGHT, ToolWindowStripe.WIDTH, bodyH));
        rightStripe.layout(new Bounds(Math.max(0, width - ToolWindowStripe.WIDTH), TopSearchBar.HEIGHT,
            ToolWindowStripe.WIDTH, bodyH));
        Bounds dockArea = new Bounds(
            ToolWindowStripe.WIDTH,
            TopSearchBar.HEIGHT,
            Math.max(0, width - ToolWindowStripe.WIDTH * 2),
            bodyH
        );
        dockWorkspace.layout(dockArea);
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
        if (requestId == pendingSnippetReq) {
            pendingSnippetReq = -1;
            if (!ok || iotaTags == null || iotaTags.isEmpty()) {
                toast.showError(error == null || error.isEmpty()
                    ? Component.translatable("almightly_staff.gui.hexparse_error").getString()
                    : error);
                return;
            }
            if (iotaTextView.activeDocument() == null || iotaTextView.activeDocument().pageIndex() != pendingSnippetPage) {
                iotaTextView.openOrFocusPage(pendingSnippetPage);
            }
            iotaTextView.insertTagsAtCursor(iotaTags, "note");
            toast.show(Component.translatable("almightly_staff.ide.applied_snippet").getString());
            return;
        }
        session.onIdeParseToIotas(requestId, pageIndex, ok, iotaTags, error, errorLine);
    }

    @Override
    public void onFullSync(int focusPage) {
        if (!workspaceApplied) {
            workspaceApplied = true;
            dockWorkspace.applyWorkspace(session.workspace());
            layoutAll();
            for (WorkspaceState.TabBookmark bm : session.workspace().openTabs) {
                if ("spell".equals(bm.kind) && session.document(bm.pageIndex) != null) {
                    iotaTextView.openOrFocusPage(bm.pageIndex);
                }
            }
        }
        int target = focusPage > 0 ? focusPage
            : (iotaTextView.activeDocument() != null ? iotaTextView.activeDocument().pageIndex() : session.castPage());
        if (session.document(target) == null && !session.documents().isEmpty()) {
            target = session.documents().iterator().next().pageIndex();
        }
        if (target > 0 && session.document(target) != null && iotaTextView.openTabs().isEmpty()) {
            iotaTextView.openOrFocusPage(target);
        } else if (focusPage > 0 && session.document(focusPage) != null) {
            iotaTextView.openOrFocusPage(focusPage);
        }
        syncSideEditors(true);
    }

    @Override
    public void onPagesUpdated(int focusPage, List<Integer> removed) {
        if (removed != null) {
            for (int r : removed) {
                iotaTextView.closePage(r);
            }
        }
        if (focusPage > 0 && session.document(focusPage) != null) {
            iotaTextView.openOrFocusPage(focusPage);
        }
        syncSideEditors(false);
    }

    @Override
    public void onGreatSpellLearned(PatternRef ref) {
        catalogPanel.refreshFilter();
    }

    public void onSpellcastingServerUpdate(ExecutionClientView info, int index) {
        castCollectPanel.onServerUpdate(info, index);
    }

    private void syncSideEditors(boolean forceParseRefresh) {
        SpellDocument doc = iotaTextView.activeDocument();
        if (doc != null) {
            treePanel.setActivePage(doc.pageIndex());
        }
        hexParsePanel.bindDocument(doc, forceParseRefresh);
        sequenceGridHost.bindDocument(doc);
    }

    // ==================== 业务操作 ====================

    private void openSpellPage(int pageIndex) {
        if (session.document(pageIndex) == null) return;
        dockWorkspace.openOrFocus("iota_text");
        iotaTextView.openOrFocusPage(pageIndex);
    }

    private void openNoteWindow(PageTreePanel.NoteEntry note) {
        String winId = "note:" + note.id();
        if (dockWorkspace.windowDef(winId) == null) {
            MarkdownNotePanel panel = new MarkdownNotePanel(
                session, note,
                this::insertPatternRefAtCursor,
                this::insertHexParseCodeSnippet
            );
            dockWorkspace.registerWindow(new DockWorkspace.WindowDef(
                winId, Component.literal(note.title()), IdeTheme.Icon.NOTE_DOC,
                panel, 200, 150, true
            ));
            Bounds initB = new Bounds(Math.max(40, width / 2 - 150), Math.max(36, height / 2 - 110), 300, 220);
            dockWorkspace.floatWindow(winId, initB, false, 0, 0);
        } else {
            dockWorkspace.openOrFocus(winId);
        }
    }

    private void openDefaultNote() {
        List<PageTreePanel.NoteEntry> notes = MarkdownNotePanel.loadAllNotes();
        if (!notes.isEmpty()) {
            openNoteWindow(notes.get(0));
        }
    }

    private void openPatchouliWindow() {
        String winId = "ext:patchouli";
        if (dockWorkspace.windowDef(winId) != null) {
            dockWorkspace.openOrFocus(winId);
            return;
        }
        Screen bookScreen = PatchouliHostBridge.openHexBookCaptured();
        if (bookScreen == null) {
            toast.showError(Component.translatable("almightly_staff.ide.patchouli_missing").getString());
            return;
        }
        EmbeddedScreenHost host = new EmbeddedScreenHost(
            bookScreen,
            () -> dockWorkspace.closeWindow(winId),
            this::insertPatternRefAtCursor
        );
        dockWorkspace.registerWindow(new DockWorkspace.WindowDef(
            winId, Component.translatable("almightly_staff.ide.patchouli_book"),
            IdeTheme.Icon.BOOK_EXTERNAL, host, 260, 190, true
        ));
        Bounds initB = new Bounds(Math.max(32, width / 2 - 170), Math.max(32, height / 2 - 125), 340, 250);
        dockWorkspace.floatWindow(winId, initB, false, 0, 0);
    }

    private void insertPatternRefAtCursor(PatternRef ref) {
        if (ref == null) return;
        SpellDocument doc = iotaTextView.activeDocument();
        if (doc == null || doc.isSealed()) return;
        if (ref.signature().isBlank()) {
            session.requestGreatSpell(ref.actionId(), doc.pageIndex(), iotaTextView.activeCursorIndex());
            return;
        }
        CompoundTag tag = StaffHex.serializeIota(new PatternIota(StaffHex.patternFor(ref)));
        iotaTextView.insertTagsAtCursor(List.of(tag), "note");
        Component dn = StaffHex.patternDisplayName(ref);
        toast.show(Component.translatable("almightly_staff.ide.inserted_pattern",
            dn != null ? dn.getString() : ref.actionId()).getString());
    }

    private void insertHexParseCodeSnippet(String code) {
        SpellDocument doc = iotaTextView.activeDocument();
        if (doc == null || doc.isSealed()) return;
        if (Platform.isModLoaded("hexparse")) {
            pendingSnippetReq = ++snippetReqSeq;
            pendingSnippetPage = doc.pageIndex();
            session.network().requestIdeParseToIotas(pendingSnippetReq, doc.pageIndex(), code);
        } else {
            toast.showError(Component.translatable("almightly_staff.gui.hexparse_missing").getString());
        }
    }

    private void insertCatalogEntryAtCursor(PatternCatalogEntry entry) {
        SpellDocument doc = iotaTextView.activeDocument();
        if (doc == null || doc.isSealed()) return;
        PatternRef ref = entry.pattern();
        if (ref.signature().isBlank()) {
            session.requestGreatSpell(ref.actionId(), doc.pageIndex(), iotaTextView.activeCursorIndex());
            return;
        }
        CompoundTag tag = StaffHex.serializeIota(new PatternIota(StaffHex.patternFor(ref)));
        iotaTextView.insertTagsAtCursor(List.of(tag), "catalog");
    }

    private void rebuildSearchIndex() {
        searchIndex.clear();
        for (SpellDocument doc : session.documents()) {
            String title = doc.name().isEmpty()
                ? Component.translatable("almightly_staff.gui.page_label", doc.pageIndex()).getString()
                : "#" + doc.pageIndex() + " " + doc.name();
            int pid = doc.pageIndex();
            searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.PAGE, "page:" + pid, title,
                "#" + pid, title + " " + pid, pid, "", "", "", () -> openSpellPage(pid)));
        }
        for (PatternCatalogEntry entry : session.catalog()) {
            PatternRef ref = entry.pattern();
            String cn = entry.displayName().getString();
            String actionId = ref.actionId();
            searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.PATTERN, actionId,
                cn + " (" + actionId + ")", ref.signature(),
                cn + " " + actionId + " " + ref.signature(),
                0, actionId, ref.startDirection(), ref.signature(),
                () -> {
                    dockWorkspace.openOrFocus("pattern_catalog");
                    catalogPanel.focusAction(actionId);
                }));
        }
        for (PageTreePanel.NoteEntry note : MarkdownNotePanel.loadAllNotes()) {
            searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.NOTE, note.id(), note.title(),
                note.id(), note.title() + " " + note.id(), 0, "", "", "",
                () -> openNoteWindow(note)));
        }
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:cast",
            Component.translatable("almightly_staff.ide.win_cast_collect").getString(), "Cast & Collect",
            "cast collect 施法 采集", 0, "", "", "", () -> dockWorkspace.openOrFocus("cast_collect")));
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:seq",
            Component.translatable("almightly_staff.ide.open_sequence_grid").getString(), "Grid",
            "grid sequence 网格", 0, "", "", "", () -> dockWorkspace.openOrFocus("sequence_grid")));
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:save",
            Component.translatable("almightly_staff.ide.btn_save").getString(), "Ctrl+S",
            "save 保存", 0, "", "", "", this::saveAll));
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:reset",
            Component.translatable("almightly_staff.ide.btn_reset_layout").getString(), "Reset Layout",
            "reset layout 重置布局", 0, "", "", "", this::resetLayout));
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:note",
            Component.translatable("almightly_staff.ide.btn_float_note").getString(), "Note",
            "note 笔记", 0, "", "", "", this::openDefaultNote));
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:legacy",
            Component.translatable("almightly_staff.ide.switch_legacy").getString(), "Legacy UI",
            "legacy classic 经典界面", 0, "", "", "", this::switchToLegacyUi));
    }

    // ==================== 渲染与事件 ====================

    // 1.21.1 的 renderBackground 会触发全屏模糊后处理（renderBlurredBackground），
    // 且 Screen.render() 也会自动调用它；这里覆写为空，避免模糊覆盖 IDE UI。
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fillGradient(0, 0, width, height, 0xC0080A0D, 0xC0000000);
        IdeTheme.drawNineSlice(g, 0, 0, width, height, IdeTheme.NineSlice.ROOT);

        topSearchBar.render(g, mouseX, mouseY, partialTick);
        leftStripe.render(g, mouseX, mouseY, partialTick);
        rightStripe.render(g, mouseX, mouseY, partialTick);
        dockWorkspace.render(g, font, mouseX, mouseY, partialTick);
        renderStatusBar(g);

        dockWorkspace.renderDropPreview(g);
        windowManager.render(g, font, mouseX, mouseY, partialTick);

        // Overlays
        dockWorkspace.renderOverlay(g, font, mouseX, mouseY, partialTick);
        leftStripe.renderOverlay(g, mouseX, mouseY, partialTick);
        rightStripe.renderOverlay(g, mouseX, mouseY, partialTick);
        topSearchBar.renderOverlay(g, mouseX, mouseY, partialTick);
        contextMenu.render(g, font, mouseX, mouseY);
        toast.render(g, font, width, height);
    }

    private void renderStatusBar(GuiGraphics g) {
        int y = height - STATUSBAR_H;
        IdeTheme.drawNineSlice(g, 0, y, width, STATUSBAR_H, IdeTheme.NineSlice.HEADER);
        try (IdeScissor.Scope sc = IdeScissor.push(g, 0, y, width, STATUSBAR_H)) {
            SpellDocument doc = iotaTextView.activeDocument();
            String left;
            if (doc == null) {
                left = Component.translatable("almightly_staff.ide.status_ready").getString();
            } else {
                String name = doc.name().isEmpty() ? "" : doc.name();
                left = Component.translatable("almightly_staff.ide.status_cursor",
                    doc.pageIndex(), name, doc.isDirty() ? " *" : "",
                    iotaTextView.activeCursorIndex(), doc.size()).getString();
            }
            g.drawString(font, IdeTheme.trimText(font, left, Math.max(40, width - 16)), 8, y + 5, IdeTheme.TEXT_MUTED, false);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (contextMenu.mouseClicked(mx, my, button)) return true;
        if (topSearchBar.isPopupOpen() && topSearchBar.mouseClicked(mx, my, button)) return true;
        if (windowManager.mouseClicked(mx, my, button, dockWorkspace.dockBounds())) {
            focusManager.clear();
            return true;
        }
        if (topSearchBar.bounds().contains(mx, my)) {
            return topSearchBar.mouseClicked(mx, my, button);
        }
        if (leftStripe.bounds().contains(mx, my)) {
            return leftStripe.mouseClicked(mx, my, button);
        }
        if (rightStripe.bounds().contains(mx, my)) {
            return rightStripe.mouseClicked(mx, my, button);
        }
        if (dockWorkspace.mouseClicked(mx, my, button)) {
            if (!hexParsePanel.bounds().contains(mx, my)
                && !iotaTextView.bounds().contains(mx, my)
                && !catalogPanel.bounds().contains(mx, my)
                && !treePanel.bounds().contains(mx, my)) {
                focusManager.clear();
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (windowManager.mouseDragged(mx, my, button, dx, dy, dockWorkspace.dockBounds())) {
            dockWorkspace.updateFloatingDragPreview(mx, my);
            return true;
        }
        if (dockWorkspace.mouseDragged(mx, my, button, dx, dy)) {
            dockWorkspace.updateFloatingDragPreview(mx, my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        FloatingWindow wasActive = windowManager.activeWindow();
        boolean wasMoving = wasActive != null && wasActive.isMoving();
        if (windowManager.mouseReleased(mx, my, button)) {
            if (wasMoving) {
                dockWorkspace.onFloatingDragReleased(mx, my);
            }
            return true;
        }
        if (dockWorkspace.mouseReleased(mx, my, button)) return true;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        if (windowManager.mouseScrolled(mx, my, vScroll)) return true;
        if (dockWorkspace.mouseScrolled(mx, my, vScroll)) return true;
        return super.mouseScrolled(mx, my, hScroll, vScroll);
    }

    @Override
    public void mouseMoved(double mx, double my) {
        windowManager.mouseMoved(mx, my);
        dockWorkspace.mouseMoved(mx, my);
        super.mouseMoved(mx, my);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (ctrl && keyCode == GLFW.GLFW_KEY_K) {
            topSearchBar.openSearch();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_S) {
            saveAll();
            return true;
        }

        // 第 1 级 ESC：关闭右键菜单 / 顶部搜索弹窗 / 聚焦子控件的重命名或补全
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (contextMenu.isOpen()) {
                contextMenu.close();
                return true;
            }
            if (topSearchBar.isPopupOpen()) {
                topSearchBar.closeSearch();
                return true;
            }
            IdeComponent focused = focusManager.focused();
            if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
            // 第 2 级 ESC：取消 HexGrid 未完成笔划
            if (hexGridPanel.hasUnfinishedStroke()) {
                hexGridPanel.cancelUnfinishedStroke();
                return true;
            }
            // 第 3 级 ESC：嵌入式外部窗口先按内部回退/关闭；再还原最大化 / 关最顶层浮动窗
            FloatingWindow topFloat = windowManager.activeWindow();
            if (topFloat == null && !windowManager.windows().isEmpty()) {
                topFloat = windowManager.windows().get(windowManager.windows().size() - 1);
            }
            if (topFloat != null) {
                if (topFloat.content() instanceof EmbeddedScreenHost ext
                    && ext.keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
                if (topFloat.isMaximized()) {
                    topFloat.toggleMaximize(dockWorkspace.dockBounds());
                } else {
                    dockWorkspace.minimizeWindow(topFloat.id());
                }
                return true;
            }
            String dockedFocus = dockWorkspace.focusedWindowId();
            if (dockedFocus != null && dockedFocus.startsWith("ext:")) {
                DockWorkspace.WindowDef edef = dockWorkspace.windowDef(dockedFocus);
                if (edef != null && edef.content().keyPressed(keyCode, scanCode, modifiers)) {
                    return true;
                }
            }
            if (dockWorkspace.hasMaximizedDocked()) {
                dockWorkspace.unmaximizeDocked();
                return true;
            }
            // 第 4 级 ESC：安全保存并退出 IDE
            onClose();
            return true;
        }

        if (contextMenu.keyPressed(keyCode)) return true;

        IdeComponent focused = focusManager.focused();
        if (focused != null && focused.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (windowManager.activeWindow() != null && windowManager.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (dockWorkspace.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
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
        dockWorkspace.tick();
    }

    @Override
    public void onClose() {
        iotaTextView.commitNameEdit();
        hexGridPanel.dispose();
        castCollectPanel.dispose();
        persistLayoutState();
        session.saveAllDirty();
        super.onClose();
    }

    @Override
    public void removed() {
        hexGridPanel.dispose();
        castCollectPanel.dispose();
        super.removed();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 内部回调 ====================

    private final class TextViewCallbacks implements IotaTextEditorView.Callbacks {
        @Override
        public void openContextMenu(int mx, int my, List<ContextMenuOverlay.MenuItem> items) {
            contextMenu.open(mx, my, width, height, font, items);
        }

        @Override
        public void onEnsureHexGridOpen() {
            dockWorkspace.openOrFocus("hex_grid");
        }

        @Override
        public void onEnsureCastCollectOpen() {
            dockWorkspace.openOrFocus("cast_collect");
            castCollectPanel.beginFreshSession();
        }

        @Override
        public void onOpenPopupDraw(int pageIndex, int slotIndex, boolean insertAfter) {
            SpellDocument doc = session.document(pageIndex);
            if (doc == null || doc.isSealed() || minecraft == null) return;
            minecraft.setScreen(new StaffDrawScreen(StaffIdeScreen.this, ref -> {
                session.learnGreatSpell(ref);
                CompoundTag tag = StaffHex.serializeIota(new PatternIota(StaffHex.patternFor(ref)));
                int pos = insertAfter ? Math.min(slotIndex + 1, doc.size()) : Math.min(Math.max(0, slotIndex), doc.size());
                doc.insertAt(pos, List.of(tag), "draw");
                onDocumentMutated(doc, "draw");
            }));
        }

        @Override
        public void onActivePageChanged(@Nullable SpellDocument doc) {
            syncSideEditors(true);
        }

        @Override
        public void onDocumentMutated(SpellDocument doc, String source) {
            session.saveDocument(doc);
            hexParsePanel.onDocumentChanged(doc, source);
        }

        @Override
        public void onSetCastPage(int pageIndex) {
            session.selectCastPage(pageIndex);
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
            new TextViewCallbacks().onOpenPopupDraw(pageIndex, slotIndex, insertAfter);
        }

        @Override
        public void openCastCollectModal(int pageIndex, int insertAt) {
            dockWorkspace.openOrFocus("cast_collect");
            castCollectPanel.beginFreshSession();
        }

        @Override
        public void onDocumentMutated(SpellDocument doc, String source) {
            session.saveDocument(doc);
            hexParsePanel.onDocumentChanged(doc, source);
        }
    }

    private final class SequenceGridHost implements IdeComponent {
        private final Map<Integer, TabItem.SpellTab> tabsByPage = new HashMap<>();
        @Nullable
        private TabItem.SpellTab activeTab;
        private Bounds bounds = Bounds.EMPTY;

        public void bindDocument(@Nullable SpellDocument doc) {
            if (doc == null) {
                activeTab = null;
                return;
            }
            activeTab = tabsByPage.computeIfAbsent(doc.pageIndex(),
                pid -> new TabItem.SpellTab(doc, new SequenceCallbacksImpl()));
            if (activeTab.document() != doc) {
                activeTab = new TabItem.SpellTab(doc, new SequenceCallbacksImpl());
                tabsByPage.put(doc.pageIndex(), activeTab);
            }
            if (!bounds.isEmpty()) {
                activeTab.view().layout(bounds);
            }
        }

        public void acceptCatalogDrop(PatternCatalogEntry entry, double mx, double my) {
            if (activeTab != null && bounds.contains(mx, my)) {
                activeTab.view().acceptCatalogDrop(entry, mx, my);
            }
        }

        @Override
        public Bounds bounds() {
            return bounds;
        }

        @Override
        public void layout(Bounds bounds) {
            this.bounds = bounds;
            if (activeTab != null) activeTab.view().layout(bounds);
        }

        @Override
        public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (bounds.isEmpty()) return;
            if (activeTab == null) {
                IdeTheme.drawNineSlice(g, bounds, IdeTheme.NineSlice.ROOT);
                IdeTheme.drawClippedString(g, font, Component.translatable("almightly_staff.ide.empty_spell_hint"),
                    bounds.x() + 10, bounds.y() + 10, bounds.w() - 20, IdeTheme.TEXT_MUTED);
                return;
            }
            activeTab.view().render(g, mouseX, mouseY, partialTick);
        }

        @Override
        public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (activeTab != null) activeTab.view().renderOverlay(g, mouseX, mouseY, partialTick);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            return activeTab != null && activeTab.view().mouseClicked(mx, my, button);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
            return activeTab != null && activeTab.view().mouseDragged(mx, my, button, dx, dy);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int button) {
            return activeTab != null && activeTab.view().mouseReleased(mx, my, button);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double delta) {
            return activeTab != null && activeTab.view().mouseScrolled(mx, my, delta);
        }

        @Override
        public void mouseMoved(double mx, double my) {
            if (activeTab != null) activeTab.view().mouseMoved(mx, my);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return activeTab != null && activeTab.view().keyPressed(keyCode, scanCode, modifiers);
        }
    }

    private final class TreeCallbacksImpl implements PageTreePanel.Callbacks {
        @Override
        public void onOpenSpellPage(int pageIndex) {
            openSpellPage(pageIndex);
        }

        @Override
        public void onOpenNote(PageTreePanel.NoteEntry note) {
            openNoteWindow(note);
        }

        @Override
        public void onOpenExternalPatchouli() {
            openPatchouliWindow();
        }

        @Override
        public void onCopyPageAsHexParse(int pageIndex) {
            session.requestCodeForAction(pageIndex, code -> {
                if (minecraft != null) {
                    minecraft.keyboardHandler.setClipboard(code);
                    toast.show(Component.translatable("almightly_staff.ide.copied_clipboard").getString());
                }
            });
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
