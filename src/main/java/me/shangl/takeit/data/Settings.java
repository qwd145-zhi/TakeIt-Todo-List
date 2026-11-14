package me.shangl.takeit.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 模组级配置（config/takeit_todo/settings.json）：与"哪份清单"无关的全局开关。
 *
 * <p>清单的选择状态分成三层，落盘位置各不相同，便于理解与排查：</p>
 * <ul>
 *   <li>{@link #globalList} —— 全局清单（任意存档/服务器可见），本文件；</li>
 *   <li>{@link #lastUsedList} —— 最近一次实际显示的清单（兜底用），本文件；</li>
 *   <li>单存档/单服务器绑定 —— 见 {@link ListBindings}（bindings.json）。</li>
 * </ul>
 */
public final class Settings {

    /** 默认 HUD 显示条数（0 = 全部）。 */
    public static final int DEFAULT_HUD_MAX_ITEMS = 5;

    /** HUD 总开关。 */
    public boolean hudVisible = true;

    /** HUD 最多显示的待办条数；0 表示全部（仍受屏幕高度限制）。 */
    public int hudMaxItems = DEFAULT_HUD_MAX_ITEMS;

    /** 全局显示清单名；null = 未设置。 */
    public String globalList;

    /** 最近一次显示的清单名；null = 未记录。 */
    public String lastUsedList;

    /** 服务器同一性判定依据。 */
    public ServerKeyMode serverKeyMode = ServerKeyMode.DEFAULT;

    /**
     * 服务器黑名单：命中时既不在 HUD 上显示待办，也不能用快捷键打开待办清单界面。
     *
     * <p>只能在 config/takeit_todo/settings.json 里手工编辑（界面上刻意不提供入口）。
     * 条目按「完整域名」精确匹配，可写成 {@code mc.example.com} 或 {@code mc.example.com:25565}
     * （端口会被忽略），大小写不敏感；填 IP 时按整个 IP 比较。</p>
     */
    public List<String> serverBlacklist = new ArrayList<>();

    /** 给定主机名（已去端口、小写）是否命中黑名单。 */
    public boolean isBlacklisted(String host) {
        return host != null && !host.isEmpty() && this.serverBlacklist.contains(host);
    }

    /** 字段清洗：空串一律按未设置处理，数值夹到合法区间。 */
    public void normalize() {
        this.hudMaxItems = Math.clamp(this.hudMaxItems, 0, TodoList.MAX_ITEMS);
        this.serverKeyMode = ServerKeyMode.orDefault(this.serverKeyMode);
        this.globalList = blankToNull(this.globalList);
        this.lastUsedList = blankToNull(this.lastUsedList);
        this.serverBlacklist = normalizeBlacklist(this.serverBlacklist);
    }

    /** 黑名单清洗：去端口/方括号、转小写、去空行、去重复，保持书写顺序。 */
    private static List<String> normalizeBlacklist(List<String> raw) {
        List<String> result = new ArrayList<>();
        if (raw == null) {
            return result;
        }
        for (String entry : raw) {
            // hostOf 负责去端口、脱 IPv6 方括号与转小写，和判定用的主机名口径保持一致
            String host = ServerKeyMode.hostOf(entry);
            if (!host.isEmpty() && !result.contains(host)) {
                result.add(host);
            }
        }
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
