package cn.xm1221.AlmightlyStaff.gui.ide.host;

import cn.xm1221.AlmightlyStaff.gui.ide.core.Bounds;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeComponent;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeScissor;
import cn.xm1221.AlmightlyStaff.gui.ide.core.IdeTheme;
import cn.xm1221.AlmightlyStaff.spell.PatternRef;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Consumer;

/**
 * 外部 {@link Screen} 宿主容器：
 * <ul>
 *   <li>渲染时用 {@link IdeScissor} + {@code pose.translate/scale} 把外部界面限域在面板内部</li>
 *   <li>通过 {@code MixinScreen} 在 {@link #isRendering()} 期间屏蔽外部界面自带的暗色背景遮罩</li>
 *   <li>通过 {@code MixinMinecraft} 在 {@link #isDispatching()} 期间拦截子界面的 {@code setScreen}：
 *       {@code null} 仅关当前宿主，非容器屏原地替换，容器屏保存 IDE 后安全切换并在关闭后自动恢复</li>
 * </ul>
 */
public final class EmbeddedScreenHost implements IdeComponent {
    private static final int FOOTER_H = 20;

    private static int renderDepth = 0;
    private static final Deque<EmbeddedScreenHost> dispatchStack = new ArrayDeque<>();
    private static boolean capturing = false;
    @Nullable
    private static Screen capturedScreen = null;
    @Nullable
    private static Screen pendingRestoreIde = null;

    private Screen subScreen;
    private final Runnable onCloseHost;
    private final Consumer<PatternRef> onInsertPattern;
    private Bounds bounds = Bounds.EMPTY;
    private int lastInitW = -1;
    private int lastInitH = -1;
    private boolean initialized = false;
    private int stackDepthBeforeDispatch = -1;

    public EmbeddedScreenHost(Screen initialScreen, Runnable onCloseHost, Consumer<PatternRef> onInsertPattern) {
        this.subScreen = initialScreen;
        this.onCloseHost = onCloseHost;
        this.onInsertPattern = onInsertPattern;
    }

    public static boolean isRendering() {
        return renderDepth > 0;
    }

    public static boolean isDispatching() {
        return !dispatchStack.isEmpty();
    }

    /** 在不改变 Minecraft.screen 的前提下捕获外部 API 打开的 Screen 实例。 */
    @Nullable
    public static Screen captureScreen(Runnable opener) {
        capturing = true;
        capturedScreen = null;
        try {
            opener.run();
            return capturedScreen;
        } finally {
            capturing = false;
            capturedScreen = null;
        }
    }

    public static boolean onInterceptSetScreen(@Nullable Screen next) {
        if (capturing) {
            capturedScreen = next;
            return true;
        }
        if (dispatchStack.isEmpty()) {
            return false;
        }
        EmbeddedScreenHost host = dispatchStack.peek();
        if (next == null) {
            host.onCloseHost.run();
            return true;
        }
        if (next instanceof AbstractContainerScreen<?>) {
            // 强依赖 AbstractContainerMenu 同步的容器界面：不强行嵌套，记录当前 IDE 后安全切换
            pendingRestoreIde = Minecraft.getInstance().screen;
            dispatchStack.clear();
            return false;
        }
        host.replaceSubScreen(next);
        return true;
    }

    /** 在 ClientTickEvent 中调用：容器类界面关闭回到空屏时自动恢复 IDE。 */
    public static void tickRestoreIfNeeded(Minecraft mc) {
        if (pendingRestoreIde != null && mc.screen == null) {
            Screen restore = pendingRestoreIde;
            pendingRestoreIde = null;
            mc.setScreen(restore);
        }
    }

    public Screen subScreen() {
        return subScreen;
    }

    public void replaceSubScreen(Screen next) {
        if (next == null) return;
        Screen prev = this.subScreen;
        // Patchouli openLexiconGui 只在 mc.screen instanceof GuiBook 时 push guiStack；
        // 在 IDE 宿主内 mc.screen 为 StaffIdeScreen，前跳导航时由宿主补回入栈，回退(pop)时不再压栈。
        int stackAfter = PatchouliHostBridge.guiStackSize(next);
        if (prev != null && prev != next && stackDepthBeforeDispatch >= 0 && stackAfter >= stackDepthBeforeDispatch) {
            PatchouliHostBridge.pushGuiStack(prev, next);
        }
        stackDepthBeforeDispatch = -1;
        this.subScreen = next;
        this.initialized = false;
        initSubScreenIfNeeded();
    }

    private Bounds bodyBounds() {
        return bounds.inset(0, 0, 0, FOOTER_H);
    }

