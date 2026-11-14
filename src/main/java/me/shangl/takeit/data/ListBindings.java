package me.shangl.takeit.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 单存档 / 单服务器 → 清单 的绑定表（config/takeit_todo/bindings.json）。
 *
 * <p>键是"世界作用域标识"，由 {@code me.shangl.takeit.client.WorldIdentity} 生成：</p>
 * <ul>
 *   <li>单存档：{@code WORLD:<存档目录名>}；</li>
 *   <li>服务器：{@code SERVER_NAME:<名称>} / {@code BASE_DOMAIN:<域名>} / {@code FULL_DOMAIN:<域名>}，
 *       具体前缀由 {@link ServerKeyMode} 决定。</li>
 * </ul>
 *
 * <p>生命周期：不随存档保存，独立落在 config 目录，跨世界切换、重启游戏都有效；
 * 存档删除或服务器不再访问后条目会留下来变成"失效绑定"（无害，可清理）。</p>
 */
public final class ListBindings {

    /** 作用域标识 → 清单名。用 LinkedHashMap 让 JSON 里的顺序稳定、便于人工查看。 */
    public Map<String, String> entries = new LinkedHashMap<>();

    /** 取某个作用域绑定的清单名；没有时返回 null。 */
    public String get(String scope) {
        if (scope == null || this.entries == null) {
            return null;
        }
        return this.entries.get(scope);
    }

    public void put(String scope, String listName) {
        if (scope == null || listName == null) {
            return;
        }
        if (this.entries == null) {
            this.entries = new LinkedHashMap<>();
        }
        this.entries.put(scope, listName);
    }

    /** 删除某个作用域的绑定，返回是否真的删掉了。 */
    public boolean remove(String scope) {
        return this.entries != null && this.entries.remove(scope) != null;
    }

    /** 清单改名后同步所有指向它的绑定。 */
    public void renameValue(String from, String to) {
        if (this.entries == null || from == null || to == null || from.equals(to)) {
            return;
        }
        for (Map.Entry<String, String> entry : this.entries.entrySet()) {
            if (from.equals(entry.getValue())) {
                entry.setValue(to);
            }
        }
    }

    /** 清单被删除后清掉所有指向它的绑定。 */
    public void removeValue(String listName) {
        if (this.entries != null && listName != null) {
            this.entries.values().removeIf(listName::equals);
        }
    }

    /** 字段清洗：丢弃空键空值。 */
    public void normalize() {
        if (this.entries == null) {
            this.entries = new LinkedHashMap<>();
            return;
        }
        this.entries.entrySet().removeIf(entry ->
                entry.getKey() == null || entry.getKey().isBlank()
                        || entry.getValue() == null || entry.getValue().isBlank());
    }
}
