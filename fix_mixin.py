# -*- coding: utf-8 -*-
path = "common/src/main/java/cn/xm1221/AlmightlyStaff/mixin/MixinGuiSpellcasting.java"
with open(path, "r", encoding="utf-8") as f:
    c = f.read()

# 移除失败的 ide$skipBackground（Screen.renderBackground 调用不在 GuiSpellcasting.render 里）
old_block = """    // IDE 内嵌渲染时，GuiSpellcasting.render() 会调用 Screen.renderBackground()
    // -> renderBlurredBackground() 全屏模糊。WrapWithCondition 拦截它，IDE 模式下跳过。
    @WrapWithCondition(
        method = "render",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;renderBackground(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            remap = true), // Screen 是 Minecraft 类，需经 refmap 映射到 SRG
        remap = false
    )
    private boolean ide$skipBackground(GuiGraphics g, int mx, int my, float pt) {
        return onDrawPattern$ide == null; // IDE 内嵌时跳过背景（含模糊）
    }

"""
assert old_block in c, "skip block not found"
c = c.replace(old_block, "")

# 移除 GuiGraphics import（不再需要）
old_imp = "import at.petrak.hexcasting.api.casting.math.HexPattern;\nimport net.minecraft.client.gui.GuiGraphics;"
new_imp = "import at.petrak.hexcasting.api.casting.math.HexPattern;"
assert old_imp in c, "import not found"
c = c.replace(old_imp, new_imp)

with open(path, "w", encoding="utf-8") as f:
    f.write(c)
print("MixinGuiSpellcasting cleaned")
