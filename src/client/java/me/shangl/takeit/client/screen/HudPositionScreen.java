package me.shangl.takeit.client.screen;

import me.shangl.takeit.client.WorldIdentity;
import me.shangl.takeit.client.hud.TodoHudRenderer;
import me.shangl.takeit.client.widget.OffsetSliderWidget;
import me.shangl.takeit.data.HudAnchor;
import me.shangl.takeit.data.HudPosition;
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
 * HUD 位置设置界面：上半屏是 HUD 预览（可直接拖动），底部是锚点 + 偏移量控制面板。
 *
 * <ul>
 *   <li>左：3×3 锚点；右：偏移量（− / 输入框 / + / 滑块，步进 1px）；</li>
 *   <li>拖动：按「最近锚点 + 步进对齐 + 边界钳制」归一化并落库；</li>
 *   <li>HUD 与面板重叠时按面板上沿裁剪；完全被遮住时用金色定位框标出，仍可抓取。</li>
 * </ul>
 *
 * <p>HUD 开关与显示条数已移到「显示设置」页，本页只负责位置。</p>
 */
public class HudPositionScreen extends PanelScreen {

    private static final int PANEL_WIDTH = 360;
    private static final int PANEL_HEIGHT = 160;
    private static final int PANEL_PADDING = 10;

    private static final int TITLE_Y = 6;
    private static final int SECTION_LABEL_Y = 22;

    private static final int ANCHOR_WIDTH = 44;
    private static final int ANCHOR_HEIGHT = 18;
    private static final int ANCHOR_GAP_X = 46;
    private static final int ANCHOR_GAP_Y = 20;
    private static final int GRID_Y = 34;

    /** 右侧偏移量区（相对面板左边缘）：标签 → − → 输入框 → + → 滑块。 */
    private static final int OFFSET_LABEL_X = 156;
    private static final int MINUS_BTN_X = 164;
    private static final int EDIT_X = 182;
    private static final int EDIT_WIDTH = 44;
    private static final int PLUS_BTN_X = 228;
    private static final int SLIDER_X = 248;
    private static final int SLIDER_WIDTH = 102;
    private static final int OFFSET_ROW_Y = 44;
    private static final int OFFSET_ROW_GAP = 26;
    private static final int CONTROL_HEIGHT = 18;

    /** 底部：重置偏移量 + 范围提示 + 拖动提示。 */
    private static final int BUTTON_ROW_Y = 104;
    private static final int RESET_WIDTH = 100;
    private static final int RANGE_Y = 126;
    private static final int HINT_Y = 140;
    private static final int DONE_Y = 138;

    private int gridX;
    private int offsetLabelX;

    private EditBox offsetXInput;
    private EditBox offsetYInput;
    private OffsetSliderWidget offsetXSlider;
    private OffsetSliderWidget offsetYSlider;

    private boolean dragging;
    private int grabDx;
    private int grabDy;
    private boolean clamped;

    private TodoHudRenderer.Layout layout;

    public HudPositionScreen(@Nullable Screen parent) {
        super(Component.translatable("screen.takeit.position.title"), parent);
    }

    @Override
    protected void init() {
        // 位置界面固定在屏幕下方，上方留给 HUD 预览
        this.layoutPanel(PANEL_WIDTH, PANEL_HEIGHT);
        this.panelY = this.height - PANEL_HEIGHT - 8;
        this.gridX = this.panelX + PANEL_PADDING;
        this.offsetLabelX = this.panelX + OFFSET_LABEL_X;
        this.relayout();

        int offsetXRowY = this.panelY + OFFSET_ROW_Y;
        int offsetYRowY = offsetXRowY + OFFSET_ROW_GAP;
        int buttonY = this.panelY + BUTTON_ROW_Y;

        // 3×3 锚点
        for (HudAnchor anchor : HudAnchor.values()) {
            this.addRenderableWidget(Button.builder(
                            Component.translatable(anchor.translationKey()),
                            _ -> {
                                this.state.hudPosition().setAnchor(anchor);
                                this.state.saveHud();
                                this.clamped = false;
                            })
                    .bounds(this.gridX + anchor.column() * ANCHOR_GAP_X,
                            this.panelY + GRID_Y + anchor.row() * ANCHOR_GAP_Y,
                            ANCHOR_WIDTH, ANCHOR_HEIGHT)
                    .build());
        }

        // 偏移量：细调按钮（步进 1px）+ 输入框 + 滑块
        this.addRenderableWidget(Button.builder(Component.literal("-"), _ -> this.adjustOffset(-HudPosition.STEP, 0))
                .bounds(this.panelX + MINUS_BTN_X, offsetXRowY, 16, CONTROL_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), _ -> this.adjustOffset(HudPosition.STEP, 0))
                .bounds(this.panelX + PLUS_BTN_X, offsetXRowY, 16, CONTROL_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.literal("-"), _ -> this.adjustOffset(0, -HudPosition.STEP))
                .bounds(this.panelX + MINUS_BTN_X, offsetYRowY, 16, CONTROL_HEIGHT).build());
        this.addRenderableWidget(Button.builder(Component.literal("+"), _ -> this.adjustOffset(0, HudPosition.STEP))
                .bounds(this.panelX + PLUS_BTN_X, offsetYRowY, 16, CONTROL_HEIGHT).build());

