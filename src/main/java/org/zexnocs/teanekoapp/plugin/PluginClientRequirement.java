package org.zexnocs.teanekoapp.plugin;

import org.zexnocs.teanekocore.plugin.PluginDescriptor;

import java.util.Set;

/**
 * 插件对已注册客户端的必需声明；连接是否在线仍在实际操作时复核。
 * <br>2026/11/07：由 App 将客户端提供者转换为通用依赖边。
 *
 * @param pluginId 所属插件
 * @param requiredClientIds 必需客户端标识
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public record PluginClientRequirement(String pluginId, Set<String> requiredClientIds) {
    /**
     * 固定声明，避免客户端目录发布后被贡献方修改。
     * @param pluginId 所属插件
     * @param requiredClientIds 必需客户端标识
     * @throws IllegalArgumentException 当标识为空时
     */
    public PluginClientRequirement {
        PluginDescriptor.requireId(pluginId);
        requiredClientIds = Set.copyOf(requiredClientIds);
        if (requiredClientIds.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("插件客户端要求不能包含空标识。");
        }
    }
}
