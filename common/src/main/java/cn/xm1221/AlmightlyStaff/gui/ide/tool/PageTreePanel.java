package cn.xm1221.AlmightlyStaff.gui.ide.tool;

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
import java.util.function.Consumer;

/**
 * 左侧虚拟书页树（文件夹折叠/展开、单击选中、双击开 Tab、拖拽跨目录或重排、行内重命名、右键菜单）。
 */
public final class PageTreePanel implements IdeComponent {
    public record NoteEntry(String id, String title, String content, boolean readOnly) {
    }

    public interface Callbacks {
        void onOpenSpellPage(int pageIndex, boolean split);

        void onOpenNote(NoteEntry note, boolean split);

        void onOpenExternalPatchouli();

        void onCopyPageAsHexParse(int pageIndex);

        void openContextMenu(int mx, int my, List<ContextMenuOverlay.MenuItem> items);

        List<NoteEntry> collectNotes();
    }

    private enum RowKind {
        FOLDER,
        PAGE,
        NOTES_ROOT,
        NOTE,
        EXTERNAL_ROOT,
        EXTERNAL_ITEM
    }

    private record TreeRow(RowKind kind, String id, int pageIndex, String label,
                           boolean collapsed, boolean sealed, boolean dirty, boolean casting,
                           int indent, @Nullable NoteEntry note) {
    }

    private static final int HEADER_H = 20;
    private static final int ROW_H = 17;
    private static final long DOUBLE_CLICK_MS = 280L;

    private final IdeSession session;
    private final FocusManager focusManager;
    private final Callbacks callbacks;

    private Bounds bounds = Bounds.EMPTY;
    private final List<TreeRow> rows = new ArrayList<>();
    private int scroll = 0;
    private int selectedPageIndex = -1;
    private String selectedRowId = "";
    private boolean notesExpanded = true;
    private boolean externalExpanded = true;
    private boolean uncategorizedExpanded = true;

    private long lastClickMs;
    private String lastClickId = "";

    // 行内重命名
    private boolean renaming = false;
    private boolean renameFolder = false;
    private String renameTargetId = "";
    private int renamePageIndex = 0;
    private String renameText = "";
    private final TextFieldHelper renameHelper;

    // 拖拽
    private boolean pendingDrag;
    private boolean activeDrag;
    private int dragPageIndex = -1;
    private double dragStartX, dragStartY, dragMouseX, dragMouseY;

    // 删除两步确认
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

        // 未分类书页
        List<SpellDocument> uncategorized = new ArrayList<>();
        for (SpellDocument doc : session.documents()) {
            if (!assigned.contains(doc.pageIndex())) {
                uncategorized.add(doc);
            }
        }
        rows.add(new TreeRow(RowKind.FOLDER, "folder:__root__", 0,
            Component.translatable("almightly_staff.ide.tree_uncategorized").getString(),
            !uncategorizedExpanded, false, false, false, 0, null));
        if (uncategorizedExpanded) {
            for (SpellDocument doc : uncategorized) {
                rows.add(pageRow(doc, 1));
            }
        }

        // 笔记分组
        List<NoteEntry> notes = callbacks.collectNotes();
        rows.add(new TreeRow(RowKind.NOTES_ROOT, "notes_root", 0,
            Component.translatable("almightly_staff.ide.tree_notes").getString(),
            !notesExpanded, false, false, false, 0, null));
        if (notesExpanded) {
            for (NoteEntry note : notes) {
                rows.add(new TreeRow(RowKind.NOTE, "note:" + note.id(), 0, note.title(),
                    false, note.readOnly(), false, false, 1, note));
            }
        }

        // 外部 GUI
        if (Platform.isModLoaded("patchouli")) {
            rows.add(new TreeRow(RowKind.EXTERNAL_ROOT, "ext_root", 0,
                Component.translatable("almightly_staff.ide.tree_external").getString(),
                !externalExpanded, false, false, false, 0, null));
            if (externalExpanded) {
                rows.add(new TreeRow(RowKind.EXTERNAL_ITEM, "ext:patchouli", 0,
                    Component.translatable("almightly_staff.ide.patchouli_book").getString(),
                    false, true, false, false, 1, null));
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
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), IdeTheme.BG_PANEL);
        g.fill(bounds.right() - 1, bounds.y(), bounds.right(), bounds.bottom(), IdeTheme.BORDER);

