package cn.xm1221.AlmightlyStaff.gui.ide.tool;

import at.petrak.hexcasting.api.casting.iota.DoubleIota;
import at.petrak.hexcasting.api.casting.iota.Iota;
import at.petrak.hexcasting.api.casting.iota.IotaType;
import at.petrak.hexcasting.api.casting.iota.PatternIota;
import at.petrak.hexcasting.api.casting.math.HexDir;
import at.petrak.hexcasting.api.casting.math.HexPattern;
import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.FocusManager;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.gui.ide.model.IdeSession;
import cn.xm1221.AlmightlyStaff.gui.ide.model.SpellDocument;
import cn.xm1221.AlmightlyStaff.spell.PatternCatalogEntry;
import cn.xm1221.AlmightlyStaff.spell.StaffHex;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 独立 HexParse 多行代码窗口：
 * <ul>
 *   <li>显式应用策略：输入不触发 200ms 自动覆写；通过「应用」按钮 / Ctrl+Enter / 失焦且语法合法时写回 {@link SpellDocument}</li>
 *   <li>支持鼠标拖拽选区、13px 舒适行高、词法高亮、括号匹配、Tab 自动补全（不劫持 Enter 换行）</li>
 * </ul>
 */
public final class HexParseEditorPanel implements IdeComponent, IdeSession.Listener {
    private static final int TOOLBAR_H = 18;
    private static final int GUTTER_W = 28;
    private static final int ROW_H = 13;
    private static final int STATUS_H = 16;

    private record WrappedRow(int logicalLine, int startOffset, int endOffset, String text) {
    }

    private record Candidate(String insertText, String displayLabel) {
    }

    private final IdeSession session;
    private final FocusManager focusManager;
    private final Runnable onDocumentSynced;

    private Bounds bounds = Bounds.EMPTY;
    @Nullable
    private SpellDocument boundDoc;
    private String code = "";
    private int textVersion = 0;
    private int cachedVersion = -1;
    private int cachedEditW = -1;
    private final List<WrappedRow> cachedRows = new ArrayList<>();

    private final TextFieldHelper helper;
    private int scrollRow = 0;
    private boolean suppressTextEvents = false;
    private boolean hasPendingDraft = false;
    private boolean draggingSelection = false;
    private int latestRequestId = 0;

    private int errorLine = -1;
    private String errorText = "";

    private final List<Candidate> completions = new ArrayList<>();
    private int completionIdx = 0;
    private boolean completionOpen = false;

    public HexParseEditorPanel(IdeSession session, FocusManager focusManager, Runnable onDocumentSynced) {
        this.session = session;
        this.focusManager = focusManager;
        this.onDocumentSynced = onDocumentSynced;
        this.session.addListener(this);
        this.helper = new TextFieldHelper(
            () -> code,
            this::onTextReplacedByHelper,
            TextFieldHelper.createClipboardGetter(Minecraft.getInstance()),
            TextFieldHelper.createClipboardSetter(Minecraft.getInstance()),
            s -> s.length() <= 65536
        );
    }

    private void onTextReplacedByHelper(String next) {
        if (next.equals(code)) return;
        code = next;
        textVersion++;
        if (!suppressTextEvents) {
            hasPendingDraft = true;
            HexParseLexer.BracketReport br = HexParseLexer.checkBrackets(code);
            if (!br.balanced()) {
                errorLine = br.errorLine();
                errorText = br.errorMessage();
            } else {
                errorLine = -1;
                errorText = "";
            }
            updateCompletions();
        }
    }

    public void bindDocument(@Nullable SpellDocument doc, boolean forceRefresh) {
        if (doc == null) {
            boundDoc = null;
            setCodeSilently("");
            errorLine = -1;
            errorText = "";
            completionOpen = false;
            return;
        }
        boolean changed = boundDoc != doc;
        boundDoc = doc;
        if (changed || forceRefresh) {
            requestCodeFromDocument(doc);
        }
    }

