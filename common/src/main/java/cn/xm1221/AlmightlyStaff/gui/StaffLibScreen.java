package cn.xm1221.AlmightlyStaff.gui;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.common.lib.hex.HexIotaTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.locale.Language;
import net.minecraft.nbt.CompoundTag;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import cn.xm1221.AlmightlyStaff.spell.PatternCatalogEntry;
import cn.xm1221.AlmightlyStaff.spell.PatternRef;
import cn.xm1221.AlmightlyStaff.spell.StaffHex;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * 万法之杖法术库主界面（三栏布局，移植自 Hex CyberStaff 的 SpellLibraryScreen）。
 * 左：页列表（法术）；中：图案目录（搜索）；右：当前法术序列编辑。
 * 点击空格子/已填充格子 → 弹出浮动绘制 GUI；保留复制/粘贴/拖拽/撤销；
 * Parse 按钮进入文本模式（服务端 HexParse 转换）。
 *
 * 特别感谢 Aurover —— Hex CyberStaff 的作者。本界面的三栏布局、配色、
 * 图案槽位样式、右键菜单、滚动条等视觉与交互设计均改编自 CyberStaff
 * （https://github.com/Aurover/Hex-CyberStaff），向 ta 致敬。
 */
public class StaffLibScreen extends Screen {
    private static final int MARGIN = 8;
    private static final int GAP = 6;
    private static final int LIST_TOP = 70;
    private static final int ROW_HEIGHT = 20;
    private static final int FOOTER_HEIGHT = 52;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_THUMB_HEIGHT = 16;
    private static final int PATTERN_PREVIEW_SIZE = 78;
    private static final int SEQUENCE_COLUMNS = 10;
    private static final int SEQUENCE_SLOT_SIZE = 18;
    private static final int MAX_UNDO_HISTORY = 64;
    private static final int DRAG_THRESHOLD = 4;
    private static final int MENU_WIDTH = 96;
    private static final int MENU_ITEM_HEIGHT = 16;

    private List<ModNetworking.PageData> pages = new ArrayList<>();
    private int selectedPageIndex = -1;
    private ModNetworking.PageData draft;   // 当前编辑的页快照
    private final ArrayDeque<List<CompoundTag>> sequenceUndoHistory = new ArrayDeque<>();

    private List<PatternCatalogEntry> catalog = new ArrayList<>();
    private List<PatternCatalogEntry> filteredCatalog = new ArrayList<>();
    private int selectedCatalogIndex = -1;
    private final Set<Integer> selectedSlots = new LinkedHashSet<>();
    private int selectionAnchor = -1;
    private List<CompoundTag> patternClipboard = new ArrayList<>();

    private static final int DRAG_NONE = -1, DRAG_CATALOG = 0, DRAG_SEQUENCE = 1;
    private int pendingDragType = DRAG_NONE, pendingDragIndex = -1;
    private int activeDragType = DRAG_NONE, activeDragIndex = -1;
    private double dragStartX, dragStartY, dragMouseX, dragMouseY;

    private int leftX, leftWidth, middleX, middleWidth, rightX, rightWidth, listBottom;
    private int spellScroll, catalogScroll, sequenceScroll;

    private EditBox nameField;
    private EditBox searchField;

    private int dragStartSlot = -1;
    private boolean dragMoved;
    private boolean menuOpen;
    private int menuX, menuY;
    private int menuSlot = -1; // 右键点击的目标格（可等于 iotas.size() 表示末尾空格）
    private final Map<String, PatternRef> learnedPerWorld = new HashMap<>();
    private String statusText = "";
    private int statusTimer = 0;
    private int nameSaveTimer = 0;
    private String pendingGreatSpellAction = null;
    private int pendingGreatSpellSlot = -1; // -1=追加, >=0=插入该格

    public StaffLibScreen() {
        super(Component.translatable("almightly_staff.gui.title"));
    }

    // ==================== 网络同步 ====================
    private final List<ModNetworking.PageData> pendingSyncPages = new ArrayList<>();

    /** 同步入口。incremental=true 表示编辑后的增量回发（只带受影响页），按 pageIndex 合并；否则为全量分块同步。 */
    public void onSyncChunk(List<ModNetworking.PageData> chunkPages, int selectedPage, boolean lastChunk, boolean incremental) {
        if (incremental) { onIncrementalSync(chunkPages, selectedPage); return; }
        pendingSyncPages.addAll(chunkPages);
        if (!lastChunk) return;
        List<ModNetworking.PageData> all = new ArrayList<>(pendingSyncPages);
        pendingSyncPages.clear();
        onSync(all, selectedPage);
    }