        this.offsetXInput = this.addRenderableWidget(new EditBox(this.font,
                this.panelX + EDIT_X, offsetXRowY, EDIT_WIDTH, CONTROL_HEIGHT,
                Component.translatable("screen.takeit.position.offset_x")));
        this.offsetYInput = this.addRenderableWidget(new EditBox(this.font,
                this.panelX + EDIT_X, offsetYRowY, EDIT_WIDTH, CONTROL_HEIGHT,
                Component.translatable("screen.takeit.position.offset_y")));
        this.offsetXInput.setMaxLength(6);
        this.offsetYInput.setMaxLength(6);

        this.offsetXSlider = this.addRenderableWidget(new OffsetSliderWidget(
                this.panelX + SLIDER_X, offsetXRowY, SLIDER_WIDTH, CONTROL_HEIGHT,
                HudPosition.MIN_OFFSET, HudPosition.MAX_OFFSET, HudPosition.STEP,
                this.state.hudPosition().offsetX, value -> this.onSliderChanged(true, value)));
        this.offsetYSlider = this.addRenderableWidget(new OffsetSliderWidget(
                this.panelX + SLIDER_X, offsetYRowY, SLIDER_WIDTH, CONTROL_HEIGHT,
                HudPosition.MIN_OFFSET, HudPosition.MAX_OFFSET, HudPosition.STEP,
                this.state.hudPosition().offsetY, value -> this.onSliderChanged(false, value)));

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.position.reset_offsets"),
                        _ -> {
                            this.state.resetHudOffsets();
                            this.refreshInputs();
                            this.clamped = false;
                        })
                .bounds(this.panelX + PANEL_PADDING, buttonY, RESET_WIDTH, CONTROL_HEIGHT).build());

        this.addRenderableWidget(Button.builder(
                        Component.translatable("screen.takeit.position.finish"),
                        _ -> this.onClose())
                .bounds(this.panelX + PANEL_WIDTH - PANEL_PADDING - 70, this.panelY + DONE_Y, 70, 18)
                .build());

        this.refreshInputs();
    }

    // ------------------------------------------------------------------
    // 数值联动
    // ------------------------------------------------------------------

    private void relayout() {
        // 可用高度就是整个界面高度：窗口足够大时"全部"能完整显示
        this.layout = TodoHudRenderer.layout(this.font, this.state.current(),
                this.state.listCount() > 1, this.state.hudMaxItems(), this.height);
    }

    private void adjustOffset(int deltaX, int deltaY) {
        HudPosition position = this.state.hudPosition();
        position.setOffsets(position.offsetX + deltaX, position.offsetY + deltaY);
        this.state.saveHud();
        this.refreshInputs();
        this.clamped = false;
    }

    private void onSliderChanged(boolean xAxis, int value) {
        HudPosition position = this.state.hudPosition();
        position.setOffsets(xAxis ? value : position.offsetX, xAxis ? position.offsetY : value);
        // 拖动过程中不落盘，松手时统一保存（见 mouseReleased）
        this.refreshInputs();
    }

    /** 把状态同步回输入框与滑块（跳过正在编辑的输入框，避免光标被重置）。 */
    private void refreshInputs() {
        HudPosition position = this.state.hudPosition();
        if (!this.offsetXInput.isFocused()) {
            this.offsetXInput.setValue(Integer.toString(position.offsetX));
        }
        if (!this.offsetYInput.isFocused()) {
            this.offsetYInput.setValue(Integer.toString(position.offsetY));
        }
        this.offsetXSlider.setValueSilently(position.offsetX);
        this.offsetYSlider.setValueSilently(position.offsetY);
    }

    /** 提交偏移量输入框：非数字回退当前值，合法值经 setOffsets 对齐并钳制。 */
    private void commitEditor(EditBox box, boolean xAxis) {
        HudPosition position = this.state.hudPosition();
        int fallback = xAxis ? position.offsetX : position.offsetY;
        int parsed;
        try {
            parsed = Integer.parseInt(box.getValue().trim());
        } catch (NumberFormatException e) {
            parsed = fallback;
        }
        position.setOffsets(xAxis ? parsed : position.offsetX, xAxis ? position.offsetY : parsed);
        this.state.saveHud();
        box.setValue(Integer.toString(xAxis ? position.offsetX : position.offsetY));
        this.refreshInputs();
    }

    private int[] currentRect() {
        return this.state.hudPosition().resolve(this.width, this.height,
                this.layout.width(), this.layout.height());
    }

    private boolean isOverPreview(double mouseX, double mouseY) {
        // 没有可显示的清单、或当前服务器在黑名单里时，HUD 与预览都不画，自然也不能拖
        if (!this.previewVisible()) {
            return false;
        }
        int[] rect = this.currentRect();
        return mouseX >= rect[0] - 2 && mouseX <= rect[0] + rect[2] + 2
                && mouseY >= rect[1] - 2 && mouseY <= rect[1] + rect[3] + 2;
    }

    /**
     * 预览是否可画：本存档/服务器有可显示的清单，且当前服务器不在黑名单里。
     *
     * <p>黑名单名下不画预览，也不在界面上说明原因——名单本身不出现在任何界面里。</p>
     */
    private boolean previewVisible() {
        return this.state.hasHudList() && !WorldIdentity.blacklisted();
    }

    private boolean overlapsPanel(int[] rect) {
        return rect[0] < this.panelX + PANEL_WIDTH && rect[0] + rect[2] > this.panelX
                && rect[1] < this.panelY + PANEL_HEIGHT && rect[1] + rect[3] > this.panelY;
    }

    // ------------------------------------------------------------------
    // 鼠标 / 键盘
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        this.commitEditorsIfClickedOutside(event.x(), event.y());

        // 只要点在 HUD 盒子上、且该点没有控件，就能抓起（含落在面板范围内的情形）
        if (event.button() == 0 && !this.dragging
                && this.isOverPreview(event.x(), event.y())
                && this.getChildAt(event.x(), event.y()).isEmpty()) {
            this.dragging = true;
            this.clamped = false;
            int[] rect = this.currentRect();
            this.grabDx = (int) Math.round(event.x()) - rect[0];
            this.grabDy = (int) Math.round(event.y()) - rect[1];
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(@NonNull MouseButtonEvent event, double dragX, double dragY) {
        if (this.dragging) {
            int targetX = (int) Math.round(event.x()) - this.grabDx;
            int targetY = (int) Math.round(event.y()) - this.grabDy;
            this.state.hudPosition().applyDrag(targetX, targetY,
                    this.layout.width(), this.layout.height(), this.width, this.height);

            int[] rect = this.currentRect();
            this.clamped = Math.abs(rect[0] - targetX) > HudPosition.STEP
                    || Math.abs(rect[1] - targetY) > HudPosition.STEP;
            this.refreshInputs();
            return true;
        }
        // 滑块拖动（鼠标移出滑块范围也要继续响应）
        if (this.offsetXSlider.mouseDragged(event, dragX, dragY)
                || this.offsetYSlider.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(@NonNull MouseButtonEvent event) {
        if (this.dragging) {
            this.dragging = false;
            this.state.saveHud();
            this.refreshInputs();
            return true;
        }
        if (this.offsetXSlider.mouseReleased(event) | this.offsetYSlider.mouseReleased(event)) {
            this.state.saveHud();
            this.refreshInputs();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(@NonNull KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER) {
            if (this.offsetXInput.isFocused()) {
                this.commitEditor(this.offsetXInput, true);
                return true;
            }
            if (this.offsetYInput.isFocused()) {
                this.commitEditor(this.offsetYInput, false);
                return true;
            }
        }
        return super.keyPressed(event);
    }

    private void commitEditorsIfClickedOutside(double mouseX, double mouseY) {
        if (this.offsetXInput.isFocused() && !this.offsetXInput.isMouseOver(mouseX, mouseY)) {
            this.commitEditor(this.offsetXInput, true);
        }
        if (this.offsetYInput.isFocused() && !this.offsetYInput.isMouseOver(mouseX, mouseY)) {
            this.commitEditor(this.offsetYInput, false);
        }
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    @Override
    public void extractRenderState(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.drawPanel(graphics);

        // 预览：与面板重叠时按面板上沿裁剪；拖动中不裁剪；完全被遮住时画定位框
        // 没有可显示的清单（本存档未绑定 + 没有全局清单）或在黑名单服务器上时，预览一并隐藏
        if (this.previewVisible()) {
            int[] rect = this.currentRect();
            boolean overlaps = this.overlapsPanel(rect);
            boolean fullyHidden = overlaps && rect[1] >= this.panelY;
            int highlight = this.dragging ? TodoHudRenderer.COLOR_DRAG
                    : (this.clamped ? TodoHudRenderer.COLOR_CLAMPED : 0);
            boolean clip = overlaps && !this.dragging;
            if (clip) {
                graphics.enableScissor(0, 0, this.width, this.panelY);
            }
            TodoHudRenderer.render(graphics, this.font, this.layout, rect[0], rect[1], highlight);
            if (clip) {
                graphics.disableScissor();
            }

            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            this.drawOverlay(graphics);

            if (fullyHidden && !this.dragging) {
                this.drawPreviewOutline(graphics, rect, this.clamped ? COLOR_WARN : COLOR_SELECTED);
            }
            return;
        }

        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        this.drawOverlay(graphics);
    }

    private void drawOverlay(GuiGraphicsExtractor graphics) {
        this.drawCenteredTitle(graphics, TITLE_Y);

        graphics.text(this.font, Component.translatable("screen.takeit.position.anchor"),
                this.gridX, this.panelY + SECTION_LABEL_Y, COLOR_LABEL, false);
        graphics.text(this.font, Component.translatable("screen.takeit.position.offset"),
                this.offsetLabelX, this.panelY + SECTION_LABEL_Y, COLOR_LABEL, false);

        int offsetXRowY = this.panelY + OFFSET_ROW_Y;
        int offsetYRowY = offsetXRowY + OFFSET_ROW_GAP;
        graphics.text(this.font, "X", this.offsetLabelX, offsetXRowY + 5, COLOR_LABEL, false);
        graphics.text(this.font, "Y", this.offsetLabelX, offsetYRowY + 5, COLOR_LABEL, false);

        // 选中的锚点：金色描边
        HudAnchor anchor = this.state.hudPosition().anchor;
        int bx = this.gridX + anchor.column() * ANCHOR_GAP_X;
        int by = this.panelY + GRID_Y + anchor.row() * ANCHOR_GAP_Y;
        graphics.fill(bx - 1, by - 1, bx + ANCHOR_WIDTH + 1, by, COLOR_SELECTED);
        graphics.fill(bx - 1, by + ANCHOR_HEIGHT, bx + ANCHOR_WIDTH + 1, by + ANCHOR_HEIGHT + 1, COLOR_SELECTED);
        graphics.fill(bx - 1, by, bx, by + ANCHOR_HEIGHT, COLOR_SELECTED);
        graphics.fill(bx + ANCHOR_WIDTH, by, bx + ANCHOR_WIDTH + 1, by + ANCHOR_HEIGHT, COLOR_SELECTED);

        Component range = Component.translatable("screen.takeit.position.range",
                HudPosition.STEP, HudPosition.MIN_OFFSET, HudPosition.MAX_OFFSET);
        graphics.text(this.font, range, this.panelX + PANEL_PADDING, this.panelY + RANGE_Y, COLOR_HINT, false);
        if (this.clamped) {
            int warnX = this.panelX + PANEL_PADDING + this.font.width(range) + 12;
            graphics.text(this.font, Component.translatable("screen.takeit.position.clamped"),
                    warnX, this.panelY + RANGE_Y, COLOR_WARN, false);
        }
        boolean hasList = this.previewVisible();
        // 提示与「完成」按钮同一行，按可用宽度截断，避免压到按钮
        Component hint = Component.translatable(hasList
                ? "screen.takeit.position.drag_hint"
                : "screen.takeit.position.no_list");
        graphics.text(this.font, this.fitText(hint, PANEL_WIDTH - PANEL_PADDING - 8 - 70),
                this.panelX + PANEL_PADDING, this.panelY + HINT_Y,
                hasList ? COLOR_HINT : COLOR_WARN, false);
    }

    /** 当 HUD 完全落在面板内时，用 1px 定位框标出（仍可点击拖动）。 */
    private void drawPreviewOutline(GuiGraphicsExtractor graphics, int[] rect, int color) {
        int x0 = rect[0];
        int y0 = rect[1];
        int x1 = rect[0] + rect[2];
        int y1 = rect[1] + rect[3];
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y0, color);
        graphics.fill(x0 - 1, y1, x1 + 1, y1 + 1, color);
        graphics.fill(x0 - 1, y0, x0, y1, color);
        graphics.fill(x1, y0, x1 + 1, y1, color);
        graphics.text(this.font, "HUD", x0 + 2, y0 + 2, color, true);
    }

    @Override
    public void onClose() {
        if (this.offsetXInput.isFocused()) {
            this.commitEditor(this.offsetXInput, true);
        }
        if (this.offsetYInput.isFocused()) {
            this.commitEditor(this.offsetYInput, false);
        }
        super.onClose();
    }
}
