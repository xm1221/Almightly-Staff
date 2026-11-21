package cn.xm1221.AlmightlyStaff.gui.ide;

import at.petrak.hexcasting.api.casting.eval.ExecutionClientView;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffConfig;
import cn.xm1221.AlmightlyStaff.api.AlmightlyStaffIdeApi;
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
import cn.xm1221.AlmightlyStaff.gui.ide.host.EmbeddedScreenHost;
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
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

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

    static {
        AlmightlyStaffIdeApi.registerExternalScreen(
            new ResourceLocation("patchouli", "hex_notebook"),
            Component.translatable("almightly_staff.ide.patchouli_book"),
            () -> Platform.isModLoaded("patchouli"),
            PatchouliHostBridge::openHexBookCaptured
        );
    }

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
            IdeTheme.Icon.CATALOG_LIST,
            catalogPanel, 180, 140, false
        ));
        dockWorkspace.registerWindow(new DockWorkspace.WindowDef(
            "cast_collect",
            Component.translatable("almightly_staff.ide.win_cast_collect"),
            IdeTheme.Icon.PLAY_CAST,
            castCollectPanel, 240, 160, false
        ));
    }

    private void buildStripeButtons() {
        leftStripe.addButton(stripeButton("page_tree", IdeTheme.Icon.FOLDER_CLOSED, "almightly_staff.ide.win_page_tree"));
        leftStripe.addButton(stripeButton("hex_grid", IdeTheme.Icon.HEX_GRID_ICON, "almightly_staff.ide.win_hex_grid"));
        leftStripe.addButton(stripeButton("hex_parse", IdeTheme.Icon.HEX_PARSE_ICON, "almightly_staff.ide.win_hex_parse"));

        rightStripe.addButton(stripeButton("iota_text", IdeTheme.Icon.SPELL_STAR, "almightly_staff.ide.win_iota_text"));
        rightStripe.addButton(stripeButton("pattern_catalog", IdeTheme.Icon.CATALOG_LIST, "almightly_staff.ide.win_catalog"));
        rightStripe.addButton(stripeButton("cast_collect", IdeTheme.Icon.PLAY_CAST, "almightly_staff.ide.win_cast_collect"));
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
        if (Platform.isModLoaded("patchouli")) {
            topSearchBar.addAction(new TopSearchBar.ActionButton(
                "patchouli", IdeTheme.Icon.BOOK_EXTERNAL,
                Component.translatable("almightly_staff.ide.patchouli_book"),
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
        for (AlmightlyStaffIdeApi.ExternalScreenEntry entry : AlmightlyStaffIdeApi.externalScreens()) {
            items.add(new ContextMenuOverlay.MenuItem(
                entry.title(), true, () -> openExternalScreen(entry)
            ));
        }
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
    }

    // ==================== 业务操作 ====================

    private void openSpellPage(int pageIndex) {
        if (session.document(pageIndex) == null) return;
        dockWorkspace.openOrFocus("iota_text");
        iotaTextView.openOrFocusPage(pageIndex);
    }

    private void openPatchouliWindow() {
        ResourceLocation patchouliId = new ResourceLocation("patchouli", "hex_notebook");
        for (AlmightlyStaffIdeApi.ExternalScreenEntry entry : AlmightlyStaffIdeApi.externalScreens()) {
            if (patchouliId.equals(entry.id())) {
                openExternalScreen(entry);
                return;
            }
        }
        toast.showError(Component.translatable("almightly_staff.ide.patchouli_missing").getString());
    }

    private void openExternalScreen(AlmightlyStaffIdeApi.ExternalScreenEntry entry) {
        if (entry == null) return;
        String winId = "ext:" + entry.id();
        if (dockWorkspace.windowDef(winId) != null) {
            dockWorkspace.openOrFocus(winId);
            return;
        }
        Screen extScreen = entry.createScreen();
        if (extScreen == null) {
            toast.showError(entry.title().getString());
            return;
        }
        EmbeddedScreenHost host = new EmbeddedScreenHost(
            extScreen,
            () -> dockWorkspace.closeWindow(winId),
            this::insertPatternRefAtCursor
        );
        int dw = Math.min(Math.max(240, entry.defaultWidth()), Math.max(260, width - 48));
        int dh = Math.min(Math.max(180, entry.defaultHeight()), Math.max(190, height - 48));
        dockWorkspace.registerWindow(new DockWorkspace.WindowDef(
            winId, entry.title(),
            IdeTheme.Icon.BOOK_EXTERNAL, host, 240, 180, true
        ));
        if (entry.preferFloating()) {
            Bounds initB = new Bounds(Math.max(24, width / 2 - dw / 2), Math.max(28, height / 2 - dh / 2), dw, dh);
            dockWorkspace.floatWindow(winId, initB, false, 0, 0);
        } else {
            dockWorkspace.openOrFocus(winId);
        }
    }

    private void insertPatternRefAtCursor(PatternRef ref) {
        if (ref == null) return;
        SpellDocument doc = iotaTextView.activeDocument();
        if (doc == null || doc.isSealed()) return;
        if (ref.signature().isBlank()) {
            session.requestGreatSpell(ref.actionId(), doc.pageIndex(), iotaTextView.activeCursorIndex());
            return;
        }
        CompoundTag tag = IotaType.serialize(new PatternIota(StaffHex.patternFor(ref)));
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
        CompoundTag tag = IotaType.serialize(new PatternIota(StaffHex.patternFor(ref)));
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
        for (AlmightlyStaffIdeApi.ExternalScreenEntry ext : AlmightlyStaffIdeApi.externalScreens()) {
            String t = ext.title().getString();
            searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "ext:" + ext.id(),
                t, ext.id().toString(),
                t + " " + ext.id(), 0, "", "", "", () -> openExternalScreen(ext)));
        }
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:cast",
            Component.translatable("almightly_staff.ide.win_cast_collect").getString(), "Cast & Collect",
            "cast collect 施法 采集", 0, "", "", "", () -> dockWorkspace.openOrFocus("cast_collect")));
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:save",
            Component.translatable("almightly_staff.ide.btn_save").getString(), "Ctrl+S",
            "save 保存", 0, "", "", "", this::saveAll));
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:reset",
            Component.translatable("almightly_staff.ide.btn_reset_layout").getString(), "Reset Layout",
            "reset layout 重置布局", 0, "", "", "", this::resetLayout));
        searchIndex.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:legacy",
            Component.translatable("almightly_staff.ide.switch_legacy").getString(), "Legacy UI",
            "legacy classic 经典界面", 0, "", "", "", this::switchToLegacyUi));
    }

    // ==================== 渲染与事件 ====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
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
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (windowManager.mouseScrolled(mx, my, delta)) return true;
        if (dockWorkspace.mouseScrolled(mx, my, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
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
            // 第 2 级 ESC：取消 HexGrid / 施法采集未完成笔划
            if (hexGridPanel.hasUnfinishedStroke()) {
                hexGridPanel.cancelUnfinishedStroke();
                return true;
            }
            if (castCollectPanel.hasUnfinishedStroke()) {
                castCollectPanel.cancelUnfinishedStroke();
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
                CompoundTag tag = IotaType.serialize(new PatternIota(StaffHex.patternFor(ref)));
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
    }

    private final class TreeCallbacksImpl implements PageTreePanel.Callbacks {
        @Override
        public void onOpenSpellPage(int pageIndex) {
            openSpellPage(pageIndex);
        }

        @Override
        public void onOpenExternalScreen(AlmightlyStaffIdeApi.ExternalScreenEntry entry) {
            openExternalScreen(entry);
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
    }
}