    /**
     * 增量同步：只把受影响页按 pageIndex 合并进现有列表（不整表替换、不打断当前编辑草稿）。
     * 受影响的页若 iota 与名字都为空 = 该页已被删除 → 从列表移除。
     * 空 pages 表示仅选中变化 → 此时再做"切换当前编辑页"。
     */
    private void onIncrementalSync(List<ModNetworking.PageData> updatedPages, int selectedPage) {
        boolean removedDraft = false;
        for (ModNetworking.PageData u : updatedPages) {
            boolean remove = u.iotas().isEmpty() && u.name().isEmpty();
            int found = -1;
            for (int i = 0; i < pages.size(); i++) if (pages.get(i).pageIndex() == u.pageIndex()) { found = i; break; }
            if (remove) {
                if (found >= 0) {
                    pages.remove(found);
                    if (draft != null && u.pageIndex() == draft.pageIndex()) removedDraft = true;
                }
            } else if (found >= 0) {
                pages.set(found, u);
            } else {
                pages.add(u);
                pages.sort(Comparator.comparingInt(ModNetworking.PageData::pageIndex));
            }
        }
        // 刷新左侧高亮（选中页）
        selectedPageIndex = -1;
        for (int i = 0; i < pages.size(); i++) if (pages.get(i).pageIndex() == selectedPage) { selectedPageIndex = i; break; }

        // 被编辑的草稿页被删掉：清空编辑器（镜像全量同步的旧行为）
        if (removedDraft) {
            setDraft(selectedPageIndex >= 0 ? pages.get(selectedPageIndex) : null);
            return;
        }
        // 纯选中变化（没带任何页数据）→ 做"切换当前编辑页"
        if (updatedPages.isEmpty()) {
            if (selectedPageIndex >= 0) {
                ModNetworking.PageData sp = pages.get(selectedPageIndex);
                if (draft == null || draft.pageIndex() != sp.pageIndex()) setDraft(sp);
                else if (nameField != null && !nameField.isFocused()) nameField.setValue(sp.name());
            } else {
                setDraft(null);
            }
            return;
        }
        // 内容更新：若恰好是正在编辑的那页，只刷新名字，保留草稿与撤销栈
        if (draft != null) {
            for (ModNetworking.PageData u : updatedPages) {
                if (u.pageIndex() == draft.pageIndex()) {
                    if (nameField != null && !nameField.isFocused()) nameField.setValue(u.name());
                }
            }
        }
    }

