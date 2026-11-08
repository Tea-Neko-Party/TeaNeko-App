package org.zexnocs.teanekocore.plugin;

/**
 * 故意位于 Core 包的非法声明，用于验证业务包边界。
 * <br>2026/11/07：仅供拒绝路径测试引用，不注册到正常容器。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class InvalidPluginFixtures {
    /** 禁止创建夹具命名空间。 */
    private InvalidPluginFixtures() {
    }

    /**
     * 不属于业务插件包的非法入口。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @TeaNekoPlugin(id = "invalid.owner", name = "非法入口", description = "应被拒绝。", version = "1")
    public static class Declaration {
    }

    /**
     * 不属于业务插件包的非法归属。
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    @PluginOwned("invalid.owner")
    public static class Owner {
        /** 无副作用方法，仅用于验证归属登记拒绝。 */
        public void run() {
        }
    }
}
