package cn.xm1221.AlmightlyStaff.gui.ide.editor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * 纯 Java 法术文本流排版引擎：模仿 {@code ListIota} 紧密内联排布（零强制像素间距），
 * 支持手动空格（Space）、手动换行（Enter）、自动换行与括号层级缩进。
 */
public final class IotaFlowLayout {

    public record FlowItem(int index, String label, int color,
                           boolean isIntro, boolean isRetro, boolean hasComma) {
    }

    public record PlacedToken(int index, int row, int x, int y, int dispW, int w, int h,
                              String label, int color, boolean isIntro, boolean isRetro, boolean hasComma) {
    }

    public record CursorAnchor(int cursorIndex, int row, int x, int y, int h) {
    }

    public record Result(List<PlacedToken> tokens, List<CursorAnchor> anchors, int totalRows) {
        public int tokenAt(double mx, double my) {
            for (PlacedToken t : tokens) {
                if (mx >= t.x() && mx < t.x() + Math.max(4, t.dispW()) && my >= t.y() && my < t.y() + t.h()) {
                    return t.index();
                }
            }
            return -1;
        }

        public int cursorAt(double mx, double my, int rowHeight) {
            if (anchors.isEmpty()) return 0;
            int targetRow = Math.max(0, (int) (my / Math.max(1, rowHeight)));
            CursorAnchor best = anchors.get(0);
            int bestDist = Integer.MAX_VALUE;
            for (CursorAnchor a : anchors) {
                int dy = Math.abs(a.row() - targetRow) * 10000;
                int dx = (int) Math.abs(a.x() - mx);
                int d = dy + dx;
                if (d < bestDist) {
                    bestDist = d;
                    best = a;
                }
            }
            return best.cursorIndex();
        }
    }

    private IotaFlowLayout() {
    }

    public static Result layout(List<FlowItem> items, ToIntFunction<FlowItem> widthMeasure,
                                int maxWidth, int rowHeight, int indentPx,
                                Set<Integer> manualBreaks) {
        return layout(items, widthMeasure, i -> 0, maxWidth, rowHeight, indentPx, 4, manualBreaks, Map.of());
    }

    public static Result layout(List<FlowItem> items,
                                ToIntFunction<FlowItem> dispWidthMeasure,
                                ToIntFunction<FlowItem> commaWidthMeasure,
                                int maxWidth, int rowHeight, int indentPx, int spaceWidthPx,
                                Set<Integer> manualBreaks, Map<Integer, Integer> manualSpaces) {
        List<PlacedToken> placed = new ArrayList<>();
        List<CursorAnchor> anchors = new ArrayList<>();
        Set<Integer> breaks = manualBreaks == null ? Collections.emptySet() : manualBreaks;
        Map<Integer, Integer> spacesMap = manualSpaces == null ? Collections.emptyMap() : manualSpaces;
        int safeMaxW = Math.max(60, maxWidth);
        int safeSpaceW = Math.max(1, spaceWidthPx);
        int row = 0;
        int depth = 0;
        int x = 0;
        boolean atLineStart = true;

        for (int i = 0; i < items.size(); i++) {
            FlowItem item = items.get(i);
            int dispW = Math.max(4, dispWidthMeasure.applyAsInt(item));
            int commaW = item.hasComma() ? Math.max(0, commaWidthMeasure.applyAsInt(item)) : 0;
            int totalW = dispW + commaW;
            int spaceCount = Math.max(0, spacesMap.getOrDefault(i, 0));
            int spacePx = spaceCount * safeSpaceW;

            if (item.isRetro() && atLineStart) {
                depth = Math.max(0, depth - 1);
                x = depth * indentPx;
            }

            if (i == 0 && breaks.contains(0)) {
                row++;
                x = depth * indentPx;
            } else if (!atLineStart && (breaks.contains(i) || x + spacePx + totalW > safeMaxW)) {
                row++;
                if (item.isRetro()) {
                    depth = Math.max(0, depth - 1);
                }
                x = depth * indentPx;
                atLineStart = true;
            } else if (item.isRetro() && !atLineStart) {
                depth = Math.max(0, depth - 1);
            }

            x += spacePx;
            int y = row * rowHeight;
            anchors.add(new CursorAnchor(i, row, x, y, rowHeight));
            placed.add(new PlacedToken(item.index(), row, x, y, dispW, totalW, rowHeight,
                item.label(), item.color(), item.isIntro(), item.isRetro(), item.hasComma()));

            x += totalW;
            atLineStart = false;

            if (item.isIntro()) {
                depth++;
            }
        }

        // 末尾光标锚点（支持末尾手动换行与手动空格）
        if (!atLineStart && breaks.contains(items.size())) {
            row++;
            x = depth * indentPx;
        }
        int trailingSpaces = Math.max(0, spacesMap.getOrDefault(items.size(), 0));
        x += trailingSpaces * safeSpaceW;
        anchors.add(new CursorAnchor(items.size(), row, x, row * rowHeight, rowHeight));
        return new Result(placed, anchors, row + 1);
    }
}