    public void onDocumentChanged(SpellDocument doc, String source) {
        if (doc != boundDoc || "parse".equals(source)) return;
        requestCodeFromDocument(doc);
    }

    private void requestCodeFromDocument(SpellDocument doc) {
        if (Platform.isModLoaded("hexparse")) {
            int req = ++latestRequestId;
            session.network().requestIdeParseToCode(req, doc.pageIndex(), doc.copyIotas());
        } else {
            setCodeSilently(formatTagsOffline(doc.iotasView()));
            errorLine = -1;
            errorText = "";
        }
    }

    private void setCodeSilently(String next) {
        suppressTextEvents = true;
        code = next == null ? "" : next;
        textVersion++;
        helper.setCursorPos(Math.min(helper.getCursorPos(), code.length()), false);
        hasPendingDraft = false;
        completionOpen = false;
        suppressTextEvents = false;
    }

    @Override
    public void onParseToCodeResult(int requestId, int pageIndex, String parsedCode, String error) {
        if (boundDoc == null || pageIndex != boundDoc.pageIndex() || requestId != latestRequestId) return;
        if (error != null && !error.isEmpty()) {
            errorLine = -1;
            errorText = error;
            return;
        }
        errorLine = -1;
        errorText = "";
        setCodeSilently(parsedCode);
    }

    @Override
    public void onParseToIotasResult(int requestId, int pageIndex, boolean ok,
                                       List<CompoundTag> tags, String error, int errLine) {
        if (boundDoc == null || pageIndex != boundDoc.pageIndex() || requestId != latestRequestId) return;
        if (!ok) {
            errorLine = errLine > 0 ? errLine : 1;
            errorText = error == null || error.isEmpty()
                ? Component.translatable("almightly_staff.ide.parse_error").getString()
                : error;
            return;
        }
        errorLine = -1;
        errorText = "";
        hasPendingDraft = false;
        if (!boundDoc.isSealed() && boundDoc.replaceAll(tags, "parse", true)) {
            onDocumentSynced.run();
        }
    }

    private Bounds toolbarBounds() {
        return new Bounds(bounds.x(), bounds.y(), bounds.w(), Math.min(TOOLBAR_H, bounds.h()));
    }

    private Bounds editorBodyBounds() {
        return bounds.inset(0, TOOLBAR_H, 0, STATUS_H);
    }

    private Bounds textContentBounds() {
        return editorBodyBounds().inset(GUTTER_W + 4, 3, IdeTheme.SCROLLBAR_WIDTH + 2, 3);
    }

    private int editWidth() {
        return Math.max(40, textContentBounds().w());
    }

    private void ensureWrapCache(Font font) {
        int ew = editWidth();
        if (cachedVersion == textVersion && cachedEditW == ew) return;
        cachedVersion = textVersion;
        cachedEditW = ew;
        cachedRows.clear();

        if (code.isEmpty()) {
            cachedRows.add(new WrappedRow(1, 0, 0, ""));
            return;
        }
        var splitter = font.getSplitter();
        int len = code.length();
        int pos = 0;
        int logicalLine = 1;
        while (pos < len) {
            int nl = code.indexOf('\n', pos);
            int lineEnd = nl >= 0 ? nl : len;
            String logicalText = code.substring(pos, lineEnd);
            if (logicalText.isEmpty()) {
                cachedRows.add(new WrappedRow(logicalLine, pos, pos, ""));
            } else {
                int local = 0;
                while (local < logicalText.length()) {
                    String rem = logicalText.substring(local);
                    int fit = splitter.plainIndexAtWidth(rem, ew, Style.EMPTY);
                    if (fit <= 0) fit = 1;
                    int segEnd = Math.min(logicalText.length(), local + fit);
                    cachedRows.add(new WrappedRow(logicalLine, pos + local, pos + segEnd,
                        logicalText.substring(local, segEnd)));
                    local = segEnd;
                }
            }
            if (nl >= 0) {
                pos = nl + 1;
                logicalLine++;
                if (pos == len) {
                    cachedRows.add(new WrappedRow(logicalLine, len, len, ""));
                }
            } else {
                break;
            }
        }
    }

