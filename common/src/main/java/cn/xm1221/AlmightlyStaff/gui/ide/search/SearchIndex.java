package cn.xm1221.AlmightlyStaff.gui.ide.search;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 统一搜索引擎索引：聚合书页 / 图案 / 笔记 / IDE 命令四类条目，
 * 支持子序列模糊匹配与分类前缀过滤（{@code @p / @f / @n / >}）。
 */
public final class SearchIndex {
    public enum Kind {
        PAGE,
        PATTERN,
        NOTE,
        COMMAND
    }

    public record Entry(
        Kind kind,
        String id,
        String title,
        String subtitle,
        String searchBlob,
        int pageIndex,
        String patternActionId,
        String patternDir,
        String patternSig,
        @Nullable Runnable action
    ) {
    }

    public record Match(Entry entry, int score) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public void clear() {
        entries.clear();
    }

    public void add(Entry entry) {
        if (entry != null) {
            entries.add(entry);
        }
    }

    public int size() {
        return entries.size();
    }

    public List<Match> search(String rawQuery, int limit) {
        String q = rawQuery == null ? "" : rawQuery.trim();
        Kind filter = null;
        if (q.startsWith("@p ")) {
            filter = Kind.PATTERN;
            q = q.substring(3).trim();
        } else if (q.startsWith("@f ")) {
            filter = Kind.PAGE;
            q = q.substring(3).trim();
        } else if (q.startsWith("@n ")) {
            filter = Kind.NOTE;
            q = q.substring(3).trim();
        } else if (q.startsWith(">")) {
            filter = Kind.COMMAND;
            q = q.substring(1).trim();
        }

        String lowerQ = q.toLowerCase(Locale.ROOT);
        List<Match> scored = new ArrayList<>();
        for (Entry e : entries) {
            if (filter != null && e.kind() != filter) continue;
            if (lowerQ.isEmpty()) {
                scored.add(new Match(e, basePriority(e.kind())));
                continue;
            }
            int s = scoreEntry(e, lowerQ);
            if (s > 0) {
                scored.add(new Match(e, s));
            }
        }
        scored.sort(Comparator.comparingInt(Match::score).reversed()
            .thenComparingInt(m -> m.entry().kind().ordinal())
            .thenComparing(m -> m.entry().title()));
        if (limit > 0 && scored.size() > limit) {
            return new ArrayList<>(scored.subList(0, limit));
        }
        return scored;
    }

    private static int basePriority(Kind kind) {
        return switch (kind) {
            case PAGE -> 40;
            case COMMAND -> 30;
            case NOTE -> 20;
            case PATTERN -> 10;
        };
    }

    private static int scoreEntry(Entry e, String q) {
        String title = e.title() == null ? "" : e.title().toLowerCase(Locale.ROOT);
        String sub = e.subtitle() == null ? "" : e.subtitle().toLowerCase(Locale.ROOT);
        String blob = e.searchBlob() == null ? "" : e.searchBlob().toLowerCase(Locale.ROOT);

        if (title.equals(q) || sub.equals(q)) return 2000;
        if (title.startsWith(q)) return 1400 - Math.min(200, title.length());
        if (sub.startsWith(q)) return 1200 - Math.min(200, sub.length());

        int tIdx = title.indexOf(q);
        if (tIdx >= 0) return 900 - Math.min(200, tIdx * 4);
        int sIdx = sub.indexOf(q);
        if (sIdx >= 0) return 750 - Math.min(200, sIdx * 4);
        int bIdx = blob.indexOf(q);
        if (bIdx >= 0) return 600 - Math.min(200, bIdx * 2);

        int fuzzyTitle = subsequenceScore(title, q);
        if (fuzzyTitle > 0) return 300 + fuzzyTitle;
        int fuzzyBlob = subsequenceScore(blob, q);
        if (fuzzyBlob > 0) return 150 + fuzzyBlob;
        return 0;
    }

    /** 子序列模糊匹配：按连续命中给额外加分，未全部命中返回 0。 */
    public static int subsequenceScore(String text, String query) {
        if (query.isEmpty()) return 1;
        if (text.isEmpty() || query.length() > text.length()) return 0;
        int qi = 0;
        int score = 0;
        int prevMatch = -2;
        for (int ti = 0; ti < text.length() && qi < query.length(); ti++) {
            if (text.charAt(ti) == query.charAt(qi)) {
                score += 10;
                if (ti == prevMatch + 1) score += 12; // 连续字符加分
                if (ti == 0 || !Character.isLetterOrDigit(text.charAt(ti - 1))) score += 8; // 词首加分
                prevMatch = ti;
                qi++;
            }
        }
        return qi == query.length() ? score : 0;
    }
}
