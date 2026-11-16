package cn.xm1221.AlmightlyStaff.network;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import at.petrak.hexcasting.api.utils.NBTHelper;
import at.petrak.hexcasting.common.items.storage.ItemSpellbook;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 万法之杖页存储的唯一入口。
 *
 * <p>四条硬约定（见 staff_gui_fix_plan.md §2）：
 * <ol>
 *   <li><b>按页号直写 NBT</b>，不借用 {@code selectPage() + writeDatum()}
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
 * <p>本类分两层：{@code CompoundTag} 纯函数层（不碰注册表、可单测）与 {@code ItemStack} 包装层。
 * 纯函数层里凡是需要"新建空页"的地方都接收一个 {@code emptyTemplate}（空 {@link ListIota} 的序列化标签），
 * 由包装层用 {@link #emptyContent()} 提供。
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
            Component parsed = Component.Serializer.fromJson(json);
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

    /**
     * 列表元素所在的键，等于 HexCasting 的 {@code HexIotaTypes.KEY_DATA}（{@code "hexcasting:data"}）。
     *
     * <p>这里刻意用字面量复制一份：那个常量在静态初始化里会拉起 {@code IXplatAbstractions} 平台层，
     * 引用它会让本类（纯 NBT 逻辑）无法在普通 JVM 单测里跑。键值属于存档格式，不会变；
     * 一致性由 {@code StaffPagesTest} 里的用例守护。
     */
    private static final String KEY_DATA = "hexcasting:data";

    /*
     * 下面四个键等于 HexCasting 的 ItemSpellbook.TAG_PAGES / TAG_PAGE_NAMES / TAG_SELECTED_PAGE /
     * TAG_SEALED。那几个字段是 public static String（非 final，不是编译期常量），引用它们会让
     * ItemSpellbook 初始化 → Item → FeatureElement → Registries → BuiltInRegistries，于是在普通
     * JVM 里抛 "Not bootstrapped"。这层刻意只留字面量，行为完全一致（存档格式不会变），
     * 一致性由 StaffPagesTest 守护。
     */
    private static final String KEY_PAGES = "pages";
    private static final String KEY_PAGE_NAMES = "page_names";
    private static final String KEY_SELECTED_PAGE = "page_idx";
    private static final String KEY_SEALED = "sealed_pages";

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
        Iota empty = new ListIota(new ArrayList<Iota>());
        return IotaType.serialize(empty);
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
        setRawName(root, pageIndex, trimmed.isEmpty() ? "" : Component.Serializer.toJson(Component.literal(trimmed)));
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
     *
     * <p>例：1=A、2=B（封存）、3=C、4=D，把 D 拖到 A 前 → 未封存页号 [1,3,4] 上的内容 [A,C,D]
     * 重排成 [D,A,C] → 1=D、2=B（不动）、3=A、4=C。
     *
     * @param after true = 插到目标页后面，false = 插到目标页前面
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

        // 目标在"移除 from 之后"的序列里的位置；from 在目标前面时它要左移一位
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
                // 旧存档里"只有名字"的页：其余页移动时不要把 null 写进 NBT
                removeContent(root, slot);
            } else {
                setContentTag(root, slot, content);
            }
            setRawName(root, slot, names.get(i));
        }
        return new Reorder(Result.OK, slots.get(insertPos));
    }

    // ==================== ItemStack 包装层 ====================

    private static CompoundTag rootOf(ItemStack stack) {
        return stack == null ? null : stack.getTag();
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
        Result result = writeContent(stack.getOrCreateTag(), pageIndex, tags, emptyContent());
        if (result == Result.OK) {
            syncHoverName(stack, pageIndex);
        }
        return result;
    }

    public static Result create(ItemStack stack, int pageIndex) {
        if (stack == null) {
            return Result.INVALID;
        }
        return create(stack.getOrCreateTag(), pageIndex, emptyContent());
    }

    public static Result rename(ItemStack stack, int pageIndex, String newName) {
        if (stack == null) {
            return Result.INVALID;
        }
        Result result = rename(stack.getOrCreateTag(), pageIndex, newName);
        if (result == Result.OK) {
            syncHoverName(stack, pageIndex);
        }
        return result;
    }

    public static Result delete(ItemStack stack, int pageIndex) {
        if (stack == null) {
            return Result.INVALID;
        }
        Result result = delete(stack.getOrCreateTag(), pageIndex);
        if (result == Result.OK) {
            // 删掉的是当前页时，悬停名必须跟着切走，否则 Hex 的 inventoryTick 会把旧名字写回来
            select(stack, castPage(stack));
        }
        return result;
    }

    public static Result duplicate(ItemStack stack, int sourceIndex, int targetIndex, String newName) {
        if (stack == null) {
            return Result.INVALID;
        }
        return duplicate(stack.getOrCreateTag(), sourceIndex, targetIndex, newName, emptyContent());
    }

    public static Reorder reorder(ItemStack stack, int fromIndex, int targetIndex, boolean after) {
        if (stack == null) {
            return new Reorder(Result.INVALID, NO_PAGE);
        }
        Reorder reorder = reorder(stack.getOrCreateTag(), fromIndex, targetIndex, after);
        if (reorder.result() == Result.OK) {
            select(stack, reorder.newIndex());
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
     *
     * <p>Hex 的 {@code inventoryTick} 每 tick 把悬停名写回当前页的页名，所以改名后必须让两者一致，
     * 否则改一个页名、下一个 tick 就被旧悬停名覆盖回去（B1/B2/B3）。
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
        NBTHelper.putInt(stack, KEY_SELECTED_PAGE, page);
        syncHoverName(stack, page);
    }

    /** 页号等于当前施放页时，把物品悬停名改成该页名字（空名字则清除自定义名）。 */
    private static void syncHoverName(ItemStack stack, int pageIndex) {
        if (castPage(stack) != pageIndex) {
            return;
        }
        String name = hoverName(rootOf(stack));
        if (!name.isEmpty()) {
            stack.setHoverName(Component.literal(name));
        } else {
            stack.resetHoverName();
        }
    }
}