    private int rowForOffset(int offset) {
        for (int i = 0; i < cachedRows.size(); i++) {
            WrappedRow r = cachedRows.get(i);
            boolean lastOfLogical = (i + 1 >= cachedRows.size()) || cachedRows.get(i + 1).logicalLine() != r.logicalLine();
            if (offset >= r.startOffset() && (offset < r.endOffset() || (lastOfLogical && offset == r.endOffset()))) {
                return i;
            }
        }
        return Math.max(0, cachedRows.size() - 1);
    }

    private int offsetAtPoint(Font font, double mx, double my) {
        ensureWrapCache(font);
        Bounds textArea = textContentBounds();
        int vi = Math.max(0, (int) ((my - textArea.y()) / ROW_H));
        int rIdx = Mth.clamp(scrollRow + vi, 0, Math.max(0, cachedRows.size() - 1));
        WrappedRow row = cachedRows.get(rIdx);
        int lx = Math.max(0, (int) (mx - textArea.x()));
        int col = font.getSplitter().plainIndexAtWidth(row.text(), lx, Style.EMPTY);
        return Math.min(code.length(), row.startOffset() + col);
    }

    private void ensureCursorVisible(Font font) {
        ensureWrapCache(font);
        int visible = Math.max(1, textContentBounds().h() / ROW_H);
        int curRow = rowForOffset(helper.getCursorPos());
        if (curRow < scrollRow) scrollRow = curRow;
        if (curRow >= scrollRow + visible) scrollRow = curRow - visible + 1;
        scrollRow = Mth.clamp(scrollRow, 0, Math.max(0, cachedRows.size() - visible));
    }

    @Override
    public Bounds bounds() {
        return bounds;
    }

