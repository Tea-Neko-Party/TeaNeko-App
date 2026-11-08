package org.zexnocs.teanekoapp.plugin;

import lombok.Getter;
import lombok.Setter;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;

import java.util.Map;

/**
 * 实例级插件启用意愿；依赖阻塞、连接在线与生命周期状态不写入配置。
 * 开关属于宿主管理权限根，标记敏感以拒绝通用文件表单绕过影响预览和生命周期。
 * <br>2026/11/07：迁移标记区分默认文件和已经导入旧启动设置的配置。
 *
 * @author zExNocs
 * @date 2026/11/07
 * @since 4.9.0-l
 * @version 4.9.0-l
 */
@Getter
@Setter
@FileConfig(value = "plugins", type = FileConfigType.YAML, sensitive = true)
public final class PluginSettings implements IFileConfigData {
    private int version = 1;
    private boolean migrated;
    private Map<String, Boolean> enabled = Map.of();

    /** 创建尚未迁移的默认意愿，不启动任何业务。 */
    public PluginSettings() {
    }
}
