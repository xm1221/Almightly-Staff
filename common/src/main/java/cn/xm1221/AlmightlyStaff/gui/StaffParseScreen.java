package cn.xm1221.AlmightlyStaff.gui;

import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.ListIota;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Parse 文本编辑模式。进入时填入当前法术的 Parse 码，编辑后确认发给服务端 HexParse 转回 iota。
 *
 * 换行/光标定位采用与 HexGuide NoteEditorScreen 相同的方案：
 * font.getSplitter().plainIndexAtWidth 拆行 + lineStarts（偏移含 \n 字符）+ cursorToRowCol，
 * 保证折行/换行时光标位置精确。
 */
public class StaffParseScreen extends Screen {
    private final Screen parent;
    private final String initialCode;
    private final Consumer<List<Iota>> callback;

    private String code = "";
    private int cursor;
    private int scroll;

    private static final int ROW_H = 12;

    public StaffParseScreen(Screen parent, String initialCode, Consumer<List<Iota>> callback) {
        super(Component.translatable("almightly_staff.gui.parse_mode"));
        this.parent = parent;
        this.initialCode = initialCode == null ? "" : initialCode;
        this.callback = callback;
    }

    @Override
    protected void init() {
        code = initialCode;
        cursor = code.length();
        scroll = 0;
        addRenderableWidget(Button.builder(Component.translatable("almightly_staff.gui.confirm"),
            b -> confirm()).bounds(width / 2 - 110, height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("almightly_staff.gui.cancel"),
            b -> onClose()).bounds(width / 2 + 10, height - 30, 100, 20).build());
    }

    private void confirm() {
        ModNetworking.sendToServer(new ModNetworking.MsgParseToIotasC2S(code));
    }

    /** 服务端解析完成回传。 */
    public void onParseResult(Iota iota) {
        List<Iota> list = new ArrayList<>();
        if (iota instanceof ListIota li) for (var x : li.getList()) list.add(x);
        else if (iota != null) list.add(iota);
        callback.accept(list);
        onClose();
    }

    // ==================== 拆行与光标（参考 HexGuide NoteEditorScreen） ====================

    private int textAreaX() { return 20; }
    private int textAreaY() { return 32; }
    private int textAreaW() { return width - 40; }
    private int textAreaH() { return height - 72; }
    private int editWidth() { return textAreaW() - 10; }

    /** 按宽度 + 换行符拆行（与光标定位同一套逻辑）。以 \n 结尾时末尾补空行。 */
    private List<String> wrapLines(String text) {
        if (text.isEmpty()) return List.of("");
        var splitter = font.getSplitter();
        List<String> out = new ArrayList<>();
        String remaining = text;
        while (!remaining.isEmpty()) {
            int nl = remaining.indexOf('\n');
            int limit = splitter.plainIndexAtWidth(remaining, editWidth(), Style.EMPTY);
            int end = (nl >= 0 && nl < limit) ? nl : Math.min(limit, remaining.length());
            out.add(remaining.substring(0, end));
            int consumed = (nl >= 0 && nl < limit) ? end + 1 : end;
            remaining = remaining.substring(consumed);
            if (remaining.isEmpty() && consumed > 0 && text.endsWith("\n")) {
                out.add("");
            }
        }
        return out;
    }

    /** 每行行首字符偏移（宽度折行行间无字符、\n 换行消费 1 字符；末尾空行含）。 */
    private List<Integer> lineStarts(String text) {
        if (text.isEmpty()) return List.of(0);
        var splitter = font.getSplitter();
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        String remaining = text;
        int consumed = 0;
        while (!remaining.isEmpty()) {
            int nl = remaining.indexOf('\n');
            int limit = splitter.plainIndexAtWidth(remaining, editWidth(), Style.EMPTY);
            int end = (nl >= 0 && nl < limit) ? nl : Math.min(limit, remaining.length());
            int seg = (nl >= 0 && nl < limit) ? end + 1 : end;
            consumed += seg;
            if (consumed < text.length()) starts.add(consumed);
            remaining = remaining.substring(seg);
            if (remaining.isEmpty() && seg > 0 && text.endsWith("\n")) {
                starts.add(consumed);
                break;
            }
        }
        return starts;
    }

    private int rowToTextOffset(String text, int row) {
        List<Integer> starts = lineStarts(text);
        return row < starts.size() ? starts.get(row) : text.length();
    }

    /** 字符偏移 → (行, 列)。 */
    private int[] cursorToRowCol(String text, int cursorPos) {
        List<Integer> starts = lineStarts(text);
        int row = 0;
        for (int i = 0; i < starts.size(); i++) {
            if (i + 1 < starts.size() && cursorPos >= starts.get(i + 1)) row = i + 1;
            else break;
        }
        return new int[] { row, Math.max(0, cursorPos - starts.get(row)) };
    }

    private void ensureCursorVisible(int gh) {
        List<Integer> starts = lineStarts(code);
        int[] rc = cursorToRowCol(code, cursor);
        int cursorY = rc[0] * ROW_H;
        int viewH = gh - 8;
        if (cursorY < scroll) scroll = Math.max(0, cursorY);
        if (cursorY + ROW_H > scroll + viewH) scroll = Math.max(0, cursorY + ROW_H - viewH);
    }

