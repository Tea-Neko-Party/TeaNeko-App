package org.zexnocs.teanekoplugin.agent.expression.sticker.model;

import lombok.Getter;
import lombok.Setter;
import org.zexnocs.teanekocore.file_config.api.FileConfig;
import org.zexnocs.teanekocore.file_config.api.FileConfigType;
import org.zexnocs.teanekocore.file_config.api.IFileConfigData;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * 表情包插件的全局备用模型，独立于聊天和图库存储配置。
 * <br>2026/11/10：默认不指定备用模型，支持系统路由和私有配置。
 *
 * @author zExNocs
 * @date 2026/11/10
 * @since 4.9.1-l
 * @version 4.9.1-l
 */
@Getter
@Setter
@FileConfig(value = "sticker-model", path = "agent", type = FileConfigType.YAML, sensitive = true)
@PluginOwned("agent.expression")
public final class StickerModelFileConfig implements IFileConfigData {
    private JsonNode fallback = JsonNodeFactory.instance.objectNode().put("source", "none");
}
