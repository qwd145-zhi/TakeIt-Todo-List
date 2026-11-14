package me.shangl.takeit.client.screen;

import me.shangl.takeit.client.hud.TodoHudRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 显示设置（二级页面）：HUD 开关、显示条数、入口到 HUD 位置设置。
 *
 * <p>待办内容的增删改查留在待办清单主界面，本页只放「显示相关」配置。</p>
 */
public class DisplayConfigScreen extends PanelScreen {

    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 160;

    private EditBox countInput;

    public DisplayConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.takeit.display.title"), parent);
    }

    @Override
    protected void init() {
        this.layoutPanel(PANEL_WIDTH, PANEL_HEIGHT);

        this.addRenderableWidget(Button.builder(
                        TodoHudRenderer.hudToggleLabel(this.state.hudVisible()),
                        button -> {
                            this.state.toggleHudVisible();
                            button.setMessage(TodoHudRenderer.hudToggleLabel(this.state.hudVisible()));
                        })
                .bounds(this.panelX + 16, this.panelY + 28, 140, 20).build());

        // 显示条数：− / 输入框 / ＋（0 = 全部）
        this.addRenderableWidget(Button.builder(Component.literal("-"), _ -> this.adjustCount(-1))
                .bounds(this.panelX + 120, this.panelY + 52, 18, 20).build());

        this.countInput = this.addRenderableWidget(new EditBox(this.font,
                this.panelX + 140, this.panelY + 52, 40, 20,
                Component.translatable("screen.takeit.hud.items_label")));
        this.countInput.setMaxLength(3);
        this.countInput.setValue(Integer.toString(this.state.hudMaxItems()));

        this.addRenderableWidget(Button.builder(Component.literal("+"), _ -> this.adjustCount(1))
                .bounds(this.panelX + 182, this.panelY + 52, 18, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.todo.position"),
                        _ -> this.minecraft.gui.setScreen(new HudPositionScreen(this)))
                .bounds(this.panelX + 16, this.panelY + 80, 150, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.display.done"),
                        _ -> this.onClose())
                .bounds(this.panelX + PANEL_WIDTH - 16 - 70, this.panelY + 130, 70, 20).build());
    }

    /** 条数 ±1（0 = 全部，钳制在 [0, MAX_ITEMS]）。 */
    private void adjustCount(int delta) {
        this.state.setHudMaxItems(this.state.hudMaxItems() + delta);
        this.countInput.setValue(Integer.toString(this.state.hudMaxItems()));
    }

    /** 提交输入框：非数字回退当前值，越界由 setHudMaxItems 钳制。 */
    private void commitCount() {
        try {
            this.state.setHudMaxItems(Integer.parseInt(this.countInput.getValue().trim()));
        } catch (NumberFormatException ignored) {
            // 非法输入直接回退到当前配置值
        }
        this.countInput.setValue(Integer.toString(this.state.hudMaxItems()));
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER && this.countInput.isFocused()) {
            this.commitCount();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        // 点击别处时提交条数输入框，避免"看起来改了其实没生效"
        if (this.countInput.isFocused() && !this.countInput.isMouseOver(event.x(), event.y())) {
            this.commitCount();
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.drawPanel(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.drawCenteredTitle(graphics, 8);

        graphics.text(this.font, Component.translatable("screen.takeit.hud.items_label"),
                this.panelX + 16, this.panelY + 58, COLOR_LABEL, false);
        graphics.text(this.font, Component.translatable("screen.takeit.hud.items_all_hint"),
                this.panelX + 206, this.panelY + 58, COLOR_HINT, false);
        boolean hasList = this.state.hasHudList();
        graphics.text(this.font, hasList
                        ? Component.translatable("screen.takeit.display.hint")
                        : Component.translatable("screen.takeit.display.no_list"),
                this.panelX + 16, this.panelY + 108, hasList ? COLOR_HINT : COLOR_WARN, false);
    }
}
