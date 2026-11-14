package me.shangl.takeit.data;

import java.util.List;

/**
 * 全局单例状态：当前清单 + 全局配置 + 位置 + 绑定，所有修改即时落库。
 *
 * <p>纯客户端使用，仅在渲染线程访问，因此不做额外同步。</p>
 *
 * <p><b>当前清单的判定顺序</b>（每次世界作用域变化或清单集合变化时重新计算）：</p>
 * <ol>
 *   <li>当前存档/服务器的绑定（bindings.json）——优先级最高；</li>
 *   <li>全局清单（settings.json → globalList）；</li>
 *   <li>最近使用的清单（settings.json → lastUsedList）——"两者都没选"时的回退；</li>
 *   <li>都没有则用清单「默认」；连文件都不存在时新建一份空的。</li>
 * </ol>
 *
 * <p>世界作用域由客户端侧的 WorldIdentity 计算后推入（{@link #updateScope}），
 * 这样数据层不需要依赖 Minecraft 类。</p>
 */
public final class State {

    /** 当前清单的来源，界面与日志用它解释"为什么显示这份清单"。 */
    public enum Source {
        BOUND,
        GLOBAL,
        LAST_USED,
        DEFAULT
    }

    private static State instance;

    private Settings settings;
    private final HudPosition hudPosition;
    private final ListBindings bindings;

    private TodoList current;
    private Source source = Source.DEFAULT;
    /** 当前世界作用域标识；null 表示不在存档/服务器中。 */
    private String scopeKey;

    /** 清单总数缓存：HUD 每帧都要判断"是否多清单"，不能每帧扫目录。 */
    private int listCount;

    private State() {
        Storage.init();
        this.settings = Storage.loadSettings();
        this.hudPosition = Storage.loadHudPosition();
        this.bindings = Storage.loadBindings();
        this.resolve();
    }

    public static synchronized State get() {
        if (instance == null) {
            instance = new State();
        }
        return instance;
    }

    // ------------------------------------------------------------------
    // 世界作用域与清单解析
    // ------------------------------------------------------------------

    /**
     * 更新当前世界作用域（进入/退出世界、切换判定模式时由客户端调用），并重新解析清单。
     *
     * @param key 作用域标识，null 表示不在任何存档/服务器中
     */
    public void updateScope(String key) {
        this.scopeKey = key;
        this.resolve();
    }

    private void resolve() {
        List<String> names = Storage.listNames();
        if (names.isEmpty()) {
            // 一份清单都没有：建一份默认空清单，保证界面永远有可操作对象
            TodoList created = new TodoList(TodoList.DEFAULT_NAME);
            Storage.saveList(created);
            names = Storage.listNames();
        }
        this.listCount = names.size();

        String chosen = null;
        Source chosenSource = Source.DEFAULT;

        String bound = this.bindings.get(this.scopeKey);
        if (bound != null && containsIgnoreCase(names, bound)) {
            chosen = findIgnoreCase(names, bound);
            chosenSource = Source.BOUND;
        }
        if (chosen == null && this.settings.globalList != null
                && containsIgnoreCase(names, this.settings.globalList)) {
            chosen = findIgnoreCase(names, this.settings.globalList);
            chosenSource = Source.GLOBAL;
        }
        if (chosen == null && this.settings.lastUsedList != null
                && containsIgnoreCase(names, this.settings.lastUsedList)) {
            chosen = findIgnoreCase(names, this.settings.lastUsedList);
            chosenSource = Source.LAST_USED;
        }
        if (chosen == null) {
            chosen = containsIgnoreCase(names, TodoList.DEFAULT_NAME)
                    ? findIgnoreCase(names, TodoList.DEFAULT_NAME)
                    : names.getFirst();
            chosenSource = Source.DEFAULT;
        }

        TodoList loaded = Storage.loadList(chosen);
        if (loaded == null) {
            // 文件刚被外部删除：重建同名空清单，避免拿着 null 继续跑
            loaded = new TodoList(chosen);
            Storage.saveList(loaded);
        }
        this.current = loaded;
        this.source = chosenSource;

        if (!chosen.equals(this.settings.lastUsedList)) {
            this.settings.lastUsedList = chosen;
            Storage.saveSettings(this.settings);
        }
    }

