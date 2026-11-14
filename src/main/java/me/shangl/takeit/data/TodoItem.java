package me.shangl.takeit.data;

import java.util.UUID;

/**
 * 单条待办事项。
 *
 * <p>字段保持 public 且保留无参构造，便于 Gson 直接序列化/反序列化；
 * 反序列化后由 {@link Storage} 统一做字段清洗（null、空文本、超长文本等）。</p>
 */
public final class TodoItem {

    /** 稳定唯一 id（UUID 字符串），删除/勾选都按它定位，避免列表重排后错位。 */
    public String id;

    /** 待办内容，纯文本，长度上限 {@link #MAX_TEXT_LENGTH}。 */
    public String text;

    /** 是否已完成。 */
    public boolean done;

    /** 创建时间戳（毫秒），仅用于展示与排序兜底。 */
    public long createdAt;

    /** 文本长度上限，防止单条过长把 HUD 与界面撑爆。 */
    public static final int MAX_TEXT_LENGTH = 100;

    /** Gson 用。 */
    public TodoItem() {
        this.id = UUID.randomUUID().toString();
        this.text = "";
        this.done = false;
        this.createdAt = System.currentTimeMillis();
    }

    public TodoItem(String text) {
        this();
        this.text = sanitize(text);
    }

    /** 清洗文本：去首尾空白、截断到上限。 */
    public static String sanitize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        return trimmed.length() > MAX_TEXT_LENGTH ? trimmed.substring(0, MAX_TEXT_LENGTH) : trimmed;
    }
}
