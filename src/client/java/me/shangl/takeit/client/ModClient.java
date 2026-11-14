package me.shangl.takeit.client;

import com.mojang.blaze3d.platform.InputConstants;
import me.shangl.takeit.TakeIt;
import me.shangl.takeit.client.screen.TodoScreen;
import me.shangl.takeit.data.State;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口：按键绑定、状态预热与降级日志。
 *
 * <p>全部逻辑只在客户端运行，没有任何网络包或服务端同步。</p>
 *
 * <p>入口一览：</p>
 * <ul>
 *   <li>快捷键 H（默认）→ 待办清单主界面；</li>
 *   <li>待办清单主界面内的「配置」按钮 → 模组配置菜单（待办清单管理 / 显示设置 / 服务器判定）；</li>
 *   <li>Mod Menu → TakeIt 的 Config → 同一个模组配置菜单。</li>
 * </ul>
 */
public class ModClient implements ClientModInitializer {

    /** 按键分类（26.2 起 {@link KeyMapping} 使用 Category 对象而不是字符串）。 */
    public static final KeyMapping.Category KEY_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(TakeIt.MOD_ID, "main"));

    /** 默认按键：H 打开待办清单（可在「选项 → 控制 → 按键绑定 → TakeIt」中修改）。 */
    public static final int DEFAULT_TODO_KEY = GLFW.GLFW_KEY_H;

    /**
     * 与 {@link #DEFAULT_TODO_KEY} 对应的字面量键名，仅供初始化阶段的日志使用（两者需同步修改）。
     *
     * <p>初始化阶段问不到按键的本地化名称：{@code KeyMapping#getTranslatedKeyMessage()} 会走
     * InputConstants 的显示名供应商 → {@code GLFW.glfwGetKeyName(...)}，而此刻 GLFW 还没初始化，
     * 会往 GLFW 错误队列塞 0x10001(NOT_INITIALIZED)，导致客户端在建窗前崩溃（详见
     * {@link #onInitializeClient()} 里的注释）。运行期（{@link #onEndTick}）则可以放心翻译键名，
     * 玩家改键后日志里显示的也是真实键位。</p>
     */
    private static final String DEFAULT_TODO_KEY_NAME = "H";

    private static KeyMapping openTodoKey;

    /** 保证"首次降级"日志只输出一次。 */
    private static boolean fallbackUseLogged;

    /** 保证"服务器在黑名单"日志只输出一次。 */
    private static boolean blacklistLogged;

    @Override
    public void onInitializeClient() {
        // 提前加载本地数据，配置损坏时在日志里尽早暴露
        State.get();

        // 快捷键注册：名字是语言文件键，分类显示为「TakeIt」
        openTodoKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.takeit.open_todo", InputConstants.Type.KEYSYM, DEFAULT_TODO_KEY, KEY_CATEGORY));

        ClientTickEvents.END_CLIENT_TICK.register(ModClient::onEndTick);

        // 进入世界/服务器时重读 settings.json：黑名单等只能手改文件的配置无需重启游戏
        ClientPlayConnectionEvents.JOIN.register((_, _, _) -> {
            State.get().reloadSettings();
            WorldIdentity.sync();
        });

        // 未安装 Mod Menu 时输出一次可读的降级提示
        // 注意：这里绝对不能用 KeyMapping#getTranslatedKeyMessage() / getKey().getDisplayName()。
        // 那些调用会走 InputConstants.Type 的显示名供应商 → GLFW.glfwGetKeyName(...)，
        // 而此刻 GLFW 尚未初始化（Minecraft 构造函数里 glfwInit 还没执行），
        // GLFW 会把 0x10001(NOT_INITIALIZED) 塞进错误队列；随后 Minecraft 在
        // GLX._initGlfw 里、glfwInit 之前先调用 Window.checkGlfwError 读该队列，
        // 于是直接抛 "GLFW error before init" 让客户端在创建窗口前退出。
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) {
            TakeIt.LOGGER.info(
                    "[takeit_todo] 未检测到 Mod Menu：降级入口已就绪 —— 默认按 {} 打开待办清单"
                            + "（可在「选项 → 控制 → 按键绑定 → TakeIt」中查看/修改）；"
                            + "配置与 HUD 位置设置可在待办清单界面内点「配置」进入（配置 → 显示设置 → HUD 位置设置）。"
                            + "安装 Mod Menu 后可从模组列表的 Config 按钮进入同一个配置菜单。",
                    DEFAULT_TODO_KEY_NAME);
        }
    }

    /** 每 tick 检查一次按键，只在没有其它界面打开时生效。 */
    private static void onEndTick(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        // 进入/退出世界、切换服务器时作用域会变：幂等同步（标识没变时内部直接返回）
        WorldIdentity.sync();
        if (minecraft.player == null) {
            return;
        }
        KeyMapping key = openTodoKey;
        if (key == null) {
            return;
        }
        while (key.consumeClick()) {
            // 黑名单服务器：快捷键不打开待办清单界面（名单只能改 settings.json）
            if (WorldIdentity.blacklisted()) {
                logBlacklistOnce();
                continue;
            }
            if (minecraft.gui.screen() == null) {
                minecraft.gui.setScreen(new TodoScreen(null));
                // 此处已在游戏循环内，GLFW 早已初始化，可以安全地翻译键名（改键后显示真实键位）
                logFallbackOnce("快捷键 " + key.getTranslatedKeyMessage().getString());
            }
        }
    }

    /**
     * 首次真正走降级入口时输出一条日志（仅在未安装 Mod Menu 时）。
     *
     * @param trigger 触发来源描述，例如 "快捷键 H"
     */
    public static void logFallbackOnce(String trigger) {
        if (fallbackUseLogged || FabricLoader.getInstance().isModLoaded("modmenu")) {
            return;
        }
        fallbackUseLogged = true;
        TakeIt.LOGGER.info("[takeit_todo] 通过降级入口（{}）打开界面；Mod Menu 未安装，功能不受影响。", trigger);
    }

    /** 命中黑名单时的日志只输出一次，避免玩家反复按键时刷屏。 */
    private static void logBlacklistOnce() {
        if (blacklistLogged) {
            return;
        }
        blacklistLogged = true;
        TakeIt.LOGGER.info("[takeit_todo] 当前服务器（{}）在待办黑名单中：已禁用待办 HUD 与快捷键入口。",
                WorldIdentity.serverHost());
    }
}