    private Bounds footerBounds() {
        return new Bounds(bounds.x(), bounds.bottom() - FOOTER_H, bounds.w(), Math.min(FOOTER_H, bounds.h()));
    }

    private void initSubScreenIfNeeded() {
        Bounds body = bodyBounds();
        if (subScreen == null || body.isEmpty()) return;
        if (!initialized || lastInitW != body.w() || lastInitH != body.h()) {
            initialized = true;
            lastInitW = body.w();
            lastInitH = body.h();
            Minecraft mc = Minecraft.getInstance();
            double prevGuiScale = mc.getWindow().getGuiScale();
            dispatchStack.push(this);
            try {
                subScreen.init(mc, Math.max(280, body.w()), Math.max(192, body.h()));
            } finally {
                if (mc.getWindow().getGuiScale() != prevGuiScale) {
                    mc.getWindow().setGuiScale(prevGuiScale);
                }
                if (!dispatchStack.isEmpty() && dispatchStack.peek() == this) {
                    dispatchStack.pop();
                }
            }
        }
    }

    private record Transform(float scale, float tx, float ty) {
    }

    private Transform computeTransform(Bounds body) {
        float bookScale = PatchouliHostBridge.bookScaleFactor(subScreen);
        if (PatchouliHostBridge.isPatchouliBook(subScreen)) {
            float effW = 284.0f * bookScale;
            float effH = 196.0f * bookScale;
            float scale = Math.min(1.0f, Math.min(body.w() / effW, body.h() / effH));
            float centerScreenX = (subScreen.width * bookScale) / 2.0f;
            float centerScreenY = (subScreen.height * bookScale) / 2.0f;
            float tx = (body.x() + body.w() / 2.0f) - centerScreenX * scale;
            float ty = (body.y() + body.h() / 2.0f) - centerScreenY * scale;
            return new Transform(scale, tx, ty);
        }
        float sw = Math.max(1, subScreen.width);
        float sh = Math.max(1, subScreen.height);
        float scale = Math.min(1.0f, Math.min(body.w() / sw, body.h() / sh));
        float tx = body.x() + (body.w() - sw * scale) / 2.0f;
        float ty = body.y() + (body.h() - sh * scale) / 2.0f;
        return new Transform(scale, tx, ty);
    }

    private int toLocalX(double mx, Transform tr) {
        return (int) Math.round((mx - tr.tx()) / tr.scale());
    }

