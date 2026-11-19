package cn.xm1221.AlmightlyStaff.gui.ide.host;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.gui.ide.model.IdeSession;
import cn.xm1221.AlmightlyStaff.gui.ide.tool.PageTreePanel;
import cn.xm1221.AlmightlyStaff.spell.PatternCatalogEntry;
import cn.xm1221.AlmightlyStaff.spell.PatternRef;
import dev.architectury.platform.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * 原生 Markdown 笔记面板：支持渲染标题、列表、代码块与可点击内嵌图案芯片，
 * 点击芯片直接把对应图案插入当前激活的法术光标处。
 */
public final class MarkdownNotePanel implements IdeComponent {
    private record ChipHit(Bounds bounds, MarkdownNoteParser.PatternChip chip) {
    }

    private record CodeHit(Bounds bounds, String code) {
    }

    private final IdeSession session;
    private final PageTreePanel.NoteEntry note;
    private final List<MarkdownNoteParser.Block> blocks;
    private final Consumer<PatternRef> onInsertPattern;
    private final Consumer<String> onInsertCode;

    private Bounds bounds = Bounds.EMPTY;
    private int scrollY = 0;
    private int contentHeight = 0;
    private final List<ChipHit> chipHits = new ArrayList<>();
    private final List<CodeHit> codeHits = new ArrayList<>();

    public MarkdownNotePanel(IdeSession session, PageTreePanel.NoteEntry note,
                             Consumer<PatternRef> onInsertPattern, Consumer<String> onInsertCode) {
        this.session = session;
        this.note = note;
        this.blocks = MarkdownNoteParser.parse(note.content());
        this.onInsertPattern = onInsertPattern;
        this.onInsertCode = onInsertCode;
    }

    public static List<PageTreePanel.NoteEntry> loadAllNotes() {
        List<PageTreePanel.NoteEntry> all = new ArrayList<>();
        Path dir = Platform.getConfigFolder().resolve("almightly_staff").resolve("notes");
        try {
            Files.createDirectories(dir);
            try (Stream<Path> s = Files.list(dir)) {
                List<Path> mdFiles = s.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
                if (mdFiles.isEmpty()) {
                    Path sample = dir.resolve("blink_guide.md");
                    Files.writeString(sample, defaultSampleNote(), StandardCharsets.UTF_8);
                    mdFiles = List.of(sample);
                }
                for (Path p : mdFiles) {
                    String content = Files.readString(p, StandardCharsets.UTF_8);
                    String fileName = p.getFileName().toString();
                    String title = extractTitle(content, fileName);
                    all.add(new PageTreePanel.NoteEntry("local:" + fileName, title, content, false));
                }
            }
        } catch (IOException ignored) {
        }
        all.addAll(HexGuideNotesBridge.loadClientNotes());
        return all;
    }

    private static String extractTitle(String md, String fallback) {
        for (String line : md.split("\n")) {
            String t = line.trim();
            if (t.startsWith("# ")) return t.substring(2).trim();
        }
        return fallback;
    }

