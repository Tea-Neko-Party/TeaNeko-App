package org.zexnocs.teanekoplugin.agent.expression.sticker;

// 2026/11/07：按插件归属迁移业务实现，保留底层契约、持久标识和事务边界。

import org.zexnocs.teanekoagent.component.DeclaredAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 采集和使用实例共享图库；保留原下载、容量和视觉识别限制。
 * <br>2026/11/06：声明组件显示优先级，运行依赖和装配行为不变。
 * <br>2026/11/07：按插件归属迁移业务实现，保留原配置与持久化契约。
 * <br>2026/11/07：参数类型复用通用底座，业务约束由本组件独立维护。
 * <br>2026/11/10：声明独立识图模型字段及检测入口，默认跟随 LLM 组件。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.9.1-l
 */
@TeaNekoAgentComponent(id = "sticker", order = 50, name = "表情包", description = "采集和使用实例共享图库；保留原下载、容量和视觉识别限制。", requires = {"expression", "tool"})
@PluginOwned("agent.expression")
public final class StickerComponent extends DeclaredAgentComponent {
    private final org.zexnocs.teanekoplugin.agent.expression.sticker.model.StickerModelConfiguration models;

    /** @param models 2026/11/10：原模型配置协议的独立验证器 */
    public StickerComponent(org.zexnocs.teanekoplugin.agent.expression.sticker.model.StickerModelConfiguration models) {
        super("sticker", Map.ofEntries(
                Map.entry("model", Map.of("source", "inherit")),
                Map.entry("stickerCollectEnabled", true),
                Map.entry("stickerReplyEnabled", true)), List.of(
                new Field("stickerCollectEnabled", "采集表情包", "按原限流采集真实入站图片。", "BOOLEAN"),
                new Field("stickerReplyEnabled", "使用表情包", "允许按类别查询并独立发送。", "BOOLEAN"),
                new Field("model", "采集识图模型", "默认跟随 LLM 组件；也可选择已有路由或聊天私有模型。",
                        "MODEL", "plugins/sticker-models/probe/component")));
        this.models = models;
    }

    /** @param parameters 2026/11/10：本组件配置，包含可选的独立模型选择 */
    @Override public void validate(tools.jackson.databind.JsonNode parameters) {
        super.validate(parameters);
        if (parameters.has("model")) {
            models.validate(parameters.path("model"), "inherit");
        }
    }

    /** @return 本组件装配的既有业务系统来源。 */
    @Override
    public Set<String> features() {
        return Set.of("agent.expression:media-replies");
    }
}
