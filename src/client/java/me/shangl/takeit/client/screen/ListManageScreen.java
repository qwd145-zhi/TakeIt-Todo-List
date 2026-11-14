package me.shangl.takeit.client.screen;

import me.shangl.takeit.client.WorldIdentity;
import me.shangl.takeit.client.widget.NameListWidget;
import me.shangl.takeit.data.State;
import me.shangl.takeit.data.TodoList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 待办清单管理（二级页面）：新建、重命名、删除，以及「单存档绑定 / 全局显示」。
 *
 * <p>每份清单对应 config/takeit_todo/lists/ 下的一个 JSON 文件，文件名就是清单名。</p>
 */
public class ListManageScreen extends PanelScreen {

    private static final int PANEL_WIDTH = 380;
    private static final int PANEL_HEIGHT = 236;

    private static final int ROW_A_Y = 120;
    private static final int ROW_B_Y = 144;

    private NameListWidget list;
    private EditBox nameInput;

    public ListManageScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.takeit.lists.title"), parent);
    }

    @Override
    protected void init() {
        this.layoutPanel(PANEL_WIDTH, PANEL_HEIGHT);

        this.list = this.addRenderableWidget(new NameListWidget(
                this.panelX + 8, this.panelY + 24, PANEL_WIDTH - 16, 92, this.font,
                (entry, doubleClick) -> {
                    this.nameInput.setValue(entry.name());
                    // 双击直接把它设为当前显示（绑定），单击只选中
                    if (doubleClick) {
                        this.state.bindScope(entry.name());
                        this.refresh();
                    }
                }));

        this.nameInput = this.addRenderableWidget(new EditBox(this.font,
                this.panelX + 8, this.panelY + ROW_A_Y, 140, 20,
                Component.translatable("screen.takeit.lists.name_hint")));
        this.nameInput.setMaxLength(TodoList.MAX_NAME_LENGTH);
        this.nameInput.setHint(Component.translatable("screen.takeit.lists.name_hint"));

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.lists.create"),
                        _ -> this.create())
                .bounds(this.panelX + 152, this.panelY + ROW_A_Y, 64, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.lists.rename"),
                        _ -> this.rename())
                .bounds(this.panelX + 220, this.panelY + ROW_A_Y, 68, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.lists.delete"),
                        _ -> this.delete())
                .bounds(this.panelX + 292, this.panelY + ROW_A_Y, 80, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.lists.bind"),
                        _ -> this.bind())
                .bounds(this.panelX + 8, this.panelY + ROW_B_Y, 110, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.lists.unbind"),
                        _ -> {
                            this.state.clearScopeBinding();
                            this.refresh();
                        })
                .bounds(this.panelX + 122, this.panelY + ROW_B_Y, 84, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.lists.global"),
                        _ -> this.setGlobal())
                .bounds(this.panelX + 210, this.panelY + ROW_B_Y, 84, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.lists.clear_global"),
                        _ -> {
                            this.state.clearGlobal();
                            this.refresh();
                        })
                .bounds(this.panelX + 298, this.panelY + ROW_B_Y, 74, 20).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.lists.done"),
                        _ -> this.onClose())
                .bounds(this.panelX + PANEL_WIDTH - 8 - 70, this.panelY + 208, 70, 20).build());

        this.refresh();
    }

    /** 重建列表行、选中项与状态文字。 */
    private void refresh() {
        String current = this.state.current().name;
        String global = this.state.settings().globalList;
        String binding = this.state.currentBinding();

        List<NameListWidget.Entry> entries = new ArrayList<>();
        for (String name : this.state.listNames()) {
            boolean isCurrent = name.equalsIgnoreCase(current);
            boolean isGlobal = name.equalsIgnoreCase(global);
            String badge = null;
            if (isCurrent) {
                badge = Component.translatable(isGlobal
                        ? "screen.takeit.lists.badge_current_global"
                        : "screen.takeit.lists.badge_current").getString();
            } else if (isGlobal) {
                badge = Component.translatable("screen.takeit.lists.badge_global").getString();
            } else if (name.equalsIgnoreCase(binding)) {
                badge = Component.translatable("screen.takeit.lists.badge_bound").getString();
            }
            entries.add(new NameListWidget.Entry(name, badge));
        }
        this.list.setEntries(entries);
        this.list.setSelected(current);
    }

    private void create() {
        String created = this.state.createList(this.nameInput.getValue());
        if (created != null) {
            this.nameInput.setValue(created);
        }
        this.refresh();
    }

    private void rename() {
        String selected = this.list.selectedName();
        if (selected != null && this.state.renameList(selected, this.nameInput.getValue())) {
            this.nameInput.setValue(TodoList.sanitizeName(this.nameInput.getValue()));
        }
        this.refresh();
    }

    private void delete() {
        String selected = this.list.selectedName();
        if (selected != null) {
            this.state.deleteList(selected);
            this.nameInput.setValue("");
        }
        this.refresh();
    }

    private void bind() {
        String selected = this.list.selectedName();
        if (selected != null) {
            this.state.bindScope(selected);
        }
        this.refresh();
    }

    private void setGlobal() {
        String selected = this.list.selectedName();
        if (selected != null) {
            this.state.setGlobal(selected);
        }
        this.refresh();
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER && this.nameInput.isFocused()) {
            // 回车 = 新建（输入框里写的就是新清单名）
            this.create();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.list.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.drawPanel(graphics);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.drawCenteredTitle(graphics, 8);

        // 状态摘要：当前清单、来源（若当前清单同时是全局清单，一并标出）、绑定与全局
        Component source = Component.translatable(this.state.sourceTranslationKey());
        // 来源已是「全局清单」时不再重复标注；绑定/最近使用等来源下补一句说明它是全局清单
        if (this.state.current().name.equalsIgnoreCase(this.state.settings().globalList)
                && this.state.source() != State.Source.GLOBAL) {
            source = source.copy().append(Component.translatable("screen.takeit.lists.global_mark"));
        }
        graphics.text(this.font, Component.translatable("screen.takeit.lists.current",
                        this.state.current().name, source),
                this.panelX + 8, this.panelY + 170, COLOR_LABEL, false);

        String scope = WorldIdentity.label();
        String binding = this.state.currentBinding();
        Component scopeLine = scope == null
                ? Component.translatable("screen.takeit.lists.no_world")
                : Component.translatable("screen.takeit.lists.scope", scope,
                binding == null ? Component.translatable("screen.takeit.lists.none") : Component.literal(binding));
        graphics.text(this.font, scopeLine, this.panelX + 8, this.panelY + 182, COLOR_HINT, false);

        graphics.text(this.font, Component.translatable("screen.takeit.lists.hint"),
                this.panelX + 8, this.panelY + 194, COLOR_HINT, false);
    }
}
