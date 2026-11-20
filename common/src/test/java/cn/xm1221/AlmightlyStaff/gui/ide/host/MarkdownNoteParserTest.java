package cn.xm1221.AlmightlyStaff.gui.ide.host;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import org.junit.jupiter.api.BeforeAll;

class MarkdownNoteParserTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        cn.xm1221.AlmightlyStaff.MinecraftBootstrap.ensure();
    }


    @Test
    void parsesHeadingsListsCodeBlocksAndPatternChips() {
        String md = """
            # Blink Guide
            Use [[pattern:hexcasting:get_caster|意识之精思]] and [[sig:WEST:awqqqwaq|瞬移]].
            - Step `one`
            ```hexparse
            get_caster
            blink
            ```
            """;
        List<MarkdownNoteParser.Block> blocks = MarkdownNoteParser.parse(md);
        assertEquals(4, blocks.size());
        assertInstanceOf(MarkdownNoteParser.Heading.class, blocks.get(0));
        assertEquals("Blink Guide", ((MarkdownNoteParser.Heading) blocks.get(0)).text());

        MarkdownNoteParser.Paragraph p = assertInstanceOf(MarkdownNoteParser.Paragraph.class, blocks.get(1));
        assertEquals(5, p.spans().size());
        MarkdownNoteParser.PatternChip chip1 = assertInstanceOf(MarkdownNoteParser.PatternChip.class, p.spans().get(1));
        assertEquals("hexcasting:get_caster", chip1.actionId());
        assertEquals("意识之精思", chip1.label());

        MarkdownNoteParser.PatternChip chip2 = assertInstanceOf(MarkdownNoteParser.PatternChip.class, p.spans().get(3));
        assertEquals("WEST", chip2.startDir());
        assertEquals("awqqqwaq", chip2.signature());

        MarkdownNoteParser.BulletItem bullet = assertInstanceOf(MarkdownNoteParser.BulletItem.class, blocks.get(2));
        assertInstanceOf(MarkdownNoteParser.CodeSpan.class, bullet.spans().get(1));

        MarkdownNoteParser.CodeBlock code = assertInstanceOf(MarkdownNoteParser.CodeBlock.class, blocks.get(3));
        assertEquals("hexparse", code.lang());
        assertEquals("get_caster\nblink", code.code());
    }

    @Test
    void toleratesMalformedTokens() {
        List<MarkdownNoteParser.Block> blocks = MarkdownNoteParser.parse("Broken [[pattern:]] and `unclosed\n```hexparse\nget_caster");
        assertEquals(2, blocks.size());
        assertInstanceOf(MarkdownNoteParser.Paragraph.class, blocks.get(0));
        assertInstanceOf(MarkdownNoteParser.CodeBlock.class, blocks.get(1));
    }
}