    // ==================== 渲染 ====================

    // 1.21.1 的 renderBackground 会触发全屏模糊后处理（renderBlurredBackground），
    // 且 Screen.render() 也会自动调用它；这里覆写为空并改画渐变，避免双重模糊覆盖 UI。
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fillGradient(0, 0, width, height, 0xC0080A0D, 0xC0000000);
        g.drawCenteredString(font, title, width / 2, 14, 0xFFF4F6F8);
        int gx = textAreaX(), gy = textAreaY(), gw = textAreaW(), gh = textAreaH();
        g.fill(gx, gy, gx + gw, gy + gh, 0x22000000);

        int px = gx + 4, lh = ROW_H;
        List<String> lines = wrapLines(code);
        int maxScroll = Math.max(0, lines.size() * lh - gh + 8);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        ensureCursorVisible(gh);

        if (maxScroll > 0) {
            int bh = Math.max(16, (gh - 4) * (gh - 4) / Math.max(1, lines.size() * lh));
            int by = gy + 2 + scroll * (gh - 4 - bh) / maxScroll;
            g.fill(gx + gw - 4, by, gx + gw - 2, by + bh, 0x66ffffff);
        }

        g.enableScissor(gx, gy, gx + gw, gy + gh);
        int y = gy + 2 - scroll;
        for (String line : lines) {
            if (y + lh >= gy && y <= gy + gh) g.drawString(font, line, px, y, 0xFFFFFFFF);
            y += lh;
        }

        // 光标：cursorToRowCol → (行, 列) → 屏幕坐标
        int[] rc = cursorToRowCol(code, cursor);
        int caretX = px + font.width(lines.get(rc[0]).substring(0, Math.min(rc[1], lines.get(rc[0]).length())));
        int caretY = gy + 2 - scroll + rc[0] * lh;
        if (caretY + lh >= gy && caretY <= gy + gh) {
            g.fill(caretX, caretY, caretX + 1, caretY + lh, 0xFFFF8800);
        }
        g.disableScissor();
        super.render(g, mx, my, pt);
    }

    // ==================== 输入 ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) { onClose(); return true; }
        if (keyCode == GLFW.GLFW_KEY_S && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) { confirm(); return true; }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && cursor > 0) {
            code = code.substring(0, cursor - 1) + code.substring(cursor);
            cursor--;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE && cursor < code.length()) {
            code = code.substring(0, cursor) + code.substring(cursor + 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_LEFT) { cursor = Math.max(0, cursor - 1); return true; }
        if (keyCode == GLFW.GLFW_KEY_RIGHT) { cursor = Math.min(code.length(), cursor + 1); return true; }
        if (keyCode == GLFW.GLFW_KEY_UP) { moveCursorVertically(-1); return true; }
        if (keyCode == GLFW.GLFW_KEY_DOWN) { moveCursorVertically(1); return true; }
        if (keyCode == GLFW.GLFW_KEY_HOME) { cursor = 0; return true; }
        if (keyCode == GLFW.GLFW_KEY_END) { cursor = code.length(); return true; }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            code = code.substring(0, cursor) + "\n" + code.substring(cursor);
            cursor++;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 上下移动光标：保持列偏移，在拆行/换行后的行间跳转。 */
    private void moveCursorVertically(int dir) {
        List<Integer> starts = lineStarts(code);
        int[] rc = cursorToRowCol(code, cursor);
        int targetRow = rc[0] + dir;
        if (targetRow < 0 || targetRow >= starts.size()) return;
        List<String> lines = wrapLines(code);
        int col = Math.min(rc[1], lines.get(targetRow).length());
        cursor = starts.get(targetRow) + col;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (c == '\n' || c == '\r') {
            code = code.substring(0, cursor) + "\n" + code.substring(cursor);
            cursor++;
            return true;
        }
        if (c >= 32 && c != 127) {
            code = code.substring(0, cursor) + c + code.substring(cursor);
            cursor++;
            return true;
        }
        return super.charTyped(c, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hScroll, double vScroll) {
        scroll = Math.max(0, scroll - (int) Math.signum(vScroll) * 12);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int gx = textAreaX(), gy = textAreaY(), gw = textAreaW(), gh = textAreaH();
        if (mx >= gx && mx < gx + gw && my >= gy && my < gy + gh) {
            int px = gx + 4;
            int lx = Math.max(0, (int) (mx - px));
            int ly = Math.max(0, (int) (my - (gy + 2 - scroll)));
            int row = ly / ROW_H;
            List<String> lines = wrapLines(code);
            if (row >= lines.size()) row = Math.max(0, lines.size() - 1);
            String lineText = lines.get(row);
            int col = font.getSplitter().plainIndexAtWidth(lineText, lx, Style.EMPTY);
            int pos = rowToTextOffset(code, row) + col;
            cursor = Math.min(code.length(), pos);
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
