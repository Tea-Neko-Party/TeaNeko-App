package org.zexnocs.teanekocore.plugin;

import java.util.Objects;
import java.util.Set;

/**
 * 不属于业务插件的宿主能力身份，供稳定资源权限和插件先决条件引用。
 * <br>2026/11/07：宿主能力独立注册，不进入插件目录、持久开关或插件生命周期。
 *
 * @param id 宿主能力的稳定标识
 * @param version 当前合同实现版本
 * @param dependencies 必需宿主能力，不允许反向依赖业务插件
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public record HostCapability(String id, String version, Set<String> dependencies) {
    /**
     * 固定宿主依赖，不把基础能力伪装为不可关闭的业务插件。
     * @param id 宿主稳定标识
     * @param version 实现版本
     * @param dependencies 必需宿主能力
     * @throws IllegalArgumentException 当标识、版本或自依赖无效时
     */
    public HostCapability {
        PluginDescriptor.requireId(id);
        Objects.requireNonNull(version, "宿主能力版本不能为空。");
        dependencies = Set.copyOf(dependencies);
        dependencies.forEach(PluginDescriptor::requireId);
        if (version.isBlank() || dependencies.contains(id)) {
            throw new IllegalArgumentException("宿主能力版本或依赖无效：" + id);
        }
    }
}