    @Override
    public void layout(Bounds bounds) {
        this.bounds = bounds;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (bounds.isEmpty()) return;
        Font font = Minecraft.getInstance().font;
        ensureWrapCache(font);
        IdeTheme.drawNineSlice(g, bounds, IdeTheme.NineSlice.ROOT);

        // 工具条：显式「应用 (Ctrl+Enter)」与「同步还原」按钮
        Bounds tb = toolbarBounds();
        IdeTheme.drawNineSlice(g, tb, IdeTheme.NineSlice.PANEL);
        try (IdeScissor.Scope sc = IdeScissor.push(g, tb)) {
            String applyTxt = Component.translatable("almightly_staff.ide.parse_apply").getString();
            int applyW = font.width(applyTxt) + 20;
            Bounds applyB = new Bounds(tb.x() + 4, tb.y() + 2, applyW, 14);
            boolean hovA = applyB.contains(mouseX, mouseY);
            IdeTheme.drawNineSlice(g, applyB, hasPendingDraft
                ? IdeTheme.NineSlice.BTN_ACTIVE
                : (hovA ? IdeTheme.NineSlice.BTN_HOVER : IdeTheme.NineSlice.BTN_NORMAL));
            IdeTheme.drawIcon(g, IdeTheme.Icon.CHECK_APPLY, applyB.x() + 4, applyB.y() + 3,
                hasPendingDraft ? IdeTheme.GREEN_CAST : IdeTheme.TEXT_MAIN);
            g.drawString(font, applyTxt, applyB.x() + 14, applyB.y() + 3, IdeTheme.TEXT_MAIN, false);

            String revertTxt = Component.translatable("almightly_staff.ide.parse_revert").getString();
            int revW = font.width(revertTxt) + 10;
            Bounds revB = new Bounds(applyB.right() + 4, tb.y() + 2, revW, 14);
            boolean hovR = revB.contains(mouseX, mouseY);
            IdeTheme.drawNineSlice(g, revB, hovR ? IdeTheme.NineSlice.BTN_HOVER : IdeTheme.NineSlice.BTN_NORMAL);
            g.drawString(font, revertTxt, revB.x() + 5, revB.y() + 3, IdeTheme.TEXT_MUTED, false);
        }

        Bounds body = editorBodyBounds();
        Bounds textArea = textContentBounds();
        g.fill(body.x(), body.y(), body.x() + GUTTER_W, body.bottom(), IdeTheme.BG_PANEL);
        g.fill(body.x() + GUTTER_W, body.y(), body.x() + GUTTER_W + 1, body.bottom(), IdeTheme.BORDER);
        if (focusManager.isFocused(this)) {
            IdeTheme.drawBorder(g, body, IdeTheme.ACCENT);
        }

        int visible = Math.max(1, textArea.h() / ROW_H);
        scrollRow = Mth.clamp(scrollRow, 0, Math.max(0, cachedRows.size() - visible));

        int cursor = Math.min(helper.getCursorPos(), code.length());
        int selPos = Math.min(helper.getSelectionPos(), code.length());
        int selStart = Math.min(cursor, selPos);
        int selEnd = Math.max(cursor, selPos);
        int matchBrace = HexParseLexer.findMatchingBrace(code, cursor);

        try (IdeScissor.Scope sc = IdeScissor.push(g, body.x(), body.y(), Math.max(0, body.w() - IdeTheme.SCROLLBAR_WIDTH), body.h())) {
            int lastLogical = -1;
            for (int vi = 0; vi < visible; vi++) {
                int rIdx = vi + scrollRow;
                if (rIdx >= cachedRows.size()) break;
                WrappedRow row = cachedRows.get(rIdx);
                int y = textArea.y() + vi * ROW_H;

                if (errorLine > 0 && row.logicalLine() == errorLine) {
                    g.fill(textArea.x() - 2, y, textArea.right(), y + ROW_H, 0x33E05555);
                }
                if (row.logicalLine() != lastLogical) {
                    lastLogical = row.logicalLine();
                    String ln = Integer.toString(row.logicalLine());
                    int lnCol = (errorLine > 0 && row.logicalLine() == errorLine) ? IdeTheme.RED_ERROR : IdeTheme.TEXT_MUTED;
                    g.drawString(font, ln, body.x() + GUTTER_W - font.width(ln) - 3, y + 2, lnCol, false);
                }

                if (selStart < selEnd && selEnd > row.startOffset() && selStart < row.endOffset()) {
                    int ls = Math.max(0, selStart - row.startOffset());
                    int le = Math.min(row.text().length(), selEnd - row.startOffset());
                    int sx1 = textArea.x() + font.width(row.text().substring(0, ls));
                    int sx2 = textArea.x() + font.width(row.text().substring(0, le));
                    g.fill(sx1, y, sx2, y + ROW_H, 0x772E436E);
                }

                if (matchBrace >= row.startOffset() && matchBrace < row.endOffset()) {
                    int local = matchBrace - row.startOffset();
                    int bx = textArea.x() + font.width(row.text().substring(0, local));
                    int bw = Math.max(4, font.width(row.text().substring(local, local + 1)));
                    g.fill(bx, y, bx + bw, y + ROW_H, 0x559C6ADE);
                }

                renderHighlightedLine(g, font, row.text(), textArea.x(), y + 2);
            }

            if (focusManager.isFocused(this)) {
                int curRow = rowForOffset(cursor);
                int vi = curRow - scrollRow;
                if (vi >= 0 && vi < visible && curRow < cachedRows.size()) {
                    WrappedRow r = cachedRows.get(curRow);
                    int local = Mth.clamp(cursor - r.startOffset(), 0, r.text().length());
                    int cx = textArea.x() + font.width(r.text().substring(0, local));
                    int cy = textArea.y() + vi * ROW_H;
                    g.fill(cx, cy + 1, cx + 1, cy + ROW_H - 1, 0xFFFF9E64);
                }
            }
        }

        IdeTheme.drawScrollbar(g, body.right() - IdeTheme.SCROLLBAR_WIDTH, body.y(), body.h(),
            cachedRows.size(), visible, scrollRow);

        Bounds sb = new Bounds(bounds.x(), body.bottom(), bounds.w(), STATUS_H);
        IdeTheme.drawNineSlice(g, sb, IdeTheme.NineSlice.PANEL);
        try (IdeScissor.Scope sc = IdeScissor.push(g, sb)) {
            String statusMsg;
            int statusCol;
            if (boundDoc == null) {
                statusMsg = Component.translatable("almightly_staff.gui.no_page").getString();
                statusCol = IdeTheme.TEXT_MUTED;
            } else if (boundDoc.isSealed()) {
                statusMsg = Component.translatable("almightly_staff.gui.sealed_readonly").getString();
                statusCol = IdeTheme.GOLD_SEAL_TEXT;
            } else if (!errorText.isEmpty()) {
                statusMsg = errorText;
                statusCol = IdeTheme.RED_ERROR;
            } else if (!Platform.isModLoaded("hexparse")) {
                statusMsg = Component.translatable("almightly_staff.ide.hexparse_offline_ok", boundDoc.size()).getString();
                statusCol = IdeTheme.GOLD_SEAL_TEXT;
            } else {
                statusMsg = Component.translatable("almightly_staff.ide.parse_ok", boundDoc.size()).getString();
                statusCol = IdeTheme.GREEN_CAST;
            }
            g.drawString(font, IdeTheme.trimText(font, statusMsg, Math.max(20, sb.w() - 12)),
                sb.x() + 6, sb.y() + 4, statusCol, false);
        }
    }

