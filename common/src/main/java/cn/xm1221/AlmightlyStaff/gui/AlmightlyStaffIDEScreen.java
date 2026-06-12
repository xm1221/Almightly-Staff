package cn.xm1221.AlmightlyStaff.gui;

import at.petrak.hexcasting.api.casting.eval.ResolvedPatternType;
import at.petrak.hexcasting.api.casting.iota.*;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import at.petrak.hexcasting.client.gui.GuiSpellcasting;
import cn.xm1221.AlmightlyStaff.network.ModNetworking;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class AlmightlyStaffIDEScreen extends Screen {

    private static final int GRID_W = 232, ROW_H = 12, TOP_H = 22, BOT_H = 15;

    private final List<Iota> iotas = new ArrayList<>();
    private int cursor, selA = -1, selB = -1, scroll;
    private boolean writeMode = true;
    private GuiSpellcasting spellcasting;
    private boolean awaitingRead;
    private static final List<Iota> clipboard = new ArrayList<>();
    private record T(int idx, int x, int y, int w) {}
    private final List<T> tokens = new ArrayList<>();

    // Parse mode
    private boolean parseMode;
    private String parseCode = "";
    private int parseCursor, parseScroll;

    public AlmightlyStaffIDEScreen() { super(Component.translatable("almightly_staff.ide.title")); }
    @SuppressWarnings("unchecked") private static <X> X as(Object o) { return (X) o; }

    @Override protected void init() {
        super.init();
        ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffReadC2S());
        awaitingRead = true;
        spellcasting = new GuiSpellcasting(InteractionHand.MAIN_HAND, new ArrayList<>(), List.of(), null, 1);
        spellcasting.init(minecraft, width, height);
        IdeSpellcastingAccess ia = as(spellcasting);
        ia.setIdeWriteMode$ide(true);
        ia.setOnDrawPattern$ide((p, idx) -> {
            if (writeMode) { ins(new PatternIota(p)); sync(); ia.setPatternType$ide(ia.patternCount$ide()-1, ResolvedPatternType.ESCAPED); }
        });
    }

    @Override public void onClose() { IdeSpellcastingAccess ia = as(spellcasting); ia.setOnDrawPattern$ide(null); spellcasting.onClose(); super.onClose(); }

    public void onIotaReceived(@Nullable Iota iota) {
        awaitingRead = false; iotas.clear();
        if (iota instanceof ListIota li) for (var x : li.getList()) iotas.add(x);
        else if (iota != null) iotas.add(iota);
        cursor = iotas.size(); selA = selB = -1; scroll = 0; layout();
    }

    public void onEscapeResult(@Nullable Iota iota) {
        if (iota != null) ins(iota); sync();
        IdeSpellcastingAccess ia = as(spellcasting);
        int n = ia.patternCount$ide(); if (n > 0) ia.setPatternType$ide(n - 1, ResolvedPatternType.ESCAPED);
    }

    public void onPatternEvaluated(ResolvedPatternType type, int index) { IdeSpellcastingAccess ia = as(spellcasting); ia.setPatternType$ide(index, type); }

    public void onParseCode(String code) { parseCode = code; parseCursor = code.length(); }
    public void onParseResult(@Nullable Iota iota) {
        if (iota == null) return; iotas.clear();
        if (iota instanceof ListIota li) for (var x : li.getList()) iotas.add(x);
        else iotas.add(iota);
        cursor = iotas.size(); selA = selB = -1; layout(); sync();
        parseMode = false; // 解析完切回施法网格
    }

    private void ins(Iota i) { iotas.add(cursor, i); cursor++; selA = selB = -1; layout(); }

    private boolean hasSel() { return selA >= 0 && selB >= 0 && selA != selB; }
    private int lo() { return Math.min(selA, selB); }
    private int hi() { return Math.max(selA, selB); }

    private int lx() { return 4; }
    private int lw() { return width - GRID_W - 8; }

    private void layout() {
        tokens.clear();
        int x = lx(), y = TOP_H + 2, mx = lx() + lw() - 4;
        for (int i = 0; i < iotas.size(); i++) {
            int w = font.width(iotas.get(i).display().getString());
            if (x + w > mx && x > lx()) { x = lx(); y += ROW_H; }
            tokens.add(new T(i, x, y, w)); x += w;
        }
    }

    @Override public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int lt = TOP_H + 2, lb = height - BOT_H - 2, ilx = lx(), ilw = lw();

        g.fill(0, 0, width, TOP_H, 0xCC_222222);
        g.renderFakeItem(minecraft.player.getMainHandItem(), 2, 3);
        g.drawString(font, "§eStaff IDE §7[" + iotas.size() + "]", 22, 5, 0xFFFFFFFF);
        topBtn(g, "<", 0, mx, my); topBtn(g, ">", 1, mx, my);
        topBtn(g, "Cast", 2, mx, my); topBtn(g, writeMode ? "W" : "C", 3, mx, my);
        topBtn(g, "Clr", 4, mx, my); topBtn(g, parseMode?"Edit":"Parse", 5, mx, my);

        for (var t : tokens.toArray(new T[0])) {
            int ty = t.y - scroll * ROW_H; if (ty < lt || ty >= lb) continue;
            boolean is = hasSel() && t.idx >= lo() && t.idx < hi();
            boolean ic = t.idx == cursor && !hasSel();
            if (is) g.fill(t.x - 1, ty, t.x + t.w + 1, ty + ROW_H, 0x55_44aaff);
            else if (ic) g.fill(t.x - 1, ty, t.x + t.w + 1, ty + ROW_H, 0xBB_ff8844);
            else if (mx >= t.x && mx < t.x + t.w && my >= ty && my < ty + ROW_H) g.fill(t.x - 1, ty, t.x + t.w + 1, ty + ROW_H, 0x11_ffffff);
            g.drawString(font, iotas.get(t.idx).display(), t.x, ty + 2, 0xFFFFFFFF);
        }
        if (iotas.isEmpty() && !awaitingRead) g.drawString(font, "§8|", ilx, lt, 0xFF888888);

        int tr = tokens.isEmpty() ? 1 : (tokens.get(tokens.size()-1).y - lt) / ROW_H + 1;
        int vr = Math.max(1, (lb - lt) / ROW_H), ms = Math.max(0, tr - vr);
        if (ms > 0) { int bh = Math.max(16, vr*(lb-lt)/tr), by = lt + scroll*((lb-lt)-bh)/ms; g.fill(ilx+ilw-2, by, ilx+ilw, by+bh, 0x66_ffffff); }

        // 右侧面板
        int gx = ilx + ilw + 4, gw = GRID_W, gh = height - BOT_H - TOP_H;
        g.fill(gx, TOP_H, gx + gw, height - BOT_H, 0x22_000000);
        if (parseMode) {
            renderParseArea(g, gx, TOP_H, gw, gh, mx, my);
        } else {
            g.fill(gx, TOP_H, gx + gw, TOP_H + 1, 0x44_ffffff);
            g.fill(gx, height - BOT_H - 1, gx + gw, height - BOT_H, 0x44_ffffff);
            g.drawCenteredString(font, writeMode ? "§aWRITE" : "§cCAST", gx+gw/2, TOP_H+2, 0xFFFFFFFF);
            RenderSystem.enableBlend(); g.enableScissor(gx, TOP_H, gx+gw, height-BOT_H); spellcasting.render(g, mx, my, pt); g.disableScissor(); RenderSystem.disableBlend();
        }

        g.fill(0, height - BOT_H, width, height, 0xCC_222222);
        String st = awaitingRead ? "§7Loading..." : "§7cur:"+cursor+(hasSel()?" sel:"+(hi()-lo()):"")+" ["+iotas.size()+"]";
        g.drawString(font, Component.literal(st), 4, height - BOT_H + 4, 0xFFAAAAAA);
        botBtn(g, "Spc", 0, mx, my); botBtn(g, "NL", 1, mx, my);
        botBtn(g, "◀", 2, mx, my); botBtn(g, "▶", 3, mx, my);
        botBtn(g, "C", 4, mx, my); botBtn(g, "V", 5, mx, my); botBtn(g, "X", 6, mx, my); botBtn(g, "Del", 7, mx, my);
        super.render(g, mx, my, pt);
    }

    // 多行文本渲染（支持光标定位和选中）
    private record PL(int start, int end, int y) {}
    private List<PL> parseLines = new ArrayList<>();
    private int parseSelA = -1, parseSelB = -1;

    private void buildParseLines(int pw) {
        parseLines.clear();
        int pos = 0, y = 0, lh = font.lineHeight + 2;
        while (pos < parseCode.length()) {
            int lineEnd = pos;
            while (lineEnd < parseCode.length() && font.width(parseCode.substring(pos, lineEnd + 1)) <= pw) lineEnd++;
            if (lineEnd == pos) lineEnd = pos + 1; // 至少一个字符
            parseLines.add(new PL(pos, lineEnd, y));
            pos = lineEnd; y += lh;
        }
    }

    private int parsePosAt(int px, double mx, int lineIdx) {
        if (lineIdx < 0 || lineIdx >= parseLines.size()) return parseCode.length();
        var l = parseLines.get(lineIdx);
        String lineText = parseCode.substring(l.start, l.end);
        for (int i = 0; i <= lineText.length(); i++) {
            if (font.width(lineText.substring(0, i)) >= mx - px) return l.start + i;
        }
        return l.end;
    }

    private void renderParseArea(GuiGraphics g, int gx, int gy, int gw, int gh, int mx, int my) {
        int px = gx + 2, py = gy + 2, pw = gw - 6, lh = font.lineHeight + 2;
        buildParseLines(pw);
        int maxScroll = Math.max(0, parseLines.size() * lh - gh + 4);
        if (parseScroll > maxScroll) parseScroll = maxScroll;
        if (maxScroll > 0) {
            int bh = Math.max(16, (gh-4)*(gh-4)/(parseLines.size()*lh));
            g.fill(gx+gw-4, gy+2+parseScroll*(gh-4-bh)/maxScroll, gx+gw-2, gy+2+parseScroll*(gh-4-bh)/maxScroll+bh, 0x66_ffffff);
        }
        g.enableScissor(gx, gy, gx+gw, gy+gh);
        for (var l : parseLines) {
            int ly = py - parseScroll + l.y;
            if (ly+lh < gy || ly > gy+gh) continue;
            String lt = parseCode.substring(l.start, l.end);
            // 选中高亮
            if (parseSelA >= 0 && parseSelB >= 0) {
                int s = Math.min(parseSelA, parseSelB), e = Math.max(parseSelA, parseSelB);
                if (l.start < e && l.end > s) {
                    int hx1 = px + font.width(lt.substring(0, Math.max(0, s-l.start)));
                    int hx2 = px + font.width(lt.substring(0, Math.min(lt.length(), e-l.start)));
                    g.fill(hx1, ly, hx2, ly+lh, 0x55_44aaff);
                }
            }
            g.drawString(font, Component.literal(lt), px, ly, 0xFFFFFFFF);
            // 光标
            if (parseCursor >= l.start && parseCursor <= l.end) {
                int cx = px + (parseCursor > l.start ? font.width(lt.substring(0, parseCursor-l.start)) : 0);
                g.fill(cx, ly, cx+1, ly+lh, 0xFFFF8800);
            }
        }
        // 光标在末尾
        if (parseCursor >= parseCode.length() && !parseLines.isEmpty()) {
            var ll = parseLines.get(parseLines.size()-1);
            int ly = py - parseScroll + ll.y;
            int cx = px + font.width(parseCode.substring(ll.start, ll.end));
            g.fill(cx, ly, cx+1, ly+lh, 0xFFFF8800);
        }
        g.disableScissor();
    }

    private void topBtn(GuiGraphics g, String s, int slot, int mx, int my) {
        int bx = 220 + slot * 34; g.fill(bx, 1, bx + 32, TOP_H - 1, mx >= bx && mx < bx + 32 && my >= 0 && my < TOP_H ? 0xBB_ff8844 : 0x44_333333);
        g.drawCenteredString(font, s, bx + 16, 4, 0xFFFFFFFF);
    }

    private void botBtn(GuiGraphics g, String s, int slot, int mx, int my) {
        int bx = 100 + slot * 28, by = height - BOT_H;
        g.fill(bx, by + 1, bx + 26, by + BOT_H - 1, mx >= bx && mx < bx + 26 && my >= by && my < by + BOT_H ? 0xBB_ff8844 : 0x44_444444);
        g.drawCenteredString(font, "§8" + s, bx + 13, by + 4, 0xFFFFFFFF);
    }

    private int at(double mx, double my) {
        int ay = (int)my + scroll * ROW_H;
        for (int i = tokens.size() - 1; i >= 0; i--) { var t = tokens.get(i); if (ay >= t.y && ay < t.y + ROW_H && mx >= t.x && mx < t.x + t.w) return t.idx; }
        if (!tokens.isEmpty()) { var t = tokens.get(tokens.size()-1); if (ay >= t.y && ay < t.y + ROW_H && mx >= t.x + t.w) return iotas.size(); if (ay > t.y + ROW_H) return iotas.size(); }
        return -1;
    }

    private boolean ig(double mx, double my) { return !parseMode && mx >= lx()+lw()+4 && mx < lx()+lw()+4+GRID_W && my >= TOP_H && my < height-BOT_H; }

    @Override public boolean mouseClicked(double mx, double my, int btn) {
        if (ig(mx, my)) { spellcasting.mouseClicked(mx, my, btn); return true; }
        if (parseMode && mx >= lx()+lw()+4 && my >= TOP_H && my < height - BOT_H) { parseIntClick(mx, my); return true; }
        if (btn == 0 && my < TOP_H) {
            int s = (int)((mx - 220) / 34);
            if (s == 0 || s == 1) { ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageC2S(s == 0 ? -1 : 1)); awaitingRead = true; }
            else if (s == 2) ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffCastC2S());
            else if (s == 3) { writeMode = !writeMode; IdeSpellcastingAccess ia = as(spellcasting); ia.setIdeWriteMode$ide(writeMode); }
            else if (s == 4) { IdeSpellcastingAccess ia = as(spellcasting); ia.clearPatterns$ide(); }
            else if (s == 5) { toggleParse(); }
            return true;
        }
        if (btn == 0 && my >= height - BOT_H) {
            int s = (int)((mx - 100) / 28);
            if (s == 0) { insComment(" "); } else if (s == 1) { insComment("\n"); }
            else if (s == 2) { cursor = Math.max(0, cursor - 1); selA = selB = -1; }
            else if (s == 3) { cursor = Math.min(iotas.size(), cursor + 1); selA = selB = -1; }
            else if (s == 4) cp(); else if (s == 5) ps(); else if (s == 6) { cp(); dl(); } else if (s == 7) dl();
            return true;
        }
        if (btn == 0) {
            int idx = at(mx, my);
            if (idx >= 0 && idx <= iotas.size()) {
                if (hasShiftDown()) { if (selA < 0) selA = cursor; selB = idx; cursor = idx; }
                else { cursor = idx; selA = selB = -1; }
                return true;
            }
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void parseIntClick(double mx, double my) {
        int lh = font.lineHeight + 2, py = TOP_H + 2;
        int lineIdx = ((int)my - py + parseScroll) / lh;
        parseCursor = parsePosAt(4 + lx() + lw() + 4 + 2, mx, lineIdx);
        if (Screen.hasShiftDown()) {
            if (parseSelA < 0) parseSelA = parseSelB = parseCursor;
            parseSelB = parseCursor;
        } else {
            parseSelA = parseSelB = -1;
        }
    }

    private boolean parseHasSel() { return parseSelA >= 0 && parseSelB >= 0 && parseSelA != parseSelB; }
    private int parseSelMin() { return Math.min(parseSelA, parseSelB); }
    private int parseSelMax() { return Math.max(parseSelA, parseSelB); }

    @Override public boolean mouseScrolled(double mx, double my, double d) {
        if (parseMode && mx >= lx()+lw()+4) { parseScroll = Math.max(0, parseScroll - (int)Math.signum(d) * 12); return true; }
        if (ig(mx, my)) { spellcasting.mouseScrolled(mx, my, d); return true; }
        int vr = Math.max(1, (height - TOP_H - BOT_H - 2) / ROW_H);
        int tr = tokens.isEmpty() ? 1 : (tokens.get(tokens.size()-1).y - TOP_H - 2) / ROW_H + 1;
        scroll = Math.max(0, Math.min(scroll - (int)Math.signum(d), Math.max(0, tr - vr)));
        return true;
    }

    @Override public boolean mouseDragged(double mx, double my, int b, double dx, double dy) { return ig(mx, my) ? spellcasting.mouseDragged(mx, my, b, dx, dy) : super.mouseDragged(mx, my, b, dx, dy); }
    @Override public boolean mouseReleased(double mx, double my, int b) { spellcasting.mouseReleased(mx, my, b); return super.mouseReleased(mx, my, b); }

    private void toggleParse() {
        parseMode = !parseMode;
        if (parseMode) {
            // 进入 Parse：发服务端求代码，先显示 Loading
            parseCode = "Loading...";
            parseCursor = 0; parseScroll = 0;
            ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgParseToCodeC2S(iotas));
        } else {
            // 退出 Parse：发服务端解析代码，等待回传后切换
            ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgParseToIotasC2S(parseCode));
        }
    }

    private void insComment(String s) { try { var ci = (Iota)Class.forName("io.yukkuric.hexparse.hooks.CommentIota").getConstructor(String.class).newInstance(s); ins(ci); sync(); } catch(Exception ignored){} }

    @Override public boolean keyPressed(int k, int sc, int m) {
        if (k == GLFW.GLFW_KEY_ESCAPE) { if (parseMode) { toggleParse(); return true; } onClose(); return true; }
        if (parseMode) {
            // 选中删除优先
            if ((k == GLFW.GLFW_KEY_BACKSPACE || k == GLFW.GLFW_KEY_DELETE) && parseHasSel()) {
                int s = parseSelMin(), e = parseSelMax();
                parseCode = parseCode.substring(0, s) + parseCode.substring(e);
                parseCursor = s; parseSelA = parseSelB = -1; return true;
            }
            if (k == GLFW.GLFW_KEY_BACKSPACE && parseCursor > 0) { parseCode = parseCode.substring(0, parseCursor-1) + parseCode.substring(parseCursor); parseCursor--; parseSelA = parseSelB = -1; return true; }
            if (k == GLFW.GLFW_KEY_DELETE && parseCursor < parseCode.length()) { parseCode = parseCode.substring(0, parseCursor) + parseCode.substring(parseCursor+1); parseSelA = parseSelB = -1; return true; }
            if (k == GLFW.GLFW_KEY_LEFT) { parseCursor = Math.max(0, parseCursor - 1); parseSelA = parseSelB = -1; return true; }
            if (k == GLFW.GLFW_KEY_RIGHT) { parseCursor = Math.min(parseCode.length(), parseCursor + 1); parseSelA = parseSelB = -1; return true; }
            if (k == GLFW.GLFW_KEY_HOME) { parseCursor = 0; parseSelA = parseSelB = -1; return true; }
            if (k == GLFW.GLFW_KEY_END) { parseCursor = parseCode.length(); parseSelA = parseSelB = -1; return true; }
            if (k == GLFW.GLFW_KEY_A && (m & GLFW.GLFW_MOD_CONTROL) != 0) { parseSelA = 0; parseSelB = parseCode.length(); return true; }
            if (k == GLFW.GLFW_KEY_C && (m & GLFW.GLFW_MOD_CONTROL) != 0) return true;
            if (k == GLFW.GLFW_KEY_V && (m & GLFW.GLFW_MOD_CONTROL) != 0) return true;
            if (k == GLFW.GLFW_KEY_X && (m & GLFW.GLFW_MOD_CONTROL) != 0) return true;
            return false;
        }
        if (spellcasting.keyPressed(k, sc, m)) return true;
        boolean c = (m & GLFW.GLFW_MOD_CONTROL) != 0, sh = (m & GLFW.GLFW_MOD_SHIFT) != 0;
        if (c && k == GLFW.GLFW_KEY_C) { cp(); return true; } if (c && k == GLFW.GLFW_KEY_V) { ps(); return true; }
        if (c && k == GLFW.GLFW_KEY_X) { cp(); dl(); return true; } if (c && k == GLFW.GLFW_KEY_S) { sync(); return true; }
        if (c && k == GLFW.GLFW_KEY_K) { IdeSpellcastingAccess ia = as(spellcasting); ia.clearPatterns$ide(); return true; }
        if (k == GLFW.GLFW_KEY_DELETE) { dl(); return true; }
        if (k == GLFW.GLFW_KEY_BACKSPACE) { if (hasSel()) dl(); else if (cursor > 0) { iotas.remove(--cursor); selA = selB = -1; layout(); sync(); } return true; }
        if (sh && k == GLFW.GLFW_KEY_LEFT) { sc(sh, Math.max(0, cursor - 1)); return true; }
        if (sh && k == GLFW.GLFW_KEY_RIGHT) { sc(sh, Math.min(iotas.size(), cursor + 1)); return true; }
        if (!sh && (k == GLFW.GLFW_KEY_LEFT || k == GLFW.GLFW_KEY_A)) { cursor = Math.max(0, cursor - 1); selA = selB = -1; return true; }
        if (!sh && (k == GLFW.GLFW_KEY_RIGHT || k == GLFW.GLFW_KEY_D)) { cursor = Math.min(iotas.size(), cursor + 1); selA = selB = -1; return true; }
        if (k == GLFW.GLFW_KEY_PAGE_UP || k == GLFW.GLFW_KEY_Q) { ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageC2S(-1)); awaitingRead = true; return true; }
        if (k == GLFW.GLFW_KEY_PAGE_DOWN || k == GLFW.GLFW_KEY_E) { ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffPageC2S(1)); awaitingRead = true; return true; }
        return super.keyPressed(k, sc, m);
    }

    @Override public boolean charTyped(char c, int mods) {
        if (parseMode && c >= 32 && c != 127) { parseCode = parseCode.substring(0, parseCursor) + c + parseCode.substring(parseCursor); parseCursor++; return true; }
        return super.charTyped(c, mods);
    }

    private void sc(boolean sh, int nc) { if (selA < 0) selA = cursor; cursor = nc; selB = nc; }
    private void cp() { clipboard.clear(); if (hasSel()) for (int i = lo(); i < hi(); i++) clipboard.add(iotas.get(i)); else if (cursor < iotas.size()) clipboard.add(iotas.get(cursor)); }
    private void ps() { if (clipboard.isEmpty()) return; for (Iota i : clipboard) iotas.add(cursor++, i); selA = selB = -1; layout(); sync(); }
    private void dl() { if (hasSel()) { for (int i = hi() - 1; i >= lo(); i--) iotas.remove(i); cursor = lo(); selA = selB = -1; } else if (cursor < iotas.size()) iotas.remove(cursor); layout(); sync(); }
    private void sync() { Iota w = iotas.isEmpty() ? null : iotas.size() == 1 ? iotas.get(0) : new ListIota(new ArrayList<>(iotas)); ModNetworking.CHANNEL.sendToServer(new ModNetworking.MsgStaffWriteC2S(w)); }
    @Override public boolean isPauseScreen() { return false; }
}
