package me.shangl.takeit.client.screen;

import me.shangl.takeit.client.WorldIdentity;
import me.shangl.takeit.data.ServerKeyMode;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 模组整体配置菜单（一级入口）。
 *
 * <p>导航路径：本界面 →「待办清单管理」「显示设置」两个二级页面；
 * 服务器判定不再单独开页面，而是这里的切换按钮：点一下切到下一种模式，
 * 悬停显示当前模式的匹配规则与当前判定标识。Mod Menu 的配置入口也指向本界面。</p>
 */
public class ConfigScreen extends PanelScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 186;

    private Button modeButton;

    public ConfigScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.takeit.config.title"), parent);
    }

    @Override
    protected void init() {
        this.layoutPanel(PANEL_WIDTH, PANEL_HEIGHT);

        int x = this.panelX + 24;
        int width = PANEL_WIDTH - 48;

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.config.lists"),
                        _ -> this.minecraft.gui.setScreen(new ListManageScreen(this)))
                .bounds(x, this.panelY + 30, width, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.config.display"),
                        _ -> this.minecraft.gui.setScreen(new DisplayConfigScreen(this)))
                .bounds(x, this.panelY + 56, width, 20).build());

        // 服务器判定：就地循环切换，规则通过悬停提示展示
        this.modeButton = this.addRenderableWidget(Button.builder(this.modeLabel(), this::cycleMode)
                .bounds(x, this.panelY + 82, width, 20).build());
        this.updateModeTooltip();

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.config.done"),
                        _ -> this.onClose())
                .bounds(this.panelX + (PANEL_WIDTH - 80) / 2, this.panelY + 154, 80, 20).build());
    }

    /** 按钮文案：服务器判定：<当前模式>。 */
    private Component modeLabel() {
        ServerKeyMode mode = this.state.settings().serverKeyMode;
        return Component.translatable("screen.takeit.config.server_mode",
                Component.translatable(mode.translationKey()));
    }

    /** 切到下一种判定模式，并立刻重算当前作用域（绑定关系跟着新模式走）。 */
    private void cycleMode(Button button) {
        ServerKeyMode[] modes = ServerKeyMode.values();
        ServerKeyMode next = modes[(this.state.settings().serverKeyMode.ordinal() + 1) % modes.length];
        this.state.setServerKeyMode(next);
        WorldIdentity.sync();
        button.setMessage(this.modeLabel());
        this.updateModeTooltip();
    }

    private void updateModeTooltip() {
        ServerKeyMode mode = this.state.settings().serverKeyMode;
        String key = WorldIdentity.key();
        Component identity = key == null
                ? Component.translatable("screen.takeit.server.none")
                : Component.literal(key);
        this.modeButton.setTooltip(Tooltip.create(Component.translatable(
                "screen.takeit.config.server_tooltip",
                Component.translatable(mode.translationKey()),
                Component.translatable("screen.takeit.server.rule." + mode.id()),
                Component.translatable("screen.takeit.server.note"),
                identity)));
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.drawPanel(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.drawCenteredTitle(graphics, 8);

        // 状态摘要：当前清单 + 来源，世界与绑定情况
        Component current = Component.translatable("screen.takeit.config.status",
                this.state.current().name,
                Component.translatable(this.state.sourceTranslationKey()));
        graphics.text(this.font, current, this.panelX + 12, this.panelY + 112, COLOR_LABEL, false);

        String scope = WorldIdentity.label();
        Component worldLine = scope == null
                ? Component.translatable("screen.takeit.config.no_world")
                : Component.translatable("screen.takeit.config.world", scope);
        graphics.text(this.font, worldLine, this.panelX + 12, this.panelY + 126, COLOR_HINT, false);

        Component hint = Component.translatable("screen.takeit.config.hint");
        graphics.text(this.font, hint, this.panelX + 12, this.panelY + 140, COLOR_HINT, false);
    }
}
