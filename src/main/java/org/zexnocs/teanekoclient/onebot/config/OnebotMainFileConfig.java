package org.zexnocs.teanekoclient.onebot.config;

import lombok.Getter;
import lombok.Setter;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.file_config.interfaces.IFileConfigService;

/**
 * onebot 的主本地数据文件。
 *
 * @see FileConfig
 * @see IFileConfigData
 * @see IFileConfigService
 * @author zExNocs
 * @date 2026/03/13
 * @since 4.2.1
 */
@FileConfig(
        value = "main-config",
        path = "onebot",
        type = FileConfigType.YAML
)
@Setter
@Getter
public class OnebotMainFileConfig implements IFileConfigData {
    /// debugger 配置
    private OnebotDebuggerConfig debugger = new OnebotDebuggerConfig();

    /// state 配置
    private OnebotStateConfig state = new OnebotStateConfig();
}
