package cn.xm1221.AlmightlyStaff.gui.ide.host;

import cn.xm1221.AlmightlyStaff.spell.PatternCatalogEntry;
import cn.xm1221.AlmightlyStaff.spell.PatternRef;
import cn.xm1221.AlmightlyStaff.spell.StaffHex;
import com.google.gson.JsonObject;
import dev.architectury.platform.Platform;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Deque;

/**
 * Patchouli 手册软依赖隔离桥：全部通过反射调用，确保 common/fabric/forge
 * 在无编译期 Patchouli 依赖或未安装 Patchouli 运行时均不崩。
 */
public final class PatchouliHostBridge {
    private PatchouliHostBridge() {
    }

    @Nullable
    public static Screen openHexBookCaptured() {
        if (!Platform.isModLoaded("patchouli")) {
            return null;
        }
        return EmbeddedScreenHost.captureScreen(() -> {
            try {
                Class<?> apiCls = Class.forName("vazkii.patchouli.api.PatchouliAPI");
                Object api = apiCls.getMethod("get").invoke(null);
                Method open = api.getClass().getMethod("openBookGUI", ResourceLocation.class);
                open.invoke(api, ResourceLocation.fromNamespaceAndPath("hexcasting", "thehexbook"));
            } catch (Throwable ignored) {
            }
        });
    }

    public static boolean isPatchouliBook(@Nullable Screen screen) {
        return screen != null && screen.getClass().getName().startsWith("vazkii.patchouli.client.book.gui.GuiBook");
    }

    public static float bookScaleFactor(@Nullable Screen screen) {
        if (!isPatchouliBook(screen)) return 1.0f;
        try {
            Class<?> guiBook = Class.forName("vazkii.patchouli.client.book.gui.GuiBook");
            Field f = guiBook.getDeclaredField("scaleFactor");
            f.setAccessible(true);
            float v = f.getFloat(screen);
            return v > 0.1f ? v : 1.0f;
        } catch (Throwable ignored) {
            return 1.0f;
        }
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Deque<Screen> getGuiStack(@Nullable Screen screen) {
        if (!isPatchouliBook(screen)) return null;
        try {
            Class<?> guiBook = Class.forName("vazkii.patchouli.client.book.gui.GuiBook");
            Field bookF = guiBook.getField("book");
            Object book = bookF.get(screen);
            if (book == null) return null;
            Object contents = book.getClass().getMethod("getContents").invoke(book);
            if (contents == null) return null;
            Field stackF = contents.getClass().getField("guiStack");
            Object stack = stackF.get(contents);
            return stack instanceof Deque<?> ? (Deque<Screen>) stack : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static int guiStackSize(@Nullable Screen screen) {
        Deque<Screen> stack = getGuiStack(screen);
        return stack == null ? -1 : stack.size();
    }

    public static void pushGuiStack(@Nullable Screen oldScreen, @Nullable Screen nextScreen) {
        if (oldScreen == null || nextScreen == null || oldScreen == nextScreen) return;
        Deque<Screen> oldStack = getGuiStack(oldScreen);
        Deque<Screen> nextStack = getGuiStack(nextScreen);
        if (oldStack != null && oldStack == nextStack && oldStack.peek() != oldScreen) {
            oldStack.push(oldScreen);
        }
    }

    public static boolean tryBack(@Nullable Screen screen) {
        if (!isPatchouliBook(screen)) return false;
        Deque<Screen> stack = getGuiStack(screen);
        if (stack == null || stack.isEmpty()) return false;
        try {
            Class<?> guiBook = Class.forName("vazkii.patchouli.client.book.gui.GuiBook");
            Method m = guiBook.getDeclaredMethod("back", boolean.class);
            m.setAccessible(true);
            m.invoke(screen, true);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Nullable
    public static PatternRef extractVisiblePattern(@Nullable Screen screen) {
        if (screen == null || !Platform.isModLoaded("patchouli")) return null;
        if (!"vazkii.patchouli.client.book.gui.GuiBookEntry".equals(screen.getClass().getName())) {
            return null;
        }
        try {
            for (String fieldName : new String[]{"leftPage", "rightPage"}) {
                Field pf = screen.getClass().getDeclaredField(fieldName);
                pf.setAccessible(true);
                Object page = pf.get(screen);
                if (page == null) continue;
                Field srcField = page.getClass().getField("sourceObject");
                Object srcObj = srcField.get(page);
                if (!(srcObj instanceof JsonObject jo)) continue;
                if (!jo.has("type") || !"hexcasting:pattern".equals(jo.get("type").getAsString())) continue;
                if (!jo.has("op_id")) continue;
                String opId = jo.get("op_id").getAsString();
                for (PatternCatalogEntry entry : StaffHex.loadCatalog()) {
                    if (opId.equals(entry.pattern().actionId()) && !entry.pattern().signature().isBlank()) {
                        return entry.pattern();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