    public void onSync(List<ModNetworking.PageData> syncedPages, int selectedPage) {
        this.pages = new ArrayList<>(syncedPages);
        this.selectedPageIndex = -1;
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).pageIndex() == selectedPage) { selectedPageIndex = i; break; }
        }
        if (selectedPageIndex >= 0 && selectedPageIndex < pages.size()) {
            ModNetworking.PageData sp = pages.get(selectedPageIndex);
            // 自动保存回包与当前页相同时：保留草稿/选择/撤销栈，仅同步名称（避免打断编辑）
            if (draft != null && draft.pageIndex() == sp.pageIndex()) {
                if (nameField != null && !nameField.isFocused()) nameField.setValue(sp.name());
            } else {
                setDraft(sp);
            }
        } else {
            setDraft(null);
        }
    }

    private void setDraft(ModNetworking.PageData page) {
        if (page == null) {
            draft = new ModNetworking.PageData(0, "", new ArrayList<>());
        } else {
            draft = new ModNetworking.PageData(page.pageIndex(), page.name(), new ArrayList<>(page.iotas()));
        }
        if (nameField != null) nameField.setValue(draft.name());
        clearSelection();
        sequenceUndoHistory.clear();
    }

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

        nameField = new EditBox(font, rightX, 42, rightWidth, 20, Component.translatable("almightly_staff.gui.spell_name"));
        nameField.setMaxLength(80);
        nameField.setValue(draft == null ? "" : draft.name());
        nameField.setResponder(s -> nameSaveTimer = 20); // 停止输入 20 tick 后自动保存名称
        addRenderableWidget(nameField);

        searchField = new EditBox(font, middleX, 42, middleWidth, 20, Component.translatable("almightly_staff.gui.search"));
        searchField.setMaxLength(100);
        searchField.setResponder(ignored -> refreshFilter());
        addRenderableWidget(searchField);

        int firstButtonY = height - 48;
        int secondButtonY = height - 25;
        int leftGap = 3;
        int leftFirstWidth = Math.max(1, (leftWidth - leftGap) / 2);
        int leftSecondX = leftX + leftFirstWidth + leftGap;
        int leftSecondWidth = Math.max(1, leftX + leftWidth - leftSecondX);
        addButton("almightly_staff.gui.new", button -> newSpell(), leftX, firstButtonY, leftFirstWidth);
        addButton("almightly_staff.gui.copy", button -> copySpell(), leftSecondX, firstButtonY, leftSecondWidth);
        addButton("almightly_staff.gui.delete", button -> deleteSpell(), leftX, secondButtonY, leftFirstWidth);
        addButton("almightly_staff.gui.up", button -> moveSpell(-1), leftSecondX, secondButtonY, leftSecondWidth);

        int actionGap = 3;
        int middleFirstWidth = Math.max(1, (middleWidth - actionGap) / 2);
        int middleSecondX = middleX + middleFirstWidth + actionGap;
        int middleSecondWidth = Math.max(1, middleX + middleWidth - middleSecondX);
        addButton("almightly_staff.gui.add_pattern", button -> appendCatalogPattern(), middleX, firstButtonY, middleFirstWidth);
        addButton("almightly_staff.gui.remove_slot", button -> removeSelectedSlots(), middleSecondX, firstButtonY, middleSecondWidth);
        addButton("almightly_staff.gui.down", button -> moveSpell(1), middleX, secondButtonY, middleFirstWidth);
        addButton("almightly_staff.gui.select_page", button -> applyPageSelection(), middleSecondX, secondButtonY, middleSecondWidth);

        addButton("almightly_staff.gui.undo", button -> undoSequenceChange(), rightX, firstButtonY, rightWidth);
        int rightGap = 3;
        int rightHalf = Math.max(1, (rightWidth - rightGap) / 2);
        addButton("almightly_staff.gui.save", button -> saveDraft(), rightX, secondButtonY, rightHalf);
        addButton("almightly_staff.gui.parse", button -> enterParseMode(), rightX + rightHalf + rightGap, secondButtonY, rightWidth - rightHalf - rightGap);

        catalog = StaffHex.loadCatalog();
        applyLearnedToCatalog();
        refreshFilter();
        if (draft != null && draft.pageIndex() > 0) {
            nameField.setValue(draft.name());
        }
        clampAllScrolls();
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffLibReadC2S());
    }

    private void addButton(String labelKey, Button.OnPress action, int x, int y, int buttonWidth) {
        Button button = Button.builder(Component.translatable(labelKey), action)
            .bounds(x, y, buttonWidth, 20).build();
        addRenderableWidget(button);
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
        // top bar (CyberStaff style)
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
        drawDragFeedback(g);
        if (menuOpen) drawSequenceMenu(g, mouseX, mouseY);
        drawCatalogTooltip(g, mouseX, mouseY); // 最后画：tooltip 在最上层，不被面板/按钮遮挡
    }

    /** panel: heading above input fields (y=24), purple accent (y=36), right separator. */
    private void drawPanel(GuiGraphics g, int x, int panelWidth, Component heading) {
        g.fill(x, 36, x + panelWidth, 38, 0xFF9C6ADE);
        g.drawString(font, trim(heading, panelWidth), x, 24, 0xE9EDF1);
        g.fill(x + panelWidth + 3, 36, x + panelWidth + 4, listBottom, 0x553C444D);
    }

    private Component trim(Component text, int maxWidth) {
        String s = text.getString();
        while (font.width(s) > Math.max(8, maxWidth - 4) && s.length() > 1) {
            s = s.substring(0, s.length() - 1);
        }
        return Component.literal(s);
    }

    private void drawSpellList(GuiGraphics g, int mx, int my) {
        int top = LIST_TOP, bottom = listBottom;
        int contentRight = leftX + leftWidth - SCROLLBAR_WIDTH;
        g.enableScissor(leftX, top, contentRight, bottom);
        int visible = (bottom - top) / ROW_HEIGHT;
        int maxScroll = Math.max(0, pages.size() - visible);
        spellScroll = Math.max(0, Math.min(spellScroll, maxScroll));
        for (int i = 0; i < visible; i++) {
            int idx = i + spellScroll;
            if (idx >= pages.size()) break;
            int y = top + i * ROW_HEIGHT;
            boolean selected = idx == selectedPageIndex;
            int fill = selected ? 0xAA493662 : (i % 2 == 0 ? 0x331E252C : 0x332A3138);
            g.fill(leftX, y, contentRight, y + ROW_HEIGHT - 2, fill);
            if (selected) {
                g.fill(leftX, y, leftX + 3, y + ROW_HEIGHT - 2, 0xFFD7B5FF);
            }
            String label = pages.get(idx).name().isEmpty()
                ? Component.translatable("almightly_staff.gui.unnamed").getString()
                : pages.get(idx).name();
            g.drawString(font, trim(Component.literal(label), contentRight - leftX - 10),
                leftX + 6, y + 5, selected ? 0xE9D7FF : 0xE2E6EA);
        }
        g.disableScissor();
        drawScrollbar(g, leftX, leftWidth, pages.size(), spellScroll);
    }

    private void drawCatalog(GuiGraphics g, int mx, int my) {
        int top = LIST_TOP, bottom = listBottom;
        int contentRight = middleX + middleWidth - SCROLLBAR_WIDTH;
        g.enableScissor(middleX, top, contentRight, bottom);
        int visible = (bottom - top) / ROW_HEIGHT;
        int maxScroll = Math.max(0, filteredCatalog.size() - visible);
        catalogScroll = Math.max(0, Math.min(catalogScroll, maxScroll));
        for (int i = 0; i < visible; i++) {
            int idx = i + catalogScroll;
            if (idx >= filteredCatalog.size()) break;
            int y = top + i * ROW_HEIGHT;
            boolean selected = idx == selectedCatalogIndex;
            int fill = selected ? 0xAA315E5B : (i % 2 == 0 ? 0x331E252C : 0x332A3138);
            g.fill(middleX, y, contentRight, y + ROW_HEIGHT - 2, fill);
            g.drawString(font, trim(filteredCatalog.get(idx).displayName(), contentRight - middleX - 8),
                middleX + 6, y + 5, selected ? 0xD7FFED : 0xE2E6EA);
        }
        g.disableScissor();
        drawScrollbar(g, middleX, middleWidth, filteredCatalog.size(), catalogScroll);
    }

    private void drawSequence(GuiGraphics g, int mx, int my) {
        int top = LIST_TOP, bottom = listBottom;
        int contentRight = rightX + rightWidth - SCROLLBAR_WIDTH;
        g.enableScissor(rightX, top, contentRight, bottom);
        List<CompoundTag> iotas = draft == null ? List.of() : draft.iotas();
        int slotSize = SEQUENCE_SLOT_SIZE, slotGap = 4;
        int usable = contentRight - rightX - 8;
        int columns = Math.max(1, Math.min(SEQUENCE_COLUMNS, usable / (slotSize + slotGap)));
        int rowsPerView = Math.max(1, (bottom - top - 4) / (slotSize + slotGap));
        int totalRows = (iotas.size() + columns - 1) / columns + 1; // +1 empty slot row
        int maxScroll = Math.max(0, totalRows - rowsPerView);
        sequenceScroll = Math.max(0, Math.min(sequenceScroll, maxScroll));

        int gridWidth = columns * slotSize + (columns - 1) * slotGap;
        int startX = rightX + Math.max(0, (contentRight - rightX - gridWidth) / 2);
        int startY = top + 4;
        for (int row = sequenceScroll; row < sequenceScroll + rowsPerView && row < totalRows; row++) {
            for (int col = 0; col < columns; col++) {
                int slotIdx = row * columns + col;
                int x = startX + col * (slotSize + slotGap);
                int y = startY + (row - sequenceScroll) * (slotSize + slotGap);
                boolean occupied = slotIdx < iotas.size();
                boolean selected = occupied && selectedSlots.contains(slotIdx);
                boolean hover = mx >= x && mx < x + slotSize && my >= y && my < y + slotSize;
                if (occupied) {
                    drawSequenceSlot(g, x, y, slotSize, selected, hover);
                    renderSlotIota(g, iotas.get(slotIdx), x + 1, y + 1, slotSize - 2);
                } else {
                    // empty slot: click to draw
                    int border = hover ? 0xFFD7B5FF : 0xFF3A414A;
                    g.fill(x, y, x + slotSize, y + slotSize, border);
                    g.fill(x + 1, y + 1, x + slotSize - 1, y + slotSize - 1, hover ? 0xFF2C2C35 : 0xFF20252B);
                    if (hover && draft != null && draft.pageIndex() > 0) {
                        g.drawCenteredString(font, "+", x + slotSize / 2, y + slotSize / 2 - 4, 0xFF9C6ADE);
                    }
                }
            }
        }
        g.disableScissor();
        drawScrollbar(g, rightX, rightWidth, totalRows, sequenceScroll);
        // 悬停已填充格子：显示该 iota 的数值 tooltip
        if (draft != null && mx >= rightX && mx < rightX + rightWidth && my >= LIST_TOP && my < listBottom) {
            int hoverSlot = sequenceSlotAt(mx, my);
            if (hoverSlot >= 0 && hoverSlot < iotas.size()) {
                g.renderTooltip(font, IotaType.getDisplay(iotas.get(hoverSlot)), mx, my);
            }
        }
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

    private PatternCatalogEntry hoveredCatalogEntry(int mx, int my) {
        if (draft == null || my < LIST_TOP || my >= listBottom || mx < middleX || mx >= middleX + middleWidth) return null;
        int row = (my - LIST_TOP) / ROW_HEIGHT;
        int idx = catalogScroll + row;
        if (idx < 0 || idx >= filteredCatalog.size()) return null;
        return filteredCatalog.get(idx);
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
        // 数字常量：无手册页，直接显示数值
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
    private void drawCatalogTooltip(GuiGraphics g, int mx, int my) {
        if (menuOpen || activeDragType != DRAG_NONE || pendingDragType != DRAG_NONE) return;
        PatternCatalogEntry entry = hoveredCatalogEntry(mx, my);
        if (entry == null) return;
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
        // 框体（CyberStaff 风格）
        g.fill(tooltipX - 1, tooltipY - 1, tooltipX + tooltipWidth + 1, tooltipY + tooltipHeight + 1, 0xF0100010);
        g.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xF018111F);
        g.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + 1, 0xFF9C6ADE);
        g.fill(tooltipX, tooltipY + tooltipHeight - 1, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xFF4B2F68);
        g.fill(tooltipX, tooltipY, tooltipX + 1, tooltipY + tooltipHeight, 0xFF9C6ADE);
        g.fill(tooltipX + tooltipWidth - 1, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xFF4B2F68);
        // 图案预览
        int previewX = tooltipX + 8, previewY = tooltipY + 8;
        if (!pattern.signature().isBlank()) {
            PoseStack ps = g.pose();
            ps.pushPose();
            StaffHex.renderPattern(pattern, ps, previewX, previewY, previewSize);
            ps.popPose();
        }
        // 文本
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

    private void drawFooter(GuiGraphics g) {
        g.drawCenteredString(font, Component.translatable("almightly_staff.gui.cast_hint"), width / 2, height - 70, 0xFF777777);
        if (statusTimer > 0 && !statusText.isEmpty()) {
            g.drawCenteredString(font, Component.literal(statusText), width / 2, height - 92, 0xFFFFE3A3);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (statusTimer > 0) statusTimer--;
        if (nameSaveTimer > 0) {
            nameSaveTimer--;
            if (nameSaveTimer == 0) saveDraftNow(); // 改名自动保存
        }
    }

    /** CyberStaff scrollbar style: dark track + purple thumb. */
    private void drawScrollbar(GuiGraphics g, int x, int panelWidth, int total, int scroll) {
        int trackX = x + panelWidth - SCROLLBAR_WIDTH;
        g.fill(trackX, LIST_TOP, trackX + SCROLLBAR_WIDTH, listBottom, 0xAA11161B);
        int visible = Math.max(1, (listBottom - LIST_TOP) / ROW_HEIGHT);
        int maxScroll = Math.max(0, total - visible);
        if (maxScroll <= 0) return;
        int track = listBottom - LIST_TOP;
        int thumb = Math.max(MIN_THUMB_HEIGHT, track * visible / Math.max(1, total));
        int y = LIST_TOP + scroll * (track - thumb) / maxScroll;
        g.fill(trackX + 1, y, trackX + SCROLLBAR_WIDTH - 1, y + thumb, 0xFF9C6ADE);
    }

    // ==================== 页操作 ====================
    private int nextFreePageIndex() {
        boolean[] used = new boolean[65];
        for (ModNetworking.PageData p : pages) used[p.pageIndex()] = true;
        for (int i = 1; i <= 64; i++) if (!used[i]) return i;
        return 64;
    }

    private void newSpell() {
        int idx = nextFreePageIndex();
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageWriteC2S(idx,
            Component.translatable("almightly_staff.gui.unnamed").getString(), List.of()));
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageSelectC2S(idx));
    }

    private void copySpell() {
        if (draft == null || draft.pageIndex() == 0) return;
        int idx = nextFreePageIndex();
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageWriteC2S(idx,
            draft.name() + " " + Component.translatable("almightly_staff.gui.copy_suffix").getString(),
            draft.iotas()));
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageSelectC2S(idx));
    }

    private void deleteSpell() {
        if (draft == null || draft.pageIndex() == 0) return;
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageWriteC2S(draft.pageIndex(), "", List.of()));
    }

    private void moveSpell(int direction) {
        if (selectedPageIndex < 0 || selectedPageIndex >= pages.size()) return;
        int target = selectedPageIndex + direction;
        if (target < 0 || target >= pages.size()) return;
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageSwapC2S(
            pages.get(selectedPageIndex).pageIndex(), pages.get(target).pageIndex()));
    }

    private void applyPageSelection() {
        if (selectedPageIndex >= 0 && selectedPageIndex < pages.size()) {
            ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageSelectC2S(
                pages.get(selectedPageIndex).pageIndex()));
        }
    }

    // ==================== 序列操作 ====================
    private void saveDraft() {
        if (draft == null || draft.pageIndex() == 0) return;
        pushUndo();
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageWriteC2S(
            draft.pageIndex(), nameField.getValue().trim(), draft.iotas()));
    }

    private void appendCatalogPattern() {
        if (selectedCatalogIndex < 0 || selectedCatalogIndex >= filteredCatalog.size()) return;
        PatternCatalogEntry entry = filteredCatalog.get(selectedCatalogIndex);
        PatternRef ref = entry.pattern();
        if (ref.signature().isBlank()) {
            // 卓越法术：自动用 ParseCode（代码→图案）检验，能转换则添加
            requestGreatSpellAutoParse(entry.pattern().actionId(), -1);
            return;
        }
        pushUndo();
        draft.iotas().add(IotaType.serialize(new PatternIota(StaffHex.patternFor(ref))));
        saveDraftNow(); // 添加即保存
    }

    private void removeSelectedSlot() {
        if (selectedSlots.isEmpty()) return;
        removeSelectedSlots();
    }

    private void pushUndo() {
        if (draft == null) return;
        sequenceUndoHistory.addFirst(new ArrayList<>(draft.iotas()));
        while (sequenceUndoHistory.size() > MAX_UNDO_HISTORY) sequenceUndoHistory.removeLast();
    }

    private void undoSequenceChange() {
        if (draft == null || sequenceUndoHistory.isEmpty()) return;
        draft.iotas().clear();
        draft.iotas().addAll(sequenceUndoHistory.removeFirst());
        clearSelection();
        saveDraftNow(); // 撤销即保存
    }

    /** 绘制完成回调：填充或覆盖指定槽位，并立即保存到服务端。 */
    private void onPatternDrawn(int slotIndex, PatternRef ref) {
        if (draft == null) return;
        pushUndo();
        CompoundTag tag = IotaType.serialize(new PatternIota(StaffHex.patternFor(ref)));
        if (slotIndex >= 0 && slotIndex < draft.iotas().size()) {
            draft.iotas().set(slotIndex, tag); // 覆盖
        } else {
            draft.iotas().add(tag); // 追加到末尾空格
        }
        saveDraftNow();
        learnDrawnGreatSpell(ref);
    }

    /** 立即把当前草稿写入服务端（返回主界面后会重新同步）。 */
    private void saveDraftNow() {
        if (draft == null || draft.pageIndex() == 0) return;
        String name = nameField != null ? nameField.getValue().trim() : draft.name();
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageWriteC2S(
            draft.pageIndex(), name, draft.iotas()));
    }

    private void openDrawGui(int slotIndex) {
        minecraft.setScreen(new StaffDrawScreen(this, ref -> onPatternDrawn(slotIndex, ref)));
    }

    // ==================== Parse ====================
    /** 进入 Parse 文本模式：请求服务端把当前法术转为代码，回包后打开文本编辑器。 */
    private void enterParseMode() {
        if (draft == null) return;
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgParseToCodeC2S(ModNetworking.buildListTag(draft.iotas())));
    }

    public void onParseCode(String code) {
        minecraft.setScreen(new StaffParseScreen(this, code, parsed -> {
            if (parsed != null) {
                if (draft == null) return;
                // 代码→图案转换结果中若有卓越法术，学习其签名
                for (Iota i : parsed) {
                    if (i instanceof PatternIota pi) learnDrawnGreatSpell(StaffHex.refFor(pi.getPattern()));
                }
                pushUndo();
                draft.iotas().clear();
                for (Iota i : parsed) if (i != null) draft.iotas().add(IotaType.serialize(i));
                saveDraftNow(); // 解析结果立即写回服务端
            }
            minecraft.setScreen(this);
        }));
    }

    // ==================== 交互 ====================
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 菜单打开时优先处理菜单（可能与其他按钮重叠，菜单选项优先级更高）
        if (menuOpen) {
            if (clickSequenceMenu(mouseX, mouseY)) return true;
            menuOpen = false;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) return true;
        if (button == 0) {
            if (clickSpellList(mouseX, mouseY)) return true;
            if (clickCatalog(mouseX, mouseY)) return true;
            if (clickSequence(mouseX, mouseY)) return true;
        }
        if (button == 1) {
            if (clickSequenceRight(mouseX, mouseY)) return true;
        }
        return false;
    }

    private boolean clickSpellList(double mx, double my) {
        if (mx < leftX || mx >= leftX + leftWidth || my < LIST_TOP || my >= listBottom) return false;
        int row = (int) my - LIST_TOP;
        int idx = spellScroll + row / ROW_HEIGHT;
        if (idx >= 0 && idx < pages.size()) {
            selectedPageIndex = idx;
            ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageSelectC2S(pages.get(idx).pageIndex()));
        }
        return true;
    }

    private boolean clickCatalog(double mx, double my) {
        if (mx < middleX || mx >= middleX + middleWidth || my < LIST_TOP || my >= listBottom) return false;
        int row = (int) my - LIST_TOP;
        int idx = catalogScroll + row / ROW_HEIGHT;
        if (idx >= 0 && idx < filteredCatalog.size()) {
            selectedCatalogIndex = idx;
            beginPendingDrag(DRAG_CATALOG, idx, mx, my); // 可拖到右侧序列插入
        }
        return true;
    }

    private int sequenceSlotAt(double mx, double my) {
        if (draft == null) return -1;
        int slotWidth = SEQUENCE_SLOT_SIZE, slotGap = 4;
        int usable = rightWidth - SCROLLBAR_WIDTH - 8;
        int columns = Math.max(1, Math.min(SEQUENCE_COLUMNS, usable / (slotWidth + slotGap)));
        int startX = rightX + 4, startY = LIST_TOP + 4;
        int col = (int) ((mx - startX) / (slotWidth + slotGap));
        int row = (int) ((my - startY) / (slotWidth + slotGap)) + sequenceScroll;
        if (col < 0 || col >= columns) return -1;
        return row * columns + col;
    }

    private boolean clickSequence(double mx, double my) {
        if (mx < rightX || mx >= rightX + rightWidth || my < LIST_TOP || my >= listBottom) return false;
        if (draft == null || draft.pageIndex() == 0) return false;
        int slot = sequenceSlotAt(mx, my);
        if (slot < 0) return false;
        List<CompoundTag> iotas = draft.iotas();
        if (slot >= iotas.size()) {
            clearSelection();
            menuOpen = false;
            openDrawGui(-1); // 点击空格 → 绘制追加
            return true;
        }
        // 已填充：单击选中 / Ctrl多选 / Shift范围选 + 可拖拽重排
        boolean ctrl = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();
        if (shift && selectionAnchor >= 0) {
            selectedSlots.clear();
            int lo = Math.min(selectionAnchor, slot);
            int hi = Math.max(selectionAnchor, slot);
            for (int i = lo; i <= hi && i < iotas.size(); i++) selectedSlots.add(i);
        } else if (ctrl) {
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
        if (draft == null || draft.pageIndex() == 0) return false;
        int slot = sequenceSlotAt(mx, my);
        if (slot < 0) return false;
        // 右键：空格子也弹菜单；无修饰键则重置选中为该格
        if (!Screen.hasControlDown() && !Screen.hasShiftDown()) {
            selectedSlots.clear();
            if (slot < draft.iotas().size()) selectedSlots.add(slot);
            selectionAnchor = slot;
        } else if (slot < draft.iotas().size() && !selectedSlots.contains(slot)) {
            selectedSlots.add(slot);
        }
        menuSlot = slot;
        menuX = (int) mx;
        menuY = (int) my;
        if (menuX + MENU_WIDTH > width) menuX = width - MENU_WIDTH - 2;
        if (menuY + MENU_ITEM_HEIGHT * 5 + 2 > height) menuY = height - MENU_ITEM_HEIGHT * 5 - 4;
        menuOpen = true;
        return true;
    }

    private boolean clickSequenceMenu(double mx, double my) {
        if (mx < menuX || mx >= menuX + MENU_WIDTH || my < menuY || my >= menuY + MENU_ITEM_HEIGHT * 5 + 2) return false;
        int item = (int) ((my - menuY - 1) / MENU_ITEM_HEIGHT);
        if (item == 2 && patternClipboard.isEmpty()) { menuOpen = false; return true; }
        switch (item) {
            case 0 -> removeSelectedSlots();
            case 1 -> copyPattern();
            case 2 -> pastePatternAt(menuSlot);
            case 3 -> { int p = primarySelectedSlot(); if (p >= 0) openDrawGui(p); } // 重绘覆盖
            case 4 -> openCastScreen(); // 真实施法采集栈
            default -> { }
        }
        menuOpen = false;
        return true;
    }

    /** 右键菜单（样式改编自 CyberStaff 的 drawSequenceMenu，致谢 Aurover）。z=600 抬高图层遮挡按键。 */
    private void drawSequenceMenu(GuiGraphics g, int mx, int my) {
        int h = MENU_ITEM_HEIGHT * 5 + 2;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 600.0F);
        try {
            g.fill(menuX - 1, menuY - 1, menuX + MENU_WIDTH + 1, menuY + h + 1, 0xEE080A0D);
            g.fill(menuX, menuY, menuX + MENU_WIDTH, menuY + h, 0xF022272D);
            String[] keys = {"almightly_staff.gui.remove", "almightly_staff.gui.copy_pattern",
                "almightly_staff.gui.paste_pattern", "almightly_staff.gui.redraw",
                "almightly_staff.gui.cast_collect"};
            boolean hasSel = !selectedSlots.isEmpty();
            for (int i = 0; i < 5; i++) {
                int y = menuY + 1 + i * MENU_ITEM_HEIGHT;
                boolean hover = mx >= menuX && mx < menuX + MENU_WIDTH && my >= y && my < y + MENU_ITEM_HEIGHT;
                boolean enabled = switch (i) {
                    case 0 -> hasSel; // 移除选中格
                    case 1 -> hasSel; // 复制选中格
                    case 2 -> !patternClipboard.isEmpty();
                    case 3 -> hasSel; // 重绘选中格
                    case 4 -> draft != null && draft.pageIndex() > 0; // 真实施法采集栈
                    default -> true;
                };
                if (hover && enabled) g.fill(menuX, y, menuX + MENU_WIDTH, y + MENU_ITEM_HEIGHT, 0xAA60457B);
                g.drawString(font, Component.translatable(keys[i]), menuX + 4, y + 3,
                    enabled ? 0xFFFFFFFF : 0x66777777);
            }
        } finally {
            ps.popPose();
        }
    }

    /** 粘贴：目标格已填充 → 逐个覆盖；空格子/末尾 → 追加。 */
    private void pastePatternAt(int targetSlot) {
        if (draft == null || patternClipboard.isEmpty()) return;
        pushUndo();
        List<CompoundTag> iotas = draft.iotas();
        if (targetSlot >= 0 && targetSlot < iotas.size()) {
            int idx = targetSlot;
            for (CompoundTag t : patternClipboard) {
                CompoundTag c = t.copy();
                if (idx < iotas.size()) iotas.set(idx, c); else iotas.add(c);
                idx++;
            }
            clearSelection();
            for (int i = targetSlot; i < idx && i < iotas.size(); i++) selectedSlots.add(i);
            selectionAnchor = targetSlot;
        } else {
            int base = iotas.size();
            for (CompoundTag t : patternClipboard) iotas.add(t.copy());
            clearSelection();
            for (int i = base; i < iotas.size(); i++) selectedSlots.add(i);
            selectionAnchor = base;
        }
        saveDraftNow(); // 粘贴即保存
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mx >= leftX && mx < leftX + leftWidth) {
            spellScroll = Math.max(0, spellScroll - (int) Math.signum(delta));
            return true;
        }
        if (mx >= middleX && mx < middleX + middleWidth) {
            catalogScroll = Math.max(0, catalogScroll - (int) Math.signum(delta));
            return true;
        }
        if (mx >= rightX && mx < rightX + rightWidth) {
            sequenceScroll = Math.max(0, sequenceScroll - (int) Math.signum(delta));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (menuOpen) { menuOpen = false; return true; }
            onClose(); return true;
        }
        // 输入框聚焦时，快捷键交给输入框处理
        boolean typing = (nameField != null && nameField.isFocused()) || (searchField != null && searchField.isFocused());
        if (!typing) {
            if (keyCode == GLFW.GLFW_KEY_P) { enterParseMode(); return true; }
            if (keyCode == GLFW.GLFW_KEY_C && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { copyPattern(); return true; }
            if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { pastePattern(); return true; }
            if (keyCode == GLFW.GLFW_KEY_X && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { cutPattern(); return true; }
            if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) { removeSelectedSlots(); return true; }
            if (keyCode == GLFW.GLFW_KEY_Z && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { undoSequenceChange(); return true; }
            if (keyCode == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { saveDraft(); return true; }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ---- 拖拽（pendingDrag → activeDrag → finishDrag，参考 CyberStaff） ----

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button == 0 && pendingDragType != DRAG_NONE
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
        if (draft == null) return;
        List<CompoundTag> iotas = draft.iotas();
        boolean inSeq = mx >= rightX && mx < rightX + rightWidth && my >= LIST_TOP && my < listBottom;
        if (activeDragType == DRAG_CATALOG && inSeq
            && activeDragIndex >= 0 && activeDragIndex < filteredCatalog.size()) {
            PatternCatalogEntry entry = filteredCatalog.get(activeDragIndex);
            if (entry.pattern().signature().isBlank()) {
                // 卓越法术：拖入时自动 ParseCode 检验
                requestGreatSpellAutoParse(entry.pattern().actionId(), sequenceInsertionIndexAt(mx, my));
                return;
            }
            int target = Math.max(0, Math.min(sequenceInsertionIndexAt(mx, my), iotas.size()));
            pushUndo();
            iotas.add(target, IotaType.serialize(new PatternIota(StaffHex.patternFor(entry.pattern()))));
            clearSelection();
            if (target < iotas.size()) { selectedSlots.add(target); selectionAnchor = target; }
        } else if (activeDragType == DRAG_SEQUENCE && inSeq
            && activeDragIndex >= 0 && activeDragIndex < iotas.size()) {
            int target = Math.max(0, Math.min(sequenceInsertionIndexAt(mx, my), iotas.size()));
            pushUndo();
            CompoundTag moved = iotas.remove(activeDragIndex);
            if (target > activeDragIndex) target--;
            target = Math.max(0, Math.min(target, iotas.size()));
            iotas.add(target, moved);
            clearSelection();
            if (target < iotas.size()) { selectedSlots.add(target); selectionAnchor = target; }
        }
        saveDraftNow(); // 拖放结束即保存
    }

    private int sequenceInsertionIndexAt(double mx, double my) {
        if (draft == null) return 0;
        int slot = sequenceSlotAt(mx, my);
        if (slot < 0) return draft.iotas().size();
        return Math.max(0, Math.min(slot, draft.iotas().size()));
    }

    private void autoScrollDuringDrag(double mx, double my) {
        if (my < LIST_TOP || my >= listBottom) return;
        if (mx >= rightX && mx < rightX + rightWidth) {
            int slotSize = SEQUENCE_SLOT_SIZE + 4;
            if (my < LIST_TOP + slotSize) sequenceScroll = Math.max(0, sequenceScroll - 1);
            else if (my > listBottom - slotSize) sequenceScroll++;
        }
    }

    /** 拖拽幽灵（简化版：跟随鼠标的半透明标记） */
    private void drawDragFeedback(GuiGraphics g) {
        if (activeDragType == DRAG_NONE) return;
        int x = (int) dragMouseX, y = (int) dragMouseY;
        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 500.0F);
        try {
            if (activeDragType == DRAG_CATALOG && activeDragIndex >= 0 && activeDragIndex < filteredCatalog.size()) {
                String name = filteredCatalog.get(activeDragIndex).displayName().getString();
                g.fill(x - 2, y - 2, x + 46, y + 18, 0xAA315E5B);
                g.drawString(font, trim(Component.literal(name), 42), x + 2, y + 2, 0xFFFFFFFF);
            } else if (activeDragType == DRAG_SEQUENCE && draft != null
                && activeDragIndex >= 0 && activeDragIndex < draft.iotas().size()) {
                g.fill(x - 2, y - 2, x + 18, y + 18, 0xAA60457B);
                g.drawCenteredString(font, "+", x + 8, y + 2, 0xFFFFFFFF);
            }
        } finally {
            ps.popPose();
        }
    }

    // ---- 选择操作 ----

    private int primarySelectedSlot() {
        return selectedSlots.isEmpty() ? -1 : selectedSlots.iterator().next();
    }

    private void clearSelection() {
        selectedSlots.clear();
        selectionAnchor = -1;
    }

    private void copyPattern() {
        if (draft == null) return;
        patternClipboard.clear();
        for (int idx : selectedSlots) {
            if (idx >= 0 && idx < draft.iotas().size()) {
                CompoundTag t = draft.iotas().get(idx);
                if (t != null) patternClipboard.add(t.copy()); // 所有 iota 类型均可复制
            }
        }
    }

    private void pastePattern() {
        pastePatternAt(primarySelectedSlot()); // Ctrl+V：覆盖选中格或追加
    }

    private void cutPattern() {
        copyPattern();
        removeSelectedSlots();
    }

    /** 移除所有选中格。 */
    private void removeSelectedSlots() {
        if (draft == null || selectedSlots.isEmpty()) return;
        pushUndo();
        List<Integer> sorted = new ArrayList<>(selectedSlots);
        sorted.sort(Collections.reverseOrder());
        for (int idx : sorted) {
            if (idx >= 0 && idx < draft.iotas().size()) draft.iotas().remove(idx);
        }
        clearSelection();
        saveDraftNow(); // 删除即保存
    }

    // ---- 卓越法术（per-world）：服务端直接调 HexParse 检查（GreatPatternUnlocker）与图案获取（PatternMapper）----

    /** 卓越法术检查：发送 C2S，服务端直接调 HexParse 的检查（GreatPatternUnlocker）与图案获取（PatternMapper）。 */
    private void requestGreatSpellAutoParse(String actionId, int targetSlot) {
        if (draft == null) return;
        pendingGreatSpellAction = actionId;
        pendingGreatSpellSlot = targetSlot;
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffGreatSpellCheckC2S(actionId, targetSlot));
    }

    /** 服务端回传：已学习则返回真实签名/方向，可添加；否则拒绝。 */
    public void onGreatSpellCheck(String actionId, boolean usable, String signature, String startDir) {
        String pendingAction = pendingGreatSpellAction;
        int slot = pendingGreatSpellSlot;
        pendingGreatSpellAction = null;
        pendingGreatSpellSlot = -1;
        if (pendingAction == null || draft == null) return;
        if (!usable || signature == null || signature.isEmpty() || startDir == null || startDir.isEmpty()) {
            showStatus(Component.translatable("almightly_staff.gui.great_spell_unusable").getString());
            return;
        }
        // 学习 + 完成添加
        PatternRef learned = new PatternRef(actionId, startDir, signature);
        learnedPerWorld.put(actionId, learned);
        applyLearnedToCatalog();
        refreshFilter();
        pushUndo();
        CompoundTag toAdd = IotaType.serialize(new PatternIota(StaffHex.patternFor(learned)));
        List<CompoundTag> iotas = draft.iotas();
        if (slot >= 0 && slot < iotas.size()) iotas.add(slot, toAdd);
        else iotas.add(toAdd);
        showStatus(Component.translatable("almightly_staff.gui.great_spell_added").getString());
        saveDraftNow(); // 卓越法术添加即保存
    }

    // ==================== 真实施法采集栈 ====================

    /** 打开真实施法子界面（消耗法杖媒质）；先发包清空服务端施法栈，退出时自动采集施法栈到序列。 */
    private void openCastScreen() {
        if (draft == null || draft.pageIndex() == 0) return;
        // 打开前先清空服务端施法栈（与后续图案包同通道，保证先处理）
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffCastClearStackC2S());
        minecraft.setScreen(new StaffCastScreen(this, this::onStackCollected));
    }

    /** 施法栈回传：插入到右键打开菜单的那一格（而非末尾）并保存。 */
    public void onStackCollected(List<CompoundTag> tags) {
        if (draft == null || tags == null || tags.isEmpty()) return;
        pushUndo();
        List<CompoundTag> iotas = draft.iotas();
        int insertAt = Math.max(0, Math.min(menuSlot, iotas.size()));
        int idx = insertAt;
        for (CompoundTag t : tags) if (t != null) iotas.add(idx++, t.copy());
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
            PatternCatalogEntry e = catalog.get(i);
            if (e.kind() == PatternCatalogEntry.Kind.PER_WORLD) {
                PatternRef learned = learnedPerWorld.get(e.pattern().actionId());
                if (learned != null && !learned.signature().isBlank()) {
                    catalog.set(i, new PatternCatalogEntry(learned, e.displayName(), PatternCatalogEntry.Kind.NORMAL));
                }
            }
        }
    }

    private void showStatus(String text) {
        statusText = text;
        statusTimer = 80;
    }

    private void clampAllScrolls() {
        spellScroll = catalogScroll = sequenceScroll = 0;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
