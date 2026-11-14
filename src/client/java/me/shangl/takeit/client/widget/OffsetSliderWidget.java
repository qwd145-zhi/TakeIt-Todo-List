package me.shangl.takeit.client.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.function.IntConsumer;

/**
 * 偏移量滑块控件。
 *
 * <p>拖动滑块头部即可调整数值：拖动过程中按 {@code step} 对齐、按 {@code [min, max]} 钳制，
 * 变化通过 {@link IntConsumer} 回调给界面（由界面决定何时落盘）。</p>
 *
 * <p>26.2 的控件仍是「抽取式渲染」：{@link #extractWidgetRenderState} 提交绘制，
 * 鼠标事件以 {@link MouseButtonEvent} 传入。</p>
 */
@NullMarked
public class OffsetSliderWidget extends AbstractWidget {

    private static final int COLOR_TRACK = 0xFF2A2A2A;
    private static final int COLOR_TRACK_BORDER = 0xFF5A5A5A;
    private static final int COLOR_FILL = 0xFF3C5A46;
    private static final int COLOR_KNOB = 0xFFFFD166;
    private static final int COLOR_KNOB_HOT = 0xFFFFFFFF;

    private final int min;
    private final int max;
    private final int step;
    private final IntConsumer onValueChanged;

    private int value;
    private boolean dragging;

    public OffsetSliderWidget(int x, int y, int width, int height,
                              int min, int max, int step, int initialValue,
                              IntConsumer onValueChanged) {
        super(x, y, width, height, Component.empty());
        this.min = min;
        this.max = max;
        this.step = Math.max(1, step);
        this.onValueChanged = onValueChanged;
        this.value = clamp(initialValue);
    }

    public int value() {
        return this.value;
    }

    /** 外部（如输入框提交）同步数值，不触发回调。 */
    public void setValueSilently(int newValue) {
        this.value = clamp(newValue);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int trackTop = getY() + getHeight() / 2 - 2;
        int trackBottom = trackTop + 4;
        int knobCenter = knobCenter(this.value);
        boolean hovered = isMouseOver(mouseX, mouseY) || this.dragging;

        // 轨道 + 已选部分
        graphics.fill(getX(), trackTop, getRight(), trackBottom, COLOR_TRACK);
        graphics.fill(getX(), trackTop, knobCenter, trackBottom, COLOR_FILL);
        graphics.fill(getX(), trackTop, getRight(), trackTop + 1, COLOR_TRACK_BORDER);
        graphics.fill(getX(), trackBottom - 1, getRight(), trackBottom, COLOR_TRACK_BORDER);
        graphics.fill(getX(), trackTop, getX() + 1, trackBottom, COLOR_TRACK_BORDER);
        graphics.fill(getRight() - 1, trackTop, getRight(), trackBottom, COLOR_TRACK_BORDER);

        // 滑块头
        int knobLeft = Math.clamp(knobCenter - 2, getX(), getRight() - 4);
        graphics.fill(knobLeft, getY() + 1, knobLeft + 4, getBottom() - 1,
                hovered ? COLOR_KNOB_HOT : COLOR_KNOB);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) {
            return;
        }
        this.dragging = true;
        applyFromMouse(event.x());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (!this.dragging) {
            return false;
        }
        applyFromMouse(event.x());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.dragging) {
            this.dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    /** 把鼠标 x 换算成「按步进对齐 + 边界钳制」的数值。 */
    private void applyFromMouse(double mouseX) {
        int span = Math.max(1, getWidth() - 2);
        double ratio = (mouseX - (getX() + 1)) / (double) span;
        ratio = Math.clamp(ratio, 0.0, 1.0);
        int raw = this.min + (int) Math.round(ratio * (this.max - this.min));
        int snapped = Math.clamp(snap(raw), this.min, this.max);
        if (snapped != this.value) {
            this.value = snapped;
            this.onValueChanged.accept(this.value);
        }
    }

    private int knobCenter(int v) {
        double ratio = (v - this.min) / (double) Math.max(1, this.max - this.min);
        return getX() + 1 + (int) Math.round(ratio * (getWidth() - 2));
    }

    private int snap(int raw) {
        return Math.round(raw / (float) this.step) * this.step;
    }

    private int clamp(int v) {
        return Math.clamp(snap(v), this.min, this.max);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
