package org.zexnocs.teanekoplugin.agent.cognition.personality.configuration.file_config;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

import java.util.ArrayList;
import java.util.List;

/**
 * 承接既有 config/agent/personality.yml 布局，文件仅含人格数据，不承载认证信息。
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 *
 * <br>2026/11/08：人格文件配置归入认知插件，持久文件路径与字段保持不变。
 *
 * @author zExNocs
 * @date 2026/10/29
 * @since 4.4.1
 * @version 4.9.0-l
 */
@Getter @Setter @NoArgsConstructor
@FileConfig(value = "personality", path = "agent", type = FileConfigType.YAML)
public class AgentPersonalityFileConfig implements IFileConfigData {
    private String defaultAgentId = "teaneko";
    private List<AgentPersonalityDefinition> characters = new ArrayList<>();
}
