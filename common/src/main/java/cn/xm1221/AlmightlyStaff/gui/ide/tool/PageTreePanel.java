package cn.xm1221.AlmightlyStaff.gui.ide.tool;

import cn.xm1221.AlmightlyStaff.api.AlmightlyStaffIdeApi;
import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.ContextMenuOverlay;
import cn.xm1221.AlmightlyStaff.gui.ide.core.FocusManager;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.gui.ide.model.IdeSession;
import cn.xm1221.AlmightlyStaff.gui.ide.model.SpellDocument;
import cn.xm1221.AlmightlyStaff.gui.ide.model.WorkspaceState;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 书页树面板：单击选中行，双击打开/聚焦；顶部提供新建/复制/删除/新建文件夹图标按钮与提示；
 * 笔记与外部界面默认折叠以突出法术页。
 */
public final class PageTreePanel implements IdeComponent {
    public record NoteEntry(String id, String title, String content, boolean readOnly) {
    }

    public interface Callbacks {
        void onOpenSpellPage(int pageIndex);

        void onOpenExternalScreen(AlmightlyStaffIdeApi.ExternalScreenEntry entry);

        void onCopyPageAsHexParse(int pageIndex);

        void openContextMenu(int mx, int my, List<ContextMenuOverlay.MenuItem> items);
    }

    private enum RowKind {
        FOLDER,
        PAGE,
        EXTERNAL_ROOT,
        EXTERNAL_ITEM
    }

    private record TreeRow(RowKind kind, String id, int pageIndex, String label,
                           boolean collapsed, boolean sealed, boolean dirty, boolean casting,
                           int indent, @Nullable AlmightlyStaffIdeApi.ExternalScreenEntry external) {
    }

    private static final int HEADER_H = 20;
    private static final int ROW_H = 17;
    private static final long DOUBLE_CLICK_MS = 320L;

    private final IdeSession session;
    private final FocusManager focusManager;
    private final Callbacks callbacks;

    private Bounds bounds = Bounds.EMPTY;
    private final List<TreeRow> rows = new ArrayList<>();
    private int scroll = 0;
    private int selectedPageIndex = -1;
    private String selectedRowId = "";
    private boolean externalExpanded = false;  // 默认折叠
    private boolean uncategorizedExpanded = true;

    private long lastClickMs;
    private String lastClickId = "";

    private boolean renaming = false;
    private boolean renameFolder = false;
    private String renameTargetId = "";
    private int renamePageIndex = 0;
    private String renameText = "";
    private final TextFieldHelper renameHelper;

    private boolean pendingDrag;
    private boolean activeDrag;
    private int dragPageIndex = -1;
    private double dragStartX, dragStartY, dragMouseX, dragMouseY;

    private int deleteConfirmPage = -1;
    private int deleteConfirmTicks = 0;

    public PageTreePanel(IdeSession session, FocusManager focusManager, Callbacks callbacks) {
        this.session = session;
        this.focusManager = focusManager;
        this.callbacks = callbacks;
        this.renameHelper = new TextFieldHelper(
            () -> renameText,
            s -> renameText = s,
            TextFieldHelper.createClipboardGetter(Minecraft.getInstance()),
            TextFieldHelper.createClipboardSetter(Minecraft.getInstance()),
            s -> s.length() <= 80
        );
    }

    public void setActivePage(int pageIndex) {
        this.selectedPageIndex = pageIndex;
        this.selectedRowId = "page:" + pageIndex;
    }