        // Header + buttons [+页] [+夹]
        Bounds hb = headerBounds();
        g.fill(hb.x(), hb.y(), hb.right() - 1, hb.bottom(), IdeTheme.BG_ELEVATED);
        g.fill(hb.x(), hb.bottom() - 1, hb.right() - 1, hb.bottom(), IdeTheme.BORDER);
        try (IdeScissor.Scope sc = IdeScissor.push(g, hb)) {
            IdeTheme.drawClippedString(g, font, Component.translatable("almightly_staff.ide.tree_title"),
                hb.x() + 6, hb.y() + 6, Math.max(10, hb.w() - 56), IdeTheme.TEXT_MUTED);
            drawSmallBtn(g, font, hb.right() - 50, hb.y() + 3, 22, 14, "+", mouseX, mouseY);
            drawSmallBtn(g, font, hb.right() - 26, hb.y() + 3, 22, 14, "D+", mouseX, mouseY);
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
                boolean selected = r.id().equals(selectedRowId)
                    || (r.kind() == RowKind.PAGE && r.pageIndex() == selectedPageIndex);
                boolean hover = mouseX >= lb.x() && mouseX < contentRight && mouseY >= y && mouseY < y + ROW_H;
                if (selected) {
                    g.fill(lb.x() + 1, y, contentRight - 1, y + ROW_H - 1, IdeTheme.BG_ACTIVE);
                } else if (hover) {
                    g.fill(lb.x() + 1, y, contentRight - 1, y + ROW_H - 1, IdeTheme.BG_HOVER);
                }

                int ix = lb.x() + 5 + r.indent() * 10;
                if (r.kind() == RowKind.FOLDER || r.kind() == RowKind.NOTES_ROOT || r.kind() == RowKind.EXTERNAL_ROOT) {
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
                } else if (r.kind() == RowKind.NOTE) {
                    IdeTheme.drawIcon(g, IdeTheme.Icon.NOTE_DOC, ix, y + 4, IdeTheme.CYAN_NOTE);
                    ix += 10;
                } else if (r.kind() == RowKind.EXTERNAL_ITEM) {
                    IdeTheme.drawIcon(g, IdeTheme.Icon.BOOK_EXTERNAL, ix, y + 4, IdeTheme.GOLD_SEAL_TEXT);
                    ix += 10;
                }

                // 行内重命名输入条
                if (renaming && r.id().equals(renameTargetId)) {
                    g.fill(ix, y + 1, contentRight - 3, y + ROW_H - 2, IdeTheme.BG_ROOT);
                    IdeTheme.drawBorder(g, ix, y + 1, Math.max(12, contentRight - 3 - ix), ROW_H - 3, IdeTheme.ACCENT);
                    g.drawString(font, renameText, ix + 3, y + 4, IdeTheme.TEXT_MAIN, false);
                    int cur = Math.min(renameHelper.getCursorPos(), renameText.length());
                    int cx = ix + 3 + font.width(renameText.substring(0, cur));
                    g.fill(cx, y + 3, cx + 1, y + ROW_H - 4, IdeTheme.ACCENT_LIGHT);
                    continue;
                }

                String display = r.label() + (r.dirty() ? " *" : "");
                int col = r.sealed() && r.kind() == RowKind.PAGE ? IdeTheme.GOLD_SEAL_TEXT
                    : r.dirty() ? IdeTheme.GOLD_SEAL_TEXT
                    : (r.kind() == RowKind.FOLDER || r.kind() == RowKind.NOTES_ROOT || r.kind() == RowKind.EXTERNAL_ROOT)
                    ? IdeTheme.TEXT_MUTED : IdeTheme.TEXT_MAIN;
                g.drawString(font, IdeTheme.trimText(font, display, Math.max(8, contentRight - ix - 4)),
                    ix, y + 4, col, false);
            }

