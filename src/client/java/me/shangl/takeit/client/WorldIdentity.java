package me.shangl.takeit.client;

import me.shangl.takeit.data.ServerKeyMode;
import me.shangl.takeit.data.State;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * 当前世界（存档 / 服务器）的身份识别，决定"单存档显示"绑定到哪一份清单。
 *
 * <p>作用域标识规则：</p>
 * <ul>
 *   <li>单人存档：{@code WORLD:<存档目录名>}（小写），存档目录名基本等同于存档唯一标识；</li>
 *   <li>多人服务器：{@code <判定模式>:<标识>}，标识由 {@link ServerKeyMode#scopeKey} 按
 *       服务器名 / 基础域名 / 完整域名三种依据计算，端口不参与；</li>
 *   <li>主菜单等没有世界上下文时作用域为 null，此时只按「全局清单 → 最近清单 → 默认清单」回退。</li>
 * </ul>
 *
 * <p>本类在进入世界、断开连接、切换判定模式后调用 {@link #sync()}，
 * 把结果推给 {@link State#updateScope}。</p>
 */
public final class WorldIdentity {

    private static final String WORLD_PREFIX = "WORLD:";

    private static @Nullable String key;
    private static @Nullable String label;

    private WorldIdentity() {
    }

    /** 当前作用域标识，没有世界上下文时为 null。 */
    public static @Nullable String key() {
        return key;
    }

    /** 当前作用域的人类可读描述（界面展示用）。 */
    public static @Nullable String label() {
        return label;
    }

    /**
     * 当前多人服务器的主机名（完整域名，已去端口、小写）。
     *
     * @return 单人存档或主菜单时为 null
     */
    public static @Nullable String serverHost() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        return server == null ? null : ServerKeyMode.hostOf(server.ip);
    }

    /**
     * 给定主机名是否命中 config/takeit_todo/settings.json 里的黑名单。
     *
     * <p>口径是「完整主机名精确相等」，与服务器同一性判定模式无关：写 {@code mc.example.com}
     * 只命中这一台，不会命中 {@code sub.mc.example.com}。</p>
     */
    public static boolean isBlacklisted(@Nullable String host) {
        return host != null && State.get().settings().isBlacklisted(host);
    }

    /**
     * 当前服务器是否命中黑名单。
     *
     * <p>命中时待办 HUD 不渲染、快捷键也打不开待办清单界面（见 {@code HudMixin} 与
     * {@code ModClient#onEndTick}）。单人存档没有服务器地址，恒为 false。</p>
     */
    public static boolean blacklisted() {
        return isBlacklisted(serverHost());
    }

    /** 重新识别当前世界并推送到状态层；主菜单 / 断开连接后会把作用域清空。 */
    public static void sync() {
        Minecraft client = Minecraft.getInstance();

        String newKey = null;
        String newLabel = null;

        IntegratedServer singleplayer = client.getSingleplayerServer();
        if (singleplayer != null) {
            newKey = WORLD_PREFIX + levelId(singleplayer).toLowerCase(Locale.ROOT);
            newLabel = singleplayer.getWorldData().getLevelName();
        } else {
            ServerData server = client.getCurrentServer();
            if (server != null) {
                ServerKeyMode mode = State.get().settings().serverKeyMode;
                String identity = mode.scopeKey(server.name, server.ip);
                if (identity != null) {
                    newKey = mode.scopePrefix() + identity;
                }
                // ServerData 的 name/ip 在 @NullMarked 包内声明为非空；名称为空时退回地址
                newLabel = server.name.isBlank() ? server.ip : server.name;
            }
        }
        apply(newKey, newLabel);
    }

    /** 幂等更新：标识没变就不重复解析清单，避免每 tick 反复读文件。 */
    private static void apply(@Nullable String newKey, @Nullable String newLabel) {
        if (Objects.equals(key, newKey) && Objects.equals(label, newLabel)) {
            return;
        }
        key = newKey;
        label = newLabel;
        State.get().updateScope(newKey);
    }

    /** 取存档目录名作为单人存档的唯一标识（ROOT 路径结尾常带 "."，先归一化再取文件名）。 */
    private static String levelId(IntegratedServer server) {
        Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        Path fileName = root.getFileName();
        return fileName == null ? root.toString() : fileName.toString();
    }
}
