package me.shangl.takeit.client.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import me.shangl.takeit.client.screen.ConfigScreen;

/**
 * Mod Menu 入口（软依赖）。
 *
 * <p>本类只在 fabric.mod.json 的 {@code modmenu} 入口点被声明；Mod Menu 未安装时
 * 没有任何组件会请求该入口点，因此这个类不会被类加载，也就不会因为缺少
 * {@code com.terraformersmc.modmenu.api.ModMenuApi} 而抛 {@code NoClassDefFoundError}。</p>
 *
 * <p>Mod Menu 每个模组只暴露一个配置入口，这里返回模组整体配置菜单
 * {@link ConfigScreen}，再逐级进入「待办清单管理 / 显示设置 / 服务器判定」，
 * 与快捷键入口复用同一批界面类。</p>
 */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ConfigScreen::new;
    }
}