    private void renderHighlightedLine(GuiGraphics g, Font font, String text, int startX, int y) {
        if (text.isEmpty()) return;
        List<HexParseLexer.Token> tokens = HexParseLexer.tokenizeLine(text, session.knownIdentifiers());
        int x = startX;
        for (HexParseLexer.Token tok : tokens) {
            int col = switch (tok.type()) {
                case COMMENT -> 0xFF6A9955;
                case BRACE_OPEN, BRACE_CLOSE -> IdeTheme.GOLD_SEAL;
                case ESCAPE -> 0xFFFF9E64;
                case NUMBER, VECTOR -> 0xFF4EC9B0;
                case PATTERN_KNOWN -> IdeTheme.ACCENT_LIGHT;
                case IDENTIFIER -> IdeTheme.TEXT_MAIN;
                case PUNCTUATION, WHITESPACE -> IdeTheme.TEXT_MUTED;
            };
            g.drawString(font, tok.text(), x, y, col, false);
            x += font.width(tok.text());
        }
    }

    @Override
    public void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (!completionOpen || completions.isEmpty() || !focusManager.isFocused(this)) return;
        Font font = Minecraft.getInstance().font;
        ensureWrapCache(font);
        int cursor = Math.min(helper.getCursorPos(), code.length());
        int curRow = rowForOffset(cursor);
        int vi = Math.max(0, curRow - scrollRow);
        Bounds textArea = textContentBounds();
        WrappedRow r = cachedRows.get(Math.min(curRow, cachedRows.size() - 1));
        int local = Mth.clamp(cursor - r.startOffset(), 0, r.text().length());
        int px = textArea.x() + font.width(r.text().substring(0, local));
        int py = textArea.y() + (vi + 1) * ROW_H;

        int popW = 200;
        int popH = completions.size() * 14 + 4;
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        if (px + popW > sw) px = Math.max(4, sw - popW - 4);
        if (py + popH > sh) py = Math.max(4, py - ROW_H - popH);

