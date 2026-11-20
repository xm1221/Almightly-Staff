package cn.xm1221.AlmightlyStaff.gui.ide.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchIndexTest {

    private SearchIndex sampleIndex() {
        SearchIndex idx = new SearchIndex();
        idx.add(new SearchIndex.Entry(SearchIndex.Kind.PAGE, "p1", "blink_strike", "#1 combat",
            "blink_strike combat", 1, "", "", "", null));
        idx.add(new SearchIndex.Entry(SearchIndex.Kind.PATTERN, "hexcasting:blink", "瞬移",
            "hexcasting:blink · awqqqwaq", "瞬移 blink hexcasting:blink awqqqwaq",
            0, "hexcasting:blink", "WEST", "awqqqwaq", null));
        idx.add(new SearchIndex.Entry(SearchIndex.Kind.PATTERN, "hexcasting:get_caster", "意识之精思",
            "hexcasting:get_caster · qaq", "意识之精思 mind reflection qaq",
            0, "hexcasting:get_caster", "EAST", "qaq", null));
        idx.add(new SearchIndex.Entry(SearchIndex.Kind.NOTE, "n1", "Blink Note", "notes/blink.md",
            "blink note", 0, "", "", "", null));
        idx.add(new SearchIndex.Entry(SearchIndex.Kind.COMMAND, "cmd:split", "分屏对照", "Split Right",
            "split right", 0, "", "", "", null));
        return idx;
    }

    @Test
    void matchesSignatureChineseAndFuzzySubsequence() {
        SearchIndex idx = sampleIndex();

        List<SearchIndex.Match> bySig = idx.search("awqqqwaq", 10);
        assertFalseEmpty(bySig);
        assertEquals("hexcasting:blink", bySig.get(0).entry().id());

        List<SearchIndex.Match> byCn = idx.search("意识", 10);
        assertEquals("hexcasting:get_caster", byCn.get(0).entry().id());

        List<SearchIndex.Match> fuzzy = idx.search("blstrk", 10);
        assertTrue(fuzzy.stream().anyMatch(m -> m.entry().id().equals("p1")));
    }

    @Test
    void filtersByCategoryPrefix() {
        SearchIndex idx = sampleIndex();
        List<SearchIndex.Match> patternsOnly = idx.search("@p blink", 10);
        assertEquals(1, patternsOnly.size());
        assertEquals(SearchIndex.Kind.PATTERN, patternsOnly.get(0).entry().kind());

        List<SearchIndex.Match> pagesOnly = idx.search("@f blink", 10);
        assertEquals(1, pagesOnly.size());
        assertEquals(SearchIndex.Kind.PAGE, pagesOnly.get(0).entry().kind());

        List<SearchIndex.Match> cmdsOnly = idx.search("> split", 10);
        assertEquals(1, cmdsOnly.size());
        assertEquals(SearchIndex.Kind.COMMAND, cmdsOnly.get(0).entry().kind());
    }

    private static void assertFalseEmpty(List<?> list) {
        assertTrue(!list.isEmpty());
    }
}