    private int toLocalY(double my, Transform tr) {
        return (int) Math.round((my - tr.ty()) / tr.scale());
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
        if (bounds.isEmpty() || subScreen == null) return;
        initSubScreenIfNeeded();
        Font font = Minecraft.getInstance().font;
        IdeTheme.drawNineSlice(g, bounds, IdeTheme.NineSlice.ROOT);

        Bounds body = bodyBounds();
        if (!body.isEmpty()) {
            Transform tr = computeTransform(body);
            int lmx = body.contains(mouseX, mouseY) ? toLocalX(mouseX, tr) : -10000;
            int lmy = body.contains(mouseX, mouseY) ? toLocalY(mouseY, tr) : -10000;

            try (IdeScissor.Scope sc = IdeScissor.push(g, body)) {
                PoseStack ps = g.pose();
                ps.pushPose();
                ps.translate(tr.tx(), tr.ty(), 0.0F);
                ps.scale(tr.scale(), tr.scale(), 1.0F);
                renderDepth++;
                dispatchStack.push(this);
                try {
                    subScreen.render(g, lmx, lmy, partialTick);
                } finally {
                    if (!dispatchStack.isEmpty() && dispatchStack.peek() == this) {
                        dispatchStack.pop();
                    }
                    renderDepth = Math.max(0, renderDepth - 1);
                    ps.popPose();
                }
            }
        }

        // 底部辅助条：提示 ESC 只关当前嵌入面板 + 识别 Patchouli 当前页图案一键插入
        Bounds fb = footerBounds();
        IdeTheme.drawNineSlice(g, fb, IdeTheme.NineSlice.HEADER);
        try (IdeScissor.Scope sc = IdeScissor.push(g, fb)) {
            PatternRef pagePat = PatchouliHostBridge.extractVisiblePattern(subScreen);
            if (pagePat != null) {
                String btnTxt = Component.translatable("almightly_staff.ide.insert_book_pattern").getString();
                int bw = font.width(btnTxt) + 12;
                Bounds btnB = new Bounds(fb.right() - bw - 6, fb.y() + 3, bw, 14);
                boolean hov = btnB.contains(mouseX, mouseY);
                IdeTheme.drawNineSlice(g, btnB, hov ? IdeTheme.NineSlice.BTN_ACTIVE : IdeTheme.NineSlice.BTN_HOVER);
                g.drawCenteredString(font, btnTxt, btnB.x() + bw / 2, btnB.y() + 3, IdeTheme.ACCENT_LIGHT);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (!bounds.contains(mx, my) || subScreen == null) return false;
        Bounds fb = footerBounds();
        if (fb.contains(mx, my) && button == 0) {
            PatternRef pagePat = PatchouliHostBridge.extractVisiblePattern(subScreen);
            if (pagePat != null) {
                Font font = Minecraft.getInstance().font;
                String btnTxt = Component.translatable("almightly_staff.ide.insert_book_pattern").getString();
                int bw = font.width(btnTxt) + 12;
                Bounds btnB = new Bounds(fb.right() - bw - 6, fb.y() + 3, bw, 14);
                if (btnB.contains(mx, my)) {
                    onInsertPattern.accept(pagePat);
                    return true;
                }
            }
            return true;
        }
        Bounds body = bodyBounds();
        if (!body.contains(mx, my)) return true;
        Transform tr = computeTransform(body);
        double lmx = (mx - tr.tx()) / tr.scale();
        double lmy = (my - tr.ty()) / tr.scale();
        stackDepthBeforeDispatch = PatchouliHostBridge.guiStackSize(subScreen);
        dispatchStack.push(this);
        try {
            subScreen.mouseClicked(lmx, lmy, button);
        } finally {
            stackDepthBeforeDispatch = -1;
            if (!dispatchStack.isEmpty() && dispatchStack.peek() == this) {
                dispatchStack.pop();
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (subScreen == null || !bodyBounds().contains(mx, my)) return false;
        Transform tr = computeTransform(bodyBounds());
        stackDepthBeforeDispatch = PatchouliHostBridge.guiStackSize(subScreen);
        dispatchStack.push(this);
        try {
            return subScreen.mouseReleased((mx - tr.tx()) / tr.scale(), (my - tr.ty()) / tr.scale(), button);
        } finally {
            stackDepthBeforeDispatch = -1;
            if (!dispatchStack.isEmpty() && dispatchStack.peek() == this) {
                dispatchStack.pop();
            }
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (subScreen == null || !bodyBounds().contains(mx, my)) return false;
        Transform tr = computeTransform(bodyBounds());
        dispatchStack.push(this);
        try {
            return subScreen.mouseDragged((mx - tr.tx()) / tr.scale(), (my - tr.ty()) / tr.scale(),
                button, dx / tr.scale(), dy / tr.scale());
        } finally {
            if (!dispatchStack.isEmpty() && dispatchStack.peek() == this) {
                dispatchStack.pop();
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (subScreen == null || !bodyBounds().contains(mx, my)) return false;
        Transform tr = computeTransform(bodyBounds());
        dispatchStack.push(this);
        try {
            return subScreen.mouseScrolled((mx - tr.tx()) / tr.scale(), (my - tr.ty()) / tr.scale(), delta);
        } finally {
            if (!dispatchStack.isEmpty() && dispatchStack.peek() == this) {
                dispatchStack.pop();
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (subScreen == null) return false;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            // 若 Patchouli 当前可回退历史栈则先退一页，否则关闭当前内嵌宿主
            stackDepthBeforeDispatch = PatchouliHostBridge.guiStackSize(subScreen);
            dispatchStack.push(this);
            try {
                if (!PatchouliHostBridge.tryBack(subScreen)) {
                    subScreen.onClose();
                }
            } finally {
                stackDepthBeforeDispatch = -1;
                if (!dispatchStack.isEmpty() && dispatchStack.peek() == this) {
                    dispatchStack.pop();
                }
            }
            return true;
        }
        stackDepthBeforeDispatch = PatchouliHostBridge.guiStackSize(subScreen);
        dispatchStack.push(this);
        try {
            return subScreen.keyPressed(keyCode, scanCode, modifiers);
        } finally {
            stackDepthBeforeDispatch = -1;
            if (!dispatchStack.isEmpty() && dispatchStack.peek() == this) {
                dispatchStack.pop();
            }
        }
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (subScreen == null) return false;
        dispatchStack.push(this);
        try {
            return subScreen.charTyped(codePoint, modifiers);
        } finally {
            if (!dispatchStack.isEmpty() && dispatchStack.peek() == this) {
                dispatchStack.pop();
            }
        }
    }

    @Override
    public void tick() {
        if (subScreen == null) return;
        dispatchStack.push(this);
        try {
            subScreen.tick();
        } finally {
            if (!dispatchStack.isEmpty() && dispatchStack.peek() == this) {
                dispatchStack.pop();
            }
        }
    }
}
