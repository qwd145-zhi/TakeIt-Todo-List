package me.shangl.takeit.data;

import java.util.Locale;

/**
 * 服务器同一性判定依据：决定"哪些服务器算同一台"，从而共用同一条清单绑定。
 *
 * <p>三种模式的匹配规则（端口号一律不参与判定）：</p>
 * <ol>
 *   <li>{@link #SERVER_NAME} —— 完全匹配服务器列表里显示的名称，不区分大小写；
 *       名称相同但地址不同的两台服务器会被视为同一台。名称为空时回退到基础域名。</li>
 *   <li>{@link #BASE_DOMAIN} —— 只保留主域名（最后两段），所以
 *       {@code mc.example.com} 与 {@code play.example.com} 视为同一台；
 *       直接填 IP 时按整个 IP 比较。</li>
 *   <li>{@link #FULL_DOMAIN} —— 完整主机名，任何子域名差异都视为不同服务器。</li>
 * </ol>
 *
 * <p>注意：{@link #BASE_DOMAIN} 取"最后两段"是简化规则，对 {@code example.co.uk}
 * 这类公共后缀会过度合并（{@code a.example.co.uk} 与 {@code b.example.co.uk}
 * 会被当成同一台）；需要精确区分时请选 {@link #FULL_DOMAIN}。</p>
 */
public enum ServerKeyMode {

    SERVER_NAME("server_name"),
    BASE_DOMAIN("base_domain"),
    FULL_DOMAIN("full_domain");

    /** 默认模式：完整域名，最不容易把不同服务器误合并。 */
    public static final ServerKeyMode DEFAULT = FULL_DOMAIN;

    private final String id;

    ServerKeyMode(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    /** 语言文件键（界面上的模式名）。 */
    public String translationKey() {
        return "screen.takeit.server.mode." + this.id;
    }

    /** 作用域标识前缀，例如 {@code FULL_DOMAIN:}。 */
    public String scopePrefix() {
        return this.id.toUpperCase(Locale.ROOT) + ":";
    }

    /**
     * 依据所选模式推出判定标识（不含前缀）。
     *
     * @param serverName 服务器列表里显示的名称
     * @param address    服务器地址（可能带端口）
     * @return 归一化后的标识；完全无法判定时返回 null
     */
    public String scopeKey(String serverName, String address) {
        String host = hostOf(address);
        return switch (this) {
            case SERVER_NAME -> {
                String name = normalize(serverName);
                if (!name.isEmpty()) {
                    yield name;
                }
                // 没有名称时退回基础域名，保证仍能区分不同服务器
                yield host.isEmpty() ? null : baseDomain(host);
            }
            case BASE_DOMAIN -> host.isEmpty() ? null : baseDomain(host);
            case FULL_DOMAIN -> host.isEmpty() ? null : host;
        };
    }

    /** 取主机名：去掉端口、去掉 IPv6 方括号、转小写。 */
    public static String hostOf(String address) {
        if (address == null) {
            return "";
        }
        String raw = address.trim();
        if (raw.isEmpty()) {
            return "";
        }
        if (raw.startsWith("[")) {
            int end = raw.indexOf(']');
            return (end > 1 ? raw.substring(1, end) : raw).toLowerCase(Locale.ROOT);
        }
        int lastColon = raw.lastIndexOf(':');
        // 只有一个冒号才当成端口；多个冒号说明是裸 IPv6 地址
        if (lastColon > 0 && raw.indexOf(':') == lastColon) {
            raw = raw.substring(0, lastColon);
        }
        return raw.toLowerCase(Locale.ROOT);
    }

    /** 取主域名：非 IP 时保留最后两段，IP 原样返回。 */
    public static String baseDomain(String host) {
        if (host == null || host.isEmpty()) {
            return "";
        }
        if (isIpLiteral(host)) {
            return host;
        }
        String[] parts = host.split("\\.");
        if (parts.length <= 2) {
            return host;
        }
        return parts[parts.length - 2] + "." + parts[parts.length - 1];
    }

    /** 是否是 IPv4 字面量或含冒号的 IPv6 字面量。 */
    public static boolean isIpLiteral(String host) {
        if (host == null || host.isEmpty()) {
            return false;
        }
        if (host.indexOf(':') >= 0) {
            return true;
        }
        String[] parts = host.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    public static ServerKeyMode orDefault(ServerKeyMode mode) {
        return mode == null ? DEFAULT : mode;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
