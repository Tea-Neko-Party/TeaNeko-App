package org.zexnocs.teanekoapp.plugin;

import org.zexnocs.teanekocore.plugin.PluginDescriptor;

/**
 * 已安装插件的静态贡献说明，不包含配置值、运行对象或调用能力。
 * <br>2026/11/07：停用后的总管理继续展示贡献，业务目录仍独立复核资格。
 *
 * @param pluginId 真实所属插件
 * @param kind 稳定贡献类别
 * @param id 类别内稳定标识
 * @param name 中文名称或原声明名称
 * @param description 功能说明
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
public record PluginContribution(String pluginId, String kind, String id, String name, String description) {
    /**
     * 验证公开目录边界，拒绝空身份和无界文本。
     * @param pluginId 插件标识
     * @param kind 贡献类别
     * @param id 稳定标识
     * @param name 名称
     * @param description 功能说明
     * @throws IllegalArgumentException 当身份或文本不满足公开合同
     */
    public PluginContribution {
        PluginDescriptor.requireId(pluginId);
        if (kind == null || !kind.matches("[a-z][a-z-]{0,31}") || id == null || id.isBlank() || id.length() > 256
                || name == null || name.isBlank() || name.length() > 256
                || description == null || description.length() > 4000) {
            throw new IllegalArgumentException("插件贡献说明的标识或文本无效。");
        }
    }
}
