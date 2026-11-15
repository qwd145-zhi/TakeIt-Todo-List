package me.shangl.takeit.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.shangl.takeit.TakeIt;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 本地 JSON 持久化工具（纯客户端，全部落在游戏 config 目录）。
 *
 * <p>目录结构（固定）：</p>
 * <pre>
 * config/takeit_todo/
 *   settings.json          全局开关：HUD 开关/条数、全局清单、最近清单、服务器判定模式
 *   bindings.json          单存档 / 单服务器 → 清单 的绑定表
 *   hud_position.json      HUD 位置（锚点 + 偏移量）
 *   lists/todo_&lt;清单名&gt;.json  每份待办清单一个文件
 * </pre>
 *
 * <p>目录与文件在首次写入时按需创建，不需要预置任何内容。</p>
 *
 * <p>写入采用「先写临时文件再原子替换」，避免游戏崩溃时留下半截 JSON。</p>
 */
public final class Storage {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final String SETTINGS_FILE = "settings.json";
    private static final String BINDINGS_FILE = "bindings.json";
    private static final String HUD_FILE = "hud_position.json";
    private static final String LISTS_DIR = "lists";

    private Storage() {
    }

    // ------------------------------------------------------------------
    // 路径
    // ------------------------------------------------------------------

    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("takeit_todo");
    }

    public static Path listsDir() {
        return configDir().resolve(LISTS_DIR);
    }

    public static Path settingsFile() {
        return configDir().resolve(SETTINGS_FILE);
    }

    public static Path bindingsFile() {
        return configDir().resolve(BINDINGS_FILE);
    }

    public static Path hudFile() {
        return configDir().resolve(HUD_FILE);
    }

    public static Path listFile(String name) {
        return listsDir().resolve(TodoList.fileName(name));
    }

    /** 建立配置目录；可重复调用。 */
    public static void init() {
        try {
            Files.createDirectories(listsDir());
        } catch (IOException e) {
            TakeIt.LOGGER.warn("[takeit_todo] 无法创建配置目录 {}：{}", listsDir(), e.toString());
        }
    }

    // ------------------------------------------------------------------
    // 清单
    // ------------------------------------------------------------------

    /** 扫描 lists/ 目录，返回全部清单名（大小写不敏感升序）。 */
    public static List<String> listNames() {
        if (!Files.isDirectory(listsDir())) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        try (Stream<Path> stream = Files.list(listsDir())) {
            stream.filter(Files::isRegularFile)
                    .map(path -> TodoList.nameFromFile(path.getFileName().toString()))
                    .filter(name -> name != null && !name.isBlank())
                    .forEach(names::add);
        } catch (IOException e) {
            TakeIt.LOGGER.warn("[takeit_todo] 扫描清单目录失败：{}", e.toString());
            return List.of();
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    /** 读取一份清单；文件不存在或损坏时返回 null。 */
    public static TodoList loadList(String name) {
        TodoList list = read(listFile(name), TodoList.class);
        if (list == null) {
            return null;
        }
        // 文件名是权威标识，JSON 里的 name 只当冗余字段
        list.name = name;
        list.normalize();
        return list;
    }

    public static void saveList(TodoList list) {
        list.normalize();
        write(listFile(list.name), list);
    }

    public static boolean deleteListFile(String name) {
        try {
            boolean removed = Files.deleteIfExists(listFile(name));
            if (removed) {
                // 顺手清掉可能残留的临时文件
                Files.deleteIfExists(listFile(name).resolveSibling(listFile(name).getFileName() + ".tmp"));
            }
            return removed;
        } catch (IOException e) {
            TakeIt.LOGGER.warn("[takeit_todo] 删除清单「{}」失败：{}", name, e.toString());
            return false;
        }
    }

    public static boolean renameListFile(String from, String to) {
        Path source = listFile(from);
        Path target = listFile(to);
        if (!Files.exists(source) || Files.exists(target)) {
            return false;
        }
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            TakeIt.LOGGER.warn("[takeit_todo] 重命名清单「{}」→「{}」失败：{}", from, to, e.toString());
            return false;
        }
    }

    // ------------------------------------------------------------------
    // 全局配置 / 绑定 / HUD 位置
    // ------------------------------------------------------------------

    public static Settings loadSettings() {
        Settings settings = read(settingsFile(), Settings.class);
        if (settings == null) {
            settings = new Settings();
        }
        settings.normalize();
        return settings;
    }

    public static void saveSettings(Settings settings) {
        settings.normalize();
        write(settingsFile(), settings);
    }

    public static ListBindings loadBindings() {
        ListBindings bindings = read(bindingsFile(), ListBindings.class);
        if (bindings == null) {
            bindings = new ListBindings();
        }
        bindings.normalize();
        return bindings;
    }

    public static void saveBindings(ListBindings bindings) {
        bindings.normalize();
        write(bindingsFile(), bindings);
    }

    /** 读取 HUD 位置；缺失字段（如旧文件）全部回落到默认值后再归一化。 */
    public static HudPosition loadHudPosition() {
        HudPosition position = read(hudFile(), HudPosition.class);
        if (position == null) {
            position = HudPosition.defaults();
        }
        if (position.anchor == null) {
            position.anchor = HudAnchor.DEFAULT;
        }
        // 对齐步进 + 钳制边界，保证从配置读回来的值一定合法
        position.normalize();
        return position;
    }

    public static void saveHudPosition(HudPosition position) {
        write(hudFile(), position);
    }

    // ------------------------------------------------------------------
    // 通用读写
    // ------------------------------------------------------------------

    private static <T> T read(Path file, Class<T> type) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return null;
            }
            return GSON.fromJson(json, type);
        } catch (Exception e) {
            TakeIt.LOGGER.warn("[takeit_todo] 读取 {} 失败，将使用默认值：{}", file, e.toString());
            return null;
        }
    }

    private static void write(Path file, Object value) {
        try {
            Files.createDirectories(file.getParent());
            String json = GSON.toJson(value);
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            TakeIt.LOGGER.warn("[takeit_todo] 写入 {} 失败：{}", file, e.toString());
        }
    }
}
