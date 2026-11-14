package me.shangl.takeit.client.widget;

import me.shangl.takeit.data.State;
import me.shangl.takeit.data.TodoItem;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NullMarked;

import java.util.List;
import java.util.function.Consumer;

/**
 * 可滚动的待办列表控件。
 *
 * <p>行内交互：点左侧方框/文本 = 切换完成状态，点右侧 "X" = 删除。</p>
 */
@NullMarked
public class TodoListWidget extends AbstractWidget {

    private static final int ROW_HEIGHT = 20;
    private static final int CHECKBOX_SIZE = 12;
    private static final int CHECKBOX_GAP = 8;
    private static final int DELETE_WIDTH = 14;
    private static final int SCROLLBAR_WIDTH = 4;

    private static final int COLOR_BG = 0x60000000;
    private static final int COLOR_BORDER = 0x80FFFFFF;
    private static final int COLOR_ROW_HOVER = 0x30FFFFFF;
    private static final int COLOR_TEXT = 0xFFEDEDED;
    private static final int COLOR_DONE = 0xFF8C8C8C;
    private static final int COLOR_DELETE = 0xFFFF7B72;
    private static final int COLOR_HINT = 0xFFAFAFAF;
    private static final int COLOR_SCROLL = 0x80808080;

    private static final int COLOR_BOX_BORDER = 0xFFB8B8B8;
    private static final int COLOR_BOX_BORDER_HOVER = 0xFFFFFFFF;
    private static final int COLOR_BOX_BG = 0x50000000;
    private static final int COLOR_BOX_DONE_BG = 0xFF3E7D4F;
    private static final int COLOR_CHECK = 0xFFFFFFFF;

    private final Font font;
    private final Consumer<TodoItem> onToggle;
    private final Consumer<TodoItem> onDelete;

    private int scroll;

    public TodoListWidget(int x, int y, int width, int height, Font font,
                          Consumer<TodoItem> onToggle, Consumer<TodoItem> onDelete) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.onToggle = onToggle;
        this.onDelete = onDelete;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        List<TodoItem> items = State.get().items();

