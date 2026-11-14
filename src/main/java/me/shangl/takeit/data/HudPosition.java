package me.shangl.takeit.data;

/**
 * HUD 位置数据模型（规范权威定义）。
 *
 * <h2>1. 形态</h2>
 * <p>固定为「锚点枚举 + 横向偏移量（整数像素）+ 纵向偏移量（整数像素）」，不引入自由坐标。</p>
 *
 * <h2>2. 字段</h2>
 * <table border="1">
 *   <tr><th>字段</th><th>类型</th><th>默认值</th><th>说明</th></tr>
 *   <tr><td>anchor</td><td>{@link HudAnchor}</td><td>{@code TOP_LEFT}</td><td>锚点，固定 9 个取值</td></tr>
 *   <tr><td>offsetX</td><td>int</td><td>{@code 0}</td><td>横向偏移，单位像素</td></tr>
 *   <tr><td>offsetY</td><td>int</td><td>{@code 0}</td><td>纵向偏移，单位像素</td></tr>
 * </table>
 *
 * <h2>3. 步进与边界</h2>
 * <ul>
 *   <li>步进常量 {@link #STEP} = 1 像素：细调按钮、滑块拖动、键入与鼠标拖动归一化都按它对齐。</li>
 *   <li>偏移量边界：[{@link #MIN_OFFSET}, {@link #MAX_OFFSET}] = [-2000, 2000]，越界一律钳制。</li>
 * </ul>
 *
 * <h2>4. 落库格式（config/takeit_todo/hud_position.json）</h2>
 * <pre>{@code
 * { "anchor": "TOP_LEFT", "offsetX": 0, "offsetY": 0 }
 * }</pre>
 *
 * <h2>5. 拖动归一化公式</h2>
 * <p>设屏幕（GUI 缩放后）为 {@code (sw, sh)}，HUD 盒子为 {@code (w, h)}，松手时盒子左上角目标点为 {@code (tx, ty)}：</p>
 * <ol>
 *   <li><b>最近锚点度量</b>：对 9 个锚点取基准点 {@code (ax, ay) = (baseX(sw, w), baseY(sh, h))}，
 *       取平方距离 {@code d² = (tx - ax)² + (ty - ay)²} 最小者；并列时按枚举声明顺序取先者。</li>
 *   <li><b>偏移量换算</b>：{@code offsetX = round((tx - ax) / STEP) * STEP}，Y 同理。</li>
 *   <li><b>边界钳制</b>：{@code offset = clamp(offset, MIN_OFFSET, MAX_OFFSET)}。</li>
 * </ol>
 *
 * <p><b>数值示例</b>：屏幕 1920×1080，HUD 盒子 120×40，拖动后左上角落到 {@code (1500, 40)}：</p>
 * <ul>
 *   <li>{@code TOP_RIGHT} 基准点 (1800, 0)，d² = 300² + 40² = 91600（最小）；</li>
 *   <li>{@code TOP_CENTER} 基准点 (900, 0)，d² = 600² + 40² = 361600；</li>
 *   <li>{@code MIDDLE_RIGHT} 基准点 (1800, 520)，d² = 300² + 480² = 320400；</li>
 *   <li>→ {@code offsetX = -300}、{@code offsetY = 40}；反算 1800-300 = 1500、0+40 = 40，与目标一致。</li>
 * </ul>
 *
 * <h2>6. 屏幕守卫</h2>
 * <p>渲染前还会兜底一次：若配置被手改导致盒子出屏，只在绘制阶段把左上角夹回
 * {@code [0, sw-w] × [0, sh-h]}，<b>不改写</b>已落库的偏移量，保证"界面数字 = 配置值"。</p>
 */
public final class HudPosition {

    /** 偏移量步进常量（像素）。 */
    public static final int STEP = 1;

    /** 偏移量最小值（像素）。 */
    public static final int MIN_OFFSET = -2000;

    /** 偏移量最大值（像素）。 */
    public static final int MAX_OFFSET = 2000;