    // ------------------------------------------------------------------
    // 只读访问
    // ------------------------------------------------------------------

    public Settings settings() {
        return this.settings;
    }

    /**
     * 重新读取 settings.json 并按新配置重新解析当前清单。
     *
     * <p>黑名单这类「只能手改文件」的配置不必重启游戏：进入世界/服务器时调用一次即可生效。</p>
     */
    public void reloadSettings() {
        this.settings = Storage.loadSettings();
        this.resolve();
    }

    public HudPosition hudPosition() {
        return this.hudPosition;
    }

    public TodoList current() {
        return this.current;
    }

    public List<TodoItem> items() {
        return this.current.items;
    }

    public Source source() {
        return this.source;
    }

    /**
     * 当前存档/服务器是否有一份「明确指定」的清单可显示在 HUD 上。
     *
     * <p>只认两种情况：本作用域绑定的清单（{@link Source#BOUND}）与全局清单（{@link Source#GLOBAL}）。
     * 仅靠「最近使用」或「默认清单」兜底时返回 false——这种兜底只用于管理界面，
     * 不能让 HUD 在没有指定清单的存档里突然出现。</p>
     */
    public boolean hasHudList() {
        return this.source == Source.BOUND || this.source == Source.GLOBAL;
    }

    /** 当前世界作用域绑定的清单名；没有绑定（或不在世界中）时为 null。 */
    public String currentBinding() {
        return this.scopeKey == null ? null : this.bindings.get(this.scopeKey);
    }

    public List<String> listNames() {
        return Storage.listNames();
    }

    /** 清单总数（缓存值，随 {@link #resolve()} 更新）。 */
    public int listCount() {
        return this.listCount;
    }

    /** 当前清单来源的语言键（界面展示用）。 */
    public String sourceTranslationKey() {
        return switch (this.source) {
            case BOUND -> "screen.takeit.source.bound";
            case GLOBAL -> "screen.takeit.source.global";
            case LAST_USED -> "screen.takeit.source.last_used";
            case DEFAULT -> "screen.takeit.source.default";
        };
    }

    // ------------------------------------------------------------------
    // 待办增删改查（作用于当前清单）
    // ------------------------------------------------------------------

    /** 新增一条待办；文本为空或已满时返回 null 且不落库。 */
    public TodoItem add(String text) {
        String cleaned = TodoItem.sanitize(text);
        if (cleaned.isEmpty() || this.current.items.size() >= TodoList.MAX_ITEMS) {
            return null;
        }
        TodoItem item = new TodoItem(cleaned);
        this.current.items.add(item);
        saveTodos();
        return item;
    }

    /** 切换完成状态。 */
    public void toggle(String id) {
        for (TodoItem item : this.current.items) {
            if (item.id.equals(id)) {
                item.done = !item.done;
                saveTodos();
                return;
            }
        }
    }

    /** 删除一条待办。 */
    public void remove(String id) {
        if (this.current.items.removeIf(item -> item.id.equals(id))) {
            saveTodos();
        }
    }

    /** 清除当前清单里全部已完成条目。 */
    public void clearCompleted() {
        if (this.current.items.removeIf(item -> item.done)) {
            saveTodos();
        }
    }

    public int totalCount() {
        return this.current.total();
    }

    public int doneCount() {
        return this.current.done();
    }

    // ------------------------------------------------------------------
    // HUD 显示开关与条数
    // ------------------------------------------------------------------

    public boolean hudVisible() {
        return this.settings.hudVisible;
    }

    public void setHudVisible(boolean visible) {
        if (this.settings.hudVisible != visible) {
            this.settings.hudVisible = visible;
            saveSettingsNow();
        }
    }

    /** 取反显示开关。 */
    public void toggleHudVisible() {
        setHudVisible(!this.settings.hudVisible);
    }

    /** HUD 最多显示的待办条数；{@code 0} 表示全部。 */
    public int hudMaxItems() {
        return this.settings.hudMaxItems;
    }

    /** 设置 HUD 显示条数（0 = 全部），自动夹到 [0, {@link TodoList#MAX_ITEMS}] 并落库。 */
    public void setHudMaxItems(int value) {
        int clamped = Math.clamp(value, 0, TodoList.MAX_ITEMS);
        if (this.settings.hudMaxItems != clamped) {
            this.settings.hudMaxItems = clamped;
            saveSettingsNow();
        }
    }

