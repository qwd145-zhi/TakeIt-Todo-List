package me.shangl.takeit.client.mixin;

import me.shangl.takeit.client.WorldIdentity;
import me.shangl.takeit.client.hud.TodoHudRenderer;
import me.shangl.takeit.client.screen.PanelScreen;
import me.shangl.takeit.data.State;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 把待办 HUD 注入到 InGameHud 的渲染流程。
 *
 * <p>26.2 中 InGameHud 的类名是 {@link Hud}，每帧入口是
 * {@code extractRenderState(GuiGraphicsExtractor, DeltaTracker)}（RenderState 抽取模型）。
 * 这里在方法尾部插入，表示在原版 HUD 全部提交之后再叠加待办内容。</p>
 *
 * <p>可见性：有世界、未被 F1 隐藏、HUD 开关打开、当前存档/服务器有绑定或全局清单、
 * 且当前服务器不在黑名单里、且当前不是本模组自己的界面（那些界面会画自己的预览，避免重复）。</p>
 */
@Mixin(Hud.class)
public abstract class HudMixin {

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void takeit$extractTodoHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        // gui / font 是 final 字段，构造时就已赋值，这里只需判断「是否已在世界里」
        if (minecraft.level == null) {
            return;
        }
        if (((Hud) (Object) this).isHidden()) {
            return;
        }
        Screen screen = minecraft.gui.screen();
        if (screen instanceof PanelScreen) {
            return;
        }
        State state = State.get();
        if (!state.hudVisible()) {
            return;
        }
        // 只有「本存档/服务器绑定的清单」或「全局清单」才显示 HUD：
        // 两者都没有时不渲染，避免在没有指定清单的世界里冒出默认清单的内容
        if (!state.hasHudList()) {
            return;
        }
        // 黑名单服务器：连 HUD 都不出现（名单只能改 config/takeit_todo/settings.json）
        if (WorldIdentity.blacklisted()) {
            return;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        TodoHudRenderer.Layout layout = TodoHudRenderer.layout(minecraft.font, state.current(),
                state.listCount() > 1, state.hudMaxItems(), screenHeight);
        int[] rect = state.hudPosition().resolve(screenWidth, screenHeight,
                layout.width(), layout.height());
        TodoHudRenderer.render(graphics, minecraft.font, layout, rect[0], rect[1], 0);
    }
}
