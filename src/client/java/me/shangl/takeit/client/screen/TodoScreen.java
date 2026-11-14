package me.shangl.takeit.client.screen;

import me.shangl.takeit.client.hud.TodoHudRenderer;
import me.shangl.takeit.client.widget.TodoListWidget;
import me.shangl.takeit.data.TodoItem;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 待办清单主界面：对「当前清单」做增删改查 + 勾选完成 + 可滚动列表。
 *
 * <p>显示相关配置（HUD 开关、显示条数、HUD 位置）不在这里，而是通过「配置」按钮
 * 进入一级配置菜单后逐级打开。</p>
 */
public class TodoScreen extends PanelScreen {

    private static final int PANEL_WIDTH = 340;
    private static final int PANEL_HEIGHT = 236;

    /** 底部「关闭」按钮宽度；底部提示行必须让开它。 */
    private static final int CLOSE_WIDTH = 70;

    private TodoListWidget list;
    private EditBox input;

    public TodoScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.takeit.todo.title"), parent);
    }

    @Override
    protected void init() {
        this.layoutPanel(PANEL_WIDTH, PANEL_HEIGHT);

        // 列表：可滚动、可勾选、可删除
        this.list = this.addRenderableWidget(new TodoListWidget(
                this.panelX + 8, this.panelY + 28, PANEL_WIDTH - 16, 120, this.font,
                item -> this.state.toggle(item.id),
                item -> this.state.remove(item.id)));

        // 输入框 + 添加按钮
        this.input = this.addRenderableWidget(new EditBox(this.font,
                this.panelX + 8, this.panelY + 158, PANEL_WIDTH - 16 - 68, 20,
                Component.translatable("screen.takeit.todo.input")));
        this.input.setMaxLength(TodoItem.MAX_TEXT_LENGTH);
        this.input.setHint(Component.translatable("screen.takeit.todo.input"));

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.todo.add"),
                        _ -> this.submitInput())
                .bounds(this.panelX + PANEL_WIDTH - 8 - 60, this.panelY + 158, 60, 20)
                .build());

        // 第二行：HUD 开关 / 配置 / 清除已完成
        this.addRenderableWidget(Button.builder(
                        TodoHudRenderer.hudToggleLabel(this.state.hudVisible()),
                        button -> {
                            this.state.toggleHudVisible();
                            button.setMessage(TodoHudRenderer.hudToggleLabel(this.state.hudVisible()));
                        })
                .bounds(this.panelX + 8, this.panelY + 184, 104, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.todo.config"),
                        _ -> this.minecraft.gui.setScreen(new ConfigScreen(this)))
                .bounds(this.panelX + 116, this.panelY + 184, 104, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.todo.clear_done"),
                        _ -> this.state.clearCompleted())
                .bounds(this.panelX + 224, this.panelY + 184, 108, 20)
                .build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.todo.close"),
                        _ -> this.onClose())
                .bounds(this.panelX + PANEL_WIDTH - 8 - CLOSE_WIDTH, this.panelY + 208, CLOSE_WIDTH, 20)
                .build());

        this.setInitialFocus(this.input);
    }

    /** 提交输入框内容（空内容忽略）。 */
    private void submitInput() {
        String text = this.input.getValue();
        if (!text.isBlank() && this.state.add(text) != null) {
            this.input.setValue("");
        }
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // 先画面板，再交给子控件（按钮、输入框、列表）绘制
        this.drawPanel(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);

        // 标题、当前清单名与统计
        graphics.text(this.font, this.title, this.panelX + 8, this.panelY + 9, COLOR_TITLE, true);
        Component count = Component.translatable("screen.takeit.todo.count_named",
                this.state.current().name, this.state.doneCount(), this.state.totalCount());
        int countWidth = this.font.width(count);
        graphics.text(this.font, count, this.panelX + PANEL_WIDTH - 8 - countWidth,
                this.panelY + 10, COLOR_LABEL, false);

        // 底部操作提示；没有可显示的清单时改成黄字说明，避免"加了待办却看不到 HUD"的困惑
        boolean hasList = this.state.hasHudList();
        Component hint = Component.translatable(hasList
                ? "screen.takeit.todo.hint"
                : "screen.takeit.todo.no_list");
        // 同一行右侧是「关闭」按钮，提示按可用宽度截断，绝不压到按钮上
        int hintWidth = PANEL_WIDTH - 8 - 12 - CLOSE_WIDTH;
        graphics.text(this.font, this.fitText(hint, hintWidth),
                this.panelX + 8, this.panelY + 214, hasList ? COLOR_HINT : COLOR_WARN, false);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        // 输入框聚焦时回车 = 添加
        if (event.key() == GLFW.GLFW_KEY_ENTER && this.input != null && this.input.isFocused()) {
            this.submitInput();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 列表自己消费滚动；未命中时交回默认处理
        if (this.list != null && this.list.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
