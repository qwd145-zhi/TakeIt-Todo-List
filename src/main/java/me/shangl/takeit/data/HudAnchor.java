package me.shangl.takeit.data;

/**
 * HUD 锚点枚举（固定 9 个取值，不可扩展）。
 *
 * <p>锚点给出 HUD 盒子的基准点，偏移量在此基础上做像素级微调。
 * 声明顺序即"最近锚点"距离并列时的取舍顺序，保证拖动归一化结果确定。</p>
 */
public enum HudAnchor {
    TOP_LEFT("top_left", 0, 0),
    TOP_CENTER("top_center", 1, 0),
    TOP_RIGHT("top_right", 2, 0),
    MIDDLE_LEFT("middle_left", 0, 1),
    CENTER("center", 1, 1),
    MIDDLE_RIGHT("middle_right", 2, 1),
    BOTTOM_LEFT("bottom_left", 0, 2),
    BOTTOM_CENTER("bottom_center", 1, 2),
    BOTTOM_RIGHT("bottom_right", 2, 2);

    /** 默认锚点：左上角。 */
    public static final HudAnchor DEFAULT = TOP_LEFT;

    private final String id;
    private final int column;
    private final int row;

    HudAnchor(String id, int column, int row) {
        this.id = id;
        this.column = column;
        this.row = row;
    }

    public int column() {
        return this.column;
    }

    public int row() {
        return this.row;
    }

    /** 基准点 X：盒子按本锚点贴到屏幕上时的左上角 X。 */
    public int baseX(int screenWidth, int boxWidth) {
        int maxX = Math.max(0, screenWidth - boxWidth);
        return switch (this.column) {
            case 1 -> maxX / 2;
            case 2 -> maxX;
            default -> 0;
        };
    }

    /** 基准点 Y：盒子按本锚点贴到屏幕上时的左上角 Y。 */
    public int baseY(int screenHeight, int boxHeight) {
        int maxY = Math.max(0, screenHeight - boxHeight);
        return switch (this.row) {
            case 1 -> maxY / 2;
            case 2 -> maxY;
            default -> 0;
        };
    }

    /** 语言文件键：screen.takeit.anchor.&lt;id&gt;。 */
    public String translationKey() {
        return "screen.takeit.anchor." + this.id;
    }
}
