package me.shangl.takeit.client.screen;

import me.shangl.takeit.data.State;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * 本模组所有界面的公共基类：统一面板配色、背景与返回逻辑。
 *
 * <p>继承它是为了让 HUD 注入能用一个 {@code instanceof PanelScreen} 就跳过所有模组界面
 * （这些界面自己会画预览或不需要 HUD 叠加）。</p>
 */
public abstract class PanelScreen extends Screen {

    protected static final int COLOR_PANEL = 0xE6181818;
    protected static final int COLOR_BORDER = 0xFF3C3C3C;
    protected static final int COLOR_TITLE = 0xFFFFD166;
    protected static final int COLOR_LABEL = 0xFFCFCFCF;
    protected static final int COLOR_HINT = 0xFF8C8C8C;
    protected static final int COLOR_WARN = 0xFFFF7070;
    protected static final int COLOR_SELECTED = 0xFFFFD166;

    /** 上级界面；从快捷键/Mod Menu 打开时为 null，关闭时直接回到游戏。 */
    @Nullable
    protected final Screen parent;

    protected final State state = State.get();

    protected int panelX;
    protected int panelY;
    protected int panelWidth;
    protected int panelHeight;

    protected PanelScreen(Component title, @Nullable Screen parent) {
        super(title);
        this.parent = parent;
    }

    /** 计算居中面板的坐标与尺寸；子类在 {@link #init()} 里第一件事调用。 */
    protected void layoutPanel(int width, int height) {
        this.panelWidth = width;
        this.panelHeight = height;
        this.panelX = (this.width - width) / 2;
        this.panelY = (this.height - height) / 2;
    }

    /** 画面板底色与 1px 边框。 */
    protected void drawPanel(GuiGraphicsExtractor graphics) {
        graphics.fill(this.panelX, this.panelY,
                this.panelX + this.panelWidth, this.panelY + this.panelHeight, COLOR_PANEL);
        graphics.fill(this.panelX, this.panelY,
                this.panelX + this.panelWidth, this.panelY + 1, COLOR_BORDER);
        graphics.fill(this.panelX, this.panelY + this.panelHeight - 1,
                this.panelX + this.panelWidth, this.panelY + this.panelHeight, COLOR_BORDER);
        graphics.fill(this.panelX, this.panelY,
                this.panelX + 1, this.panelY + this.panelHeight, COLOR_BORDER);
        graphics.fill(this.panelX + this.panelWidth - 1, this.panelY,
                this.panelX + this.panelWidth, this.panelY + this.panelHeight, COLOR_BORDER);
    }

    /** 面板内居中的标题。 */
    protected void drawCenteredTitle(GuiGraphicsExtractor graphics, int relativeY) {
        int titleWidth = this.font.width(this.title);
        graphics.text(this.font, this.title,
                this.panelX + (this.panelWidth - titleWidth) / 2, this.panelY + relativeY, COLOR_TITLE, true);
    }

    /**
     * 把文本截断到 {@code maxWidth} 像素内，超出部分用省略号代替。
     *
     * <p>底部提示常和右侧按钮同一行（「关闭」「完成」），文案随语言变化长度不可控，
     * 统一走这里才能保证不压到按钮上。</p>
     */
    protected String fitText(Component text, int maxWidth) {
        String raw = text.getString();
        if (this.font.width(raw) <= maxWidth) {
            return raw;
        }
        String ellipsis = "…";
        return this.font.plainSubstrByWidth(raw, maxWidth - this.font.width(ellipsis)) + ellipsis;
    }

    @Override
    public void extractBackground(@NonNull GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // 半透明压暗背景，保留世界画面
        this.extractTransparentBackground(graphics);
    }

    @Override
    public void onClose() {
        if (this.parent == null) {
            super.onClose();
        } else {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
