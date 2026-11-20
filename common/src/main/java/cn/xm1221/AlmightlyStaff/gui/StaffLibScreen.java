package cn.xm1221.AlmightlyStaff.gui;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import cn.xm1221.AlmightlyStaff.AlmightlyStaffConfig;
import cn.xm1221.AlmightlyStaff.gui.ide.StaffIdeScreen;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import cn.xm1221.AlmightlyStaff.network.StaffSyncReceiver;
import cn.xm1221.AlmightlyStaff.spell.PatternCatalogEntry;
import cn.xm1221.AlmightlyStaff.spell.PatternRef;
import cn.xm1221.AlmightlyStaff.spell.StaffHex;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 万法之杖法术库主界面（三栏布局，移植自 Hex CyberStaff 的 SpellLibraryScreen）。
 * 左：页列表；中：图案目录；右：当前页的序列编辑。
 *
 * <p>页模型见 {@link cn.xm1221.AlmightlyStaff.network.StaffPages}：页号稀疏（删除只留空号），
 * 页存在 = 服务端 {@code pages} 里有该 key。界面自己记住"正在编辑哪一页"（{@link #editingPage}），
 * 服务端回包只更新页列表与 ▶ 标记，不反向驱动草稿。
 *
 * <p>特别感谢 Aurover —— Hex CyberStaff 的作者。本界面的三栏布局、配色、
 * 图案槽位样式、右键菜单、滚动条等视觉与交互设计均改编自 CyberStaff
 * （https://github.com/Aurover/Hex-CyberStaff），向 ta 致敬。
 */
public class StaffLibScreen extends Screen implements StaffSyncReceiver {
    private static final int MARGIN = 8;
    private static final int GAP = 6;
    private static final int LIST_TOP = 70;
    private static final int ROW_HEIGHT = 20;
    private static final int SEQUENCE_SLOT_SIZE = 18;
    private static final int SEQUENCE_SLOT_GAP = 4;
    private static final int SEQUENCE_COLUMNS = 10;
    private static final int FOOTER_HEIGHT = 52;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_THUMB_HEIGHT = 16;
    private static final int SEALED_BANNER_HEIGHT = 14;
    private static final int DRAG_THRESHOLD = 4;
    private static final int MENU_WIDTH = 96;
    private static final int MENU_ITEM_HEIGHT = 16;
    private static final int MENU_ITEMS = 6;
    private static final int MAX_UNDO_HISTORY = 64;
    private static final int NAME_SAVE_DELAY = 20;
    private static final int DELETE_CONFIRM_TICKS = 60;
    private static final long DOUBLE_CLICK_MS = 280L;
    private static final int MAX_NAME_LENGTH = 80;

    private static final int COLOR_ACCENT = 0xFF9C6ADE;
    private static final int COLOR_SELECTED_FILL = 0xAA493662;
    private static final int COLOR_SEALED_FILL = 0x33E8A82E;
    private static final int COLOR_SEALED_BORDER = 0xFFE8A82E;
    private static final int COLOR_SEALED_TEXT = 0xFFFFD9A0;
    private static final int COLOR_TEXT = 0xE2E6EA;

    private static final int DRAG_NONE = -1, DRAG_CATALOG = 0, DRAG_SEQUENCE = 1, DRAG_PAGE = 2;

    // ---- 页（服务端同步的状态）----
    private final List<ModNetworking.PageData> pages = new ArrayList<>();
    private final List<ModNetworking.PageData> pendingSyncPages = new ArrayList<>();
    private int castPage = 0;      // V 键施放页（▶），服务端 page_idx
    private int editingPage = 0;   // 正在编辑的页号，0 = 没有
    private final List<CompoundTag> draftIotas = new ArrayList<>();
    private final ArrayDeque<List<CompoundTag>> undoHistory = new ArrayDeque<>();
    private final ArrayDeque<List<CompoundTag>> redoHistory = new ArrayDeque<>();

    // ---- 图案库 ----
    private List<PatternCatalogEntry> catalog = new ArrayList<>();
    private List<PatternCatalogEntry> filteredCatalog = new ArrayList<>();
    private int selectedCatalogIndex = -1;
    private final Map<String, PatternRef> learnedPerWorld = new HashMap<>();
    private long lastCatalogClickTime;
    private int lastCatalogClickRow = -1;

    // ---- 序列编辑 ----
    private final Set<Integer> selectedSlots = new LinkedHashSet<>();
    private int selectionAnchor = -1;
    private final List<CompoundTag> patternClipboard = new ArrayList<>();

    // ---- 拖拽 ----
    private int pendingDragType = DRAG_NONE, pendingDragIndex = -1;
    private int activeDragType = DRAG_NONE, activeDragIndex = -1;
    private double dragStartX, dragStartY, dragMouseX, dragMouseY;

    // ---- 控件 ----
    private int leftX, leftWidth, middleX, middleWidth, rightX, rightWidth, listBottom;
    private int spellScroll, catalogScroll, sequenceScroll;
    private EditBox nameField;
    private EditBox searchField;
    private Button deleteButton;
    private boolean suppressNameResponder;
    private int nameSaveTimer;
    private boolean deleteArmed;
    private int deleteConfirmTimer;
    private boolean pageListFocused;

    // ---- 右键菜单 ----
    private boolean menuOpen;
    private int menuX, menuY;
    private int menuSlot = -1;

    // ---- 杂项 ----
    private String statusText = "";
    private int statusTimer;
    private String pendingGreatSpellAction;
    private int pendingGreatSpellSlot = -1;
    private int pendingGreatSpellPage = 0;
    private long lastPageClickTime;
    private int lastPageClickRow = -1;
    private boolean syncRequested;
    private boolean focusNameAfterSync;

    public StaffLibScreen() {
        super(Component.translatable("almightly_staff.gui.title"));
    }

    // ==================== 网络同步 ====================

    /** 同步入口。incremental=true 表示操作后的增量回发（只带受影响页），按 pageIndex 合并；否则为全量分块同步。 */
    @Override
    public void onSyncChunk(List<ModNetworking.PageData> chunkPages, int castPage, int focusPage,
                            List<Integer> removed, boolean lastChunk, boolean incremental,
                            String status, String staffUuid) {
        if (incremental) {
            onIncrementalSync(chunkPages, castPage, focusPage, removed, status);
            return;
        }
        pendingSyncPages.addAll(chunkPages);
        if (!lastChunk) return;
        List<ModNetworking.PageData> all = new ArrayList<>(pendingSyncPages);
        pendingSyncPages.clear();
        onFullSync(all, castPage, focusPage);
    }

    /**
     * 全量同步（首开 / 拖拽排序后）：整表替换。
     * focusPage &gt; 0 表示服务端要求切到该页；否则尽量保持正在编辑的页不变。
     */
    private void onFullSync(List<ModNetworking.PageData> synced, int castPage, int focusPage) {
        int keep = focusPage > 0 ? focusPage : (findPage(editingPage) == null ? 0 : editingPage);
        // 全量同步就是"服务端真值"：先断开草稿与名字框，免得把旧草稿 flush 到已经换了内容的页上（B4 的另一半）
        editingPage = 0;
        nameSaveTimer = 0;
        setNameFieldValue("");
        draftIotas.clear();
        pages.clear();
        pages.addAll(synced);
        pages.sort(Comparator.comparingInt(ModNetworking.PageData::pageIndex));
        this.castPage = castPage;
        if (keep == 0) {
            // 首次打开：优先落在 V 键施放页上，否则第 1 页（一打开就能直接编辑）
            keep = findPage(castPage) != null ? castPage : (pages.isEmpty() ? 0 : pages.get(0).pageIndex());
        }
        setEditingPage(keep);
        afterServerFocus();
    }

    /**
     * 增量同步：只把受影响页按 pageIndex 合并进列表，不覆盖正在编辑的草稿（修 B4）。
     * 被拒的操作会带回该页真实内容 + status，此时才回滚草稿。
     */
    private void onIncrementalSync(List<ModNetworking.PageData> updated, int castPage, int focusPage,
                                   List<Integer> removed, String status) {
        this.castPage = castPage;
        if (removed != null) {
            for (int idx : removed) {
                ModNetworking.PageData gone = findPage(idx);
                if (gone != null) pages.remove(gone);
            }
        }
        for (ModNetworking.PageData page : updated) {
            ModNetworking.PageData old = findPage(page.pageIndex());
            if (old == null) pages.add(page);
            else pages.set(pages.indexOf(old), page);
        }
        pages.sort(Comparator.comparingInt(ModNetworking.PageData::pageIndex));

        if (!status.isEmpty()) {
            showStatus(Component.translatable("almightly_staff.gui." + status).getString());
            // 写内容/改名被拒：用服务端真值回滚草稿
            for (ModNetworking.PageData page : updated) {
                if (page.pageIndex() == editingPage) {
                    loadDraft(page);
                    break;
                }
            }
        }

        if (focusPage > 0 && focusPage != editingPage) {
            setEditingPage(focusPage);
            afterServerFocus();
            return;
        }
        if (editingPage > 0 && findPage(editingPage) == null) setEditingPage(0);
        afterServerFocus();
    }

    private ModNetworking.PageData findPage(int pageIndex) {
        if (pageIndex <= 0) return null;
        for (ModNetworking.PageData page : pages) {
            if (page.pageIndex() == pageIndex) return page;
        }
        return null;
    }

    private int pageRow(int pageIndex) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).pageIndex() == pageIndex) return i;
        }
        return -1;
    }

    private boolean isSealed(int pageIndex) {
        ModNetworking.PageData page = findPage(pageIndex);
        return page != null && page.sealed();
    }

    /** 只有"有页 + 未封存"才允许改序列（所有编辑入口的统一守卫）。 */
    private boolean canEditContent() {
        return editingPage > 0 && findPage(editingPage) != null && !isSealed(editingPage);
    }

    /** 切到某一页：刷新草稿、名字框、选择与撤销栈。页号 0 = 清空编辑器。 */
    private void setEditingPage(int pageIndex) {
        flushRename();
        editingPage = 0;
        draftIotas.clear();
        clearSelection();
        undoHistory.clear();
        redoHistory.clear();
        menuOpen = false;
        sequenceScroll = 0;

        ModNetworking.PageData page = findPage(pageIndex);
        if (page != null) {
            editingPage = page.pageIndex();
            loadDraft(page);
        }
        setNameFieldValue(page == null ? "" : page.name());
        if (nameField != null) nameField.setEditable(editingPage > 0);
        deleteConfirmTimer = 0;
        deleteArmed = false;
        updateDeleteButton();
    }

    private void loadDraft(ModNetworking.PageData page) {
        draftIotas.clear();
        for (CompoundTag tag : page.iotas()) if (tag != null) draftIotas.add(tag.copy());
        editingPage = page.pageIndex();
        setNameFieldValue(page.name());
        clearSelection();
        undoHistory.clear();
        redoHistory.clear();
    }

    private void setNameFieldValue(String value) {
        if (nameField == null) return;
        suppressNameResponder = true;
        nameField.setValue(value == null ? "" : value);
        suppressNameResponder = false;
    }

    /** 把当前草稿（名字 + 内容）写进本地页缓存，保证切走再切回来看到的是最新内容。 */
    private void syncLocalPage() {
        int row = pageRow(editingPage);
        if (row < 0) return;
        ModNetworking.PageData old = pages.get(row);
        pages.set(row, new ModNetworking.PageData(editingPage, nameFieldText(),
            new ArrayList<>(draftIotas), old.sealed()));
    }

    private String nameFieldText() {
        return nameField == null ? "" : nameField.getValue().trim();
    }

    // ==================== 初始化 ====================

    @Override
    protected void init() {
        int usableWidth = width - MARGIN * 2 - GAP * 2;
        leftWidth = Math.max(72, usableWidth * 20 / 100);
        rightWidth = Math.max(128, usableWidth * 40 / 100);
        middleWidth = usableWidth - leftWidth - rightWidth;
        if (middleWidth < 96) {
            int shortage = 96 - middleWidth;
            int leftReduction = Math.min(shortage, Math.max(0, leftWidth - 64));
            leftWidth -= leftReduction;
            shortage -= leftReduction;
            rightWidth -= Math.min(shortage, Math.max(0, rightWidth - 120));
            middleWidth = usableWidth - leftWidth - rightWidth;
        }
        leftX = MARGIN;
        middleX = leftX + leftWidth + GAP;
        rightX = middleX + middleWidth + GAP;
        listBottom = height - FOOTER_HEIGHT - 8;

        // 画完图案 / 改窗口大小时重建控件：保留搜索词、滚动位置与目录选中项（修 B14）
        String keepSearch = searchField == null ? "" : searchField.getValue();
        int keepSpellScroll = spellScroll, keepCatalogScroll = catalogScroll, keepSequenceScroll = sequenceScroll;
        int keepCatalogSelection = selectedCatalogIndex;

        nameField = new EditBox(font, rightX, 42, rightWidth, 20, Component.translatable("almightly_staff.gui.spell_name"));
        nameField.setMaxLength(MAX_NAME_LENGTH);
        ModNetworking.PageData editing = findPage(editingPage);
        setNameFieldValue(editing == null ? "" : editing.name());
        nameField.setEditable(editingPage > 0);
        nameField.setResponder(ignored -> {
            if (!suppressNameResponder) nameSaveTimer = NAME_SAVE_DELAY;
        });
        addRenderableWidget(nameField);

        searchField = new EditBox(font, middleX, 42, middleWidth, 20, Component.translatable("almightly_staff.gui.search"));
        searchField.setMaxLength(100);
        suppressNameResponder = true;
        searchField.setValue(keepSearch);
        suppressNameResponder = false;
        searchField.setResponder(ignored -> refreshFilter());
        addRenderableWidget(searchField);

        buildButtons();

        catalog = StaffHex.loadCatalog();
        applyLearnedToCatalog();
        refreshFilter();
        selectedCatalogIndex = keepCatalogSelection;
        if (selectedCatalogIndex >= filteredCatalog.size()) selectedCatalogIndex = -1;

        spellScroll = keepSpellScroll;
        catalogScroll = keepCatalogScroll;
        sequenceScroll = keepSequenceScroll;
        clampScrolls();

        // 只在第一次打开时请求全量同步：从绘制/Parse/施法子界面回来时也会再走一次 init()，
        // 那时重复请求会把刚改好的草稿用旧数据覆盖（还会多一次丢内容的竞态）。
        if (!syncRequested || pages.isEmpty()) {
            syncRequested = true;
            ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffLibReadC2S());
        }
    }

    /** 按钮按栏归位：左栏管页、中栏管图案库、右栏管序列（修 P2 按钮放错栏）。 */
    private void buildButtons() {
        int firstRowY = height - 48;
        int secondRowY = height - 25;
        int gap = 3;
        int halfLeft = Math.max(1, (leftWidth - gap) / 2);
        int leftSecondX = leftX + halfLeft + gap;
        int leftSecondWidth = Math.max(1, leftX + leftWidth - leftSecondX);
        addButton("almightly_staff.gui.new", button -> newPage(), leftX, firstRowY, halfLeft);
        addButton("almightly_staff.gui.copy", button -> duplicatePage(), leftSecondX, firstRowY, leftSecondWidth);
        deleteButton = addButton("almightly_staff.gui.delete", button -> requestDelete(), leftX, secondRowY, leftWidth);

        addButton("almightly_staff.gui.add_pattern", button -> addCatalogPattern(), middleX, firstRowY, middleWidth);
        addButton("almightly_staff.gui.switch_ascode", button -> switchToAsCode(), middleX, secondRowY, middleWidth);

        int halfRight = Math.max(1, (rightWidth - gap) / 2);
        int rightSecondX = rightX + halfRight + gap;
        int rightSecondWidth = Math.max(1, rightX + rightWidth - rightSecondX);
        addButton("almightly_staff.gui.undo", button -> undoSequenceChange(), rightX, firstRowY, halfRight);
        addButton("almightly_staff.gui.redo", button -> redoSequenceChange(), rightSecondX, firstRowY, rightSecondWidth);
        addButton("almightly_staff.gui.remove_slot", button -> removeSelectedSlots(), rightX, secondRowY, halfRight);
        addButton("almightly_staff.gui.parse", button -> enterParseMode(), rightSecondX, secondRowY, rightSecondWidth);
        updateDeleteButton();
    }

    private Button addButton(String labelKey, Button.OnPress action, int x, int y, int buttonWidth) {
        Button button = Button.builder(Component.translatable(labelKey), action)
            .bounds(x, y, buttonWidth, 20).build();
        addRenderableWidget(button);
        return button;
    }

    private void switchToAsCode() {
        flushRename();
        AlmightlyStaffConfig.get().useIdeScreen = true;
        AlmightlyStaffConfig.save();
        if (minecraft != null) {
            minecraft.setScreen(new StaffIdeScreen());
        }
    }

    private void refreshFilter() {
        String query = searchField == null ? "" : searchField.getValue().trim().toLowerCase();
        filteredCatalog = new ArrayList<>();
        for (PatternCatalogEntry entry : catalog) {
            if (query.isEmpty() || entry.displayName().getString().toLowerCase().contains(query)
                || entry.pattern().actionId().toLowerCase().contains(query)
                || entry.pattern().signature().contains(query)) {
                filteredCatalog.add(entry);
            }
        }
    }

    // ==================== RENDER ====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        renderBackground(g);
        g.fill(0, 0, width, 34, 0xE813171C);
        g.drawCenteredString(font, title, width / 2, 14, 0xF4F6F8);
        drawPanel(g, leftX, leftWidth, Component.translatable("almightly_staff.gui.saved_spells"));
        drawPanel(g, middleX, middleWidth, Component.translatable("almightly_staff.gui.pattern_library"));
        drawPanel(g, rightX, rightWidth, Component.translatable("almightly_staff.gui.sequence"));
        drawSpellList(g, mouseX, mouseY);
        drawCatalog(g, mouseX, mouseY);
        drawSequence(g, mouseX, mouseY);
        drawFooter(g);
        super.render(g, mouseX, mouseY, delta);
        drawPageDragFeedback(g);
        drawSequenceDragFeedback(g);
        if (menuOpen) drawSequenceMenu(g, mouseX, mouseY);
        drawPageTooltip(g, mouseX, mouseY);
        drawCatalogTooltip(g, mouseX, mouseY);
    }

    /** 悬停封存页行：提示"已封存：不能移动、删除、编辑内容，可改名"（计划 §4.2）。 */
    private void drawPageTooltip(GuiGraphics g, int mouseX, int mouseY) {
        if (menuOpen || activeDragType != DRAG_NONE || pendingDragType != DRAG_NONE) return;
        int row = spellRowAt(mouseX, mouseY);
        if (row < 0 || !pages.get(row).sealed()) return;
        g.renderTooltip(font, Component.translatable("almightly_staff.gui.sealed_hint"), mouseX, mouseY);
    }

    /** panel: heading above input fields (y=24), purple accent (y=36), right separator. */
    private void drawPanel(GuiGraphics g, int x, int panelWidth, Component heading) {
        g.fill(x, 36, x + panelWidth, 38, COLOR_ACCENT);
        g.drawString(font, trim(heading, panelWidth), x, 24, 0xE9EDF1);
        g.fill(x + panelWidth + 3, 36, x + panelWidth + 4, listBottom, 0x553C444D);
    }

    private Component trim(Component text, int maxWidth) {
        return Component.literal(trimText(text.getString(), maxWidth));
    }

    private String trimText(String text, int maxWidth) {
        String s = text;
        while (font.width(s) > Math.max(8, maxWidth - 4) && s.length() > 1) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    // ---- 左栏：页列表 ----

    private int visiblePageRows() {
        return Math.max(1, (listBottom - LIST_TOP) / ROW_HEIGHT);
    }

    /** 鼠标所在的内容行（pages 下标），不在列表内返回 -1。 */
    private int spellRowAt(double mx, double my) {
        if (mx < leftX || mx >= leftX + leftWidth - SCROLLBAR_WIDTH || my < LIST_TOP || my >= listBottom) return -1;
        int row = spellScroll + (int) ((my - LIST_TOP) / ROW_HEIGHT);
        return row >= 0 && row < pages.size() ? row : -1;
    }

    private void drawSpellList(GuiGraphics g, double mx, double my) {
        int top = LIST_TOP, bottom = listBottom;
        int contentRight = leftX + leftWidth - SCROLLBAR_WIDTH;
        int visible = visiblePageRows();
        int maxScroll = Math.max(0, pages.size() - visible);
        spellScroll = Mth.clamp(spellScroll, 0, maxScroll);
        g.enableScissor(leftX, top, contentRight, bottom);
        for (int i = 0; i < visible; i++) {
            int idx = i + spellScroll;
            if (idx >= pages.size()) break;
            ModNetworking.PageData page = pages.get(idx);
            int y = top + i * ROW_HEIGHT;
            int rowBottom = y + ROW_HEIGHT - 2;
            boolean editing = page.pageIndex() == editingPage;
            boolean casting = page.pageIndex() == castPage;
            int fill = page.sealed() ? COLOR_SEALED_FILL
                : editing ? COLOR_SELECTED_FILL
                : (i % 2 == 0 ? 0x331E252C : 0x332A3138);
            g.fill(leftX, y, contentRight, rowBottom, fill);
            if (editing) g.fill(leftX, y, leftX + 3, rowBottom, 0xFFD7B5FF);
            if (page.sealed()) drawFrame(g, leftX, y, contentRight, rowBottom, 2, COLOR_SEALED_BORDER);

            int textX = leftX + 6;
            if (casting) {
                g.drawString(font, "▶", textX, y + 5, 0xFF8CE99A);
                textX += font.width("▶") + 3;
            }
            if (page.sealed()) {
                drawPadlock(g, textX, y + 6, COLOR_SEALED_BORDER);
                textX += 9;
            }
            String label = page.name().isEmpty()
                ? Component.translatable("almightly_staff.gui.page_label", page.pageIndex()).getString()
                : "#" + page.pageIndex() + " " + page.name();
            int color = page.sealed() ? COLOR_SEALED_TEXT : editing ? 0xE9D7FF : COLOR_TEXT;
            g.drawString(font, trimText(label, contentRight - textX - 2), textX, y + 5, color);
        }
        g.disableScissor();
        drawScrollbar(g, leftX, leftWidth, pages.size(), spellScroll, ROW_HEIGHT);

        // 页拖拽插入线（封存行不显示，改为禁止标记）
        if (activeDragType == DRAG_PAGE) drawPageInsertLine(g);
    }

    /** 小挂锁图标（两笔 U 形锁梁 + 锁体），不依赖字体里的 emoji。 */
    private void drawPadlock(GuiGraphics g, int x, int y, int color) {
        g.fill(x + 1, y, x + 5, y + 1, color);          // 锁梁顶
        g.fill(x + 1, y + 1, x + 2, y + 4, color);      // 锁梁左
        g.fill(x + 4, y + 1, x + 5, y + 4, color);      // 锁梁右
        g.fill(x, y + 4, x + 6, y + 9, color);          // 锁体
        g.fill(x + 2, y + 6, x + 4, y + 8, 0xFF2A2114); // 锁孔
    }

    private void drawFrame(GuiGraphics g, int x1, int y1, int x2, int y2, int thickness, int color) {
        g.fill(x1, y1, x2, y1 + thickness, color);
        g.fill(x1, y2 - thickness, x2, y2, color);
        g.fill(x1, y1, x1 + thickness, y2, color);
        g.fill(x2 - thickness, y1, x2, y2, color);
    }

    private void drawCross(GuiGraphics g, int cx, int cy, int size, int color) {
        int half = Math.max(2, size / 2);
        for (int i = -half; i <= half; i++) {
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
            g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
        }
    }

    private void drawPageInsertLine(GuiGraphics g) {
        int dropRow = pageDropRow(dragMouseY);
        if (dropRow < 0) {
            drawCross(g, (int) dragMouseX, (int) dragMouseY, 10, 0xFFFF5555);
            return;
        }
        if (!isPageDropAllowed(dropRow)) {
            drawCross(g, (int) dragMouseX, (int) dragMouseY, 10, 0xFFFF5555);
            return;
        }
        int rowInView = dropRow - spellScroll;
        int y = LIST_TOP + rowInView * ROW_HEIGHT - 1;
        int contentRight = leftX + leftWidth - SCROLLBAR_WIDTH;
        g.fill(leftX + 2, y, contentRight - 2, y + 2, COLOR_ACCENT);
    }

    // ---- 中栏：图案库 ----

    private void drawCatalog(GuiGraphics g, double mx, double my) {
        int top = LIST_TOP, bottom = listBottom;
        int contentRight = middleX + middleWidth - SCROLLBAR_WIDTH;
        int visible = Math.max(1, (bottom - top) / ROW_HEIGHT);
        int maxScroll = Math.max(0, filteredCatalog.size() - visible);
        catalogScroll = Mth.clamp(catalogScroll, 0, maxScroll);
        g.enableScissor(middleX, top, contentRight, bottom);
        for (int i = 0; i < visible; i++) {
            int idx = i + catalogScroll;
            if (idx >= filteredCatalog.size()) break;
            int y = top + i * ROW_HEIGHT;
            boolean selected = idx == selectedCatalogIndex;
            int fill = selected ? 0xAA315E5B : (i % 2 == 0 ? 0x331E252C : 0x332A3138);
            g.fill(middleX, y, contentRight, y + ROW_HEIGHT - 2, fill);
            g.drawString(font, trim(filteredCatalog.get(idx).displayName(), contentRight - middleX - 8),
                middleX + 6, y + 5, selected ? 0xD7FFED : COLOR_TEXT);
        }
        g.disableScissor();
        drawScrollbar(g, middleX, middleWidth, filteredCatalog.size(), catalogScroll, ROW_HEIGHT);
    }

    private int catalogRowAt(double mx, double my) {
        if (mx < middleX || mx >= middleX + middleWidth - SCROLLBAR_WIDTH || my < LIST_TOP || my >= listBottom) return -1;
        int row = catalogScroll + (int) ((my - LIST_TOP) / ROW_HEIGHT);
        return row >= 0 && row < filteredCatalog.size() ? row : -1;
    }

    // ---- 右栏：序列（绘制与命中共用同一个布局）----

    /** 序列网格布局：{@code computeSeqLayout()} 是唯一来源，绘制与命中测试都读它（修 B10）。 */
    private record SeqLayout(int startX, int startY, int columns, int rowsPerView, int totalRows,
                             int slotSize, int slotGap, int top) {
        int step() {
            return slotSize + slotGap;
        }
    }

    private SeqLayout computeSeqLayout() {
        boolean sealed = isSealed(editingPage);
        int top = LIST_TOP + (sealed ? SEALED_BANNER_HEIGHT : 0);
        int bottom = listBottom;
        int contentRight = rightX + rightWidth - SCROLLBAR_WIDTH;
        int usable = contentRight - rightX - 8;
        int columns = Math.max(1, Math.min(SEQUENCE_COLUMNS, usable / (SEQUENCE_SLOT_SIZE + SEQUENCE_SLOT_GAP)));
        int rowsPerView = Math.max(1, (bottom - top - 4) / (SEQUENCE_SLOT_SIZE + SEQUENCE_SLOT_GAP));
        int totalRows = draftIotas.size() / columns + 1; // 末尾总留一行空槽
        int gridWidth = columns * SEQUENCE_SLOT_SIZE + (columns - 1) * SEQUENCE_SLOT_GAP;
        int startX = rightX + Math.max(0, (contentRight - rightX - gridWidth) / 2);
        return new SeqLayout(startX, top + 4, columns, rowsPerView, totalRows,
            SEQUENCE_SLOT_SIZE, SEQUENCE_SLOT_GAP, top);
    }

    /** 鼠标所在格子（可能 &gt;= iotas.size()，即末尾空槽）；不在格子上（含格间空隙）返回 -1。 */
    private int sequenceSlotAt(double mx, double my) {
        if (editingPage <= 0) return -1;
        if (mx < rightX || mx >= rightX + rightWidth || my < LIST_TOP || my >= listBottom) return -1;
        SeqLayout layout = computeSeqLayout();
        double localX = mx - layout.startX();
        double localY = my - layout.startY();
        if (localX < 0 || localY < 0) return -1;
        int step = layout.step();
        int col = (int) (localX / step);
        int rowInView = (int) (localY / step);
        if (col >= layout.columns() || rowInView >= layout.rowsPerView()) return -1;
        if (localX - col * step >= layout.slotSize()) return -1;   // 格子间隙不算格子
        if (localY - rowInView * step >= layout.slotSize()) return -1;
        int row = rowInView + sequenceScroll;
        if (row >= layout.totalRows()) return -1;
        return row * layout.columns() + col;
    }

    /** 拖放位置：按落点在格子左/右半决定插到它前面还是后面（修 B12）。 */
    private int sequenceInsertionIndexAt(double mx, double my) {
        SeqLayout layout = computeSeqLayout();
        int slot = sequenceSlotAt(mx, my);
        if (slot < 0) {
            // 落在网格下方 → 末尾；上方 → 开头
            return my >= layout.startY() ? draftIotas.size() : 0;
        }
        int col = slot % layout.columns();
        double localX = mx - layout.startX() - col * layout.step();
        boolean rightHalf = localX >= layout.slotSize() / 2.0;
        int base = Math.min(slot, draftIotas.size());
        return rightHalf ? base + 1 : base;
    }

    private void drawSequence(GuiGraphics g, double mx, double my) {
        boolean sealed = isSealed(editingPage);
        SeqLayout layout = computeSeqLayout();
        int contentRight = rightX + rightWidth - SCROLLBAR_WIDTH;
        int maxScroll = Math.max(0, layout.totalRows() - layout.rowsPerView());
        sequenceScroll = Mth.clamp(sequenceScroll, 0, maxScroll);
        int hoverSlot = sequenceSlotAt(mx, my);

        if (editingPage <= 0) {
            g.drawCenteredString(font, Component.translatable("almightly_staff.gui.no_page"),
                rightX + rightWidth / 2, LIST_TOP + 20, 0xFF777777);
            return;
        }
        if (sealed) drawSealedBanner(g);

        g.enableScissor(rightX, layout.top(), contentRight, listBottom);
        for (int rowInView = 0; rowInView < layout.rowsPerView(); rowInView++) {
            int row = rowInView + sequenceScroll;
            if (row >= layout.totalRows()) break;
            for (int col = 0; col < layout.columns(); col++) {
                int slotIdx = row * layout.columns() + col;
                int x = layout.startX() + col * layout.step();
                int y = layout.startY() + rowInView * layout.step();
                boolean occupied = slotIdx < draftIotas.size();
                boolean selected = occupied && selectedSlots.contains(slotIdx);
                boolean hover = slotIdx == hoverSlot;
                if (occupied) {
                    drawSequenceSlot(g, x, y, layout.slotSize(), selected, hover);
                    renderSlotIota(g, draftIotas.get(slotIdx), x + 1, y + 1, layout.slotSize() - 2);
                } else if (!sealed) {
                    int border = hover ? 0xFFD7B5FF : 0xFF3A414A;
                    g.fill(x, y, x + layout.slotSize(), y + layout.slotSize(), border);
                    g.fill(x + 1, y + 1, x + layout.slotSize() - 1, y + layout.slotSize() - 1,
                        hover ? 0xFF2C2C35 : 0xFF20252B);
                    if (hover) {
                        g.drawCenteredString(font, "+", x + layout.slotSize() / 2,
                            y + layout.slotSize() / 2 - 4, COLOR_ACCENT);
                    }
                }
            }
        }
        // 序列拖拽插入线
        if (activeDragType == DRAG_SEQUENCE && !sealed) {
            int dropIndex = sequenceInsertionIndexAt(dragMouseX, dragMouseY);
            int row = dropIndex / layout.columns();
            int col = dropIndex % layout.columns();
            int x = layout.startX() + col * layout.step() - (dropIndex == draftIotas.size() ? 0 : 2);
            int y = layout.startY() + (row - sequenceScroll) * layout.step();
            if (row - sequenceScroll >= 0 && row - sequenceScroll <= layout.rowsPerView()) {
                g.fill(x, y, x + 2, y + layout.slotSize(), COLOR_ACCENT);
            }
        }
        g.disableScissor();
        drawScrollbar(g, rightX, rightWidth, layout.totalRows(), sequenceScroll, layout.step());
        if (sealed) drawFrame(g, rightX, LIST_TOP, contentRight + SCROLLBAR_WIDTH, listBottom, 2, COLOR_SEALED_BORDER);

        if (hoverSlot >= 0 && hoverSlot < draftIotas.size()) {
            Component hover = sequenceHoverName(draftIotas.get(hoverSlot));
            g.renderTooltip(font, hover != null ? hover : IotaType.getDisplay(draftIotas.get(hoverSlot)), (int) mx, (int) my);
        }
    }

    private void drawSealedBanner(GuiGraphics g) {
        int y = LIST_TOP;
        g.fill(rightX, y, rightX + rightWidth - SCROLLBAR_WIDTH, y + SEALED_BANNER_HEIGHT - 2, 0x55E8A82E);
        drawPadlock(g, rightX + 5, y + 3, COLOR_SEALED_BORDER);
        g.drawString(font, Component.translatable("almightly_staff.gui.sealed_readonly"),
            rightX + 16, y + 3, COLOR_SEALED_TEXT);
    }

    /** 悬停名称：图案 iota 且能解析到已注册动作 → 显示其本地化名称；否则 null（回退 getDisplay）。 */
    private Component sequenceHoverName(CompoundTag tag) {
        if (IotaType.getTypeFromTag(tag) != HexIotaTypes.PATTERN) return null;
        try {
            Iota i = IotaType.deserialize(tag, null); // 图案无需 level
            if (i instanceof PatternIota pi) {
                return StaffHex.patternDisplayName(StaffHex.refFor(pi.getPattern()));
            }
        } catch (Exception ignored) { }
        return null;
    }

    /** CyberStaff slot style: border + fill + top highlight. */
    private void drawSequenceSlot(GuiGraphics g, int x, int y, int size, boolean selected, boolean hovered) {
        int border = selected ? 0xFFFFCA72 : hovered ? 0xFFD7B5FF : 0xFF4B535C;
        int fill = selected ? 0xFF4E3D2C : hovered ? 0xFF303039 : 0xFF20252B;
        g.fill(x, y, x + size, y + size, border);
        g.fill(x + 1, y + 1, x + size - 1, y + size - 1, fill);
        g.fill(x + 2, y + 2, x + size - 2, y + 3, 0x553B424A);
    }

    private void renderSlotIota(GuiGraphics g, CompoundTag tag, int x, int y, int size) {
        if (IotaType.getTypeFromTag(tag) == HexIotaTypes.PATTERN) {
            // 图案：反序列化只需标签（pattern 无需 level），渲染真实图案
            try {
                Iota iota = IotaType.deserialize(tag, null);
                if (iota instanceof PatternIota pi) {
                    PoseStack ps = g.pose();
                    ps.pushPose();
                    PatternRef ref = StaffHex.refFor(pi.getPattern());
                    StaffHex.renderPattern(ref, ps, x, y, (int) (size / 16.0f * 14.0f));
                    ps.popPose();
                    return;
                }
            } catch (Exception ignored) { }
        }
        // 非图案 iota：Hex 官方类型色方块（IotaType.getColor(tag)）；数值由悬停 tooltip 显示
        int color = IotaType.getColor(tag) | 0xFF000000;
        int cx = x + size / 2, cy = y + size / 2;
        int half = Math.max(3, size / 4);
        g.fill(cx - half, cy - half, cx + half + 1, cy + half + 1, color);
        g.fill(cx - half + 1, cy - half + 1, cx + half, cy + half, 0x66FFFFFF); // 内高光
    }

    // ==================== 图案目录悬停详情 tooltip（参考 CyberStaff drawPatternTooltip） ====================

    private PatternCatalogEntry hoveredCatalogEntry(double mx, double my) {
        int row = catalogRowAt(mx, my);
        return row < 0 ? null : filteredCatalog.get(row);
    }

    /** 树堆改变：优先用手册 JSON 的 input/output，没有则回退到常见动作对照表。 */
    private String patternSummary(PatternRef pattern) {
        String actionId = pattern.actionId();
        if (actionId.isEmpty() || pattern.signature().isBlank()) return "";
        StaffBookData.Entry book = StaffBookData.get(actionId);
        if (book != null && !book.args().isEmpty()) return book.args();
        String path = actionPath(actionId);
        return switch (path) {
            case "get_caster" -> "→ entity";
            case "empty_list" -> "→ list";
            case "singleton" -> "iota → list";
            case "append" -> "list, iota → list";
            case "unappend" -> "list → list, iota";
            case "index" -> "list, number → iota";
            case "splat" -> "list → ...";
            case "reverse" -> "list → list";
            case "duplicate" -> "iota → iota, iota";
            case "2dup" -> "iota, iota → iota, iota, iota, iota";
            case "swap" -> "iota, iota → iota, iota";
            case "stack_len" -> "→ number";
            case "and", "or", "xor" -> "boolean, boolean → boolean";
            case "not", "bool_coerce" -> "iota → boolean";
            case "greater", "less", "greater_eq", "less_eq", "equals", "not_equals" -> "iota, iota → boolean";
            case "add", "sub", "mul", "div", "pow" -> "number | vector, number | vector → number | vector";
            case "modulo", "logarithm", "arctan2" -> "number, number → number";
            case "sin", "cos", "tan", "arcsin", "arccos", "arctan" -> "number → number";
            case "construct_vec" -> "number, number, number → vector";
            case "deconstruct_vec" -> "vector → number, number, number";
            case "read", "read/entity", "read/local", "akashic/read" -> "source → iota";
            case "write", "write/entity", "write/local", "akashic/write" -> "source, iota →";
            case "print", "beep" -> "iota →";
            default -> "";
        };
    }

    private String actionPath(String actionId) {
        int colon = actionId.indexOf(':');
        return colon >= 0 ? actionId.substring(colon + 1) : actionId;
    }

    private String cleanPatchouliText(String value) {
        return value
            .replace("$(br2)", " ")
            .replace("$(br)", " ")
            .replace("$(li)", " ")
            .replace("$(p)", " ")
            .replaceAll("\\$\\([^)]*\\)", "")
            .replace("/$", "")
            .replace("\\", "")
            .trim();
    }

    /** 详细描述：优先用手册 JSON 的 text 页面（参考 hexcessible），回退到译文键猜测。 */
    private String patternDescription(PatternRef pattern) {
        String actionId = pattern.actionId();
        if (actionId.isEmpty() || pattern.signature().isBlank()) return "";
        if (actionId.startsWith("hexcasting:number/")) {
            return Component.translatable("almightly_staff.gui.number_desc",
                actionId.substring(actionId.lastIndexOf('/') + 1)).getString();
        }
        Language lang = Language.getInstance();
        StaffBookData.Entry book = StaffBookData.get(actionId);
        if (book != null && !book.descKey().isEmpty()) {
            String descKey = book.descKey();
            String text = lang.has(descKey) ? lang.getOrDefault(descKey, "") : descKey;
            String cleaned = StaffBookData.clean(text);
            if (!cleaned.isEmpty()) return cleaned;
        }
        // 回退：译文键猜测（仅作保底）
        String path = actionPath(actionId);
        String[] sections = {
            "basics_pattern", "math", "advanced_math", "lists", "sets", "logic", "stackmanip",
            "readwrite", "meta", "spells", "great_spells", "circle_patterns", "akashic_patterns"
        };
        for (String section : sections) {
            String base = "hexcasting.page." + section + "." + path;
            for (String key : new String[]{base, base + ".1", base + ".2"}) {
                if (lang.has(key)) return cleanPatchouliText(lang.getOrDefault(key, ""));
            }
        }
        return "";
    }

    /** 图案目录悬停时的详情框（CyberStaff 风格：深背景 + 紫色边）。 */
    private void drawCatalogTooltip(GuiGraphics g, double mouseX, double mouseY) {
        if (menuOpen || activeDragType != DRAG_NONE || pendingDragType != DRAG_NONE) return;
        PatternCatalogEntry entry = hoveredCatalogEntry(mouseX, mouseY);
        if (entry == null) return;
        int mx = (int) mouseX, my = (int) mouseY;
        PatternRef pattern = entry.pattern();
        String summary = patternSummary(pattern);
        String description = patternDescription(pattern);
        PoseStack psBox = g.pose();
        psBox.pushPose();
        psBox.translate(0.0F, 0.0F, 600.0F); // 抬高 z：不低于目录/按钮文字
        try {
            int previewSize = 40;
            int maxWidth = Math.max(200, width - 8);
            int tooltipWidth = Math.min(Math.max(220, width / 2), maxWidth);
            int textWidth = Math.max(48, tooltipWidth - previewSize - 24);
            List<net.minecraft.util.FormattedCharSequence> nameLines = font.split(entry.displayName(), textWidth);
            List<net.minecraft.util.FormattedCharSequence> summaryLines = summary.isEmpty() ? List.of()
                : font.split(Component.literal(summary).withStyle(ChatFormatting.GRAY), textWidth);
            List<net.minecraft.util.FormattedCharSequence> descLines = description.isEmpty() ? List.of()
                : font.split(Component.literal(description).withStyle(ChatFormatting.GRAY), textWidth);
            int lineH = 10;
            int textHeight = 8 + (nameLines.size() + summaryLines.size() + descLines.size()) * lineH + 8;
            int tooltipHeight = Math.max(previewSize + 16, textHeight);
            int tooltipX = mx + 12, tooltipY = my + 12;
            if (tooltipX + tooltipWidth > width) tooltipX = mx - tooltipWidth - 12;
            if (tooltipY + tooltipHeight > height) tooltipY = height - tooltipHeight - 6;
            tooltipX = Math.max(4, tooltipX);
            tooltipY = Math.max(4, tooltipY);
            g.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipWidth + 1, tooltipY + tooltipHeight + 1, 0xF0100010);
            g.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xF018111F);
            g.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + 1, COLOR_ACCENT);
            g.fill(tooltipX, tooltipY + tooltipHeight - 1, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xFF4B2F68);
            g.fill(tooltipX, tooltipY, tooltipX + 1, tooltipY + tooltipHeight, COLOR_ACCENT);
            g.fill(tooltipX + tooltipWidth - 1, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xFF4B2F68);
            int previewX = tooltipX + 8, previewY = tooltipY + 8;
            if (!pattern.signature().isBlank()) {
                PoseStack ps = g.pose();
                ps.pushPose();
                StaffHex.renderPattern(pattern, ps, previewX, previewY, previewSize);
                ps.popPose();
            }
            int textX = previewX + previewSize + 8;
            int textY = tooltipY + 8;
            for (var c : nameLines) { g.drawString(font, c, textX, textY, 0xFFFFFFFF); textY += lineH; }
            textY += 2;
            for (var c : summaryLines) { g.drawString(font, c, textX, textY, 0xFF9DA6B0); textY += lineH; }
            for (var c : descLines) { g.drawString(font, c, textX, textY, 0xFF9DA6B0); textY += lineH; }
        } finally {
            psBox.popPose();
        }
    }

    /** 顶部状态条：左侧施法提示，右侧操作反馈（底部两行按钮已占满，放这儿不会压住列表）。 */
    private void drawFooter(GuiGraphics g) {
        int maxWidth = Math.max(40, width / 2 - 60);
        g.drawString(font, trim(Component.translatable("almightly_staff.gui.cast_hint"), maxWidth),
            MARGIN, 13, 0xFF7A828C);
        if (statusTimer > 0 && !statusText.isEmpty()) {
            String text = trimText(statusText, maxWidth);
            g.drawString(font, text, width - MARGIN - font.width(text), 13, 0xFFFFE3A3);
        }
    }

    /** CyberStaff scrollbar style: dark track + purple thumb；行高由调用方给（修 B16）。 */
    private void drawScrollbar(GuiGraphics g, int x, int panelWidth, int total, int scroll, int rowHeight) {
        int trackX = x + panelWidth - SCROLLBAR_WIDTH;
        g.fill(trackX, LIST_TOP, trackX + SCROLLBAR_WIDTH, listBottom, 0xAA11161B);
        int visible = Math.max(1, (listBottom - LIST_TOP) / rowHeight);
        int maxScroll = Math.max(0, total - visible);
        if (maxScroll <= 0) return;
        int track = listBottom - LIST_TOP;
        int thumb = Math.max(MIN_THUMB_HEIGHT, track * visible / Math.max(1, total));
        int y = LIST_TOP + scroll * (track - thumb) / maxScroll;
        g.fill(trackX + 1, y, trackX + SCROLLBAR_WIDTH - 1, y + thumb, COLOR_ACCENT);
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTimer > 0) statusTimer--;
        if (deleteConfirmTimer > 0) {
            deleteConfirmTimer--;
            if (deleteConfirmTimer == 0) {
                deleteArmed = false;
                updateDeleteButton();
            }
        }
        if (nameSaveTimer > 0) {
            nameSaveTimer--;
            if (nameSaveTimer == 0) flushRename();
        }
    }

    // ==================== 页操作 ====================

    private void newPage() {
        focusNameAfterSync = true;
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageOpC2S(ModNetworking.OP_NEW, 0, 0, false, ""));
    }

    private void duplicatePage() {
        if (editingPage <= 0) return;
        ModNetworking.PageData page = findPage(editingPage);
        if (page == null) return;
        focusNameAfterSync = true;
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageOpC2S(
            ModNetworking.OP_DUPLICATE, editingPage, 0, false, uniqueCopyName(page.name())));
    }

    /** 新建/复制的回包生效后：滚动到该页并聚焦名字框（计划 §4.2）。 */
    private void afterServerFocus() {
        if (!focusNameAfterSync) return;
        focusNameAfterSync = false;
        if (editingPage <= 0) return;
        scrollPageIntoView(editingPage);
        beginRename();
    }

    /** 副本名去重：X → X (副本) → X (副本 2) → …，不会叠成 "X (副本) (副本)"。 */
    private String uniqueCopyName(String baseName) {
        String suffix = Component.translatable("almightly_staff.gui.copy_suffix").getString();
        String stem = baseName == null ? "" : baseName.trim();
        // 去掉末尾已有的 "(副本 N)"，从原始名字开始编号
        String marker = suffix.trim();
        while (!marker.isEmpty() && stem.endsWith(marker)) {
            stem = stem.substring(0, stem.length() - marker.length()).trim();
        }
        String candidate = stem.isEmpty() ? suffix.trim() : stem + " " + marker;
        int n = 1;
        while (nameTaken(candidate)) {
            n++;
            String indexed = Component.translatable("almightly_staff.gui.copy_suffix_n", n).getString();
            candidate = stem.isEmpty() ? indexed.trim() : stem + " " + indexed;
        }
        return candidate;
    }

    private boolean nameTaken(String name) {
        for (ModNetworking.PageData page : pages) if (page.name().equals(name)) return true;
        return false;
    }

    /** 删除是两步确认：第一次点击进入确认态，3 秒内再点才真正删除（封存页不可删）。 */
    private void requestDelete() {
        if (editingPage <= 0) return;
        if (isSealed(editingPage)) {
            showStatus(Component.translatable("almightly_staff.gui.sealed_hint").getString());
            return;
        }
        if (!deleteArmed) {
            deleteArmed = true;
            deleteConfirmTimer = DELETE_CONFIRM_TICKS;
            updateDeleteButton();
            return;
        }
        deleteArmed = false;
        deleteConfirmTimer = 0;
        updateDeleteButton();
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageOpC2S(
            ModNetworking.OP_DELETE, editingPage, 0, false, ""));
    }

    private void updateDeleteButton() {
        if (deleteButton == null) return;
        boolean armed = deleteArmed;
        boolean sealed = isSealed(editingPage);
        deleteButton.setMessage(Component.translatable(armed
            ? "almightly_staff.gui.confirm_delete"
            : "almightly_staff.gui.delete"));
        deleteButton.active = armed || (editingPage > 0 && !sealed);
        deleteButton.setTooltip(sealed
            ? Tooltip.create(Component.translatable("almightly_staff.gui.sealed_hint"))
            : null);
    }

    /** 选中一页（同时切换 V 键施放页）。 */
    private void selectPage(int pageIndex) {
        setEditingPage(pageIndex);
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageOpC2S(
            ModNetworking.OP_SELECT, pageIndex, 0, false, ""));
        scrollPageIntoView(pageIndex);
    }

    private void scrollPageIntoView(int pageIndex) {
        int row = pageRow(pageIndex);
        if (row < 0) return;
        int visible = visiblePageRows();
        if (row < spellScroll) spellScroll = row;
        else if (row >= spellScroll + visible) spellScroll = row - visible + 1;
        spellScroll = Math.max(0, spellScroll);
    }

    /** 拖拽排序（替代上移/下移）：跨过 4px 才算拖拽，落点按行上/下半决定插前/插后。 */
    private void finishPageDrag(double mx, double my) {
        int from = activeDragIndex;
        if (from <= 0) return;
        if (isSealed(from)) {
            showStatus(Component.translatable("almightly_staff.gui.sealed_hint").getString());
            return;
        }
        int dropRow = pageDropRow(my);
        if (dropRow < 0) return;
        if (mx < leftX || mx >= leftX + leftWidth) return; // 拖到列表外 = 取消
        int[] target = pageDropTarget(dropRow);
        if (target == null) return;
        if (target[0] == from) return;
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageOpC2S(
            ModNetworking.OP_REORDER, from, target[0], target[1] != 0, ""));
    }

    /** 落点行（0..pages.size()，即"插在第 n 行之前"）；不在页列表内返回 -1。 */
    private int pageDropRow(double my) {
        if (my < LIST_TOP - 6 || my >= listBottom + 6) return -1;
        int visible = visiblePageRows();
        int rowInView = Mth.clamp((int) ((my - LIST_TOP) / ROW_HEIGHT), 0, visible - 1);
        int row = rowInView + spellScroll;
        boolean after = ((my - LIST_TOP) % ROW_HEIGHT) > ROW_HEIGHT / 2.0;
        int dropRow = after ? row + 1 : row;
        return Mth.clamp(dropRow, 0, pages.size());
    }

    /**
     * 落点行 → {@code {目标页号, after?1:0}}；落点无效（封存页或被拖页自身）返回 null。
     */
    private int[] pageDropTarget(int dropRow) {
        if (pages.isEmpty()) return null;
        int targetRow = Math.min(dropRow, pages.size() - 1);
        boolean after = dropRow > pages.size() - 1;
        ModNetworking.PageData target = pages.get(targetRow);
        if (target.sealed()) return null;
        if (target.pageIndex() == activeDragIndex) return null;
        return new int[]{target.pageIndex(), after ? 1 : 0};
    }

    private boolean isPageDropAllowed(int dropRow) {
        return pageDropTarget(dropRow) != null && !isSealed(activeDragIndex);
    }

    // ==================== 序列操作 ====================

    private List<CompoundTag> copySequence() {
        List<CompoundTag> copy = new ArrayList<>(draftIotas.size());
        for (CompoundTag tag : draftIotas) copy.add(tag == null ? null : tag.copy());
        return copy;
    }

    private boolean sameSequence(List<CompoundTag> a, List<CompoundTag> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            CompoundTag x = a.get(i), y = b.get(i);
            if (x == null || y == null) {
                if (x != y) return false;
            } else if (!x.equals(y)) {
                return false;
            }
        }
        return true;
    }

    private void undoSequenceChange() {
        if (!canEditContent() || undoHistory.isEmpty()) return;
        redoHistory.addFirst(copySequence());
        replaceSequence(undoHistory.removeFirst());
        saveDraftNow();
    }

    private void redoSequenceChange() {
        if (!canEditContent() || redoHistory.isEmpty()) return;
        undoHistory.addFirst(copySequence());
        replaceSequence(redoHistory.removeFirst());
        saveDraftNow();
    }

    private void replaceSequence(List<CompoundTag> sequence) {
        draftIotas.clear();
        for (CompoundTag tag : sequence) draftIotas.add(tag == null ? null : tag.copy());
        clearSelection();
    }

    /** 立即把当前页内容写到服务端（所有编辑都自动保存，不再有"保存"按钮）。 */
    private void saveDraftNow() {
        if (!canEditContent()) return;
        syncLocalPage();
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageWriteC2S(editingPage, new ArrayList<>(draftIotas)));
    }

    /** 改名走独立的 RENAME 操作，立即 flush（切页/失焦/Enter/Ctrl+S/关窗）。 */
    private void flushRename() {
        nameSaveTimer = 0;
        if (editingPage <= 0 || nameField == null) return;
        int row = pageRow(editingPage);
        if (row < 0) return;
        String newName = nameFieldText();
        if (newName.equals(pages.get(row).name())) return;
        syncLocalPage();
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageOpC2S(
            ModNetworking.OP_RENAME, editingPage, 0, false, newName));
    }

    /**
     * 添加图案：有选中格 → 插到最后一个选中格之后；没有选中 → 追加到末尾（计划 §4.6，双击同此规则）。
     */
    private void addCatalogPattern() {
        if (!canEditContent()) return;
        if (selectedCatalogIndex < 0 || selectedCatalogIndex >= filteredCatalog.size()) return;
        PatternCatalogEntry entry = filteredCatalog.get(selectedCatalogIndex);
        PatternRef ref = entry.pattern();
        if (ref.signature().isBlank()) {
            // 卓越法术：自动用 ParseCode（代码→图案）检验，能转换则添加
            requestGreatSpellAutoParse(ref.actionId(), -1);
            return;
        }
        int insertAt = selectedSlots.isEmpty()
            ? draftIotas.size()
            : Math.min(Collections.max(selectedSlots) + 1, draftIotas.size());
        pushUndo();
        draftIotas.add(insertAt, IotaType.serialize(new PatternIota(StaffHex.patternFor(ref))));
        clearSelection();
        selectedSlots.add(insertAt);
        selectionAnchor = insertAt;
        saveDraftNow();
    }

    private void pushUndo() {
        if (!canEditContent()) return;
        undoHistory.addFirst(copySequence());
        while (undoHistory.size() > MAX_UNDO_HISTORY) undoHistory.removeLast();
        redoHistory.clear();
    }

    /** 绘制完成回调：填充或覆盖指定槽位，并立即保存到服务端。 */
    private void onPatternDrawn(int slotIndex, PatternRef ref) {
        if (!canEditContent()) return;
        pushUndo();
        CompoundTag tag = IotaType.serialize(new PatternIota(StaffHex.patternFor(ref)));
        if (slotIndex >= 0 && slotIndex < draftIotas.size()) {
            draftIotas.set(slotIndex, tag); // 覆盖
        } else {
            draftIotas.add(tag); // 追加到末尾空格
        }
        saveDraftNow();
        learnDrawnGreatSpell(ref);
    }

    private void openDrawGui(int slotIndex) {
        if (!canEditContent()) return;
        flushRename();
        minecraft.setScreen(new StaffDrawScreen(this, ref -> onPatternDrawn(slotIndex, ref)));
    }

    /** 向后绘制回调：新图案插入到 targetSlot 之后一格。 */
    private void onPatternDrawnInsert(int targetSlot, PatternRef ref) {
        if (!canEditContent()) return;
        pushUndo();
        CompoundTag tag = IotaType.serialize(new PatternIota(StaffHex.patternFor(ref)));
        int insertAt = Math.max(0, Math.min(targetSlot + 1, draftIotas.size()));
        draftIotas.add(insertAt, tag);
        clearSelection();
        selectedSlots.add(insertAt);
        selectionAnchor = insertAt;
        saveDraftNow();
        learnDrawnGreatSpell(ref);
    }

    /** 打开"向后绘制"画布：画完插入到 targetSlot 之后。 */
    private void openDrawInsertAfter(int targetSlot) {
        if (!canEditContent()) return;
        flushRename();
        minecraft.setScreen(new StaffDrawScreen(this, ref -> onPatternDrawnInsert(targetSlot, ref)));
    }

    // ==================== Parse ====================

    /** 进入 Parse 文本模式：请求服务端把当前页转为代码，回包后打开文本编辑器。 */
    private void enterParseMode() {
        if (!canEditContent()) return;
        if (!dev.architectury.platform.Platform.isModLoaded("hexparse")) {
            showStatus(Component.translatable("almightly_staff.gui.hexparse_missing").getString());
            return;
        }
        flushRename();
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgParseToCodeC2S(ModNetworking.buildListTag(draftIotas)));
    }

    public void onParseCode(String code) {
        int page = editingPage;
        minecraft.setScreen(new StaffParseScreen(this, code, parsed -> {
            if (parsed != null && canEditContent() && editingPage == page) {
                // 代码→图案转换结果中若有卓越法术，学习其签名
                for (Iota i : parsed) {
                    if (i instanceof PatternIota pi) learnDrawnGreatSpell(StaffHex.refFor(pi.getPattern()));
                }
                pushUndo();
                draftIotas.clear();
                for (Iota i : parsed) if (i != null) draftIotas.add(IotaType.serialize(i));
                saveDraftNow(); // 解析结果立即写回服务端
            }
            minecraft.setScreen(this);
        }));
    }

    // ==================== 交互 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 点空白/其它区域让输入框失焦，否则 Delete 会删名字里的字（修 B11）
        if (nameField != null && nameField.isFocused() && !nameField.isMouseOver(mouseX, mouseY)) {
            flushRename();
            this.setFocused((GuiEventListener) null);
        }
        if (searchField != null && searchField.isFocused() && !searchField.isMouseOver(mouseX, mouseY)) {
            this.setFocused((GuiEventListener) null);
        }
        // 菜单打开时优先处理菜单；关闭菜单的这一次点击要被吞掉（修 B17）
        if (menuOpen) {
            if (clickSequenceMenu(mouseX, mouseY)) return true;
            menuOpen = false;
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            if (clickSpellList(mouseX, mouseY)) return true;
            if (clickCatalog(mouseX, mouseY)) return true;
            if (clickSequence(mouseX, mouseY)) return true;
        } else if (button == 1) {
            if (clickSequenceRight(mouseX, mouseY)) return true;
        }
        return false;
    }

    private boolean clickSpellList(double mx, double my) {
        if (mx < leftX || mx >= leftX + leftWidth || my < LIST_TOP || my >= listBottom) return false;
        pageListFocused = true;
        int row = spellRowAt(mx, my);
        if (row < 0) return true;
        ModNetworking.PageData page = pages.get(row);
        long now = System.currentTimeMillis();
        boolean doubleClick = row == lastPageClickRow && now - lastPageClickTime <= DOUBLE_CLICK_MS;
        lastPageClickTime = now;
        lastPageClickRow = row;
        if (doubleClick) {
            beginRename();
            return true;
        }
        deleteArmed = false;
        deleteConfirmTimer = 0;
        updateDeleteButton();
        selectPage(page.pageIndex());
        // 按下即开始待拖拽：移动超过阈值才算拖拽，否则就是单纯的点击选中
        beginPendingDrag(DRAG_PAGE, page.pageIndex(), mx, my);
        return true;
    }

    /** 聚焦名字框并全选（F2 / 双击页行）。 */
    private void beginRename() {
        if (editingPage <= 0 || nameField == null) return;
        this.setFocused(nameField);
        nameField.setFocused(true);
        nameField.moveCursorToEnd();
        nameField.setHighlightPos(0);
    }

    private boolean clickCatalog(double mx, double my) {
        if (mx < middleX || mx >= middleX + middleWidth || my < LIST_TOP || my >= listBottom) return false;
        pageListFocused = false;
        int row = catalogRowAt(mx, my);
        if (row < 0) return true;
        long now = System.currentTimeMillis();
        boolean doubleClick = row == lastCatalogClickRow && now - lastCatalogClickTime <= DOUBLE_CLICK_MS;
        lastCatalogClickTime = now;
        lastCatalogClickRow = row;
        selectedCatalogIndex = row;
        if (doubleClick) addCatalogPattern(); // 双击 = 添加（P2）
        else if (canEditContent()) beginPendingDrag(DRAG_CATALOG, row, mx, my); // 也可拖到序列插入
        return true;
    }

    private boolean clickSequence(double mx, double my) {
        if (mx < rightX || mx >= rightX + rightWidth || my < LIST_TOP || my >= listBottom) return false;
        pageListFocused = false;
        if (editingPage <= 0) return true;
        int slot = sequenceSlotAt(mx, my);
        if (slot < 0) return true;
        if (slot >= draftIotas.size()) {
            // 点末尾空格 → 绘制追加（封存页不显示空格，也就走不到这里）
            clearSelection();
            menuOpen = false;
            openDrawGui(-1);
            return true;
        }
        // 已填充：单击选中 / Ctrl 多选 / Shift 范围选；随后可拖拽重排（多选整块移动）
        if (Screen.hasShiftDown() && selectionAnchor >= 0) {
            selectedSlots.clear();
            int lo = Math.min(selectionAnchor, slot);
            int hi = Math.max(selectionAnchor, slot);
            for (int i = lo; i <= hi && i < draftIotas.size(); i++) selectedSlots.add(i);
        } else if (Screen.hasControlDown()) {
            if (!selectedSlots.remove(slot)) selectedSlots.add(slot);
            selectionAnchor = slot;
        } else {
            selectedSlots.clear();
            selectedSlots.add(slot);
            selectionAnchor = slot;
        }
        beginPendingDrag(DRAG_SEQUENCE, slot, mx, my);
        menuOpen = false;
        return true;
    }

    private boolean clickSequenceRight(double mx, double my) {
        if (mx < rightX || mx >= rightX + rightWidth || my < LIST_TOP || my >= listBottom) return false;
        if (editingPage <= 0) return false;
        int slot = sequenceSlotAt(mx, my);
        if (slot < 0) return false;
        if (!Screen.hasControlDown() && !Screen.hasShiftDown()) {
            selectedSlots.clear();
            if (slot < draftIotas.size()) selectedSlots.add(slot);
            selectionAnchor = slot;
        } else if (slot < draftIotas.size() && !selectedSlots.contains(slot)) {
            selectedSlots.add(slot);
        }
        menuSlot = slot;
        menuX = (int) mx;
        menuY = (int) my;
        int menuHeight = menuHeight();
        if (menuX + MENU_WIDTH > width) menuX = width - MENU_WIDTH - 2;
        if (menuY + menuHeight > height) menuY = height - menuHeight - 4;
        menuOpen = true;
        return true;
    }

    private int menuHeight() {
        return MENU_ITEM_HEIGHT * MENU_ITEMS + 2;
    }

    private boolean menuItemEnabled(int item) {
        boolean editable = canEditContent();
        boolean hasSelection = !selectedSlots.isEmpty();
        return switch (item) {
            case 0 -> editable && hasSelection;                        // 移除
            case 1 -> hasSelection;                                    // 复制（封存页也允许）
            case 2 -> editable && !patternClipboard.isEmpty();          // 粘贴
            case 3 -> editable && hasSelection;                        // 重绘覆盖
            case 4 -> editable;                                        // 向后绘制
            case 5 -> editable;                                        // 施法采集
            default -> false;
        };
    }

    private boolean clickSequenceMenu(double mx, double my) {
        if (mx < menuX || mx >= menuX + MENU_WIDTH || my < menuY || my >= menuY + menuHeight()) return false;
        int item = (int) ((my - menuY - 1) / MENU_ITEM_HEIGHT);
        if (item < 0 || item >= MENU_ITEMS) {
            menuOpen = false;
            return true;
        }
        menuOpen = false;
        if (!menuItemEnabled(item)) return true;
        switch (item) {
            case 0 -> removeSelectedSlots();
            case 1 -> copyPattern();
            case 2 -> pasteClipboard();
            case 3 -> openDrawGui(menuSlot);                       // 重绘覆盖：用右键那一格（修 B13）
            case 4 -> openDrawInsertAfter(menuSlot);                // 向后绘制
            case 5 -> openCastScreen();                             // 真实施法采集栈
            default -> { }
        }
        return true;
    }

    /** 右键菜单（样式改编自 CyberStaff 的 drawSequenceMenu，致谢 Aurover）。z=600 抬高图层遮挡按键。 */
    private void drawSequenceMenu(GuiGraphics g, double mouseX, double mouseY) {
        int mx = (int) mouseX, my = (int) mouseY;
        int h = menuHeight();
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 600.0F);
        try {
            g.fill(menuX - 1, menuY - 1, menuX + MENU_WIDTH + 1, menuY + h + 1, 0xEE080A0D);
            g.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + h, 0xF022272D);
            String[] keys = {"almightly_staff.gui.remove", "almightly_staff.gui.copy_pattern",
                "almightly_staff.gui.paste_pattern", "almightly_staff.gui.redraw",
                "almightly_staff.gui.draw_backward", "almightly_staff.gui.cast_collect"};
            for (int i = 0; i < MENU_ITEMS; i++) {
                int y = menuY + 1 + i * MENU_ITEM_HEIGHT;
                boolean hover = mx >= menuX && mx < menuX + MENU_WIDTH && my >= y && my < y + MENU_ITEM_HEIGHT;
                boolean enabled = menuItemEnabled(i);
                if (hover && enabled) g.fill(menuX, y, menuX + MENU_WIDTH, y + MENU_ITEM_HEIGHT, 0xAA60457B);
                g.drawString(font, Component.translatable(keys[i]), menuX + 4, y + 3,
                    enabled ? 0xFFFFFFFF : 0x66777777);
            }
        } finally {
            ps.popPose();
        }
    }

    // ==================== 复制 / 粘贴 / 剪切 ====================

    private void copyPattern() {
        patternClipboard.clear();
        List<Integer> sorted = new ArrayList<>(selectedSlots);
        Collections.sort(sorted);
        for (int idx : sorted) {
            if (idx >= 0 && idx < draftIotas.size()) {
                CompoundTag tag = draftIotas.get(idx);
                if (tag != null) patternClipboard.add(tag.copy()); // 所有 iota 类型均可复制
            }
        }
    }

    /**
     * 粘贴（Q2）：有选中格 → 删掉所有选中格，在最小的选中位置插入剪贴板内容；
     * 没有选中 → 追加到末尾。覆盖范围只由选中决定，不会顺手覆盖后面的格子（修 B18）。
     */
    private void pasteClipboard() {
        if (!canEditContent() || patternClipboard.isEmpty()) return;
        pushUndo();
        int insertAt;
        if (!selectedSlots.isEmpty()) {
            List<Integer> sorted = new ArrayList<>(selectedSlots);
            insertAt = Collections.min(sorted);
            sorted.sort(Collections.reverseOrder());
            for (int idx : sorted) if (idx >= 0 && idx < draftIotas.size()) draftIotas.remove(idx);
            insertAt = Math.min(insertAt, draftIotas.size());
        } else {
            insertAt = draftIotas.size();
        }
        clearSelection();
        int cursor = insertAt;
        for (CompoundTag tag : patternClipboard) {
            if (tag == null) continue;
            draftIotas.add(cursor, tag.copy());
            selectedSlots.add(cursor);
            cursor++;
        }
        selectionAnchor = insertAt;
        saveDraftNow();
    }

    private void cutPattern() {
        if (!canEditContent() || selectedSlots.isEmpty()) return;
        copyPattern();
        removeSelectedSlots();
    }

    /** 移除所有选中格。 */
    private void removeSelectedSlots() {
        if (!canEditContent() || selectedSlots.isEmpty()) return;
        pushUndo();
        List<Integer> sorted = new ArrayList<>(selectedSlots);
        sorted.sort(Collections.reverseOrder());
        for (int idx : sorted) {
            if (idx >= 0 && idx < draftIotas.size()) draftIotas.remove(idx);
        }
        clearSelection();
        saveDraftNow();
    }

    /** 把选中格整块搬到 dropIndex 之前（多选也能整体移动，向右移一格不再失效 —— 修 B12）。 */
    private void moveSelectedBlock(int dropIndex) {
        if (!canEditContent() || selectedSlots.isEmpty()) return;
        List<Integer> sorted = new ArrayList<>(selectedSlots);
        Collections.sort(sorted);
        List<CompoundTag> moved = new ArrayList<>();
        for (int idx : sorted) if (idx >= 0 && idx < draftIotas.size()) moved.add(draftIotas.get(idx));
        if (moved.isEmpty()) return;

        int insertAt = 0;
        for (int i = 0; i < dropIndex && i < draftIotas.size(); i++) {
            if (!selectedSlots.contains(i)) insertAt++;
        }
        pushUndo();
        List<Integer> descending = new ArrayList<>(sorted);
        descending.sort(Collections.reverseOrder());
        for (int idx : descending) if (idx >= 0 && idx < draftIotas.size()) draftIotas.remove(idx);
        insertAt = Mth.clamp(insertAt, 0, draftIotas.size());
        draftIotas.addAll(insertAt, moved);
        clearSelection();
        for (int i = 0; i < moved.size(); i++) selectedSlots.add(insertAt + i);
        selectionAnchor = insertAt;
        if (sameSequence(undoHistory.peekFirst(), draftIotas)) {
            undoHistory.removeFirst(); // 原地放下：不留无意义的撤销记录
            return;
        }
        saveDraftNow();
    }

    private void clearSelection() {
        selectedSlots.clear();
        selectionAnchor = -1;
    }

    // ==================== 滚动 ====================

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int step = (int) Math.signum(delta);
        if (mx >= leftX && mx < leftX + leftWidth) {
            int max = Math.max(0, pages.size() - visiblePageRows());
            spellScroll = Mth.clamp(spellScroll - step, 0, max);
            return true;
        }
        if (mx >= middleX && mx < middleX + middleWidth) {
            int visible = Math.max(1, (listBottom - LIST_TOP) / ROW_HEIGHT);
            int max = Math.max(0, filteredCatalog.size() - visible);
            catalogScroll = Mth.clamp(catalogScroll - step, 0, max);
            return true;
        }
        if (mx >= rightX && mx < rightX + rightWidth) {
            SeqLayout layout = computeSeqLayout();
            int max = Math.max(0, layout.totalRows() - layout.rowsPerView());
            sequenceScroll = Mth.clamp(sequenceScroll - step, 0, max);
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    // ==================== 键盘 ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean typing = (nameField != null && nameField.isFocused()) || (searchField != null && searchField.isFocused());
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (menuOpen) { menuOpen = false; return true; }
            if (activeDragType != DRAG_NONE || pendingDragType != DRAG_NONE) { clearDrag(); return true; }
            if (typing) { flushRename(); this.setFocused((GuiEventListener) null); return true; }
            onClose();
            return true;
        }
        if (typing) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                flushRename();
                this.setFocused((GuiEventListener) null);
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (keyCode == GLFW.GLFW_KEY_P) { enterParseMode(); return true; }
        if (keyCode == GLFW.GLFW_KEY_F2) { beginRename(); return true; }
        if (ctrl && keyCode == GLFW.GLFW_KEY_C) { copyPattern(); return true; }
        if (ctrl && keyCode == GLFW.GLFW_KEY_V) { pasteClipboard(); return true; }
        if (ctrl && keyCode == GLFW.GLFW_KEY_X) { cutPattern(); return true; }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
            if (shift) redoSequenceChange(); else undoSequenceChange();
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_Y) { redoSequenceChange(); return true; }
        if (ctrl && keyCode == GLFW.GLFW_KEY_S) { flushRename(); saveDraftNow(); return true; }
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!selectedSlots.isEmpty()) removeSelectedSlots();
            else if (pageListFocused) requestDelete();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ==================== 拖拽（pendingDrag → activeDrag → finishDrag） ====================

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && pendingDragType != DRAG_NONE && pendingDragIndex >= 0
            && Math.hypot(mx - dragStartX, my - dragStartY) >= DRAG_THRESHOLD) {
            activeDragType = pendingDragType;
            activeDragIndex = pendingDragIndex;
            pendingDragType = DRAG_NONE;
        }
        if (button == 0 && activeDragType != DRAG_NONE) {
            dragMouseX = mx;
            dragMouseY = my;
            autoScrollDuringDrag(mx, my);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && activeDragType != DRAG_NONE) {
            finishDrag(mx, my);
            clearDrag();
            return true;
        }
        if (button == 0) clearDrag();
        return super.mouseReleased(mx, my, button);
    }

    private void beginPendingDrag(int type, int index, double mx, double my) {
        pendingDragType = type;
        pendingDragIndex = index;
        dragStartX = mx;
        dragStartY = my;
        dragMouseX = mx;
        dragMouseY = my;
    }

    private void clearDrag() {
        pendingDragType = DRAG_NONE;
        pendingDragIndex = -1;
        activeDragType = DRAG_NONE;
        activeDragIndex = -1;
    }

    private void finishDrag(double mx, double my) {
        if (activeDragType == DRAG_PAGE) {
            finishPageDrag(mx, my);
            return;
        }
        boolean inSequence = mx >= rightX && mx < rightX + rightWidth && my >= LIST_TOP && my < listBottom;
        if (activeDragType == DRAG_CATALOG && inSequence
            && activeDragIndex >= 0 && activeDragIndex < filteredCatalog.size()) {
            if (!canEditContent()) return;
            PatternCatalogEntry entry = filteredCatalog.get(activeDragIndex);
            if (entry.pattern().signature().isBlank()) {
                requestGreatSpellAutoParse(entry.pattern().actionId(), sequenceInsertionIndexAt(mx, my));
                return;
            }
            int target = Mth.clamp(sequenceInsertionIndexAt(mx, my), 0, draftIotas.size());
            pushUndo();
            draftIotas.add(target, IotaType.serialize(new PatternIota(StaffHex.patternFor(entry.pattern()))));
            clearSelection();
            selectedSlots.add(target);
            selectionAnchor = target;
            saveDraftNow();
        } else if (activeDragType == DRAG_SEQUENCE && inSequence) {
            moveSelectedBlock(sequenceInsertionIndexAt(mx, my));
        }
    }

    private void autoScrollDuringDrag(double mx, double my) {
        if (activeDragType == DRAG_PAGE) {
            int visible = visiblePageRows();
            int max = Math.max(0, pages.size() - visible);
            if (my < LIST_TOP + 6 && my > 36) spellScroll = Mth.clamp(spellScroll - 1, 0, max);
            else if (my > listBottom - 6 && my < listBottom + 12) spellScroll = Mth.clamp(spellScroll + 1, 0, max);
            return;
        }
        if (my < LIST_TOP || my >= listBottom) return;
        if (mx >= rightX && mx < rightX + rightWidth) {
            SeqLayout layout = computeSeqLayout();
            int max = Math.max(0, layout.totalRows() - layout.rowsPerView());
            int edge = layout.step();
            if (my < LIST_TOP + edge) sequenceScroll = Mth.clamp(sequenceScroll - 1, 0, max);
            else if (my > listBottom - edge) sequenceScroll = Mth.clamp(sequenceScroll + 1, 0, max);
        }
    }

    /** 页拖拽幽灵：跟着鼠标的整行标签。 */
    private void drawPageDragFeedback(GuiGraphics g) {
        if (activeDragType != DRAG_PAGE) return;
        ModNetworking.PageData page = findPage(activeDragIndex);
        if (page == null) return;
        int x = (int) dragMouseX, y = (int) dragMouseY;
        String label = page.name().isEmpty()
            ? Component.translatable("almightly_staff.gui.page_label", page.pageIndex()).getString()
            : "#" + page.pageIndex() + " " + page.name();
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 500.0F);
        try {
            g.fill(x - 2, y - 2, x + Math.min(leftWidth, font.width(label) + 10), y + 16, 0xDD493662);
            g.drawString(font, trimText(label, Math.min(leftWidth, font.width(label) + 6)), x + 1, y + 2, 0xFFFFFFFF);
        } finally {
            ps.popPose();
        }
    }

    /** 目录/序列拖拽幽灵。 */
    private void drawSequenceDragFeedback(GuiGraphics g) {
        if (activeDragType == DRAG_NONE || activeDragType == DRAG_PAGE) return;
        int x = (int) dragMouseX, y = (int) dragMouseY;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 500.0F);
        try {
            if (activeDragType == DRAG_CATALOG && activeDragIndex >= 0 && activeDragIndex < filteredCatalog.size()) {
                String name = filteredCatalog.get(activeDragIndex).displayName().getString();
                g.fill(x - 2, y - 2, x + 46, y + 18, 0xAA315E5B);
                g.drawString(font, trimText(name, 42), x + 2, y + 2, 0xFFFFFFFF);
            } else if (activeDragType == DRAG_SEQUENCE && activeDragIndex >= 0 && activeDragIndex < draftIotas.size()) {
                // 幽灵直接画出被拖的图案
                CompoundTag tag = draftIotas.get(activeDragIndex);
                g.fill(x - 2, y - 2, x + SEQUENCE_SLOT_SIZE + 2, y + SEQUENCE_SLOT_SIZE + 2, 0xCC20252B);
                if (tag != null) renderSlotIota(g, tag, x, y, SEQUENCE_SLOT_SIZE);
            }
        } finally {
            ps.popPose();
        }
    }

    // ==================== 卓越法术（per-world） ====================

    private void requestGreatSpellAutoParse(String actionId, int targetSlot) {
        if (!canEditContent()) return;
        pendingGreatSpellAction = actionId;
        pendingGreatSpellSlot = targetSlot;
        pendingGreatSpellPage = editingPage;
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffGreatSpellCheckC2S(actionId, targetSlot));
    }

    /** 服务端回传：已学习则返回真实签名/方向，可添加；否则拒绝。 */
    @Override
    public void onGreatSpellCheck(String actionId, boolean usable, String signature, String startDir) {
        String pendingAction = pendingGreatSpellAction;
        int slot = pendingGreatSpellSlot;
        int page = pendingGreatSpellPage;
        pendingGreatSpellAction = null;
        pendingGreatSpellSlot = -1;
        pendingGreatSpellPage = 0;
        if (pendingAction == null || !canEditContent()) return;
        if (page != editingPage) return; // 回包时用户已切页，丢弃
        if (!usable || signature == null || signature.isEmpty() || startDir == null || startDir.isEmpty()) {
            showStatus(Component.translatable("almightly_staff.gui.great_spell_unusable").getString());
            return;
        }
        PatternRef learned = new PatternRef(actionId, startDir, signature);
        learnedPerWorld.put(actionId, learned);
        applyLearnedToCatalog();
        refreshFilter();
        pushUndo();
        CompoundTag toAdd = IotaType.serialize(new PatternIota(StaffHex.patternFor(learned)));
        if (slot >= 0 && slot < draftIotas.size()) draftIotas.add(slot, toAdd);
        else draftIotas.add(toAdd);
        showStatus(Component.translatable("almightly_staff.gui.great_spell_added").getString());
        saveDraftNow();
    }

    // ==================== 真实施法采集栈 ====================

    /** 打开真实施法子界面（消耗法杖媒质）；退出时自动采集施法栈到序列。 */
    private void openCastScreen() {
        if (!canEditContent()) return;
        flushRename();
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffCastClearStackC2S());
        minecraft.setScreen(new StaffCastScreen(this, this::onStackCollected));
    }

    /** 施法栈回传：插入到右键打开菜单的那一格（而非末尾）并保存。 */
    public void onStackCollected(List<CompoundTag> tags) {
        if (!canEditContent() || tags == null || tags.isEmpty()) return;
        pushUndo();
        int insertAt = menuSlot >= 0 ? Mth.clamp(menuSlot, 0, draftIotas.size()) : draftIotas.size();
        int idx = insertAt;
        for (CompoundTag tag : tags) if (tag != null) draftIotas.add(idx++, tag.copy());
        clearSelection();
        for (int i = insertAt; i < idx; i++) selectedSlots.add(i);
        selectionAnchor = insertAt;
        saveDraftNow();
        showStatus(Component.translatable("almightly_staff.gui.stack_collected", tags.size()).getString());
    }

    /** 绘制或解析出的图案若是 per-world 卓越法术，记住其真实签名。 */
    private void learnDrawnGreatSpell(PatternRef ref) {
        if (ref.actionId().isEmpty() || ref.signature().isBlank()) return;
        if (StaffHex.isPerWorldAction(ref.actionId())) {
            learnedPerWorld.put(ref.actionId(), ref);
            applyLearnedToCatalog();
            refreshFilter();
        }
    }

    /** 将已学习的卓越法术替换目录占位，使其可直接添加。 */
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

    private void showStatus(String text) {
        statusText = text;
        statusTimer = 80;
    }

    private void clampScrolls() {
        spellScroll = Mth.clamp(spellScroll, 0, Math.max(0, pages.size() - visiblePageRows()));
        int visible = Math.max(1, (listBottom - LIST_TOP) / ROW_HEIGHT);
        catalogScroll = Mth.clamp(catalogScroll, 0, Math.max(0, filteredCatalog.size() - visible));
        SeqLayout layout = computeSeqLayout();
        sequenceScroll = Mth.clamp(sequenceScroll, 0, Math.max(0, layout.totalRows() - layout.rowsPerView()));
    }

    @Override
    public void onClose() {
        flushRename();
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
