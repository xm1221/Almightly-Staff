package cn.xm1221.AlmightlyStaff.network;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.utils.NBTHelper;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import at.petrak.hexcasting.common.lib.HexDataComponents;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 法杖页存储操作（1.21.1 DataComponents 与纯 CompoundTag 双层架构）。
 *
 * <p>核心不变量：
 * <ol>
 *   <li><b>按指定页号直接改页数据</b>，不借道 {@code ItemSpellbook.writeDatum}
 *       ——Hex 的 {@code getPage(stack, ifEmpty)} 在 {@code pages} 为空时会无视 {@code page_idx}，
 *       且 {@code writeDatum} 只写"当前页"、对封存页静默返回。</li>
 *   <li><b>页存在 = {@code pages} 里有该页号的 key</b>。空页存一个空 {@link ListIota}，
 *       所以"名字空 + 内容空"的页不会消失；旧存档里"只有名字没有 {@code pages} key"的页也视为存在。</li>
 *   <li><b>封存标记按页号读</b>（{@code sealed_pages}），不依赖"当前页"
 *       ——{@code ItemSpellbook.isSealed(stack)} 只看当前页，无法用于任意页校验。</li>
 *   <li><b>改当前页的名字要同步物品悬停名</b>：{@code ItemSpellbook.inventoryTick} 每 tick
 *       把悬停名写回"当前页"的页名（{@code page_names}），不同步就会被旧名字覆盖。</li>
 * </ol>
 *
 * <p>本类分两层：{@code CompoundTag} 纯函数层（不碰注册表、可单测）与 {@code ItemStack} 包装层（1.21.1 双向同步
 * {@code DataComponents.CUSTOM_DATA}、{@code DataComponents.CUSTOM_NAME} 与 {@code HexDataComponents}）。
 */
public final class StaffPages {
    public static final int MAX_PAGES = ItemSpellbook.MAX_PAGES;
    /** 没有页时的页号（{@code page_idx} 用 1 起算）。 */
    public static final int NO_PAGE = 0;

    private static final int MAX_NAME_LENGTH = 80;

    public enum Result { OK, SEALED, FULL, INVALID }

    /** {@link #reorder} 的结果：成功时带上被拖那一页的新页号。 */
    public record Reorder(Result result, int newIndex) {}

    private StaffPages() {
    }

    // ==================== CompoundTag 纯函数层 ====================

    private static CompoundTag child(CompoundTag root, String key) {
        if (root == null || !root.contains(key, Tag.TAG_COMPOUND)) {
            return null;
        }
        return root.getCompound(key);
    }

    private static CompoundTag ensureChild(CompoundTag root, String key) {
        CompoundTag existing = child(root, key);
        if (existing != null) {
            return existing;
        }
        CompoundTag created = new CompoundTag();
        root.put(key, created);
        return created;
    }

    private static String keyOf(int pageIndex) {
        return Integer.toString(pageIndex);
    }

    /** 页号是否落在 1..64。 */
    public static boolean validIndex(int pageIndex) {
        return pageIndex >= 1 && pageIndex <= MAX_PAGES;
    }

    /** 页是否存在：{@code pages} 有 key（空内容也算），或旧存档里只有名字。 */
    public static boolean exists(CompoundTag root, int pageIndex) {
        if (root == null || !validIndex(pageIndex)) {
            return false;
        }
        CompoundTag pages = child(root, KEY_PAGES);
        if (pages != null && pages.contains(keyOf(pageIndex), Tag.TAG_COMPOUND)) {
            return true;
        }
        CompoundTag names = child(root, KEY_PAGE_NAMES);
        return names != null && names.contains(keyOf(pageIndex), Tag.TAG_STRING);
    }