    public void rebuildTree() {
        rows.clear();
        WorkspaceState ws = session.workspace();
        Set<Integer> assigned = new HashSet<>();

        for (WorkspaceState.VirtualFolder folder : ws.folders) {
            rows.add(new TreeRow(RowKind.FOLDER, "folder:" + folder.id, 0, folder.name,
                folder.collapsed, false, false, false, 0, null));
            if (!folder.collapsed) {
                for (Integer pid : folder.pageIndices) {
                    SpellDocument doc = session.document(pid);
                    if (doc != null) {
                        assigned.add(pid);
                        rows.add(pageRow(doc, 1));
                    }
                }
            } else {
                for (Integer pid : folder.pageIndices) {
                    if (session.document(pid) != null) assigned.add(pid);
                }
            }
        }

        List<SpellDocument> uncategorized = new ArrayList<>();
        for (SpellDocument doc : session.documents()) {
            if (!assigned.contains(doc.pageIndex())) uncategorized.add(doc);
        }
        rows.add(new TreeRow(RowKind.FOLDER, "folder:__root__", 0,
            Component.translatable("almightly_staff.ide.tree_uncategorized").getString(),
            !uncategorizedExpanded, false, false, false, 0, null));
        if (uncategorizedExpanded) {
            for (SpellDocument doc : uncategorized) rows.add(pageRow(doc, 1));
        }

        List<AlmightlyStaffIdeApi.ExternalScreenEntry> externals = AlmightlyStaffIdeApi.externalScreens();
        if (!externals.isEmpty()) {
            rows.add(new TreeRow(RowKind.EXTERNAL_ROOT, "ext_root", 0,
                Component.translatable("almightly_staff.ide.tree_external").getString(),
                !externalExpanded, false, false, false, 0, null));
            if (externalExpanded) {
                for (AlmightlyStaffIdeApi.ExternalScreenEntry entry : externals) {
                    rows.add(new TreeRow(RowKind.EXTERNAL_ITEM, "ext:" + entry.id(), 0,
                        entry.title().getString(),
                        false, true, false, false, 1, entry));
                }
            }
        }
    }

    private TreeRow pageRow(SpellDocument doc, int indent) {
        String label = doc.name().isEmpty()
            ? Component.translatable("almightly_staff.gui.page_label", doc.pageIndex()).getString()
            : "#" + doc.pageIndex() + " " + doc.name();
        return new TreeRow(RowKind.PAGE, "page:" + doc.pageIndex(), doc.pageIndex(), label,
            false, doc.isSealed(), doc.isDirty(), doc.pageIndex() == session.castPage(), indent, null);
    }

    private Bounds headerBounds() {
        return new Bounds(bounds.x(), bounds.y(), bounds.w(), Math.min(HEADER_H, bounds.h()));
    }

