package org.zexnocs.teanekoapp.plugin;

import org.zexnocs.teanekocore.plugin.PluginDescriptor;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 插件管理的公开值合同，不暴露 Bean、配置原文或运行控制。
 * <br>2026/11/07：区分保存意愿、实际启用、依赖阻塞及操作回执。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public final class PluginManagement {
    /** 值合同容器不需要实例。 */
    private PluginManagement() {
    }

    /**
     * 完整管理目录，停用插件仍保留在此处。
     * @param revision 整体预览版本
     * @param items 插件快照
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Catalog(String revision, List<Item> items) {
        /**
         * 固定目录。
         * @param revision 当前版本
         * @param items 插件条目
         */
        public Catalog {
            items = List.copyOf(items);
        }
    }

    /**
     * 单个插件的公开状态，原因只包含宿主生成的安全诊断。
     * @param id 插件标识
     * @param name 显示名称
     * @param description 功能说明
     * @param category 分类
     * @param version 实现版本
     * @param mandatory 是否为不可停用宿主
     * @param desiredEnabled 用户启用意愿
     * @param enabled 当前实际可调用状态
     * @param state 生命周期状态
     * @param reason 阻塞或失败原因
     * @param restartRequired 是否等待重启才能应用意愿
     * @param dependencies 必需插件
     * @param requiredClients 必需客户端
     * @param contributions 完整静态贡献，停用后仍可查看
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Item(String id, String name, String description, String category, String version,
                       boolean mandatory, boolean desiredEnabled, boolean enabled, String state, String reason,
                       boolean restartRequired, Set<String> dependencies, Set<String> requiredClients,
                       List<PluginContribution> contributions) {
        /**
         * 固定依赖集合。
         * @param id 插件标识
         * @param name 显示名称
         * @param description 功能说明
         * @param category 分类
         * @param version 实现版本
         * @param mandatory 是否为基础模块
         * @param desiredEnabled 启用意愿
         * @param enabled 有效开关
         * @param state 生命周期状态
         * @param reason 安全诊断
         * @param restartRequired 是否待重启
         * @param dependencies 必需插件
         * @param requiredClients 必需客户端
         * @param contributions 完整静态贡献
         */
        public Item {
            dependencies = Set.copyOf(dependencies);
            requiredClients = Set.copyOf(requiredClients);
            contributions = List.copyOf(contributions);
        }
    }

    /**
     * 启停预览请求，不修改原启用意愿。
     * @param pluginId 插件标识
     * @param enabled 目标意愿
     * @param expectedRevision 已查看的目录版本
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Request(String pluginId, boolean enabled, String expectedRevision) {
        /**
         * 校验边界请求。
         * @param pluginId 插件标识
         * @param enabled 目标意愿
         * @param expectedRevision 预期版本
         */
        public Request {
            PluginDescriptor.requireId(pluginId);
            Objects.requireNonNull(expectedRevision, "插件目录版本不能为空。");
        }
    }

    /**
     * 已确认影响的持久管理请求，重复编号必须保留原请求。
     * @param operationId 操作编号
     * @param pluginId 插件标识
     * @param enabled 目标意愿
     * @param expectedRevision 预览版本
     * @param includeDependencies 是否已确认影响其他插件
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Change(UUID operationId, String pluginId, boolean enabled, String expectedRevision,
                         boolean includeDependencies) {
        /**
         * 校验请求身份与固定版本。
         * @param operationId 操作编号
         * @param pluginId 插件标识
         * @param enabled 目标意愿
         * @param expectedRevision 预期版本
         * @param includeDependencies 影响确认
         */
        public Change {
            Objects.requireNonNull(operationId, "插件操作编号不能为空。");
            PluginDescriptor.requireId(pluginId);
            Objects.requireNonNull(expectedRevision, "插件目录版本不能为空。");
        }
    }

    /**
     * 依赖影响预览，不自动打开原来关闭的依赖。
     * @param pluginId 目标插件
     * @param enabled 目标意愿
     * @param revision 当前预览版本
     * @param affected 将发生有效状态变化的插件
     * @param blockers 当前阻塞说明
     * @param restartRequired 是否需要重启
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Preview(String pluginId, boolean enabled, String revision, List<String> affected,
                          List<String> blockers, boolean restartRequired) {
        /**
         * 固定预览快照。
         * @param pluginId 目标插件
         * @param enabled 目标意愿
         * @param revision 当前版本
         * @param affected 影响集合
         * @param blockers 阻塞原因
         * @param restartRequired 是否待重启
         */
        public Preview {
            affected = List.copyOf(affected);
            blockers = List.copyOf(blockers);
        }
    }

    /**
     * 原操作的实际结果，保存成功不代表资源已经切换成功。
     * @param operationId 操作编号
     * @param status 操作状态
     * @param revision 回执产生时的目录版本
     * @param note 安全结果说明
     * @param restartRequired 是否待重启
     * @author zExNocs
     * @date 2026/11/07
     * @since 4.9.0-l
     * @version 4.9.0-l
     */
    public record Receipt(UUID operationId, String status, String revision, String note, boolean restartRequired) {
    }
}
