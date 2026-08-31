package cn.xm1221.AlmightlyStaff.spell;

import at.petrak.hexcasting.api.casting.ActionRegistryEntry;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.render.PatternColors;
import at.petrak.hexcasting.client.render.PatternRenderer;
import at.petrak.hexcasting.client.render.WorldlyPatternRenderHelpers;
import at.petrak.hexcasting.xplat.IXplatAbstractions;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Hex Casting 桥接：图案目录加载 + 图案渲染 + PatternRef 转换。
 * 本项目直接依赖 Hex Casting，因此使用直接 API（非反射）。
 */
public final class StaffHex {
    private static final ResourceLocation PER_WORLD_TAG = new ResourceLocation("hexcasting", "per_world_pattern");
    private static final String[] DIRECTION_NAMES = {
        "NORTH_EAST", "EAST", "SOUTH_EAST", "SOUTH_WEST", "WEST", "NORTH_WEST"
    };

    /** per-world（卓越法术）动作ID集合，首次 loadCatalog 时缓存。 */
    private static Set<String> perWorldActionIds;

    private StaffHex() {
    }

    public static Registry<ActionRegistryEntry> actionRegistry() {
        return IXplatAbstractions.INSTANCE.getActionRegistry();
    }

    /** 该动作是否为 per-world 卓越法术（签名按世界生成，需学习后绘制）。 */
    public static boolean isPerWorldAction(String actionId) {
        if (perWorldActionIds == null) loadCatalog();
        return perWorldActionIds != null && perWorldActionIds.contains(actionId);
    }

    /** 加载全部动作图案目录（含元图案与 per-world 占位）。 */
    public static List<PatternCatalogEntry> loadCatalog() {
        Registry<ActionRegistryEntry> registry = actionRegistry();
        List<PatternCatalogEntry> output = new ArrayList<>();
        for (ActionRegistryEntry entry : registry) {
            ResourceLocation id = registry.getKey(entry);
            if (id == null) continue;
            HexPattern proto = entry.prototype();
            String direction = proto.getStartDir().name();
            String signature = proto.anglesSignature();
            PatternRef ref = new PatternRef(id.toString(), direction, signature);
            if (registry.getHolderOrThrow(registry.getResourceKey(entry).orElseThrow())
                .is(TagKey.create(registry.key(), PER_WORLD_TAG))) {
                // per-world：无固定签名（枚举已移除，仅作目录占位）
                output.add(new PatternCatalogEntry(
                    new PatternRef(id.toString(), "EAST", ""),
                    Component.translatable("hexcasting.action." + id),
                    PatternCatalogEntry.Kind.PER_WORLD));
                continue;
            }
            if (isMeta(ref)) {
                output.add(new PatternCatalogEntry(ref, metaDisplayName(id.toString()), PatternCatalogEntry.Kind.META));
            } else {
                output.add(new PatternCatalogEntry(ref, Component.translatable("hexcasting.action." + id)));
            }
        }
        // 补全内置元图案
        for (MetaPattern meta : META_PATTERNS) {
            boolean present = false;
            for (PatternCatalogEntry e : output) {
                if (e.pattern().actionId().equals(meta.actionId)) { present = true; break; }
            }
            if (!present) {
                PatternRef ref = new PatternRef(meta.actionId, meta.dir, meta.signature);
                output.add(new PatternCatalogEntry(ref, metaDisplayName(meta.actionId),
                    PatternCatalogEntry.Kind.META));
            }
        }
        return output;
    }

    /** 把 HexPattern 解析为 PatternRef（尽量匹配注册表动作ID）。 */
    public static PatternRef refFor(HexPattern pattern) {
        String dir = pattern.getStartDir().name();
        String sig = pattern.anglesSignature();
        for (ActionRegistryEntry entry : actionRegistry()) {
            HexPattern proto = entry.prototype();
            if (proto.getStartDir() == pattern.getStartDir()
                && proto.anglesSignature().equals(sig)) {
                ResourceLocation id = actionRegistry().getKey(entry);
                if (id != null) return new PatternRef(id.toString(), dir, sig);
            }
        }
        return new PatternRef("", dir, sig);
    }

