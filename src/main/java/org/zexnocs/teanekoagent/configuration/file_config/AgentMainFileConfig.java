package org.zexnocs.teanekoagent.configuration.file_config;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;


/**
 * Agent 主文件配置。
 * <br>只保存 Agent 运行总开关，默认路由和全部模型设置统一位于 model.yml。
 *
 * <br>2026/11/04：为控制台提供总开关用途及字段解释。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.8.0-l
 */
@FileConfig(
        value = "main-config",
        path = "agent",
        type = FileConfigType.YAML
)
@Getter
@Setter
@NoArgsConstructor
// 2026/11/04：控制台展示类型及字段注解，不读取运行秘密。
@org.zexnocs.teanekocore.framework.description.Description("Agent 总开关。模型及凭据统一在模型配置中管理。")
public class AgentMainFileConfig implements IFileConfigData {
    /** 本地 Agent 总开关，关闭时不继续检查模型或凭据。 */
    @org.zexnocs.teanekocore.framework.description.Description("Agent 全局开关；聊天内启用助手仍需此处开启。")
    private boolean enabled = true;

}
