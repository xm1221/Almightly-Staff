package cn.xm1221.AlmightlyStaff.gui.ide.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HexParseLexerTest {

    @Test
    void tokenizesCommentsBracesVectorsNumbersAndKnownPatterns() {
        Set<String> known = Set.of("hexcasting:get_caster", "hexcasting:blink");
        List<HexParseLexer.Token> tokens = HexParseLexer.tokenizeLine(
            "get_caster, (1, 2.5, -3), 6.0, { \\ blink } // note", known);

        assertTrue(tokens.stream().anyMatch(t -> t.type() == HexParseLexer.TokenType.PATTERN_KNOWN && t.text().equals("get_caster")));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == HexParseLexer.TokenType.VECTOR && t.text().equals("(1, 2.5, -3)")));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == HexParseLexer.TokenType.NUMBER && t.text().equals("6.0")));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == HexParseLexer.TokenType.BRACE_OPEN));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == HexParseLexer.TokenType.ESCAPE));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == HexParseLexer.TokenType.BRACE_CLOSE));
        assertTrue(tokens.stream().anyMatch(t -> t.type() == HexParseLexer.TokenType.COMMENT && t.text().equals("// note")));
    }

    @Test
    void checksBalancedAndUnmatchedBracketsIgnoringComments() {
        assertTrue(HexParseLexer.checkBrackets("{\n// }\nget_caster\n}").balanced());

        HexParseLexer.BracketReport unclosed = HexParseLexer.checkBrackets("get_caster\n{\nblink");
        assertFalse(unclosed.balanced());
        assertEquals(2, unclosed.errorLine());

        HexParseLexer.BracketReport extraClose = HexParseLexer.checkBrackets("get_caster\n}\nblink");
        assertFalse(extraClose.balanced());
        assertEquals(2, extraClose.errorLine());

        String text = "a { b { c } d }";
        assertEquals(14, HexParseLexer.findMatchingBrace(text, 2));
        assertEquals(2, HexParseLexer.findMatchingBrace(text, 15));
    }

    @Test
    void handles500LinesQuickly() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            sb.append("get_caster, 12, { blink } // line ").append(i).append('\n');
        }
        String src = sb.toString();
        long t0 = System.nanoTime();
        assertTrue(HexParseLexer.checkBrackets(src).balanced());
        Set<String> known = Set.of("hexcasting:get_caster", "hexcasting:blink");
        for (String line : src.split("\n")) {
            HexParseLexer.tokenizeLine(line, known);
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(elapsedMs < 500, "500 lines lexing took too long: " + elapsedMs + "ms");
    }
}