    /** 所有存在的页号，升序。 */
    public static List<Integer> indices(CompoundTag root) {
        List<Integer> out = new ArrayList<>();
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (exists(root, i)) {
                out.add(i);
            }
        }
        return out;
    }

    /** 所有存在且未封存的页号，升序（拖拽排序的可移动槽位集合）。 */
    public static List<Integer> movableIndices(CompoundTag root) {
        List<Integer> out = new ArrayList<>();
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (exists(root, i) && !isSealed(root, i)) {
                out.add(i);
            }
        }
        return out;
    }

    /** 最小空闲页号；1..64 全被占用时返回 -1。 */
    public static int lowestFree(CompoundTag root) {
        for (int i = 1; i <= MAX_PAGES; i++) {
            if (!exists(root, i)) {
                return i;
            }
        }
        return -1;
    }

    /** 该页是否封存（直接读 {@code sealed_pages[页号]}，与当前页无关）。 */
    public static boolean isSealed(CompoundTag root, int pageIndex) {
        CompoundTag sealed = child(root, KEY_SEALED);
        return sealed != null && sealed.getBoolean(keyOf(pageIndex));
    }

    /** 页名原始 JSON（{@code page_names} 里存的就是 Component 的 JSON）；没有则空串。 */
    public static String rawName(CompoundTag root, int pageIndex) {
        CompoundTag names = child(root, KEY_PAGE_NAMES);
        if (names == null) {
            return "";
        }
        return names.getString(keyOf(pageIndex));
    }

    /** 页名的纯文本；没有名字则空串。 */
    public static String name(CompoundTag root, int pageIndex) {
        String json = rawName(root, pageIndex);
        if (json.isEmpty()) {
            return "";
        }
        try {
            Component parsed = Component.Serializer.fromJson(json, RegistryAccess.EMPTY);
            return parsed == null ? "" : parsed.getString();
        } catch (Exception e) {
            return json;
        }
    }

    /** 页内容的原始序列化标签（{@code {type, data}}）；没有内容则 null。 */
    public static CompoundTag rawContent(CompoundTag root, int pageIndex) {
        CompoundTag pages = child(root, KEY_PAGES);
        if (pages == null || !pages.contains(keyOf(pageIndex), Tag.TAG_COMPOUND)) {
            return null;
        }
        return pages.getCompound(keyOf(pageIndex));
    }

    private static final String KEY_DATA = "hexcasting:data";
    private static final String KEY_PAGES = "pages";
    private static final String KEY_PAGE_NAMES = "page_names";
    private static final String KEY_SELECTED_PAGE = "page_idx";
    private static final String KEY_SEALED = "sealed_pages";
    public static final String KEY_STAFF_UUID = "staff_uuid";

    /** 读取法杖 UUID；缺失时返回空串。 */
    public static String getStaffUuid(CompoundTag root) {
        if (root == null || !root.contains(KEY_STAFF_UUID, Tag.TAG_STRING)) {
            return "";
        }
        return root.getString(KEY_STAFF_UUID);
    }

    /** 确保法杖持有稳定 UUID；缺失时写入并返回新生成的 UUID。 */
    public static String ensureStaffUuid(CompoundTag root) {
        if (root == null) {
            return "";
        }
        String existing = getStaffUuid(root);
        if (!existing.isEmpty()) {
            return existing;
        }
        String created = UUID.randomUUID().toString();
        root.putString(KEY_STAFF_UUID, created);
        return created;
    }

    /**
     * 页内容的逐元素标签。
     *
     * <p>页内容正常是一个 {@link ListIota}（{@code data} 是元素列表），此时直接取原始元素标签，
     * 不做反序列化；非列表 iota（例如旧消息写入的单图案）视为单元素，交给客户端展示。
     */
    public static List<CompoundTag> contentTags(CompoundTag root, int pageIndex) {
        List<CompoundTag> out = new ArrayList<>();
        CompoundTag content = rawContent(root, pageIndex);
        if (content == null) {
            return out;
        }
        Tag data = content.get(KEY_DATA);
        if (data instanceof ListTag list && isCompoundList(list)) {
            for (Tag element : list) {
                out.add(((CompoundTag) element).copy());
            }
        } else if (!content.isEmpty()) {
            out.add(content.copy());
        }
        return out;
    }

    private static boolean isCompoundList(ListTag list) {
        for (Tag element : list) {
            if (!(element instanceof CompoundTag)) {
                return false;
            }
        }
        return true;
    }

    /** 用 {@code template}（空 ListIota 标签）拼出一个 ListIota 序列化标签。 */
    public static CompoundTag listTagOf(List<CompoundTag> tags, CompoundTag template) {
        CompoundTag out = template == null ? new CompoundTag() : template.copy();
        ListTag items = new ListTag();
        if (tags != null) {
            for (CompoundTag tag : tags) {
                if (tag != null) {
                    items.add(tag.copy());
                }
            }
        }
        out.put(KEY_DATA, items);
        return out;
    }

    /** 空页的内容模板：序列化后的空 {@link ListIota}。 */
    public static CompoundTag emptyContent() {
        try {
            var encoded = IotaType.TYPED_CODEC.encodeStart(NbtOps.INSTANCE, new ListIota(new ArrayList<>())).result().orElse(null);
            if (encoded instanceof CompoundTag ct) {
                return ct;
            }
        } catch (Throwable ignored) {
        }
        CompoundTag fallback = new CompoundTag();
        fallback.putString("hexcasting:type", "hexcasting:list");
        fallback.put(KEY_DATA, new ListTag());
        return fallback;
    }

    public static void setContentTag(CompoundTag root, int pageIndex, CompoundTag content) {
        if (root == null || !validIndex(pageIndex)) {
            return;
        }
        ensureChild(root, KEY_PAGES).put(keyOf(pageIndex), content);
    }

    public static void setContentTags(CompoundTag root, int pageIndex, List<CompoundTag> tags, CompoundTag template) {
        setContentTag(root, pageIndex, listTagOf(tags, template));
    }

    /** 移除页内容（页号本身仍存在，因为名字还在；两者都空才算该页不存在）。 */
    public static void removeContent(CompoundTag root, int pageIndex) {
        if (root == null || !validIndex(pageIndex)) {
            return;
        }
        CompoundTag pages = child(root, KEY_PAGES);
        if (pages != null) {
            pages.remove(keyOf(pageIndex));
            prune(root, KEY_PAGES);
        }
    }

    public static void setRawName(CompoundTag root, int pageIndex, String json) {
        if (root == null || !validIndex(pageIndex)) {
            return;
        }
        CompoundTag names = ensureChild(root, KEY_PAGE_NAMES);
        if (json == null || json.isEmpty()) {
            names.remove(keyOf(pageIndex));
        } else {
            names.putString(keyOf(pageIndex), json);
        }
        prune(root, KEY_PAGE_NAMES);
    }

    /** 写入页名（纯文本，空串 = 清除名字，页不会消失）。 */
    public static void setName(CompoundTag root, int pageIndex, String name) {
        String trimmed = trimName(name);
        setRawName(root, pageIndex, trimmed.isEmpty() ? "" : Component.Serializer.toJson(Component.literal(trimmed), RegistryAccess.EMPTY));
    }

    private static String trimName(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }

    public static void clearSealed(CompoundTag root, int pageIndex) {
        CompoundTag sealed = child(root, KEY_SEALED);
        if (sealed != null) {
            sealed.remove(keyOf(pageIndex));
            prune(root, KEY_SEALED);
        }
    }

    /** 设置某页的封存标记（按页号，与"当前页"无关）。 */
    public static void setSealed(CompoundTag root, int pageIndex, boolean sealed) {
        if (root == null || !validIndex(pageIndex)) {
            return;
        }
        if (!sealed) {
            clearSealed(root, pageIndex);
            return;
        }
        ensureChild(root, KEY_SEALED).putBoolean(keyOf(pageIndex), true);
    }

    /** 删除某页后应选中的相邻页：优先更大的页号，否则更小的；一页都没有则第 1 页。 */
    public static int nearestPage(CompoundTag root, int removedIndex) {
        for (int i = removedIndex + 1; i <= MAX_PAGES; i++) {
            if (exists(root, i)) {
                return i;
            }
        }
        for (int i = removedIndex - 1; i >= 1; i--) {
            if (exists(root, i)) {
                return i;
            }
        }
        return 1;
    }

    private static void prune(CompoundTag root, String key) {
        CompoundTag tag = child(root, key);
        if (tag != null && tag.isEmpty()) {
            root.remove(key);
        }
    }

    /**
     * 写整页内容（内容空列表 = 清空该页，页仍然存在）。封存页拒绝。
     */
    public static Result writeContent(CompoundTag root, int pageIndex, List<CompoundTag> tags, CompoundTag template) {
        if (root == null || !validIndex(pageIndex)) {
            return Result.INVALID;
        }
        if (isSealed(root, pageIndex)) {
            return Result.SEALED;
        }
        setContentTags(root, pageIndex, tags, template);
        return Result.OK;
    }

    /** 新建空页（占位为空 ListIota，名字为空 = 界面显示"第 N 页"）。 */
    public static Result create(CompoundTag root, int pageIndex, CompoundTag template) {
        if (root == null || !validIndex(pageIndex)) {
            return Result.INVALID;
        }
        if (exists(root, pageIndex)) {
            return Result.INVALID;
        }
        setContentTags(root, pageIndex, List.of(), template);
        return Result.OK;
    }

    /** 改名。封存页也允许；页必须存在。 */
    public static Result rename(CompoundTag root, int pageIndex, String name) {
        if (root == null || !validIndex(pageIndex) || !exists(root, pageIndex)) {
            return Result.INVALID;
        }
        setName(root, pageIndex, name);
        return Result.OK;
    }

    /** 删除一页（页号变为空闲，其它页号不动）。封存页拒绝。 */
    public static Result delete(CompoundTag root, int pageIndex) {
        if (root == null || !exists(root, pageIndex)) {
            return Result.INVALID;
        }
        if (isSealed(root, pageIndex)) {
            return Result.SEALED;
        }
        CompoundTag pages = child(root, KEY_PAGES);
        if (pages != null) {
            pages.remove(keyOf(pageIndex));
        }
        CompoundTag names = child(root, KEY_PAGE_NAMES);
        if (names != null) {
            names.remove(keyOf(pageIndex));
        }
        clearSealed(root, pageIndex);
        prune(root, KEY_PAGES);
        prune(root, KEY_PAGE_NAMES);
        return Result.OK;
    }

    /**
     * 复制一页到 {@code targetIndex}（调用方保证目标页号空闲）。副本不封存。
     *
     * @param newName 副本名；空则沿用源页名
     */
    public static Result duplicate(CompoundTag root, int sourceIndex, int targetIndex, String newName, CompoundTag template) {
        if (root == null || !validIndex(sourceIndex) || !validIndex(targetIndex)) {
            return Result.INVALID;
        }
        if (!exists(root, sourceIndex)) {
            return Result.INVALID;
        }
        if (exists(root, targetIndex)) {
            return Result.INVALID;
        }
        CompoundTag content = rawContent(root, sourceIndex);
        setContentTag(root, targetIndex, content == null ? listTagOf(List.of(), template) : content.copy());
        String name = trimName(newName);
        setName(root, targetIndex, name.isEmpty() ? name(root, sourceIndex) : name);
        clearSealed(root, targetIndex);
        return Result.OK;
    }

    /**
     * 拖拽排序：页号集合不变，只有"名字 + 内容"在未封存页号之间重新分配。
     */
    public static Reorder reorder(CompoundTag root, int fromIndex, int targetIndex, boolean after) {
        List<Integer> slots = movableIndices(root);
        int fromPos = slots.indexOf(fromIndex);
        if (root == null || fromPos < 0) {
            return new Reorder(Result.INVALID, NO_PAGE);
        }
        if (fromIndex == targetIndex) {
            return new Reorder(Result.OK, fromIndex);
        }
        if (!validIndex(targetIndex) || !exists(root, targetIndex)) {
            return new Reorder(Result.INVALID, NO_PAGE);
        }
        if (isSealed(root, targetIndex)) {
            return new Reorder(Result.SEALED, NO_PAGE);
        }

        List<String> names = new ArrayList<>(slots.size());
        List<CompoundTag> contents = new ArrayList<>(slots.size());
        for (int slot : slots) {
            names.add(rawName(root, slot));
            contents.add(rawContent(root, slot));
        }

        int targetPos = slots.indexOf(targetIndex);
        int targetPosAfterRemoval = targetPos < fromPos ? targetPos : targetPos - 1;
        int insertPos = after ? targetPosAfterRemoval + 1 : targetPosAfterRemoval;

        String movingName = names.remove(fromPos);
        CompoundTag movingContent = contents.remove(fromPos);
        names.add(insertPos, movingName);
        contents.add(insertPos, movingContent);

        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            CompoundTag content = contents.get(i);
            if (content == null) {
                removeContent(root, slot);
            } else {
                setContentTag(root, slot, content);
            }
            setRawName(root, slot, names.get(i));
        }
        return new Reorder(Result.OK, slots.get(insertPos));
    }

    // ==================== ItemStack 包装层（1.21.1 DataComponents 双向桥接） ====================

    private static CompoundTag serializeIota(Iota i) {
        if (i == null) return null;
        try {
            var t = IotaType.TYPED_CODEC.encodeStart(NbtOps.INSTANCE, i).result().orElse(null);
            return t instanceof CompoundTag ct ? ct : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Iota deserializeIota(CompoundTag tag) {
        if (tag == null) return null;
        try {
            return IotaType.TYPED_CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 从 ItemStack 的 DataComponents.CUSTOM_DATA 与 HexDataComponents 合成统一的 CompoundTag 视图。
     * 这样即便外部代码（如 ItemSpellbook.writeDatum、rotatePageIdx、inventoryTick、setSealed）
     * 直接修改了 HexDataComponents，本层也能读到最新页状态。
     */
    private static CompoundTag rootOf(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        CompoundTag root = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();

        Integer selectedComp = stack.get(HexDataComponents.SELECTED_SPELLBOOK_PAGE.get());
        if (selectedComp != null && selectedComp > 0) {
            root.putInt(KEY_SELECTED_PAGE, selectedComp);
        }

        Map<String, Iota> compPages = stack.get(HexDataComponents.SPELLBOOK_PAGES.get());
        if (compPages != null) {
            CompoundTag template = emptyContent();
            for (Map.Entry<String, Iota> entry : compPages.entrySet()) {
                int idx;
                try {
                    idx = Integer.parseInt(entry.getKey());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (!validIndex(idx)) continue;
                Iota iota = entry.getValue();
                if (iota instanceof ListIota li) {
                    List<CompoundTag> elemTags = new ArrayList<>();
                    for (Iota el : li.getList()) {
                        CompoundTag ct = serializeIota(el);
                        if (ct != null) elemTags.add(ct);
                    }
                    setContentTags(root, idx, elemTags, template);
                } else if (iota != null) {
                    CompoundTag ct = serializeIota(iota);
                    if (ct != null) setContentTag(root, idx, ct);
                }
            }
        }

        Map<String, Component> compNames = stack.get(HexDataComponents.SPELLBOOK_PAGE_NAMES.get());
        if (compNames != null) {
            for (Map.Entry<String, Component> entry : compNames.entrySet()) {
                int idx;
                try {
                    idx = Integer.parseInt(entry.getKey());
                } catch (NumberFormatException e) {
                    continue;
                }
                if (!validIndex(idx)) continue;
                Component comp = entry.getValue();
                if (comp != null && !comp.getString().isEmpty()) {
                    setName(root, idx, comp.getString());
                }
            }
        }

        if (ItemSpellbook.isSealed(stack)) {
            setSealed(root, castPage(root), true);
        }
        return root;
    }

    /**
     * 将修改后的 CompoundTag 视图写回 ItemStack 的 DataComponents.CUSTOM_DATA 与 HexDataComponents。
     */
    private static void saveRoot(ItemStack stack, CompoundTag root) {
        if (stack == null || root == null) {
            return;
        }
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

        Map<String, Iota> pagesMap = new HashMap<>();
        Map<String, Component> namesMap = new HashMap<>();
        for (int idx : indices(root)) {
            String key = keyOf(idx);
            List<CompoundTag> tags = contentTags(root, idx);
            List<Iota> iotas = new ArrayList<>(tags.size());
            for (CompoundTag t : tags) {
                Iota parsed = deserializeIota(t);
                if (parsed != null) iotas.add(parsed);
            }
            if (!iotas.isEmpty()) {
                pagesMap.put(key, new ListIota(iotas));
            }
            String pageName = name(root, idx);
            if (!pageName.isEmpty()) {
                namesMap.put(key, Component.literal(pageName));
            }
        }
        if (pagesMap.isEmpty()) {
            stack.remove(HexDataComponents.SPELLBOOK_PAGES.get());
        } else {
            stack.set(HexDataComponents.SPELLBOOK_PAGES.get(), pagesMap);
        }

        if (namesMap.isEmpty()) {
            stack.remove(HexDataComponents.SPELLBOOK_PAGE_NAMES.get());
        } else {
            stack.set(HexDataComponents.SPELLBOOK_PAGE_NAMES.get(), namesMap);
        }

        int cp = castPage(root);
        stack.set(HexDataComponents.SELECTED_SPELLBOOK_PAGE.get(), cp);
        ItemSpellbook.setSealed(stack, isSealed(root, cp));
        syncHoverName(stack, root, cp);
    }

    public static String getStaffUuid(ItemStack stack) {
        return getStaffUuid(rootOf(stack));
    }

    public static String ensureStaffUuid(ItemStack stack) {
        if (stack == null) {
            return "";
        }
        CompoundTag root = rootOf(stack);
        String existing = getStaffUuid(root);
        if (!existing.isEmpty()) {
            return existing;
        }
        String created = ensureStaffUuid(root);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return created;
    }

    public static boolean exists(ItemStack stack, int pageIndex) {
        return exists(rootOf(stack), pageIndex);
    }

    public static List<Integer> indices(ItemStack stack) {
        return indices(rootOf(stack));
    }

    public static int lowestFree(ItemStack stack) {
        return lowestFree(rootOf(stack));
    }

    public static boolean isSealed(ItemStack stack, int pageIndex) {
        return isSealed(rootOf(stack), pageIndex);
    }

    public static void setSealed(ItemStack stack, int pageIndex, boolean sealed) {
        if (stack == null) return;
        CompoundTag root = rootOf(stack);
        setSealed(root, pageIndex, sealed);
        saveRoot(stack, root);
    }

    public static int nearestPage(ItemStack stack, int removedIndex) {
        return nearestPage(rootOf(stack), removedIndex);
    }

    public static String name(ItemStack stack, int pageIndex) {
        return name(rootOf(stack), pageIndex);
    }

    public static List<CompoundTag> contentTags(ItemStack stack, int pageIndex) {
        return contentTags(rootOf(stack), pageIndex);
    }

    public static Result writeContent(ItemStack stack, int pageIndex, List<CompoundTag> tags) {
        if (stack == null) {
            return Result.INVALID;
        }
        CompoundTag root = rootOf(stack);
        Result result = writeContent(root, pageIndex, tags, emptyContent());
        if (result == Result.OK) {
            saveRoot(stack, root);
        }
        return result;
    }

    public static Result create(ItemStack stack, int pageIndex) {
        if (stack == null) {
            return Result.INVALID;
        }
        CompoundTag root = rootOf(stack);
        Result result = create(root, pageIndex, emptyContent());
        if (result == Result.OK) {
            saveRoot(stack, root);
        }
        return result;
    }

    public static Result rename(ItemStack stack, int pageIndex, String newName) {
        if (stack == null) {
            return Result.INVALID;
        }
        CompoundTag root = rootOf(stack);
        Result result = rename(root, pageIndex, newName);
        if (result == Result.OK) {
            saveRoot(stack, root);
        }
        return result;
    }

    public static Result delete(ItemStack stack, int pageIndex) {
        if (stack == null) {
            return Result.INVALID;
        }
        CompoundTag root = rootOf(stack);
        Result result = delete(root, pageIndex);
        if (result == Result.OK) {
            saveRoot(stack, root);
        }
        return result;
    }

    public static Result duplicate(ItemStack stack, int sourceIndex, int targetIndex, String newName) {
        if (stack == null) {
            return Result.INVALID;
        }
        CompoundTag root = rootOf(stack);
        Result result = duplicate(root, sourceIndex, targetIndex, newName, emptyContent());
        if (result == Result.OK) {
            saveRoot(stack, root);
        }
        return result;
    }

    public static Reorder reorder(ItemStack stack, int fromIndex, int targetIndex, boolean after) {
        if (stack == null) {
            return new Reorder(Result.INVALID, NO_PAGE);
        }
        CompoundTag root = rootOf(stack);
        Reorder reorder = reorder(root, fromIndex, targetIndex, after);
        if (reorder.result() == Result.OK) {
            root.putInt(KEY_SELECTED_PAGE, reorder.newIndex());
            saveRoot(stack, root);
        }
        return reorder;
    }

    /** 当前施放页（{@code page_idx}），已 clamp 到 1..64。 */
    public static int castPage(ItemStack stack) {
        return castPage(rootOf(stack));
    }

    /** 当前施放页（{@code page_idx}）；没有页时返回 1。 */
    public static int castPage(CompoundTag root) {
        int stored = root == null ? 0 : NBTHelper.getInt(root, KEY_SELECTED_PAGE);
        if (stored <= 0) {
            List<Integer> all = indices(root);
            return all.isEmpty() ? 1 : all.get(0);
        }
        return Math.min(stored, MAX_PAGES);
    }

    /**
     * 物品悬停名应该显示的文本：只跟"当前施放页"的页名走（空串 = 不该有自定义名）。
     */
    public static String hoverName(CompoundTag root) {
        return name(root, castPage(root));
    }

    /** 把某一页设为当前施放页，并同步物品悬停名（Hex 的 inventoryTick 依赖它）。 */
    public static void select(ItemStack stack, int pageIndex) {
        if (stack == null) {
            return;
        }
        int page = Math.max(1, Math.min(MAX_PAGES, pageIndex));
        CompoundTag root = rootOf(stack);
        root.putInt(KEY_SELECTED_PAGE, page);
        saveRoot(stack, root);
    }

    /** 页号等于当前施放页时，把物品悬停名改成该页名字（空名字则清除自定义名）。 */
    private static void syncHoverName(ItemStack stack, CompoundTag root, int pageIndex) {
        if (castPage(root) != pageIndex) {
            return;
        }
        String name = hoverName(root);
        if (!name.isEmpty()) {
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        } else {
            stack.remove(DataComponents.CUSTOM_NAME);
        }
    }
}
