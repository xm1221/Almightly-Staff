package cn.xm1221.AlmightlyStaff.gui.ide.host;

import cn.xm1221.AlmightlyStaff.gui.ide.tool.PageTreePanel;
import dev.architectury.platform.Platform;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * HexGuide 笔记软联动隔离桥：仅在 {@code Platform.isModLoaded("hexguide")} 时通过反射读取 {@code ClientNotes}。
 */
public final class HexGuideNotesBridge {
    private HexGuideNotesBridge() {
    }

    public static List<PageTreePanel.NoteEntry> loadClientNotes() {
        if (!Platform.isModLoaded("hexguide")) {
            return List.of();
        }
        List<PageTreePanel.NoteEntry> out = new ArrayList<>();
        try {
            Class<?> clientNotesCls = Class.forName("cn.xm1221.HexGuide.api.notes.ClientNotes");
            Object instance = clientNotesCls.getField("INSTANCE").get(null);
            Method sectionsMethod = clientNotesCls.getMethod("sections");
            Object rawSections = sectionsMethod.invoke(instance);
            if (!(rawSections instanceof List<?> sections)) {
                return out;
            }
            Class<?> noteIotaCls = Class.forName("cn.xm1221.HexGuide.api.notes.NoteIota");
            Method getTitle = noteIotaCls.getMethod("getTitle");
            Method getBody = noteIotaCls.getMethod("getBody");
            Method getAuthor = noteIotaCls.getMethod("getAuthor");

            int secIdx = 1;
            for (Object secObj : sections) {
                if (!(secObj instanceof List<?> pages)) continue;
                int pageIdx = 1;
                for (Object noteObj : pages) {
                    if (!noteIotaCls.isInstance(noteObj)) continue;
                    String title = String.valueOf(getTitle.invoke(noteObj));
                    String body = String.valueOf(getBody.invoke(noteObj));
                    String author = String.valueOf(getAuthor.invoke(noteObj));
                    String displayTitle = title.isBlank()
                        ? "HexGuide #" + secIdx + "." + pageIdx
                        : title;
                    String md = "# " + displayTitle
                        + (author.isBlank() ? "" : "\n*" + author + "*")
                        + "\n\n" + body;
                    out.add(new PageTreePanel.NoteEntry(
                        "hexguide:" + secIdx + ":" + pageIdx,
                        "[HexGuide] " + displayTitle,
                        md,
                        true
                    ));
                    pageIdx++;
                }
                secIdx++;
            }
        } catch (Throwable ignored) {
        }
        return out;
    }
}
