package org.zexnocs.teanekoagent.component.sticker;

import org.zexnocs.teanekoagent.component.BuiltinAgentComponent;
import org.zexnocs.teanekoagent.component.TeaNekoAgentComponent;
import org.zexnocs.teanekocore.plugin.PluginOwned;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 采集和使用实例共享图库；保留原下载、容量和视觉识别限制。
 *
 * @author zExNocs
 * @date 2026/11/04
 * @since 4.8.0-l
 * @version 4.8.0-l
 */
@TeaNekoAgentComponent(id = "sticker", name = "表情包", description = "采集和使用实例共享图库；保留原下载、容量和视觉识别限制。", requires = {"expression", "tool"})
@PluginOwned("teaneko.agent")
public final class StickerComponent extends BuiltinAgentComponent {
    /** 创建本组件独立参数模板，不启动业务或修改存储。 */
    public StickerComponent() {
        super("sticker", Map.ofEntries(
                Map.entry("stickerCollectEnabled", true),
                Map.entry("stickerReplyEnabled", true)), List.of(
                new Field("stickerCollectEnabled", "采集表情包", "按原限流采集真实入站图片。", "BOOLEAN"),
                new Field("stickerReplyEnabled", "使用表情包", "允许按类别查询并独立发送。", "BOOLEAN")));
    }

    /** @return 本组件装配的既有业务系统来源。 */
    @Override
    public Set<String> features() {
        return Set.of("teaneko.agent:media-replies");
    }
}
