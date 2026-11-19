package cn.xm1221.AlmightlyStaff.gui.ide.editor;

import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.gui.ide.model.SpellDocument;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 编辑器 Tab 页条目类型：法术页 / Markdown 笔记 / 外部 Screen 宿主。
 */
public sealed interface TabItem permits TabItem.SpellTab, TabItem.NoteTab, TabItem.ExternalTab {
    String tabId();

    Component title();

    IdeTheme.Icon icon();

    boolean isDirty();

    IdeComponent view();

    final class SpellTab implements TabItem {
        private final SpellDocument document;
        private final SequenceEditorView view;
        private final Set<Integer> selectedSlots = new LinkedHashSet<>();
        private int selectionAnchor = -1;
        private int cursorIndex;
        private int scrollRow;

        public SpellTab(SpellDocument document, SequenceEditorView.Callbacks callbacks) {
            this.document = document;
            this.cursorIndex = document.size();
            this.view = new SequenceEditorView(this, callbacks);
        }

        @Override
        public String tabId() {
            return "spell:" + document.pageIndex();
        }

        @Override
        public Component title() {
            String n = document.name();
            if (n.isEmpty()) {
                return Component.translatable("almightly_staff.gui.page_label", document.pageIndex());
            }
            return Component.literal("#" + document.pageIndex() + " " + n);
        }

        @Override
        public IdeTheme.Icon icon() {
            return IdeTheme.Icon.SPELL_STAR;
        }

        @Override
        public boolean isDirty() {
            return document.isDirty();
        }

        @Override
        public SequenceEditorView view() {
            return view;
        }

        public SpellDocument document() {
            return document;
        }

        public Set<Integer> selectedSlots() {
            return selectedSlots;
        }

        public int selectionAnchor() {
            return selectionAnchor;
        }

        public void setSelectionAnchor(int selectionAnchor) {
            this.selectionAnchor = selectionAnchor;
        }

        public int cursorIndex() {
            return Math.max(0, Math.min(cursorIndex, document.size()));
        }

        public void setCursorIndex(int cursorIndex) {
            this.cursorIndex = Math.max(0, Math.min(cursorIndex, document.size()));
        }

        public int scrollRow() {
            return scrollRow;
        }

        public void setScrollRow(int scrollRow) {
            this.scrollRow = Math.max(0, scrollRow);
        }
    }

    final class NoteTab implements TabItem {
        private final String noteId;
        private final Component title;
        private final IdeComponent view;

        public NoteTab(String noteId, Component title, IdeComponent view) {
            this.noteId = noteId;
            this.title = title;
            this.view = view;
        }

        @Override
        public String tabId() {
            return "note:" + noteId;
        }

        @Override
        public Component title() {
            return title;
        }

        @Override
        public IdeTheme.Icon icon() {
            return IdeTheme.Icon.NOTE_DOC;
        }

        @Override
        public boolean isDirty() {
            return false;
        }

        @Override
        public IdeComponent view() {
            return view;
        }
    }

    final class ExternalTab implements TabItem {
        private final String hostId;
        private final Component title;
        private final IdeComponent view;

        public ExternalTab(String hostId, Component title, IdeComponent view) {
            this.hostId = hostId;
            this.title = title;
            this.view = view;
        }

        @Override
        public String tabId() {
            return "ext:" + hostId;
        }

        @Override
        public Component title() {
            return title;
        }

        @Override
        public IdeTheme.Icon icon() {
            return IdeTheme.Icon.BOOK_EXTERNAL;
        }

        @Override
        public boolean isDirty() {
            return false;
        }

        @Override
        public IdeComponent view() {
            return view;
        }
    }
}
