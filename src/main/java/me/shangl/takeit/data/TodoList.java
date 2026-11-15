package me.shangl.takeit.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 一份独立的待办清单（对应 config/takeit_todo/lists/ 下的一个文件）。
 *
 * <p>文件名就是清单的权威标识：{@code todo_<清单名>.json}。JSON 内的 {@link #name}
 * 只作冗余展示，加载时一律以文件名为准，避免改名后两处不一致。</p>
 *
 * <p>字段保持 public 便于 Gson 直接序列化/反序列化；反序列化后由 {@link #normalize()}
 * 统一清洗（null、空条目、超长文本等）。</p>
 */
public final class TodoList {

    /** 单份清单最多保存的待办条数。 */
    public static final int MAX_ITEMS = 500;

    /** 清单名长度上限（按字符算）。 */
    public static final int MAX_NAME_LENGTH = 32;

    /** 默认清单名：一份清单都没有时用它兜底。 */
    public static final String DEFAULT_NAME = "默认";

    private static final String FILE_PREFIX = "todo_";
    private static final String FILE_SUFFIX = ".json";

    /** Windows 文件名非法字符，统一替换成下划线。 */
    private static final String ILLEGAL_CHARS = "<>:\"/\\|?*";

    /** Windows 保留设备名（大小写不敏感），直接当文件名会创建失败。 */
    private static final Set<String> RESERVED_NAMES = Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    /** 清单名（冗余字段，以文件名为准）。 */
    public String name;

    /** 待办条目；反序列化后可能为 null，由 {@link #normalize()} 兜底。 */
    public List<TodoItem> items = new ArrayList<>();

    /** 新建一份空清单，名字会先经过 {@link #sanitizeName(String)} 清洗。 */
    public TodoList(String name) {
        this.name = sanitizeName(name);
    }

    /**
     * 清洗清单名：去掉首尾空白与文件名非法字符、折叠空白、限长、规避保留名。
     *
     * @return 永远不会返回 null；无法得到合法名字时返回 {@link #DEFAULT_NAME}
     */
    public static String sanitizeName(String raw) {
        if (raw == null) {
            return DEFAULT_NAME;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT_NAME;
        }
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            builder.append(c < 0x20 || ILLEGAL_CHARS.indexOf(c) >= 0 ? '_' : c);
        }
        String cleaned = builder.toString().trim();
        // Windows 不允许文件名以点或空格结尾
        while (!cleaned.isEmpty() && (cleaned.endsWith(".") || cleaned.endsWith(" "))) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        if (cleaned.length() > MAX_NAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_NAME_LENGTH).trim();
        }
        if (cleaned.isEmpty()) {
            return DEFAULT_NAME;
        }
        if (RESERVED_NAMES.contains(cleaned.toLowerCase(Locale.ROOT))) {
            cleaned = "清单_" + cleaned;
        }
        return cleaned;
    }

    /** 清单名 → 文件名，例如 {@code 默认} → {@code todo_默认.json}。 */
    public static String fileName(String name) {
        return FILE_PREFIX + name + FILE_SUFFIX;
    }

    /**
     * 文件名 → 清单名；不符合 {@code todo_*.json} 规则时返回 null。
     */
    public static String nameFromFile(String fileName) {
        if (fileName == null
                || !fileName.startsWith(FILE_PREFIX)
                || !fileName.endsWith(FILE_SUFFIX)) {
            return null;
        }
        String name = fileName.substring(FILE_PREFIX.length(), fileName.length() - FILE_SUFFIX.length());
        return name.isEmpty() ? null : name;
    }

    public int total() {
        return this.items.size();
    }

    public int done() {
        int done = 0;
        for (TodoItem item : this.items) {
            if (item.done) {
                done++;
            }
        }
        return done;
    }

    /** 字段清洗：补齐 null 容器、丢弃空条目、补 id、截断超长文本、限制总条数。 */
    public void normalize() {
        this.name = sanitizeName(this.name);
        if (this.items == null) {
            this.items = new ArrayList<>();
        }
        this.items.removeIf(item -> item == null || TodoItem.sanitize(item.text).isEmpty());
        if (this.items.size() > MAX_ITEMS) {
            this.items = new ArrayList<>(this.items.subList(0, MAX_ITEMS));
        }
        for (TodoItem item : this.items) {
            if (item.id == null || item.id.isBlank()) {
                item.id = java.util.UUID.randomUUID().toString();
            }
            item.text = TodoItem.sanitize(item.text);
            if (item.createdAt <= 0L) {
                item.createdAt = System.currentTimeMillis();
            }
        }
    }
}
