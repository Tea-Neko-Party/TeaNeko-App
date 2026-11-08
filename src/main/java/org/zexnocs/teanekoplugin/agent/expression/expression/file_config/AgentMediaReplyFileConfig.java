package org.zexnocs.teanekoplugin.agent.expression.expression.file_config;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekoplugin.agent.expression.expression.MediaReplySettings;

import java.util.Map;

/**
 * 类别选图与原生表情本地配置，和采集开关分开。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：默认原生表情目录由各客户端贡献，文件配置仅保存覆盖值。
 * <br>2026/11/05：缺省与显式空表分开表示，保留原空目录禁用行为。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.9.0-l
 */
@Getter @Setter @NoArgsConstructor
@FileConfig(value="media-replies",path="agent",type=FileConfigType.YAML)
@org.zexnocs.teanekocore.plugin.PluginOwned("agent.expression")
public class AgentMediaReplyFileConfig implements IFileConfigData {
    private boolean enabled=true;
    private int maxPerReply=2,maxCategories=2,cooldownTurns=3,maxCategoryCharacters=7800;
    // 2026/11/05：null 专门表示字段缺省；显式空表或子表不会恢复客户端默认目录。
    private @Nullable Map<String, Map<String, String>> nativeFaces;

    /** @return 已配置的完整目录；null 表示使用各客户端默认 */
    public @Nullable Map<String, Map<String, String>> getNativeFaces() {
        return nativeFaces;
    }

    /**
     * 保存显式目录，同时允许配置绑定器表达缺省值。
     * @param nativeFaces 客户端目录；null 表示使用客户端默认，空表表示禁用
     */
    public void setNativeFaces(@Nullable Map<String, Map<String, String>> nativeFaces) {
        this.nativeFaces = nativeFaces;
    }

    /** @return 2026/11/05：保留目录缺省语义且深层冻结的媒体预算 */
    public MediaReplySettings snapshot() {
        return new MediaReplySettings(enabled, maxPerReply, maxCategories, cooldownTurns,
                maxCategoryCharacters, nativeFaces);
    }
}