    private Bounds listBounds() {
        return bounds.inset(0, HEADER_H, 0, 2);
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public void layout(Bounds bounds) {
        this.bounds = bounds;
    }

    private int rowIndexAt(double mx, double my) {
        Bounds lb = listBounds();
        if (!lb.contains(mx, my) || mx >= lb.right() - IdeTheme.SCROLLBAR_WIDTH) return -1;
        int idx = scroll + (int) ((my - lb.y()) / ROW_H);
        return idx >= 0 && idx < rows.size() ? idx : -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (bounds.isEmpty()) return;
        rebuildTree();
        Font font = Minecraft.getInstance().font;
        IdeTheme.drawNineSlice(g, bounds, IdeTheme.NineSlice.PANEL);

        Bounds hb = headerBounds();
        IdeTheme.drawNineSlice(g, hb, IdeTheme.NineSlice.HEADER);
        try (IdeScissor.Scope sc = IdeScissor.push(g, hb)) {
            IdeTheme.drawClippedString(g, font, Component.translatable("almightly_staff.ide.tree_title"),
                hb.x() + 6, hb.y() + 6, Math.max(10, hb.w() - 78), IdeTheme.TEXT_MUTED);
            drawHeaderIconBtn(g, hb.right() - 70, hb.y() + 3, IdeTheme.Icon.PLUS_ICON, false, mouseX, mouseY);
            drawHeaderIconBtn(g, hb.right() - 53, hb.y() + 3, IdeTheme.Icon.COPY_ICON, false, mouseX, mouseY);
            drawHeaderIconBtn(g, hb.right() - 36, hb.y() + 3, IdeTheme.Icon.TRASH_ICON,
                deleteConfirmPage > 0 && deleteConfirmTicks > 0, mouseX, mouseY);
            drawHeaderIconBtn(g, hb.right() - 19, hb.y() + 3, IdeTheme.Icon.FOLDER_CLOSED, false, mouseX, mouseY);
        }

        Bounds lb = listBounds();
        if (lb.isEmpty()) return;
        int visible = Math.max(1, lb.h() / ROW_H);
        scroll = Mth.clamp(scroll, 0, Math.max(0, rows.size() - visible));
        int contentRight = lb.right() - IdeTheme.SCROLLBAR_WIDTH;

        try (IdeScissor.Scope sc = IdeScissor.push(g, lb.x(), lb.y(), Math.max(0, contentRight - lb.x()), lb.h())) {
            for (int vi = 0; vi < visible; vi++) {
                int idx = vi + scroll;
                if (idx >= rows.size()) break;
                TreeRow r = rows.get(idx);
                int y = lb.y() + vi * ROW_H;
                boolean armedDelete = r.kind() == RowKind.PAGE && r.pageIndex() == deleteConfirmPage && deleteConfirmTicks > 0;
                boolean selected = r.id().equals(selectedRowId)
                    || (r.kind() == RowKind.PAGE && r.pageIndex() == selectedPageIndex);
                boolean hover = mouseX >= lb.x() && mouseX < contentRight && mouseY >= y && mouseY < y + ROW_H;
                if (armedDelete) {
                    g.fill(lb.x() + 1, y, contentRight - 1, y + ROW_H - 1, 0x44E05555);
                    IdeTheme.drawBorder(g, lb.x() + 1, y, Math.max(2, contentRight - lb.x() - 2), ROW_H - 1, IdeTheme.RED_ERROR);
                } else if (selected) {
                    g.fill(lb.x() + 1, y, contentRight - 1, y + ROW_H - 1, IdeTheme.BG_ACTIVE);
                } else if (hover) {
                    g.fill(lb.x() + 1, y, contentRight - 1, y + ROW_H - 1, IdeTheme.BG_HOVER);
                }

                int ix = lb.x() + 5 + r.indent() * 10;
                if (r.kind() == RowKind.FOLDER || r.kind() == RowKind.EXTERNAL_ROOT) {
                    IdeTheme.drawIcon(g, r.collapsed() ? IdeTheme.Icon.FOLDER_CLOSED : IdeTheme.Icon.FOLDER_OPEN,
                        ix, y + 4, IdeTheme.TEXT_MUTED);
                    ix += 11;
                } else if (r.kind() == RowKind.PAGE) {
                    if (r.casting()) {
                        IdeTheme.drawIcon(g, IdeTheme.Icon.PLAY_CAST, ix, y + 4, IdeTheme.GREEN_CAST);
                        ix += 10;
                    }
                    if (r.sealed()) {
                        IdeTheme.drawIcon(g, IdeTheme.Icon.PADLOCK, ix, y + 4, IdeTheme.GOLD_SEAL);
                        ix += 10;
                    } else {
                        IdeTheme.drawIcon(g, IdeTheme.Icon.SPELL_STAR, ix, y + 4, IdeTheme.ACCENT_LIGHT);
                        ix += 10;
                    }
                } else if (r.kind() == RowKind.EXTERNAL_ITEM) {
                    IdeTheme.drawIcon(g, IdeTheme.Icon.BOOK_EXTERNAL, ix, y + 4, IdeTheme.GOLD_SEAL_TEXT);
                    ix += 10;
                }

                if (renaming && r.id().equals(renameTargetId)) {
                    IdeTheme.drawNineSlice(g, ix, y + 1, Math.max(12, contentRight - 3 - ix), ROW_H - 3, IdeTheme.NineSlice.INPUT);
                    g.drawString(font, renameText, ix + 3, y + 4, IdeTheme.TEXT_MAIN, false);
                    int cur = Math.min(renameHelper.getCursorPos(), renameText.length());
                    int cx = ix + 3 + font.width(renameText.substring(0, cur));
                    g.fill(cx, y + 3, cx + 1, y + ROW_H - 4, IdeTheme.ACCENT_LIGHT);
                    continue;
                }

                String display = r.label() + (r.dirty() ? " *" : "");
                int col = armedDelete ? IdeTheme.RED_ERROR
                    : r.sealed() && r.kind() == RowKind.PAGE ? IdeTheme.GOLD_SEAL_TEXT
                    : r.dirty() ? IdeTheme.GOLD_SEAL_TEXT
                    : (r.kind() == RowKind.FOLDER || r.kind() == RowKind.EXTERNAL_ROOT)
                    ? IdeTheme.TEXT_MUTED : IdeTheme.TEXT_MAIN;
                g.drawString(font, IdeTheme.trimText(font, display, Math.max(8, contentRight - ix - 4)),
                    ix, y + 4, col, false);
            }

            if (activeDrag) {
                int hoverIdx = rowIndexAt(dragMouseX, dragMouseY);
                if (hoverIdx >= 0 && hoverIdx < rows.size()) {
                    TreeRow target = rows.get(hoverIdx);
                    int ty = lb.y() + (hoverIdx - scroll) * ROW_H;
                    if (target.kind() == RowKind.FOLDER) {
                        IdeTheme.drawBorder(g, lb.x() + 2, ty, Math.max(4, contentRight - lb.x() - 4), ROW_H - 1, IdeTheme.ACCENT);
                    } else if (target.kind() == RowKind.PAGE && !target.sealed()) {
                        boolean after = ((dragMouseY - lb.y()) % ROW_H) > ROW_H / 2.0;
                        int ly = after ? ty + ROW_H - 1 : ty;
                        g.fill(lb.x() + 4, ly, contentRight - 4, ly + 2, IdeTheme.ACCENT);
                    }
                }
            }
        }
        IdeTheme.drawScrollbar(g, contentRight, lb.y(), lb.h(), rows.size(), visible, scroll);
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Font font = Minecraft.getInstance().font;
        if (activeDrag) {
            PoseStack ps = g.pose();
            ps.pushPose();
            ps.translate(0.0F, 0.0F, 520.0F);
            try {
                IdeTheme.drawNineSlice(g, (int) dragMouseX, (int) dragMouseY, 50, 15, IdeTheme.NineSlice.BTN_ACTIVE);
                g.drawString(font, "#" + dragPageIndex, (int) dragMouseX + 5, (int) dragMouseY + 3, 0xFFFFFFFF, false);
            } finally {
                ps.popPose();
            }
            return;
        }
        Bounds hb = headerBounds();
        if (hb.contains(mouseX, mouseY) && mouseY >= hb.y() + 3 && mouseY < hb.y() + 17) {
            if (mouseX >= hb.right() - 70 && mouseX < hb.right() - 56) {
                g.renderTooltip(font, Component.translatable("almightly_staff.gui.new"), mouseX, mouseY);
            } else if (mouseX >= hb.right() - 53 && mouseX < hb.right() - 39) {
                g.renderTooltip(font, Component.translatable("almightly_staff.gui.copy"), mouseX, mouseY);
            } else if (mouseX >= hb.right() - 36 && mouseX < hb.right() - 22) {
                g.renderTooltip(font, Component.translatable(
                    deleteConfirmPage > 0 && deleteConfirmTicks > 0
                        ? "almightly_staff.gui.confirm_delete" : "almightly_staff.gui.delete"), mouseX, mouseY);
            } else if (mouseX >= hb.right() - 19 && mouseX < hb.right() - 5) {
                g.renderTooltip(font, Component.translatable("almightly_staff.ide.new_folder_btn"), mouseX, mouseY);
            }
        }
    }

    private static void drawHeaderIconBtn(GuiGraphics g, int x, int y, IdeTheme.Icon icon, boolean danger, int mx, int my) {
        boolean hov = mx >= x && mx < x + 14 && my >= y && my < y + 14;
        IdeTheme.drawNineSlice(g, x, y, 14, 14, danger ? IdeTheme.NineSlice.BTN_ACTIVE
            : (hov ? IdeTheme.NineSlice.BTN_HOVER : IdeTheme.NineSlice.BTN_NORMAL));
        IdeTheme.drawIcon(g, icon, x + 3, y + 3, danger ? IdeTheme.RED_ERROR : IdeTheme.TEXT_MAIN);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        if (renaming) commitRename();

        Bounds hb = headerBounds();
        if (hb.contains(mx, my) && button == 0) {
            if (mx >= hb.right() - 70 && mx < hb.right() - 56) {
                session.createNewPage();
                return true;
            }
            if (mx >= hb.right() - 53 && mx < hb.right() - 39 && selectedPageIndex > 0) {
                duplicateWithLocalizedSuffix(selectedPageIndex);
                return true;
            }
            if (mx >= hb.right() - 36 && mx < hb.right() - 22 && selectedPageIndex > 0) {
                requestDelete(selectedPageIndex);
                return true;
            }
            if (mx >= hb.right() - 19 && mx < hb.right() - 5) {
                createFolder();
                return true;
            }
            return true;
        }

        int idx = rowIndexAt(mx, my);
        if (idx < 0) {
            focusManager.clear();
            return true;
        }
        TreeRow row = rows.get(idx);
        selectedRowId = row.id();
        if (button == 0) {
            focusManager.focus(this);
            long now = System.currentTimeMillis();
            boolean dbl = row.id().equals(lastClickId) && now - lastClickMs <= DOUBLE_CLICK_MS;
            lastClickId = row.id();
            lastClickMs = now;
            switch (row.kind()) {
                case FOLDER -> toggleFolder(row.id());
                case EXTERNAL_ROOT -> externalExpanded = !externalExpanded;
                case PAGE -> {
                    selectedPageIndex = row.pageIndex();
                    // 单击仅选中，双击打开/聚焦 Tab
                    if (dbl) {
                        callbacks.onOpenSpellPage(row.pageIndex());
                    } else if (!row.sealed()) {
                        pendingDrag = true;
                        dragPageIndex = row.pageIndex();
                        dragStartX = mx;
                        dragStartY = my;
                        dragMouseX = mx;
                        dragMouseY = my;
                    }
                }
                case EXTERNAL_ITEM -> {
                    if (dbl && row.external() != null) callbacks.onOpenExternalScreen(row.external());
                }
            }
            return true;
        }
        if (button == 1) {
            if (row.kind() == RowKind.PAGE) {
                selectedPageIndex = row.pageIndex();
                openPageMenu((int) mx, (int) my, row);
            } else if (row.kind() == RowKind.FOLDER && !"folder:__root__".equals(row.id())) {
                openFolderMenu((int) mx, (int) my, row.id().substring("folder:".length()), row.label());
            } else if (row.kind() == RowKind.EXTERNAL_ITEM && row.external() != null) {
                AlmightlyStaffIdeApi.ExternalScreenEntry entry = row.external();
                callbacks.openContextMenu((int) mx, (int) my, List.of(
                    new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.open_page"),
                        true, () -> callbacks.onOpenExternalScreen(entry))
                ));
            }
            return true;
        }
        return true;
    }