    /** 把 PatternRef 还原为 HexPattern。 */
    public static HexPattern patternFor(PatternRef ref) {
        HexDir dir = dirFor(ref.startDirection());
        return HexPattern.fromAnglesUnchecked(ref.signature(), dir);
    }

    /** 渲染图案（卷轴可读样式）。返回是否成功。 */
    public static boolean renderPattern(PatternRef ref, PoseStack ps, int x, int y, int size) {
        try {
            HexPattern pattern = patternFor(ref);
            PatternColors colors = PatternColors.READABLE_SCROLL_COLORS;
            ps.pushPose();
            ps.translate(x, y, 100.0F);
            ps.scale(size, size, 1.0F);
            PatternRenderer.renderPattern(pattern, ps, WorldlyPatternRenderHelpers.READABLE_SCROLL_SETTINGS,
                colors, 0.0D, 512);
            ps.popPose();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 内联图案文本（用于 tooltip）。 */
    public static Component inlinePatternText(PatternRef ref) {
        try {
            return PatternIota.display(patternFor(ref));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 已注册图案的本地化显示名；未知/无名称键的返回 null（调用方回退 IotaType.getDisplay）。 */
    public static Component patternDisplayName(PatternRef ref) {
        String id = ref.actionId();
        if (id == null || id.isEmpty()) return null;
        if (id.startsWith("hexcasting:number/")) {
            return Component.translatable("almightly_staff.gui.number_desc",
                id.substring(id.lastIndexOf('/') + 1));
        }
        // 元图案（escape/undo/括号）：用本项目自定义译名
        String metaKey = metaKey(id);
        if (metaKey != null && net.minecraft.locale.Language.getInstance().has(metaKey)) {
            return Component.translatable(metaKey);
        }
        String actKey = "hexcasting.action." + id;
        if (net.minecraft.locale.Language.getInstance().has(actKey)) {
            return Component.translatable(actKey);
        }
        return null;
    }

    private static String metaKey(String actionId) {
        return switch (actionId) {
            case "hexcasting:escape" -> "almightly_staff.meta.escape";
            case "hexcasting:open_paren" -> "almightly_staff.meta.open_paren";
            case "hexcasting:close_paren" -> "almightly_staff.meta.close_paren";
            case "hexcasting:undo" -> "almightly_staff.meta.undo";
            default -> null;
        };
    }

    private static boolean isMeta(PatternRef ref) {
        for (MetaPattern meta : META_PATTERNS) {
            if (meta.actionId.equals(ref.actionId())) return true;
        }
        return false;
    }

    private static Component metaDisplayName(String actionId) {
        return switch (actionId) {
            case "hexcasting:escape" -> Component.translatable("almightly_staff.meta.escape");
            case "hexcasting:open_paren" -> Component.translatable("almightly_staff.meta.open_paren");
            case "hexcasting:close_paren" -> Component.translatable("almightly_staff.meta.close_paren");
            case "hexcasting:undo" -> Component.translatable("almightly_staff.meta.undo");
            default -> Component.literal(actionId);
        };
    }

    private static HexDir dirFor(String name) {
        for (HexDir dir : HexDir.values()) {
            if (dir.name().equals(name.toUpperCase(Locale.ROOT))) return dir;
        }
        return HexDir.EAST;
    }

    public static String directionName(HexDir dir) {
        return DIRECTION_NAMES[dir.ordinal() % 6];
    }

    private record MetaPattern(String actionId, String dir, String signature) {
    }

    private static final MetaPattern[] META_PATTERNS = {
        new MetaPattern("hexcasting:escape", "WEST", "qqqaw"),
        new MetaPattern("hexcasting:open_paren", "WEST", "qqq"),
        new MetaPattern("hexcasting:close_paren", "EAST", "eee"),
        new MetaPattern("hexcasting:undo", "EAST", "eeedw")
    };
}