            // 拖拽目标提示线
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
        if (!activeDrag) return;
        Font font = Minecraft.getInstance().font;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 520.0F);
        try {
            int x = (int) dragMouseX;
            int y = (int) dragMouseY;
            String txt = "#" + dragPageIndex;
            g.fill(x, y, x + 52, y + 15, IdeTheme.BG_ACTIVE);
            g.drawString(font, txt, x + 5, y + 3, 0xFFFFFFFF, false);
        } finally {
            ps.popPose();
        }
    }

    private static void drawSmallBtn(GuiGraphics g, Font font, int x, int y, int w, int h, String txt, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hov ? IdeTheme.BG_HOVER : IdeTheme.BG_PANEL);
        IdeTheme.drawBorder(g, x, y, w, h, IdeTheme.BORDER_STRONG);
        g.drawCenteredString(font, txt, x + w / 2, y + 3, IdeTheme.TEXT_MAIN);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        if (renaming) {
            commitRename();
        }
        Bounds hb = headerBounds();
        if (hb.contains(mx, my) && button == 0) {
            if (mx >= hb.right() - 50 && mx < hb.right() - 28) {
                session.createNewPage();
                return true;
            }
            if (mx >= hb.right() - 26 && mx < hb.right() - 4) {
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
                case NOTES_ROOT -> notesExpanded = !notesExpanded;
                case EXTERNAL_ROOT -> externalExpanded = !externalExpanded;
                case PAGE -> {
                    selectedPageIndex = row.pageIndex();
                    if (dbl) {
                        callbacks.onOpenSpellPage(row.pageIndex(), false);
                    } else {
                        callbacks.onOpenSpellPage(row.pageIndex(), false);
                        if (!row.sealed()) {
                            pendingDrag = true;
                            dragPageIndex = row.pageIndex();
                            dragStartX = mx;
                            dragStartY = my;
                            dragMouseX = mx;
                            dragMouseY = my;
                        }
                    }
                }
                case NOTE -> {
                    if (row.note() != null) callbacks.onOpenNote(row.note(), false);
                }
                case EXTERNAL_ITEM -> callbacks.onOpenExternalPatchouli();
            }
            return true;
        }
        if (button == 1) {
            if (row.kind() == RowKind.PAGE) {
                selectedPageIndex = row.pageIndex();
                openPageMenu((int) mx, (int) my, row);
            } else if (row.kind() == RowKind.FOLDER && !"folder:__root__".equals(row.id())) {
                openFolderMenu((int) mx, (int) my, row.id().substring("folder:".length()), row.label());
            } else if (row.kind() == RowKind.NOTE && row.note() != null) {
                NoteEntry n = row.note();
                callbacks.openContextMenu((int) mx, (int) my, List.of(
                    new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.open_in_split"),
                        true, () -> callbacks.onOpenNote(n, true))
                ));
            }
            return true;
        }
        return true;
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
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.open_in_split"),
                true, () -> callbacks.onOpenSpellPage(page, true)),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.set_cast_page"),
                true, () -> session.selectCastPage(page)),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.rename"),
                true, () -> beginPageRename(page)),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.gui.copy"),
                true, () -> {
                    SpellDocument doc = session.document(page);
                    session.duplicatePage(page, doc == null ? "" : doc.name() + " (copy)");
                }),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.copy_hexparse"),
                true, () -> callbacks.onCopyPageAsHexParse(page)),
            new ContextMenuOverlay.MenuItem(Component.translatable("almightly_staff.ide.export_hex"),
                true, () -> exportPageToFile(page)),
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
        if (deleteConfirmPage == page && deleteConfirmTicks > 0) {
            deleteConfirmPage = -1;
            deleteConfirmTicks = 0;
            session.deletePage(page);
        } else {
            deleteConfirmPage = page;
            deleteConfirmTicks = 60;
            session.toast().show(Component.translatable("almightly_staff.gui.confirm_delete").getString());
        }
    }

    private void exportPageToFile(int page) {
        SpellDocument doc = session.document(page);
        if (doc == null) return;
        try {
            Path dir = Platform.getConfigFolder().resolve("almightly_staff").resolve("exports");
            Files.createDirectories(dir);
            String safeName = (doc.name().isEmpty() ? "page_" + page : doc.name()).replaceAll("[^a-zA-Z0-9._-]", "_");
            Path file = dir.resolve(safeName + ".hex");
            StringBuilder sb = new StringBuilder();
            for (var tag : doc.iotasView()) {
                sb.append(tag.toString()).append('\n');
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
            session.toast().show(Component.translatable("almightly_staff.ide.exported", file.getFileName().toString()).getString());
        } catch (Exception e) {
            session.toast().showError(e.getMessage());
        }
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
            SpellDocument doc = session.document(selectedPageIndex);
            if (doc != null && !doc.isSealed()) {
                requestDelete(selectedPageIndex);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (renaming) {
            return renameHelper.charTyped(codePoint);
        }
        return false;
    }

    @Override
    public void tick() {
        if (deleteConfirmTicks > 0) {
            deleteConfirmTicks--;
            if (deleteConfirmTicks == 0) {
                deleteConfirmPage = -1;
            }
        }
    }

    @Override
    public void onFocusLost() {
        commitRename();
    }
}