        PoseStack ps = g.pose();
        ps.pushPose();
        ps.translate(0.0F, 0.0F, 640.0F);
        try {
            IdeTheme.drawNineSlice(g, px, py, popW, popH, IdeTheme.NineSlice.POPUP);
            for (int i = 0; i < completions.size(); i++) {
                int iy = py + 2 + i * 14;
                boolean sel = i == completionIdx;
                if (sel) {
                    g.fill(px + 1, iy, px + popW - 1, iy + 14, IdeTheme.BG_ACTIVE);
                }
                g.drawString(font, IdeTheme.trimText(font, completions.get(i).displayLabel(), popW - 10),
                    px + 5, iy + 3, sel ? 0xFFFFFFFF : IdeTheme.TEXT_MAIN, false);
            }
        } finally {
            ps.popPose();
        }
    }

    private String currentWordPrefix() {
        int cur = Math.min(helper.getCursorPos(), code.length());
        int s = cur;
        while (s > 0) {
            char c = code.charAt(s - 1);
            if (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '/') s--;
            else break;
        }
        return code.substring(s, cur);
    }

    private void updateCompletions() {
        completions.clear();
        String prefix = currentWordPrefix();
        if (prefix.isEmpty()) {
            completionOpen = false;
            return;
        }
        String lp = prefix.toLowerCase(Locale.ROOT);
        addCompletionIfMatch(lp, "{", "{ (Introspection)");
        addCompletionIfMatch(lp, "}", "} (Retrospection)");
        addCompletionIfMatch(lp, "\\", "\\ (Consideration)");
        for (PatternCatalogEntry entry : session.catalog()) {
            if (completions.size() >= 8) break;
            String id = entry.pattern().actionId();
            String shortId = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            String disp = entry.displayName().getString();
            if (shortId.toLowerCase(Locale.ROOT).startsWith(lp)
                || id.toLowerCase(Locale.ROOT).startsWith(lp)
                || disp.toLowerCase(Locale.ROOT).contains(lp)) {
                completions.add(new Candidate(shortId, disp + " (" + shortId + ")"));
            }
        }
        completionIdx = 0;
        completionOpen = !completions.isEmpty();
    }

    private void addCompletionIfMatch(String lp, String token, String label) {
        if (token.startsWith(lp) || label.toLowerCase(Locale.ROOT).contains(lp)) {
            completions.add(new Candidate(token, label));
        }
    }

    private void acceptCompletion() {
        if (!completionOpen || completions.isEmpty()) return;
        Candidate c = completions.get(Mth.clamp(completionIdx, 0, completions.size() - 1));
        String prefix = currentWordPrefix();
        int cur = Math.min(helper.getCursorPos(), code.length());
        int start = cur - prefix.length();
        String next = code.substring(0, start) + c.insertText() + code.substring(cur);
        onTextReplacedByHelper(next);
        helper.setCursorPos(start + c.insertText().length(), false);
        completionOpen = false;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        Bounds tb = toolbarBounds();
        if (tb.contains(mx, my) && button == 0) {
            Font font = Minecraft.getInstance().font;
            String applyTxt = Component.translatable("almightly_staff.ide.parse_apply").getString();
            int applyW = font.width(applyTxt) + 20;
            Bounds applyB = new Bounds(tb.x() + 4, tb.y() + 2, applyW, 14);
            if (applyB.contains(mx, my)) {
                applyParseToDocument();
                return true;
            }
            String revertTxt = Component.translatable("almightly_staff.ide.parse_revert").getString();
            int revW = font.width(revertTxt) + 10;
            Bounds revB = new Bounds(applyB.right() + 4, tb.y() + 2, revW, 14);
            if (revB.contains(mx, my) && boundDoc != null) {
                requestCodeFromDocument(boundDoc);
                return true;
            }
            return true;
        }
        focusManager.focus(this);
        if (button == 0 && textContentBounds().contains(mx, my)) {
            int offset = offsetAtPoint(Minecraft.getInstance().font, mx, my);
            boolean extend = net.minecraft.client.gui.screens.Screen.hasShiftDown();
            helper.setCursorPos(offset, extend);
            draggingSelection = true;
            completionOpen = false;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (button != 0 || !draggingSelection) return false;
        int offset = offsetAtPoint(Minecraft.getInstance().font, mx, my);
        helper.setCursorPos(offset, true);
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && draggingSelection) {
            draggingSelection = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!bounds.contains(mx, my)) return false;
        Font font = Minecraft.getInstance().font;
        ensureWrapCache(font);
        int visible = Math.max(1, textContentBounds().h() / ROW_H);
        int max = Math.max(0, cachedRows.size() - visible);
        scrollRow = Mth.clamp(scrollRow - (int) Math.signum(delta) * 2, 0, max);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!focusManager.isFocused(this)) return false;
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;

        if (ctrl && (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)) {
            applyParseToDocument();
            return true;
        }
        if (completionOpen) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                completionOpen = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_UP) {
                completionIdx = (completionIdx - 1 + completions.size()) % completions.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DOWN) {
                completionIdx = (completionIdx + 1) % completions.size();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_TAB) {
                acceptCompletion();
                return true;
            }
            // Enter 不被补全劫持：关闭补全弹窗并正常换行
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            focusManager.clear();
            return true;
        }
        if (boundDoc != null && boundDoc.isSealed()) {
            if (ctrl && (keyCode == GLFW.GLFW_KEY_C || keyCode == GLFW.GLFW_KEY_A)) {
                return helper.keyPressed(keyCode);
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT
                || keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                || keyCode == GLFW.GLFW_KEY_HOME || keyCode == GLFW.GLFW_KEY_END) {
                return handleNavigationKey(keyCode, modifiers);
            }
            return true;
        }
        if (ctrl && keyCode == GLFW.GLFW_KEY_SPACE) {
            updateCompletions();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            completionOpen = false;
            helper.insertText("\n");
            ensureCursorVisible(Minecraft.getInstance().font);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            return handleNavigationKey(keyCode, modifiers);
        }
        boolean handled = helper.keyPressed(keyCode);
        if (handled) {
            ensureCursorVisible(Minecraft.getInstance().font);
        }
        return handled;
    }

    private boolean handleNavigationKey(int keyCode, int modifiers) {
        Font font = Minecraft.getInstance().font;
        ensureWrapCache(font);
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN) {
            int curRow = rowForOffset(helper.getCursorPos());
            int targetRow = curRow + (keyCode == GLFW.GLFW_KEY_UP ? -1 : 1);
            if (targetRow >= 0 && targetRow < cachedRows.size()) {
                WrappedRow from = cachedRows.get(curRow);
                WrappedRow to = cachedRows.get(targetRow);
                int col = Math.max(0, helper.getCursorPos() - from.startOffset());
                int newPos = Math.min(to.endOffset(), to.startOffset() + col);
                helper.setCursorPos(newPos, shift);
                ensureCursorVisible(font);
            }
            return true;
        }
        return helper.keyPressed(keyCode);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!focusManager.isFocused(this)) return false;
        if (boundDoc != null && boundDoc.isSealed()) return true;
        if (codePoint == '\n' || codePoint == '\r') {
            completionOpen = false;
            helper.insertText("\n");
            ensureCursorVisible(Minecraft.getInstance().font);
            return true;
        }
        boolean handled = helper.charTyped(codePoint);
        if (handled) {
            ensureCursorVisible(Minecraft.getInstance().font);
        }
        return handled;
    }

    @Override
    public void onFocusLost() {
        completionOpen = false;
        if (hasPendingDraft) {
            applyParseToDocument();
        }
    }

    public void applyParseToDocument() {
        if (boundDoc == null || boundDoc.isSealed()) return;
        HexParseLexer.BracketReport br = HexParseLexer.checkBrackets(code);
        if (!br.balanced()) {
            errorLine = br.errorLine();
            errorText = br.errorMessage();
            return;
        }
        if (Platform.isModLoaded("hexparse")) {
            int req = ++latestRequestId;
            session.network().requestIdeParseToIotas(req, boundDoc.pageIndex(), code);
            return;
        }
        List<CompoundTag> parsed = parseOfflineFallback(code);
        if (parsed == null) {
            errorLine = 1;
            errorText = Component.translatable("almightly_staff.gui.hexparse_missing").getString();
            return;
        }
        errorLine = -1;
        errorText = "";
        hasPendingDraft = false;
        if (boundDoc.replaceAll(parsed, "parse", true)) {
            onDocumentSynced.run();
        }
    }

    @Nullable
    private List<CompoundTag> parseOfflineFallback(String src) {
        List<CompoundTag> out = new ArrayList<>();
        String[] rawLines = src.replace("\r\n", "\n").split("\n");
        for (int lineIdx = 0; lineIdx < rawLines.length; lineIdx++) {
            String line = rawLines[lineIdx];
            int cmt = line.indexOf("//");
            if (cmt >= 0) line = line.substring(0, cmt);
            for (String part : line.split("[,;]+")) {
                String tok = part.trim();
                if (tok.isEmpty()) continue;
                CompoundTag tag = resolveOfflineToken(tok);
                if (tag == null) {
                    errorLine = lineIdx + 1;
                    return null;
                }
                out.add(tag);
            }
        }
        return out;
    }

    @Nullable
    private CompoundTag resolveOfflineToken(String tok) {
        if ("{".equals(tok)) {
            return StaffHex.serializeIota(new PatternIota(HexPattern.fromAngleString("qqq", HexDir.WEST, false)));
        }
        if ("}".equals(tok)) {
            return StaffHex.serializeIota(new PatternIota(HexPattern.fromAngleString("eee", HexDir.EAST, false)));
        }
        if ("\\".equals(tok)) {
            return StaffHex.serializeIota(new PatternIota(HexPattern.fromAngleString("qqqaw", HexDir.WEST, false)));
        }
        try {
            double num = Double.parseDouble(tok);
            return StaffHex.serializeIota(new DoubleIota(num));
        } catch (NumberFormatException ignored) {
        }
        String lower = tok.toLowerCase(Locale.ROOT);
        for (PatternCatalogEntry e : session.catalog()) {
            if (e.pattern().signature().isBlank()) continue;
            String id = e.pattern().actionId().toLowerCase(Locale.ROOT);
            String shortId = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            if (id.equals(lower) || shortId.equals(lower)
                || e.displayName().getString().equalsIgnoreCase(tok)) {
                return StaffHex.serializeIota(new PatternIota(StaffHex.patternFor(e.pattern())));
            }
        }
        return null;
    }

    public static String formatTagsOffline(List<CompoundTag> tags) {
        if (tags == null || tags.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) {
            CompoundTag t = tags.get(i);
            String token;
            if (StaffHex.isPatternTag(t)) {
                try {
                    Iota iota = StaffHex.deserializeIota(t);
                    if (iota instanceof PatternIota pi) {
                        var ref = StaffHex.refFor(pi.getPattern());
                        if (!ref.actionId().isEmpty()) {
                            String id = ref.actionId();
                            token = id.startsWith("hexcasting:") ? id.substring("hexcasting:".length()) : id;
                        } else {
                            token = StaffHex.displayOfTag(t).getString();
                        }
                    } else {
                        token = StaffHex.displayOfTag(t).getString();
                    }
                } catch (Exception e) {
                    token = StaffHex.displayOfTag(t).getString();
                }
            } else {
                token = StaffHex.displayOfTag(t).getString();
            }
            if (i > 0) sb.append(",\n");
            sb.append(token);
        }
        return sb.toString();
    }
}
