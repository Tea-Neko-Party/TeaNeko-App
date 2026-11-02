package org.zexnocs.teanekoagent.file_config;

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
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.4.1
 * @version 4.5.0-l
 */
@FileConfig(
        value = "main-config",
        path = "agent",
        type = FileConfigType.YAML
)
@Getter
@Setter
@NoArgsConstructor
public class AgentMainFileConfig implements IFileConfigData {
    /** 本地 Agent 总开关，关闭时不继续检查模型或凭据。 */
    private boolean enabled = true;

}