        graphics.fill(getX(), getY(), getRight(), getBottom(), COLOR_BG);
        graphics.fill(getX(), getY(), getRight(), getY() + 1, COLOR_BORDER);
        graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), COLOR_BORDER);
        graphics.fill(getX(), getY(), getX() + 1, getBottom(), COLOR_BORDER);
        graphics.fill(getRight() - 1, getY(), getRight(), getBottom(), COLOR_BORDER);

        int innerTop = getY() + 1;
        int innerBottom = getBottom() - 1;
        int innerHeight = Math.max(0, innerBottom - innerTop);
        int contentHeight = items.size() * ROW_HEIGHT;
        this.scroll = clampScroll(this.scroll, contentHeight, innerHeight);

        if (items.isEmpty()) {
            String hint = Component.translatable("screen.takeit.todo.empty").getString();
            graphics.text(this.font, hint,
                    getX() + (getWidth() - this.font.width(hint)) / 2,
                    getY() + (getHeight() - this.font.lineHeight) / 2, COLOR_HINT, false);
            return;
        }

        int textRight = getRight() - 2 - DELETE_WIDTH;
        graphics.enableScissor(getX() + 1, innerTop, getRight() - 1 - SCROLLBAR_WIDTH, innerBottom);
        int firstVisible = this.scroll / ROW_HEIGHT;
        int visibleCount = innerHeight / ROW_HEIGHT + 2;
        for (int index = firstVisible; index < Math.min(items.size(), firstVisible + visibleCount); index++) {
            int rowY = innerTop + index * ROW_HEIGHT - this.scroll;
            if (rowY + ROW_HEIGHT > innerTop && rowY < innerBottom) {
                drawRow(graphics, items.get(index), rowY, textRight, mouseX, mouseY);
            }
        }
        graphics.disableScissor();

        drawScrollbar(graphics, contentHeight, innerHeight);
    }

    private void drawRow(GuiGraphicsExtractor graphics, TodoItem item, int rowY, int textRight,
                         int mouseX, int mouseY) {
        boolean hovered = mouseX >= getX() + 1 && mouseX < getRight() - 1 - SCROLLBAR_WIDTH
                && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
        if (hovered) {
            graphics.fill(getX() + 1, rowY, getRight() - 1 - SCROLLBAR_WIDTH, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);
        }

        // 勾选框：独立方块 + 内嵌勾号，比 "[x]" 更醒目
        int boxX = getX() + 4;
        int boxY = rowY + (ROW_HEIGHT - CHECKBOX_SIZE) / 2;
        int border = item.done ? COLOR_BOX_DONE_BG : (hovered ? COLOR_BOX_BORDER_HOVER : COLOR_BOX_BORDER);
        graphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE,
                item.done ? COLOR_BOX_DONE_BG : COLOR_BOX_BG);
        graphics.fill(boxX, boxY, boxX + CHECKBOX_SIZE, boxY + 1, border);
        graphics.fill(boxX, boxY + CHECKBOX_SIZE - 1, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, border);
        graphics.fill(boxX, boxY, boxX + 1, boxY + CHECKBOX_SIZE, border);
        graphics.fill(boxX + CHECKBOX_SIZE - 1, boxY, boxX + CHECKBOX_SIZE, boxY + CHECKBOX_SIZE, border);
        if (item.done) {
            drawCheck(graphics, boxX, boxY);
        }

        // 文本与勾选框之间留出明确间距
        int textX = boxX + CHECKBOX_SIZE + CHECKBOX_GAP;
        int available = Math.max(0, textRight - textX);
        String text = item.text;
        if (this.font.width(text) > available) {
            text = this.font.plainSubstrByWidth(text, Math.max(0, available - this.font.width("..."))) + "...";
        }
        graphics.text(this.font, text, textX, rowY + (ROW_HEIGHT - this.font.lineHeight) / 2,
                item.done ? COLOR_DONE : COLOR_TEXT, false);

        if (hovered) {
            graphics.text(this.font, "X", getRight() - 2 - DELETE_WIDTH + 4,
                    rowY + (ROW_HEIGHT - this.font.lineHeight) / 2, COLOR_DELETE, false);
        }
    }

    /** 用 2px 方块拼出白色勾号（默认字体没有 U+2714，只能用图形画）。 */
    private void drawCheck(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x + 3, y + 6, x + 5, y + 8, COLOR_CHECK);
        graphics.fill(x + 4, y + 7, x + 6, y + 9, COLOR_CHECK);
        graphics.fill(x + 5, y + 6, x + 7, y + 8, COLOR_CHECK);
        graphics.fill(x + 6, y + 5, x + 8, y + 7, COLOR_CHECK);
        graphics.fill(x + 7, y + 4, x + 9, y + 6, COLOR_CHECK);
        graphics.fill(x + 8, y + 3, x + 10, y + 5, COLOR_CHECK);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int contentHeight, int innerHeight) {
        if (contentHeight <= innerHeight || innerHeight <= 0) {
            return;
        }
        int trackX = getRight() - 1 - SCROLLBAR_WIDTH;
        int trackTop = getY() + 1;
        int thumbHeight = Math.max(8, innerHeight * innerHeight / contentHeight);
        int maxScroll = Math.max(1, contentHeight - innerHeight);
        int thumbTop = trackTop + (innerHeight - thumbHeight) * this.scroll / maxScroll;
        graphics.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackTop + innerHeight, 0x30000000);
        graphics.fill(trackX, thumbTop, trackX + SCROLLBAR_WIDTH, thumbTop + thumbHeight, COLOR_SCROLL);
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int index = rowIndexAt(event.x(), event.y());
        if (index < 0) {
            return;
        }
        TodoItem item = State.get().items().get(index);
        if (event.x() >= getRight() - 2 - DELETE_WIDTH - 2) {
            this.onDelete.accept(item);
        } else {
            this.onToggle.accept(item);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int innerHeight = Math.max(0, getHeight() - 2);
        int contentHeight = State.get().items().size() * ROW_HEIGHT;
        this.scroll = clampScroll(this.scroll - (int) Math.round(scrollY * ROW_HEIGHT), contentHeight, innerHeight);
        return true;
    }

    private int rowIndexAt(double mouseX, double mouseY) {
        if (mouseX < getX() + 1 || mouseX >= getRight() - 1 - SCROLLBAR_WIDTH) {
            return -1;
        }
        if (mouseY < getY() + 1 || mouseY >= getBottom() - 1) {
            return -1;
        }
        int index = (int) ((mouseY - getY() - 1 + this.scroll) / ROW_HEIGHT);
        return index >= 0 && index < State.get().items().size() ? index : -1;
    }

    private static int clampScroll(int scroll, int contentHeight, int innerHeight) {
        int maxScroll = Math.max(0, contentHeight - innerHeight);
        return Math.clamp(scroll, 0, maxScroll);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
