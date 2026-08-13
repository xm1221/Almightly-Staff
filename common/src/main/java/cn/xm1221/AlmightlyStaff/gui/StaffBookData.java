package cn.xm1221.AlmightlyStaff.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 读取 Hex Casting Patchouli 手册 JSON（参考 hexcessible 的 BookEntries），
 * 建立 op_id → {描述翻译键, 输入, 输出} 映射，用于图案目录悬停的详细描述。
 * 直接从资源管理器读书 JSON，比猜测 `hexcasting.page.<章节>.<动作>` 翻译键可靠得多
 * （有的动作页面不在固定章节下，猜键会漏）。
 */
public final class StaffBookData {
    private static final Map<String, Entry> ENTRIES = new HashMap<>();
    private static boolean loaded = false;

    public record Entry(String descKey, String input, String output) {
        /** 栈变化摘要：in -> out（照 hexcessible 的 getArgs）。 */
        public String args() {
            String s = (input + " -> " + output).strip();
            return s.equals(" -> ") ? "" : s;
        }
    }

    private StaffBookData() { }

    /** 惰性加载：当前语言 + en_us 兜底，只扫一次。 */
    public static void loadIfNeeded() {
        if (loaded) return;
        loaded = true;
        try {
            var rm = Minecraft.getInstance().getResourceManager();
            String lang = Minecraft.getInstance().getLanguageManager().getSelected();
            scan(rm, lang);
            if (!"en_us".equals(lang)) scan(rm, "en_us");
        } catch (Exception ignored) { }
    }

    private static void scan(net.minecraft.server.packs.resources.ResourceManager rm, String lang) {
        // 扫【所有 mod】的 patchouli 书（hexcasting / hexal / hexical / ...），
        // 只要含 hexcasting:pattern 页面（op_id + text/input/output）就收录；
        // descKey 是翻译键，显示时才解析语言，所以跨书去重取第一个即可。
        String marker = "/" + lang + "/entries/";
        for (Map.Entry<ResourceLocation, net.minecraft.server.packs.resources.Resource> e :
            rm.listResources("patchouli_books", p -> {
                String path = p.getPath();
                return path.endsWith(".json") && path.contains(marker);
            }).entrySet()) {
            try (var in = e.getValue().open()) {
                JsonObject root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                JsonArray pages = root.getAsJsonArray("pages");
                if (pages == null) continue;
                for (JsonElement pe : pages) {
                    if (!(pe instanceof JsonObject po)) continue;
                    if (!"hexcasting:pattern".equals(getStr(po, "type"))) continue;
                    String opId = getStr(po, "op_id");
                    if (opId.isEmpty() || ENTRIES.containsKey(opId)) continue;
                    ENTRIES.put(opId, new Entry(getStr(po, "text"),
                        getStr(po, "input"), getStr(po, "output")));
                }
            } catch (Exception ignored) { }
        }
    }

    private static String getStr(JsonObject o, String key) {
        try { return o.has(key) ? o.get(key).getAsString() : ""; } catch (Exception e) { return ""; }
    }

    @Nullable
    public static Entry get(String opId) {
        loadIfNeeded();
        return ENTRIES.get(opId);
    }

    /** 清理 Patchouli 格式码（$(...) 宏、斜杠、下划线转义）。 */
    public static String clean(String raw) {
        if (raw == null) return "";
        return raw
            .replaceAll("\\$\\([^)]*\\)|/\\$", "")
            .replaceAll("[\\s^]_", " ")
            .trim();
    }
}
