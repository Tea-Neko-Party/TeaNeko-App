package org.zexnocs.teanekoapp.config;

/**
 * 提供了 tea neko config 中 general 的命名空间常量。
 *
 * @author zExNocs
 * @date 2026/03/07
 * @since 4.1.0
 */
public class TeaNekoConfigNamespaces {
    /// 该命名空间下的配置会被所有客户端共享。
    public static final String GENERAL = "teaneko-general";

    /// 该命名空间下的配置会被所有客户端中私聊共享。
    public static final String PRIVATE = "teaneko-private";

     /// 该命名空间下的配置会被所有客户端中群聊共享。
    public static final String GROUP = "teaneko-group";
}