    /** 默认横向偏移（像素）。 */
    public static final int DEFAULT_OFFSET_X = 0;

    /** 默认纵向偏移（像素）。 */
    public static final int DEFAULT_OFFSET_Y = 0;

    /** 锚点（Gson 直接读写枚举名）。 */
    public HudAnchor anchor = HudAnchor.DEFAULT;

    /** 横向偏移（整数像素），始终对齐 {@link #STEP} 且在边界内。 */
    public int offsetX = DEFAULT_OFFSET_X;

    /** 纵向偏移（整数像素），始终对齐 {@link #STEP} 且在边界内。 */
    public int offsetY = DEFAULT_OFFSET_Y;

    public HudPosition() {
    }

    /** 默认位置（左上角、零偏移）。 */
    public static HudPosition defaults() {
        return new HudPosition();
    }

    public void setAnchor(HudAnchor anchor) {
        this.anchor = anchor == null ? HudAnchor.DEFAULT : anchor;
    }

    /**
     * 直接设置偏移量（细调按钮 / 滑块 / 键入）：按 {@link #STEP} 对齐后钳制到边界。
     */
    public void setOffsets(int newOffsetX, int newOffsetY) {
        int snappedX = clampOffset(snap(newOffsetX));
        int snappedY = clampOffset(snap(newOffsetY));
        if (snappedX == this.offsetX && snappedY == this.offsetY) {
            return;
        }
        this.offsetX = snappedX;
        this.offsetY = snappedY;
    }

    /** 仅把偏移量恢复成默认值，锚点保持不变。 */
    public void resetOffsets() {
        setOffsets(DEFAULT_OFFSET_X, DEFAULT_OFFSET_Y);
    }

    /** 把偏移量对齐步进并钳制边界（读取配置后调用一次）。 */
    public void normalize() {
        if (this.anchor == null) {
            this.anchor = HudAnchor.DEFAULT;
        }
        setOffsets(this.offsetX, this.offsetY);
    }

    /**
     * 计算 HUD 盒子的最终绘制矩形，含"屏幕守卫"兜底。
     *
     * @return 长度 4 的数组 {@code {x, y, w, h}}，x/y 已夹在屏幕内
     */
    public int[] resolve(int screenWidth, int screenHeight, int boxWidth, int boxHeight) {
        int baseX = this.anchor.baseX(screenWidth, boxWidth);
        int baseY = this.anchor.baseY(screenHeight, boxHeight);
        int maxX = Math.max(0, screenWidth - boxWidth);
        int maxY = Math.max(0, screenHeight - boxHeight);
        return new int[]{
                Math.clamp(baseX + this.offsetX, 0, maxX),
                Math.clamp(baseY + this.offsetY, 0, maxY),
                boxWidth, boxHeight
        };
    }

    /** 拖动归一化：把"松手时盒子左上角的目标点"换算成合法的「锚点 + 偏移量」。 */
    public void applyDrag(int targetX, int targetY, int boxWidth, int boxHeight, int screenWidth, int screenHeight) {
        HudAnchor best = HudAnchor.DEFAULT;
        long bestDistance = Long.MAX_VALUE;
        for (HudAnchor candidate : HudAnchor.values()) {
            long dx = (long) targetX - candidate.baseX(screenWidth, boxWidth);
            long dy = (long) targetY - candidate.baseY(screenHeight, boxHeight);
            long distance = dx * dx + dy * dy;
            // 严格小于：并列时保留先声明的锚点，保证结果确定
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        this.anchor = best;
        setOffsets(targetX - best.baseX(screenWidth, boxWidth),
                targetY - best.baseY(screenHeight, boxHeight));
    }

    /** 对齐到最近步进倍数。 */
    public static int snap(int value) {
        return Math.round(value / (float) STEP) * STEP;
    }

    /** 偏移量钳制到 [MIN_OFFSET, MAX_OFFSET]。 */
    public static int clampOffset(int value) {
        return Math.clamp(value, MIN_OFFSET, MAX_OFFSET);
    }
}
