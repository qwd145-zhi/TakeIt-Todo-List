package me.shangl.takeit.client.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 可滚动的名称列表控件（清单管理界面用）。
 *
 * <p>每行 = 名称 + 右侧可选标记（如「当前」「全局」「绑定」），单击选中并回调，
 * 双击额外告知回调方，方便实现「双击切换显示」这类快捷操作。</p>
 */
public class NameListWidget extends AbstractWidget {

    /** 一行数据：名称 + 右侧标记（标记可为 null）。 */
    public record Entry(String name, @Nullable String badge) {
    }

    /** 点击回调：entry 为被点的行，doubleClick 表示是否双击。 */
    public interface PickHandler {
        void onPick(Entry entry, boolean doubleClick);
    }

    private static final int ROW_HEIGHT = 16;
    private static final int SCROLLBAR_WIDTH = 4;

    private static final int COLOR_BG = 0x60000000;
    private static final int COLOR_BORDER = 0x80FFFFFF;
    private static final int COLOR_ROW_HOVER = 0x30FFFFFF;
    private static final int COLOR_ROW_SELECTED = 0x40FFD166;
    private static final int COLOR_TEXT = 0xFFEDEDED;
    private static final int COLOR_BADGE = 0xFFFFD166;
    private static final int COLOR_HINT = 0xFFAFAFAF;
    private static final int COLOR_SCROLL = 0x80808080;

    private final Font font;
    private final PickHandler handler;
    private final List<Entry> entries = new ArrayList<>();

    private int selectedIndex = -1;
    private int scroll;

    public NameListWidget(int x, int y, int width, int height, Font font, PickHandler handler) {
        super(x, y, width, height, Component.empty());
        this.font = font;
        this.handler = handler;
    }

    /** 整体替换行数据，尽量保留原选中项。 */
    public void setEntries(List<Entry> newEntries) {
        String previous = this.selectedName();
        this.entries.clear();
        this.entries.addAll(newEntries);
        this.selectedIndex = previous == null ? -1 : indexOf(previous);
    }

    /** 按名称选中（不存在则清空选中）。 */
    public void setSelected(@Nullable String name) {
        this.selectedIndex = name == null ? -1 : indexOf(name);
    }

    /** 当前选中的名称；没有选中时为 null。 */
    public @Nullable String selectedName() {
        return this.selectedIndex >= 0 && this.selectedIndex < this.entries.size()
                ? this.entries.get(this.selectedIndex).name()
                : null;
    }

    private int indexOf(String name) {
        for (int i = 0; i < this.entries.size(); i++) {
            if (this.entries.get(i).name().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected void extractWidgetRenderState(@NonNull GuiGraphicsExtractor graphics,
                                            int mouseX, int mouseY, float partialTick) {
        graphics.fill(getX(), getY(), getRight(), getBottom(), COLOR_BG);
        graphics.fill(getX(), getY(), getRight(), getY() + 1, COLOR_BORDER);
        graphics.fill(getX(), getBottom() - 1, getRight(), getBottom(), COLOR_BORDER);
        graphics.fill(getX(), getY(), getX() + 1, getBottom(), COLOR_BORDER);
        graphics.fill(getRight() - 1, getY(), getRight(), getBottom(), COLOR_BORDER);

        int innerTop = getY() + 1;
        int innerHeight = Math.max(0, getBottom() - 1 - innerTop);
        int contentHeight = this.entries.size() * ROW_HEIGHT;
        this.scroll = clampScroll(this.scroll, contentHeight, innerHeight);

        if (this.entries.isEmpty()) {
            String hint = Component.translatable("screen.takeit.lists.empty").getString();
            graphics.text(this.font, hint,
                    getX() + (getWidth() - this.font.width(hint)) / 2,
                    getY() + (getHeight() - this.font.lineHeight) / 2, COLOR_HINT, false);
            return;
        }

        int textRight = getRight() - 4 - SCROLLBAR_WIDTH;
        graphics.enableScissor(getX() + 1, innerTop, getRight() - 1 - SCROLLBAR_WIDTH, getBottom() - 1);
        int firstVisible = this.scroll / ROW_HEIGHT;
        int visibleCount = innerHeight / ROW_HEIGHT + 2;
        for (int index = firstVisible; index < Math.min(this.entries.size(), firstVisible + visibleCount); index++) {
            int rowY = innerTop + index * ROW_HEIGHT - this.scroll;
            if (rowY + ROW_HEIGHT <= innerTop || rowY >= getBottom() - 1) {
                continue;
            }
            boolean hovered = mouseX >= getX() + 1 && mouseX < textRight
                    && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT;
            if (index == this.selectedIndex) {
                graphics.fill(getX() + 1, rowY, textRight, rowY + ROW_HEIGHT, COLOR_ROW_SELECTED);
            } else if (hovered) {
                graphics.fill(getX() + 1, rowY, textRight, rowY + ROW_HEIGHT, COLOR_ROW_HOVER);
            }

            Entry entry = this.entries.get(index);
            int textY = rowY + (ROW_HEIGHT - this.font.lineHeight) / 2;
            String badge = entry.badge();
            int badgeWidth = badge == null ? 0 : this.font.width(badge);
            int available = Math.max(0, textRight - 6 - getX() - badgeWidth - (badge == null ? 0 : 6));
            String name = fit(entry.name(), available);
            graphics.text(this.font, name, getX() + 4, textY, COLOR_TEXT, false);
            if (badge != null) {
                graphics.text(this.font, badge, textRight - 4 - badgeWidth, textY, COLOR_BADGE, false);
            }
        }
        graphics.disableScissor();

        drawScrollbar(graphics, contentHeight, innerHeight);
    }

    private String fit(String text, int maxWidth) {
        if (maxWidth <= 0) {
            return "";
        }
        if (this.font.width(text) <= maxWidth) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width("..."))) + "...";
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
    public void onClick(@NonNull MouseButtonEvent event, boolean doubleClick) {
        int index = rowIndexAt(event.x(), event.y());
        if (index < 0) {
            return;
        }
        this.selectedIndex = index;
        this.handler.onPick(this.entries.get(index), doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        int innerHeight = Math.max(0, getHeight() - 2);
        int contentHeight = this.entries.size() * ROW_HEIGHT;
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
        return index >= 0 && index < this.entries.size() ? index : -1;
    }

    private static int clampScroll(int scroll, int contentHeight, int innerHeight) {
        int maxScroll = Math.max(0, contentHeight - innerHeight);
        return Math.clamp(scroll, 0, maxScroll);
    }

    @Override
    protected void updateWidgetNarration(@NonNull NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
    }
}