    private void duplicateWithLocalizedSuffix(int page) {
        SpellDocument doc = session.document(page);
        if (doc == null) return;
        String suffix = Component.translatable("almightly_staff.gui.copy_suffix").getString();
        String base = doc.name().trim();
        String copyName = base.isEmpty() ? suffix.trim() : base + suffix;
        session.duplicatePage(page, copyName);
    }

    private void toggleFolder(String id) {
        if ("folder:__root__".equals(id)) {
            uncategorizedExpanded = !uncategorizedExpanded;
            return;
        }
        String fid = id.substring("folder:".length());
        for (WorkspaceState.VirtualFolder f : session.workspace().folders) {
            if (fid.equals(f.id)) {
                f.collapsed = !f.collapsed;
                session.saveWorkspace();
                return;
            }
        }
    }

    private void createFolder() {
        String id = "dir_" + System.currentTimeMillis();
        String name = Component.translatable("almightly_staff.ide.new_folder_name",
            session.workspace().folders.size() + 1).getString();
        session.workspace().folders.add(new WorkspaceState.VirtualFolder(id, name, false, List.of()));
        session.saveWorkspace();
        beginFolderRename(id, name);
    }

    private void openPageMenu(int mx, int my, TreeRow row) {
        int page = row.pageIndex();
        boolean sealed = row.sealed();
        String delKey = (deleteConfirmPage == page && deleteConfirmTicks > 0)
            ? "almightly_staff.gui.confirm_delete" : "almightly_staff.gui.delete";
        List<ContextMenuOverlay.MenuItem> items = List.of(
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.open_page"),
                true, () -> callbacks.onOpenSpellPage(page)),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.rename"),
                true, () -> beginPageRename(page)),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.copy"),
                true, () -> duplicateWithLocalizedSuffix(page)),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.copy_hexparse"),
                true, () -> callbacks.onCopyPageAsHexParse(page)),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.export_hex"),
                true, () -> exportPageToHexParseFile(page)),
            new ContextMenuOverlay.MenuItem(Component.translatable(delKey),
                !sealed, () -> requestDelete(page))
        );
        callbacks.openContextMenu(mx, my, items);
    }

    private void openFolderMenu(int mx, int my, String folderId, String currentName) {
        List<ContextMenuOverlay.MenuItem> items = List.of(
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.new"),
                true, session::createNewPage),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.rename"),
                true, () -> beginFolderRename(folderId, currentName)),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.delete"),
                true, () -> {
                    session.workspace().removeFolderKeepPages(folderId);
                    session.saveWorkspace();
                })
        );
        callbacks.openContextMenu(mx, my, items);
    }

    private void requestDelete(int page) {
        SpellDocument doc = session.document(page);
        if (doc != null && doc.isSealed()) {
            session.toast().showError(Component.translatable("almightly_staff.gui.page_sealed").getString());
            return;
        }
        if (deleteConfirmPage == page && deleteConfirmTicks > 0) {
            deleteConfirmPage = -1;
            deleteConfirmTicks = 0;
            session.deletePage(page);
        } else {
            deleteConfirmPage = page;
            deleteConfirmTicks = 80;
            session.toast().show(Component.translatable("almightly_staff.ide.confirm_delete_again").getString());
        }
    }

    private void exportPageToHexParseFile(int page) {
        SpellDocument doc = session.document(page);
        if (doc == null) return;
        session.requestCodeForAction(page, code -> {
            try {
                Path dir = Platform.getConfigFolder().resolve("almightly_staff").resolve("exports");
                Files.createDirectories(dir);
                String safeName = (doc.name().isEmpty() ? "page_" + page : doc.name()).replaceAll("[^a-zA-Z0-9._-]", "_");
                Path file = dir.resolve(safeName + ".hexparse");
                Files.writeString(file, code, StandardCharsets.UTF_8);
                session.toast().show(Component.translatable("almightly_staff.ide.exported", file.getFileName().toString()).getString());
            } catch (Exception e) {
                session.toast().showError(e.getMessage());
            }
        });
    }

    public void beginPageRename(int page) {
        SpellDocument doc = session.document(page);
        if (doc == null) return;
        renaming = true;
        renameFolder = false;
        renameTargetId = "page:" + page;
        renamePageIndex = page;
        renameText = doc.name();
        renameHelper.selectAll();
        focusManager.focus(this);
    }

    private void beginFolderRename(String folderId, String current) {
        renaming = true;
        renameFolder = true;
        renameTargetId = "folder:" + folderId;
        renameText = current == null ? "" : current;
        renameHelper.selectAll();
        focusManager.focus(this);
    }

    private void commitRename() {
        if (!renaming) return;
        renaming = false;
        if (renameFolder) {
            String fid = renameTargetId.substring("folder:".length());
            String clean = renameText.trim();
            if (!clean.isEmpty()) {
                for (WorkspaceState.VirtualFolder f : session.workspace().folders) {
                    if (fid.equals(f.id)) {
                        f.name = clean;
                        session.saveWorkspace();
                        break;
                    }
                }
            }
        } else if (renamePageIndex > 0) {
            session.renamePage(renamePageIndex, renameText);
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0) return false;
        if (pendingDrag && Math.hypot(mx - dragStartX, my - dragStartY) >= 4) {
            pendingDrag = false;
            activeDrag = true;
        }
        if (activeDrag) {
            dragMouseX = mx;
            dragMouseY = my;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button != 0) return false;
        boolean wasActive = activeDrag;
        int fromPage = dragPageIndex;
        pendingDrag = false;
        activeDrag = false;
        dragPageIndex = -1;
        if (!wasActive || fromPage <= 0) return wasActive;

        int idx = rowIndexAt(mx, my);
        if (idx < 0 || idx >= rows.size()) return true;
        TreeRow target = rows.get(idx);
        if (target.kind() == RowKind.FOLDER) {
            String fid = "folder:__root__".equals(target.id()) ? null : target.id().substring("folder:".length());
            session.workspace().movePageToFolder(fromPage, fid);
            session.saveWorkspace();
        } else if (target.kind() == RowKind.PAGE && target.pageIndex() != fromPage && !target.sealed()) {
            boolean after = ((my - listBounds().y()) % ROW_H) > ROW_H / 2.0;
            session.reorderPage(fromPage, target.pageIndex(), after);
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!bounds.contains(mx, my)) return false;
        int visible = Math.max(1, listBounds().h() / ROW_H);
        int max = Math.max(0, rows.size() - visible);
        scroll = Mth.clamp(scroll - (int) Math.signum(delta), 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (renaming) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                renaming = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                commitRename();
                return true;
            }
            return renameHelper.keyPressed(keyCode);
        }
        if (!focusManager.isFocused(this)) return false;
        if (keyCode == GLFW.GLFW_KEY_F2 && selectedPageIndex > 0) {
            beginPageRename(selectedPageIndex);
            return true;
        }
        if ((keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) && selectedPageIndex > 0) {
            requestDelete(selectedPageIndex);
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return renaming && renameHelper.charTyped(codePoint);
    }

    @Override
    public void tick() {
        if (deleteConfirmTicks > 0) {
            deleteConfirmTicks--;
            if (deleteConfirmTicks == 0) deleteConfirmPage = -1;
        }
    }

    @Override
    public void onFocusLost() {
        commitRename();
    }
}