    // ------------------------------------------------------------------
    // 清单管理
    // ------------------------------------------------------------------

    /**
     * 新建清单并立即切过去。
     *
     * @return 实际创建的清单名；名称为空或重名时返回 null
     */
    public String createList(String rawName) {
        String name = TodoList.sanitizeName(rawName);
        if (name.isBlank() || findIgnoreCase(Storage.listNames(), name) != null) {
            return null;
        }
        Storage.saveList(new TodoList(name));
        this.settings.lastUsedList = name;
        saveSettingsNow();
        this.resolve();
        return name;
    }

    /** 重命名一份清单，所有指向它的绑定/全局/最近引用同步改掉。 */
    public boolean renameList(String from, String rawName) {
        String to = TodoList.sanitizeName(rawName);
        if (to.equals(from)) {
            return false;
        }
        String existing = findIgnoreCase(Storage.listNames(), to);
        if (existing != null && !existing.equals(from)) {
            return false;
        }
        if (!Storage.renameListFile(from, to)) {
            return false;
        }
        this.bindings.renameValue(from, to);
        Storage.saveBindings(this.bindings);
        if (from.equals(this.settings.globalList)) {
            this.settings.globalList = to;
        }
        if (from.equals(this.settings.lastUsedList)) {
            this.settings.lastUsedList = to;
        }
        saveSettingsNow();
        this.resolve();
        return true;
    }

    /** 删除一份清单；至少保留一份，删掉当前清单后自动回退到别的清单。 */
    public void deleteList(String name) {
        if (Storage.listNames().size() <= 1) {
            return;
        }
        if (!Storage.deleteListFile(name)) {
            return;
        }
        this.bindings.removeValue(name);
        Storage.saveBindings(this.bindings);
        if (name.equals(this.settings.globalList)) {
            this.settings.globalList = null;
        }
        if (name.equals(this.settings.lastUsedList)) {
            this.settings.lastUsedList = null;
        }
        saveSettingsNow();
        this.resolve();
    }

    /** 把清单绑定到当前存档/服务器（最高优先级）。没有世界上下文时直接忽略。 */
    public void bindScope(String name) {
        if (this.scopeKey == null || findIgnoreCase(Storage.listNames(), name) == null) {
            return;
        }
        this.bindings.put(this.scopeKey, name);
        Storage.saveBindings(this.bindings);
        this.resolve();
    }

    /** 清掉当前存档/服务器的绑定。 */
    public void clearScopeBinding() {
        if (this.scopeKey == null || !this.bindings.remove(this.scopeKey)) {
            return;
        }
        Storage.saveBindings(this.bindings);
        this.resolve();
    }

    /** 设为全局清单（所有存档/服务器可见）。 */
    public void setGlobal(String name) {
        this.settings.globalList = name;
        saveSettingsNow();
        this.resolve();
    }

    /** 取消全局清单。 */
    public void clearGlobal() {
        this.settings.globalList = null;
        saveSettingsNow();
        this.resolve();
    }

    /** 切换服务器判定依据（调用方随后需要重新推送作用域）。 */
    public void setServerKeyMode(ServerKeyMode mode) {
        this.settings.serverKeyMode = ServerKeyMode.orDefault(mode);
        saveSettingsNow();
    }

    // ------------------------------------------------------------------
    // 落库
    // ------------------------------------------------------------------

    public void saveTodos() {
        Storage.saveList(this.current);
    }

    public void saveSettingsNow() {
        Storage.saveSettings(this.settings);
    }

    public void saveHud() {
        this.hudPosition.normalize();
        Storage.saveHudPosition(this.hudPosition);
    }

    /** 仅把 HUD 偏移量恢复默认（锚点不变）并落库。 */
    public void resetHudOffsets() {
        this.hudPosition.resetOffsets();
        saveHud();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    /** 大小写不敏感地找同名清单，返回目录里的实际名字；没有时返回 null。 */
    private static String findIgnoreCase(List<String> names, String name) {
        if (name == null) {
            return null;
        }
        for (String candidate : names) {
            if (candidate.equalsIgnoreCase(name)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean containsIgnoreCase(List<String> names, String name) {
        return findIgnoreCase(names, name) != null;
    }
}
