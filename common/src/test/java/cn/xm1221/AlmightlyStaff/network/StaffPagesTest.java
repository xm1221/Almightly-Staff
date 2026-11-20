package cn.xm1221.AlmightlyStaff.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 页存储（{@link StaffPages}）的纯 NBT 层单测。
 *
 * <p>这层刻意不碰注册表/平台层，所以能在普通 JVM 里跑；页号、封存、拖拽排序这些
 * 容易出数据丢失的地方都在这里钉住（见 staff_gui_fix_plan.md §4.6）。
 */
class StaffPagesTest {

    /** 空 ListIota 标签（真实运行时由 {@code IotaType.serialize(new ListIota(List.of()))} 生成）。 */
    private static CompoundTag template() {
        CompoundTag tag = new CompoundTag();
        tag.putString("hexcasting:type", "hexcasting:list");
        tag.put("hexcasting:data", new ListTag());
        return tag;
    }

    private static CompoundTag iota(String marker) {
        CompoundTag tag = new CompoundTag();
        tag.putString("hexcasting:type", "hexcasting:pattern");
        tag.putString("marker", marker);
        return tag;
    }

    /** 建页并写入一个带标记的元素。 */
    private static void put(CompoundTag root, int pageIndex, String marker) {
        assertEquals(StaffPages.Result.OK, StaffPages.create(root, pageIndex, template()));
        assertEquals(StaffPages.Result.OK,
            StaffPages.writeContent(root, pageIndex, List.of(iota(marker)), template()));
    }

    private static String markerAt(CompoundTag root, int pageIndex) {
        List<CompoundTag> tags = StaffPages.contentTags(root, pageIndex);
        return tags.isEmpty() ? "" : tags.get(0).getString("marker");
    }

    /** 形如 {@code ["1=A", "3=C"]} 的快照，便于整体比较。 */
    private static List<String> snapshot(CompoundTag root) {
        List<String> out = new ArrayList<>();
        for (int index : StaffPages.indices(root)) {
            out.add(index + "=" + markerAt(root, index));
        }
        return out;
    }

    // ==================== 页存在性 / 页号 ====================

    @Test
    void emptyPageStillExists() {
        CompoundTag root = new CompoundTag();
        assertEquals(StaffPages.Result.OK, StaffPages.create(root, 3, template()));

        assertTrue(StaffPages.exists(root, 3));
        assertEquals(List.of(3), StaffPages.indices(root));
        assertEquals(List.of(), StaffPages.contentTags(root, 3));
        assertEquals("", StaffPages.name(root, 3));
    }

    @Test
    void deleteLeavesHoleInsteadOfShifting() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        put(root, 2, "B");
        put(root, 3, "C");

