package cn.xm1221.AlmightlyStaff.client;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 万法之杖客户端外观与窗口设置界面（可从模组配置入口或 IDE 菜单进入，支持实时预览）。
 */
public final class StaffClientConfigScreen extends Screen {
    @Nullable
    private final Screen parent;

    private IdeTheme.ThemePreset draftTheme;
    private int draftSnapDistance;
    private int draftBorderThickness;

    private CycleButton<IdeTheme.ThemePreset> themeBtn;
    private IntSlider snapSlider;
    private IntSlider borderSlider;
    private Bounds previewBounds = Bounds.EMPTY;
    private boolean saved = false;

    public StaffClientConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("almightly_staff.config.title"));
        this.parent = parent;
        loadFromCurrentConfig();
    }

    private void loadFromCurrentConfig() {
        AlmightlyStaffClientConfig cfg = AlmightlyStaffClientConfig.get();
        this.draftTheme = cfg.resolvedTheme();
        this.draftSnapDistance = cfg.windowSnapDistance();
        this.draftBorderThickness = cfg.borderThickness();
        IdeTheme.applyPreset(draftTheme, draftBorderThickness);
    }

    @Override
    protected void init() {
        clearWidgets();

        int colW = Math.min(260, Math.max(200, width - 40));
        int leftX = width >= 540 ? Math.max(24, width / 2 - colW - 12) : (width - colW) / 2;
        int startY = 44;
        int rowStep = 28;

        themeBtn = CycleButton.<IdeTheme.ThemePreset>builder(IdeTheme.ThemePreset::displayName)
            .withValues(List.of(IdeTheme.ThemePreset.values()))
            .withInitialValue(draftTheme)
            .create(
                leftX, startY, colW, 20,
                Component.translatable("almightly_staff.config.theme"),
                (btn, val) -> {
                    draftTheme = val;
                    IdeTheme.applyPreset(draftTheme, draftBorderThickness);
                }
            );
        addRenderableWidget(themeBtn);

        snapSlider = new IntSlider(
            leftX, startY + rowStep, colW, 20,
            AlmightlyStaffClientConfig.MIN_SNAP_DISTANCE,
            AlmightlyStaffClientConfig.MAX_SNAP_DISTANCE,
            draftSnapDistance,
            val -> {
                draftSnapDistance = val;
                return val == 0
                    ? Component.translatable("almightly_staff.config.snap_distance_off")
                    : Component.translatable("almightly_staff.config.snap_distance_value", val);
            }
        );
        addRenderableWidget(snapSlider);

        borderSlider = new IntSlider(
            leftX, startY + rowStep * 2, colW, 20,
            AlmightlyStaffClientConfig.MIN_BORDER_THICKNESS,
            AlmightlyStaffClientConfig.MAX_BORDER_THICKNESS,
            draftBorderThickness,
            val -> {
                draftBorderThickness = val;
                IdeTheme.applyPreset(draftTheme, draftBorderThickness);
                return val == 0
                    ? Component.translatable("almightly_staff.config.border_thickness_off")
                    : Component.translatable("almightly_staff.config.border_thickness_value", val);
            }
        );
        addRenderableWidget(borderSlider);

        int previewY = width >= 540 ? startY : startY + rowStep * 3 + 6;
        int previewX = width >= 540 ? leftX + colW + 20 : leftX;
        int previewW = width >= 540 ? Math.min(250, Math.max(170, width - previewX - 24)) : colW;
        int previewH = Math.max(96, height - previewY - 46);
        previewBounds = new Bounds(previewX, previewY, previewW, Math.min(148, previewH));

        int footerY = height - 30;
        int btnW = 86;
        int gap = 8;
        int totalFooterW = btnW * 3 + gap * 2;
        int fx = (width - totalFooterW) / 2;

        addRenderableWidget(Button.builder(
            Component.translatable("almightly_staff.config.reset"),
            b -> resetToDefaults()
        ).bounds(fx, footerY, btnW, 20).build());

        addRenderableWidget(Button.builder(
            CommonComponents.GUI_CANCEL,
            b -> onClose()
        ).bounds(fx + btnW + gap, footerY, btnW, 20).build());

        addRenderableWidget(Button.builder(
            Component.translatable("almightly_staff.config.save"),
            b -> saveAndClose()
        ).bounds(fx + (btnW + gap) * 2, footerY, btnW, 20).build());
    }

    private void resetToDefaults() {
        draftTheme = IdeTheme.ThemePreset.AMETHYST;
        draftSnapDistance = AlmightlyStaffClientConfig.DEFAULT_SNAP_DISTANCE;
        draftBorderThickness = AlmightlyStaffClientConfig.DEFAULT_BORDER_THICKNESS;
        IdeTheme.applyPreset(draftTheme, draftBorderThickness);
        if (themeBtn != null) themeBtn.setValue(draftTheme);
        if (snapSlider != null) snapSlider.setIntValue(draftSnapDistance);
        if (borderSlider != null) borderSlider.setIntValue(draftBorderThickness);
    }

    private void saveAndClose() {
        AlmightlyStaffClientConfig cfg = AlmightlyStaffClientConfig.get();
        cfg.setTheme(draftTheme);
        cfg.setWindowSnapDistance(draftSnapDistance);
        cfg.setBorderThickness(draftBorderThickness);
        AlmightlyStaffClientConfig.save();
        saved = true;
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void onClose() {
        if (!saved) {
            IdeTheme.applyConfig(AlmightlyStaffClientConfig.get());
        }
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        IdeTheme.drawNineSlice(g, 0, 0, width, height, IdeTheme.NineSlice.ROOT);
        IdeTheme.drawNineSlice(g, 0, 0, width, 28, IdeTheme.NineSlice.HEADER);
        g.drawCenteredString(font, title, width / 2, 10, IdeTheme.TEXT_MAIN);

        if (themeBtn != null) {
            int swX = themeBtn.getX() - 16;
            int swY = themeBtn.getY() + 2;
            if (swX >= 4) {
                g.fill(swX, swY, swX + 12, swY + 16, draftTheme.previewBg());
                g.fill(swX + 2, swY + 3, swX + 10, swY + 6, draftTheme.previewAccent());
                IdeTheme.drawBorder(g, swX, swY, 12, 16, draftTheme.previewBorder(), Math.max(1, draftBorderThickness));
            }
        }

        renderPreviewCard(g);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderPreviewCard(GuiGraphics g) {
        if (previewBounds.w() < 80 || previewBounds.h() < 70) return;
        IdeTheme.drawNineSlice(g, previewBounds, IdeTheme.NineSlice.PANEL);

        int inset = Math.max(2, draftBorderThickness);
        Bounds headerB = new Bounds(
            previewBounds.x() + inset, previewBounds.y() + inset,
            previewBounds.w() - inset * 2, 16
        );
        IdeTheme.drawNineSlice(g, headerB, IdeTheme.NineSlice.HEADER);
        g.drawString(font, Component.translatable("almightly_staff.config.preview"),
            headerB.x() + 5, headerB.y() + 4, IdeTheme.TEXT_MAIN, false);

        // 模拟嵌套浮窗与按钮，直观展示主题配色、边框粗细与吸附阈值
        int fw = Math.max(84, previewBounds.w() - 28);
        int fh = Math.max(44, previewBounds.h() - 36);
        Bounds floatB = new Bounds(previewBounds.x() + 14, previewBounds.y() + 24, fw, fh);
        g.fill(floatB.x() - 2, floatB.y() - 2, floatB.right() + 2, floatB.bottom() + 2, 0x66000000);
        IdeTheme.drawNineSlice(g, floatB, IdeTheme.NineSlice.FLOAT_ACTIVE);

        int fin = Math.max(1, draftBorderThickness);
        Bounds fHead = new Bounds(floatB.x() + fin, floatB.y() + fin, Math.max(0, floatB.w() - fin * 2), 14);
        IdeTheme.drawNineSlice(g, fHead, IdeTheme.NineSlice.HEADER);
        g.fill(fHead.x(), fHead.y(), fHead.right(), fHead.y() + 2, IdeTheme.ACCENT);
        g.drawString(font, "ASCode", fHead.x() + 5, fHead.y() + 4, IdeTheme.TEXT_MAIN, false);

        if (fh >= 52 && fw >= 90) {
            Bounds sampleBtn = new Bounds(floatB.x() + fin + 6, floatB.y() + 22, Math.min(82, fw - fin * 2 - 12), 14);
            IdeTheme.drawNineSlice(g, sampleBtn, IdeTheme.NineSlice.BTN_ACTIVE);
            g.drawString(font, draftTheme.displayName(), sampleBtn.x() + 4, sampleBtn.y() + 3, IdeTheme.TEXT_MAIN, false);

            String meta = (draftSnapDistance == 0 ? "Snap Off" : "Snap " + draftSnapDistance + "px")
                + " · " + draftBorderThickness + "px";
            g.drawString(font, IdeTheme.trimText(font, meta, fw - fin * 2 - 12),
                floatB.x() + fin + 6, floatB.y() + 40, IdeTheme.TEXT_MUTED, false);
        }
        if (draftBorderThickness > 1) {
            IdeTheme.drawBorder(g, floatB, IdeTheme.ACCENT);
        }
    }

    private static final class IntSlider extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final java.util.function.IntFunction<Component> labelFactory;
        private int intValue;

        IntSlider(int x, int y, int w, int h, int min, int max, int initial,
                  java.util.function.IntFunction<Component> labelFactory) {
            super(x, y, w, h, Component.empty(), toRatio(min, max, initial));
            this.min = min;
            this.max = max;
            this.labelFactory = labelFactory;
            this.intValue = clamp(initial);
            updateMessage();
        }

        void setIntValue(int val) {
            this.intValue = clamp(val);
            this.value = toRatio(min, max, this.intValue);
            updateMessage();
        }

        private int clamp(int v) {
            return Math.max(min, Math.min(max, v));
        }

        private static double toRatio(int min, int max, int v) {
            if (max <= min) return 0.0;
            return (double) (Math.max(min, Math.min(max, v)) - min) / (double) (max - min);
        }

        @Override
        protected void updateMessage() {
            setMessage(labelFactory.apply(intValue));
        }

        @Override
        protected void applyValue() {
            int next = clamp((int) Math.round(min + value * (max - min)));
            if (next != intValue) {
                intValue = next;
                updateMessage();
            } else {
                labelFactory.apply(intValue);
            }
        }
    }
}
