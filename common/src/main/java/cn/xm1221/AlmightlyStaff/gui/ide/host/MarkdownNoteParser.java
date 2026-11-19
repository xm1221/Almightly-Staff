package cn.xm1221.AlmightlyStaff.gui.ide.host;

import java.util.ArrayList;
import java.util.List;

/**
 * 轻量 Markdown 笔记解析器（纯 Java，不依赖 MC 类）。
 * 支持标题（#/##/###）、无序列表（-/ *）、代码块（```）、行内代码（`...`）与内嵌图案芯片：
 * <ul>
 *   <li>{@code [[pattern:hexcasting:get_caster]]} 或 {@code [[pattern:hexcasting:get_caster|自定义名]]}</li>
 *   <li>{@code [[sig:EAST:qaq|意识之精思]]}</li>
 * </ul>
 */
public final class MarkdownNoteParser {

    public sealed interface Block permits Heading, Paragraph, BulletItem, CodeBlock {
    }

    public record Heading(int level, String text) implements Block {
    }

    public record Paragraph(List<InlineSpan> spans) implements Block {
    }

    public record BulletItem(List<InlineSpan> spans) implements Block {
    }

    public record CodeBlock(String lang, String code) implements Block {
    }

    public sealed interface InlineSpan permits TextSpan, CodeSpan, PatternChip {
    }

    public record TextSpan(String text) implements InlineSpan {
    }

    public record CodeSpan(String code) implements InlineSpan {
    }

    public record PatternChip(String actionId, String startDir, String signature, String label) implements InlineSpan {
    }

    private MarkdownNoteParser() {
    }

    public static List<Block> parse(String markdown) {
        List<Block> blocks = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) return blocks;
        String[] lines = markdown.replace("\r\n", "\n").split("\n", -1);
        boolean inCode = false;
        String codeLang = "";
        StringBuilder codeBuf = new StringBuilder();
        StringBuilder paraBuf = new StringBuilder();

        for (String line : lines) {
            if (inCode) {
                if (line.trim().startsWith("```")) {
                    blocks.add(new CodeBlock(codeLang, codeBuf.toString()));
                    inCode = false;
                    codeLang = "";
                    codeBuf.setLength(0);
                } else {
                    if (codeBuf.length() > 0) codeBuf.append('\n');
                    codeBuf.append(line);
                }
                continue;
            }
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                flushParagraph(paraBuf, blocks);
                inCode = true;
                codeLang = trimmed.substring(3).trim();
                codeBuf.setLength(0);
                continue;
            }
            if (trimmed.isEmpty()) {
                flushParagraph(paraBuf, blocks);
                continue;
            }
            if (trimmed.startsWith("### ")) {
                flushParagraph(paraBuf, blocks);
                blocks.add(new Heading(3, trimmed.substring(4).trim()));
                continue;
            }
            if (trimmed.startsWith("## ")) {
                flushParagraph(paraBuf, blocks);
                blocks.add(new Heading(2, trimmed.substring(3).trim()));
                continue;
            }
            if (trimmed.startsWith("# ")) {
                flushParagraph(paraBuf, blocks);
                blocks.add(new Heading(1, trimmed.substring(2).trim()));
                continue;
            }
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                flushParagraph(paraBuf, blocks);
                blocks.add(new BulletItem(parseInlines(trimmed.substring(2).trim())));
                continue;
            }
            if (paraBuf.length() > 0) paraBuf.append(' ');
            paraBuf.append(trimmed);
        }
        if (inCode) {
            blocks.add(new CodeBlock(codeLang, codeBuf.toString()));
        }
        flushParagraph(paraBuf, blocks);
        return blocks;
    }

    private static void flushParagraph(StringBuilder buf, List<Block> out) {
        if (buf.length() == 0) return;
        out.add(new Paragraph(parseInlines(buf.toString())));
        buf.setLength(0);
    }

    public static List<InlineSpan> parseInlines(String text) {
        List<InlineSpan> spans = new ArrayList<>();
        if (text == null || text.isEmpty()) return spans;
        int len = text.length();
        int i = 0;
        StringBuilder plain = new StringBuilder();
        while (i < len) {
            if (text.startsWith("[[", i)) {
                int end = text.indexOf("]]", i + 2);
                if (end > i + 2) {
                    PatternChip chip = tryParseChip(text.substring(i + 2, end));
                    if (chip != null) {
                        flushPlain(plain, spans);
                        spans.add(chip);
                        i = end + 2;
                        continue;
                    }
                }
            }
            if (text.charAt(i) == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i + 1) {
                    flushPlain(plain, spans);
                    spans.add(new CodeSpan(text.substring(i + 1, end)));
                    i = end + 1;
                    continue;
                }
            }
            plain.append(text.charAt(i));
            i++;
        }
        flushPlain(plain, spans);
        return spans;
    }

    private static void flushPlain(StringBuilder plain, List<InlineSpan> spans) {
        if (plain.length() > 0) {
            spans.add(new TextSpan(plain.toString()));
            plain.setLength(0);
        }
    }

    private static PatternChip tryParseChip(String raw) {
        String body = raw.trim();
        String label = "";
        int pipe = body.indexOf('|');
        if (pipe >= 0) {
            label = body.substring(pipe + 1).trim();
            body = body.substring(0, pipe).trim();
        }
        if (body.startsWith("pattern:")) {
            String actionId = body.substring("pattern:".length()).trim();
            if (actionId.isEmpty()) return null;
            return new PatternChip(actionId, "", "", label.isEmpty() ? actionId : label);
        }
        if (body.startsWith("sig:")) {
            String rest = body.substring("sig:".length()).trim();
            int colon = rest.indexOf(':');
            if (colon <= 0 || colon >= rest.length() - 1) return null;
            String dir = rest.substring(0, colon).trim();
            String sig = rest.substring(colon + 1).trim();
            if (sig.isEmpty()) return null;
            return new PatternChip("", dir, sig, label.isEmpty() ? sig : label);
        }
        return null;
    }
}