        assertEquals(StaffPages.Result.OK, StaffPages.delete(root, 2));
        assertEquals(List.of(1, 3), StaffPages.indices(root));
        assertEquals("A", markerAt(root, 1));
        assertEquals("C", markerAt(root, 3));
        assertFalse(StaffPages.exists(root, 2));
    }

    @Test
    void createRejectsOccupiedPage() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        assertEquals(StaffPages.Result.INVALID, StaffPages.create(root, 1, template()));
        assertEquals("A", markerAt(root, 1));
    }

    @Test
    void lowestFreePageIsSmallestGap() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        put(root, 3, "C");
        assertEquals(2, StaffPages.lowestFree(root));
    }

    @Test
    void allPagesFullIsReported() {
        CompoundTag root = new CompoundTag();
        for (int i = 1; i <= StaffPages.MAX_PAGES; i++) {
            assertEquals(StaffPages.Result.OK, StaffPages.create(root, i, template()));
        }
        assertEquals(StaffPages.MAX_PAGES, StaffPages.indices(root).size());
        assertEquals(-1, StaffPages.lowestFree(root));
        assertEquals(StaffPages.Result.INVALID, StaffPages.create(root, 65, template()));
    }

    @Test
    void emptyContentKeepsPageAlive() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        assertEquals(StaffPages.Result.OK, StaffPages.writeContent(root, 1, List.of(), template()));
        assertTrue(StaffPages.exists(root, 1));
        assertEquals(List.of(), StaffPages.contentTags(root, 1));
    }

    /** 旧存档里"只有名字、没有 pages 条目"的页也算存在（否则会被当成空号覆盖掉）。 */
    @Test
    void nameOnlyLegacyPageCountsAsExisting() {
        CompoundTag root = new CompoundTag();
        CompoundTag names = new CompoundTag();
        names.putString("5", "{\"text\":\"Legacy\"}");
        root.put("page_names", names);

        assertTrue(StaffPages.exists(root, 5));
        assertEquals(List.of(5), StaffPages.indices(root));
        assertEquals(List.of(), StaffPages.contentTags(root, 5));
        assertEquals(StaffPages.Result.INVALID, StaffPages.create(root, 5, template()));
    }

    // ==================== 封存 ====================

    @Test
    void sealedPageRejectsDeleteAndWrite() {
        CompoundTag root = new CompoundTag();
        put(root, 2, "B");
        StaffPages.setSealed(root, 2, true);

        assertEquals(StaffPages.Result.SEALED, StaffPages.delete(root, 2));
        assertTrue(StaffPages.exists(root, 2));
        assertEquals(StaffPages.Result.SEALED,
            StaffPages.writeContent(root, 2, List.of(iota("X")), template()));
        assertEquals("B", markerAt(root, 2));
    }

    @Test
    void sealedPageCanStillBeRenamed() {
        CompoundTag root = new CompoundTag();
        put(root, 2, "B");
        StaffPages.setSealed(root, 2, true);

        assertEquals(StaffPages.Result.OK, StaffPages.rename(root, 2, "新名字"));
        assertEquals("新名字", StaffPages.name(root, 2));
    }

    // ==================== 拖拽排序 ====================

    /**
     * §2 的官方例子：1=A、2=B（封存）、3=C、4=D，把 D 拖到 A 前
     * → 未封存页号 [1,3,4] 上的 [A,C,D] 重排成 [D,A,C]，封存的 2 原地不动。
     */
    @Test
    void reorderMovesContentAroundSealedSlots() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        put(root, 2, "B");
        put(root, 3, "C");
        put(root, 4, "D");
        StaffPages.setSealed(root, 2, true);

        StaffPages.Reorder result = StaffPages.reorder(root, 4, 1, false);
        assertEquals(StaffPages.Result.OK, result.result());
        assertEquals(1, result.newIndex());
        assertEquals(List.of("1=D", "2=B", "3=A", "4=C"), snapshot(root));
        assertTrue(StaffPages.isSealed(root, 2));
    }

    @Test
    void reorderToAfterTarget() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        put(root, 2, "C");
        put(root, 3, "E");

        StaffPages.Reorder result = StaffPages.reorder(root, 1, 3, true);
        assertEquals(StaffPages.Result.OK, result.result());
        assertEquals(3, result.newIndex());
        assertEquals(List.of("1=C", "2=E", "3=A"), snapshot(root));
    }

    @Test
    void reorderRejectsSealedTarget() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        put(root, 2, "B");
        put(root, 3, "C");
        StaffPages.setSealed(root, 2, true);

        StaffPages.Reorder result = StaffPages.reorder(root, 3, 2, false);
        assertEquals(StaffPages.Result.SEALED, result.result());
        assertEquals(StaffPages.NO_PAGE, result.newIndex());
        assertEquals(List.of("1=A", "2=B", "3=C"), snapshot(root));
    }

    @Test
    void reorderRejectsSealedSource() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        put(root, 2, "B");
        StaffPages.setSealed(root, 1, true);

        // 封存页不在"可移动"集合里，所以 from 找不到位置
        assertEquals(StaffPages.Result.INVALID, StaffPages.reorder(root, 1, 2, true).result());
        assertEquals(List.of("1=A", "2=B"), snapshot(root));
    }

    @Test
    void reorderOntoItselfIsNoop() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        put(root, 2, "B");

        StaffPages.Reorder result = StaffPages.reorder(root, 2, 2, true);
        assertEquals(StaffPages.Result.OK, result.result());
        assertEquals(2, result.newIndex());
        assertEquals(List.of("1=A", "2=B"), snapshot(root));
    }

    /** 拖过一个空号/封存页不影响结果：只有未封存页号参与重排。 */
    @Test
    void reorderIgnoresHolesAndSealedPages() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        put(root, 2, "B");
        put(root, 4, "D");
        put(root, 7, "G");
        StaffPages.setSealed(root, 2, true);

        StaffPages.Reorder result = StaffPages.reorder(root, 7, 4, false);
        assertEquals(StaffPages.Result.OK, result.result());
        assertEquals(List.of("1=A", "2=B", "4=G", "7=D"), snapshot(root));
    }

    /** 旧存档里"只有名字"的页在重排中不能被写成 null 内容，也不能消失。 */
    @Test
    void reorderKeepsNameOnlyLegacyPage() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        put(root, 2, "B");
        // 页 1 只有名字、没有内容；页 2 有内容
        StaffPages.removeContent(root, 1);
        StaffPages.setRawName(root, 1, "{\"text\":\"OnlyName\"}");
        assertTrue(StaffPages.exists(root, 1));

        assertEquals(StaffPages.Result.OK, StaffPages.reorder(root, 2, 1, false).result());
        // 名字跟内容一起搬家：2 的"B"到页 1，1 的 OnlyName 到页 2（页 2 仍然存在，只是没内容）
        assertEquals(List.of(1, 2), StaffPages.indices(root));
        assertEquals("B", markerAt(root, 1));
        assertEquals("", StaffPages.name(root, 1));
        assertEquals("OnlyName", StaffPages.name(root, 2));
        assertEquals(List.of(), StaffPages.contentTags(root, 2));
    }

    // ==================== 改名 / 复制 / 相邻页 ====================

    @Test
    void nameRoundTrip() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        assertEquals(StaffPages.Result.OK, StaffPages.rename(root, 1, "火球术"));
        assertEquals("火球术", StaffPages.name(root, 1));
        assertEquals(StaffPages.Result.OK, StaffPages.rename(root, 1, ""));
        assertEquals("", StaffPages.name(root, 1));
    }

    @Test
    void duplicateCopiesContentAndNameButNotSeal() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        assertEquals(StaffPages.Result.OK, StaffPages.rename(root, 1, "原名"));
        StaffPages.setSealed(root, 1, true);

        assertEquals(StaffPages.Result.OK, StaffPages.duplicate(root, 1, 2, "原名 (副本)", template()));
        assertEquals("A", markerAt(root, 2));
        assertEquals("原名 (副本)", StaffPages.name(root, 2));
        assertFalse(StaffPages.isSealed(root, 2));   // 副本未封存，可继续编辑
        assertTrue(StaffPages.isSealed(root, 1));
    }

    @Test
    void duplicateRejectsOccupiedTarget() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        put(root, 2, "B");
        assertEquals(StaffPages.Result.INVALID, StaffPages.duplicate(root, 1, 2, "x", template()));
        assertEquals("B", markerAt(root, 2));
    }

    @Test
    void nearestPageAfterDelete() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        put(root, 3, "C");
        put(root, 5, "E");

        assertEquals(3, StaffPages.nearestPage(root, 1));   // 优先更大的页号
        assertEquals(5, StaffPages.nearestPage(root, 3));
        assertEquals(3, StaffPages.nearestPage(root, 5));   // 没有更大的 → 更小的
        assertEquals(1, StaffPages.nearestPage(new CompoundTag(), 4));
    }

    // ==================== 悬停名（Hex 的 inventoryTick 会把悬停名写回当前页页名） ====================

    /**
     * 悬停名只跟"当前施放页"的页名走：改非当前页名不该动它，
     * 否则 Hex 每 tick 的写回会把别的页名字覆盖到当前页（B1/B2/B3）。
     */
    @Test
    void hoverNameFollowsCastPageOnly() {
        CompoundTag root = new CompoundTag();
        put(root, 1, "A");
        put(root, 2, "B");
        StaffPages.rename(root, 1, "甲");
        StaffPages.rename(root, 2, "乙");

        root.putInt("page_idx", 2);
        assertEquals(2, StaffPages.castPage(root));
        assertEquals("乙", StaffPages.hoverName(root));

        StaffPages.rename(root, 1, "甲改");          // 改非当前页
        assertEquals("乙", StaffPages.hoverName(root));

        StaffPages.rename(root, 2, "乙改");          // 改当前页
        assertEquals("乙改", StaffPages.hoverName(root));

        StaffPages.rename(root, 2, "");              // 清空当前页名字 → 不该有自定义名
        assertEquals("", StaffPages.hoverName(root));
    }

    /** 没有 page_idx 时按最小存在页号算；空杖也不该崩。 */
    @Test
    void castPageFallsBackToLowestPage() {
        assertEquals(1, StaffPages.castPage(new CompoundTag()));
        CompoundTag root = new CompoundTag();
        put(root, 4, "D");
        assertEquals(4, StaffPages.castPage(root));
        root.putInt("page_idx", 99);
        assertEquals(StaffPages.MAX_PAGES, StaffPages.castPage(root));
    }

    @Test
    void ensureStaffUuidIsStable() {
        CompoundTag root = new CompoundTag();
        assertEquals("", StaffPages.getStaffUuid(root));
        String first = StaffPages.ensureStaffUuid(root);
        assertFalse(first.isEmpty());
        assertEquals(first, StaffPages.getStaffUuid(root));
        assertEquals(first, StaffPages.ensureStaffUuid(root));
    }
}