    private static String defaultSampleNote() {
        return """
            # Blink Raycast Guide
            Use caster position and look vector to blink forward:
            - [[pattern:hexcasting:get_caster|Mind's Reflection]]
            - [[pattern:hexcasting:entity_pos/eye|Compass' Purification]]
            - [[pattern:hexcasting:get_caster|Mind's Reflection]] + [[pattern:hexcasting:entity_pos/look|Alidade's Purification]]
            - [[pattern:hexcasting:raycast|Archer's Distillation]] + [[sig:EAST:aqaa|Numerical 6]] + [[pattern:hexcasting:blink|Blink]]
            ```hexparse
            get_caster, entity_pos/eye, get_caster, entity_pos/look, raycast, 6, blink
            ```
            """;
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
        g.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), IdeTheme.BG_ROOT);
        chipHits.clear();
        codeHits.clear();

        Bounds body = bounds.inset(8, 8, IdeTheme.SCROLLBAR_WIDTH + 6, 8);
        if (body.isEmpty()) return;
        int maxScroll = Math.max(0, contentHeight - body.h());
        scrollY = Mth.clamp(scrollY, 0, maxScroll);

        try (IdeScissor.Scope sc = IdeScissor.push(g, body)) {
            int y = body.y() - scrollY;
            int totalH = 0;

            for (MarkdownNoteParser.Block block : blocks) {
                if (block instanceof MarkdownNoteParser.Heading h) {
                    int col = h.level() == 1 ? IdeTheme.ACCENT_LIGHT : IdeTheme.TEXT_MAIN;
                    List<String> lines = wrapPlain(font, h.text(), body.w());
                    for (String ln : lines) {
                        g.drawString(font, ln, body.x(), y, col, false);
                        y += 13;
                        totalH += 13;
                    }
                    if (h.level() == 1) {
                        g.fill(body.x(), y - 2, body.right(), y - 1, IdeTheme.BORDER);
                    }
                    y += 4;
                    totalH += 4;
                } else if (block instanceof MarkdownNoteParser.Paragraph p) {
                    int used = renderSpans(g, font, body.x(), y, body.w(), p.spans(), mouseX, mouseY);
                    y += used + 6;
                    totalH += used + 6;
                } else if (block instanceof MarkdownNoteParser.BulletItem b) {
                    g.fill(body.x() + 2, y + 4, body.x() + 5, y + 7, IdeTheme.ACCENT);
                    int used = renderSpans(g, font, body.x() + 10, y, Math.max(20, body.w() - 10),
                        b.spans(), mouseX, mouseY);
                    y += used + 4;
                    totalH += used + 4;
                } else if (block instanceof MarkdownNoteParser.CodeBlock cb) {
                    String[] codeLines = cb.code().split("\n", -1);
                    int boxH = codeLines.length * 11 + 22;
                    g.fill(body.x(), y, body.right(), y + boxH, IdeTheme.BG_PANEL);
                    IdeTheme.drawBorder(g, body.x(), y, body.w(), boxH, IdeTheme.BORDER_STRONG);
                    String langLabel = cb.lang().isEmpty() ? "code" : cb.lang();
                    g.drawString(font, langLabel, body.x() + 6, y + 4, IdeTheme.TEXT_MUTED, false);

                    String insertLabel = Component.translatable("almightly_staff.ide.insert_code").getString();
                    int bw = font.width(insertLabel) + 10;
                    Bounds btnB = new Bounds(body.right() - bw - 4, y + 2, bw, 14);
                    boolean hov = btnB.contains(mouseX, mouseY);
                    g.fill(btnB.x(), btnB.y(), btnB.right(), btnB.bottom(), hov ? IdeTheme.BG_ACTIVE : IdeTheme.ACCENT_SOFT);
                    IdeTheme.drawBorder(g, btnB, IdeTheme.ACCENT);
                    g.drawCenteredString(font, insertLabel, btnB.x() + bw / 2, btnB.y() + 3, IdeTheme.TEXT_MAIN);
                    codeHits.add(new CodeHit(btnB, cb.code()));

                    int cy = y + 18;
                    for (String cl : codeLines) {
                        g.drawString(font, IdeTheme.trimText(font, cl, body.w() - 12),
                            body.x() + 6, cy, IdeTheme.CYAN_NOTE, false);
                        cy += 11;
                    }
                    y += boxH + 6;
                    totalH += boxH + 6;
                }
            }
            contentHeight = totalH;
        }
        IdeTheme.drawScrollbar(g, bounds.right() - IdeTheme.SCROLLBAR_WIDTH, bounds.y() + 4,
            Math.max(0, bounds.h() - 8), Math.max(1, contentHeight), Math.max(1, body.h()), scrollY);
    }

    private int renderSpans(GuiGraphics g, Font font, int startX, int startY, int maxW,
                            List<MarkdownNoteParser.InlineSpan> spans, int mx, int my) {
        int x = startX;
        int y = startY;
        int lineH = 16;
        for (MarkdownNoteParser.InlineSpan span : spans) {
            if (span instanceof MarkdownNoteParser.TextSpan ts) {
                for (String word : splitKeepSpaces(ts.text())) {
                    int ww = font.width(word);
                    if (x > startX && x + ww > startX + maxW) {
                        x = startX;
                        y += lineH;
                    }
                    g.drawString(font, word, x, y + 3, IdeTheme.TEXT_MAIN, false);
                    x += ww;
                }
            } else if (span instanceof MarkdownNoteParser.CodeSpan cs) {
                int cw = font.width(cs.code()) + 6;
                if (x > startX && x + cw > startX + maxW) {
                    x = startX;
                    y += lineH;
                }
                g.fill(x, y + 1, x + cw, y + 13, IdeTheme.BG_ELEVATED);
                g.drawString(font, cs.code(), x + 3, y + 3, IdeTheme.CYAN_NOTE, false);
                x += cw + 3;
            } else if (span instanceof MarkdownNoteParser.PatternChip chip) {
                String txt = "+ " + chip.label();
                int cw = font.width(txt) + 10;
                if (x > startX && x + cw > startX + maxW) {
                    x = startX;
                    y += lineH;
                }
                Bounds cb = new Bounds(x, y, cw, 14);
                boolean hov = cb.contains(mx, my);
                g.fill(cb.x(), cb.y(), cb.right(), cb.bottom(), hov ? IdeTheme.BG_ACTIVE : IdeTheme.ACCENT_SOFT);
                IdeTheme.drawBorder(g, cb, IdeTheme.ACCENT);
                g.drawString(font, txt, cb.x() + 5, cb.y() + 3, IdeTheme.ACCENT_LIGHT, false);
                chipHits.add(new ChipHit(cb, chip));
                x += cw + 4;
            }
        }
        return (y - startY) + lineH;
    }

    private static List<String> splitKeepSpaces(String text) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < text.length()) {
            int j = text.indexOf(' ', i);
            if (j < 0) {
                out.add(text.substring(i));
                break;
            }
            out.add(text.substring(i, j + 1));
            i = j + 1;
        }
        return out;
    }

    private static List<String> wrapPlain(Font font, String text, int maxW) {
        List<String> out = new ArrayList<>();
        if (text.isEmpty() || maxW <= 8) return List.of(text);
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            cur.append(text.charAt(i));
            if (font.width(cur.toString()) > maxW && cur.length() > 1) {
                out.add(cur.substring(0, cur.length() - 1));
                cur.setLength(0);
                cur.append(text.charAt(i));
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    @Nullable
    private PatternRef resolveChip(MarkdownNoteParser.PatternChip chip) {
        if (!chip.signature().isBlank()) {
            return new PatternRef(chip.actionId(), chip.startDir(), chip.signature());
        }
        String id = chip.actionId();
        for (PatternCatalogEntry entry : session.catalog()) {
            if (id.equals(entry.pattern().actionId())) {
                return entry.pattern();
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my)) return false;
        if (button == 0) {
            for (ChipHit hit : chipHits) {
                if (hit.bounds().contains(mx, my)) {
                    PatternRef ref = resolveChip(hit.chip());
                    if (ref != null) {
                        onInsertPattern.accept(ref);
                    } else {
                        session.toast().showError(Component.translatable("almightly_staff.gui.great_spell_unusable").getString());
                    }
                    return true;
                }
            }
            for (CodeHit hit : codeHits) {
                if (hit.bounds().contains(mx, my)) {
                    onInsertCode.accept(hit.code());
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!bounds.contains(mx, my)) return false;
        Bounds body = bounds.inset(8, 8, IdeTheme.SCROLLBAR_WIDTH + 6, 8);
        int max = Math.max(0, contentHeight - body.h());
        scrollY = Mth.clamp(scrollY - (int) Math.signum(delta) * 16, 0, max);
        return true;
    }
}
