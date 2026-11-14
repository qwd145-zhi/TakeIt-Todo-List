package me.shangl.takeit.client.hud;

import me.shangl.takeit.data.TodoItem;
import me.shangl.takeit.data.TodoList;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 待办 HUD 的统一绘制器（游戏内 HUD 与位置设置界面的预览复用同一份代码）。
 *
 * <p>26.2 的 GUI 采用「RenderState 抽取」模型：绘制通过 {@link GuiGraphicsExtractor} 提交，
 * 颜色为 ARGB（{@code 0xAARRGGBB}）。</p>
 *
 * <p>显示条数由配置 {@code hudMaxItems} 决定（0 = 全部），再做两层收紧：</p>
 * <ol>
 *   <li>不超过配置条数；</li>
 *   <li>不超过 {@code maxHeight} 能容纳的行数 —— 窗口够大时"全部"就真的全部显示。</li>
 * </ol>
 */
public final class TodoHudRenderer {

    public static final int PADDING = 4;
    public static final int LINE_HEIGHT = 10;
    public static final int MAX_BOX_WIDTH = 220;
    public static final int MIN_BOX_WIDTH = 90;

    public static final int COLOR_BG = 0x90000000;
    public static final int COLOR_BORDER = 0x80FFFFFF;
    public static final int COLOR_TITLE = 0xFFFFD166;
    public static final int COLOR_PENDING = 0xFFEDEDED;
    public static final int COLOR_DONE = 0xFF8C8C8C;
    public static final int COLOR_HINT = 0xFFAFAFAF;

    /** 拖动中的预览描边。 */
    public static final int COLOR_DRAG = 0xFFFFD166;
    /** 钳制反馈描边。 */
    public static final int COLOR_CLAMPED = 0xFFFF5555;

    private TodoHudRenderer() {
    }

    /** HUD 开关按钮的文案（配置界面共用）。 */
    public static Component hudToggleLabel(boolean visible) {
        return Component.translatable(visible ? "screen.takeit.hud.on" : "screen.takeit.hud.off");
    }

    public record Line(String text, int color) {
    }

    public record Layout(int width, int height, List<Line> lines) {
    }

    /**
     * 计算 HUD 盒子尺寸与文本行。
     *
     * @param list       要显示的清单
     * @param namedTitle 是否有全局/绑定清单之外的"多清单"语境，标题附带清单名
     * @param limit      显示条数上限，{@code <= 0} 表示全部
     * @param maxHeight  可用高度（游戏内为屏幕高度，预览界面为界面高度）
     */
    public static Layout layout(Font font, TodoList list, boolean namedTitle, int limit, int maxHeight) {
        List<TodoItem> items = list.items;
        int total = items.size();
        int done = list.done();

        // 高度允许的总行数（至少容得下标题）
        int maxLinesTotal = Math.max(1, (maxHeight - PADDING * 2) / LINE_HEIGHT);
        int cap = limit <= 0 ? Integer.MAX_VALUE : limit;
        int show = Math.min(total, Math.min(cap, maxLinesTotal - 1));

        // 还有没显示的条目时预留一行 "… 另有 N 项"；高度不够就少显示一条
        boolean overflow = total > show;
        if (overflow && show > 0 && 1 + show + 1 > maxLinesTotal) {
            show--;
            overflow = total > show;
        }
        if (overflow && 1 + show + 1 > maxLinesTotal) {
            overflow = false;
        }

        Component title = namedTitle
                ? Component.translatable("hud.takeit.title_named", list.name, done, total)
                : Component.translatable("hud.takeit.title", done, total);

        List<Line> lines = new ArrayList<>();
        lines.add(new Line(title.getString(), COLOR_TITLE));
        if (total == 0) {
            lines.add(new Line(Component.translatable("hud.takeit.empty").getString(), COLOR_HINT));
        } else {
            for (int i = 0; i < show; i++) {
                TodoItem item = items.get(i);
                // 用 [x] / [ ] 这类 ASCII 记号，避免默认字体缺字
                lines.add(new Line((item.done ? "[x] " : "[ ] ") + item.text,
                        item.done ? COLOR_DONE : COLOR_PENDING));
            }
            if (overflow) {
                lines.add(new Line(Component.translatable("hud.takeit.more", total - show).getString(), COLOR_HINT));
            }
        }

        // 先按内容测宽，再收进 [MIN, MAX] 区间并截断超宽行
        int textWidth = 0;
        for (Line line : lines) {
            textWidth = Math.max(textWidth, font.width(line.text()));
        }
        int boxWidth = Math.clamp(textWidth + PADDING * 2, MIN_BOX_WIDTH, MAX_BOX_WIDTH);
        int available = boxWidth - PADDING * 2;
        List<Line> fitted = new ArrayList<>(lines.size());
        for (Line line : lines) {
            fitted.add(new Line(fit(font, line.text(), available), line.color()));
        }
        return new Layout(boxWidth, lines.size() * LINE_HEIGHT + PADDING * 2, fitted);
    }

    /**
     * 绘制 HUD 盒子。
     *
     * @param highlight 0 = 无描边；{@link #COLOR_DRAG} = 拖动中；{@link #COLOR_CLAMPED} = 已钳制
     */
    public static void render(GuiGraphicsExtractor graphics, Font font, Layout layout,
                              int x, int y, int highlight) {
        int w = layout.width();
        int h = layout.height();

        graphics.fill(x, y, x + w, y + h, COLOR_BG);
        graphics.fill(x, y, x + w, y + 1, COLOR_BORDER);
        graphics.fill(x, y + h - 1, x + w, y + h, COLOR_BORDER);
        graphics.fill(x, y, x + 1, y + h, COLOR_BORDER);
        graphics.fill(x + w - 1, y, x + w, y + h, COLOR_BORDER);

        int textY = y + PADDING;
        for (Line line : layout.lines()) {
            graphics.text(font, line.text(), x + PADDING, textY, line.color(), true);
            textY += LINE_HEIGHT;
        }

        if (highlight != 0) {
            int x0 = x - 1;
            int y0 = y - 1;
            int x1 = x + w + 1;
            int y1 = y + h + 1;
            graphics.fill(x0, y0, x1, y0 + 1, highlight);
            graphics.fill(x0, y1 - 1, x1, y1, highlight);
            graphics.fill(x0, y0, x0 + 1, y1, highlight);
            graphics.fill(x1 - 1, y0, x1, y1, highlight);
        }
    }

    private static String fit(Font font, String text, int maxWidth) {
        if (maxWidth <= 0 || font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        int budget = Math.max(0, maxWidth - font.width(ellipsis));
        return font.plainSubstrByWidth(text, budget) + ellipsis;
    }
}
