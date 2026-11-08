package org.zexnocs.teanekoplugin.web.common.agent;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.runtime.delivery.AgentReplyPlan;
import org.zexnocs.teanekoplugin.agent.expression.expression.IExpressionClientAdapter;
import org.zexnocs.teanekoplugin.agent.expression.expression.MediaReplySettings;
import org.zexnocs.teanekoplugin.agent.expression.expression.TeaNekoExpressionClient;
import org.zexnocs.teanekoagent.runtime.delivery.AgentReplySender;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoclient.general.agent.AgentMediaSender;
import org.zexnocs.teanekoclient.web.WebTeaNekoClient;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 网页表达适配器，图片沿用原持久发送器，客户端表情以明确 Unicode 目录单独发送。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：图片发送复用客户端通用素材规则，保留网页 Unicode 表情映射。
 *
 * <br>2026/11/07：表达适配随网页业务迁入插件。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.9.0-l
 */
@Component
@org.zexnocs.teanekocore.plugin.PluginOwned("web.common")
@TeaNekoExpressionClient(WebTeaNekoClient.ID)
public final class WebExpressionClient implements IExpressionClientAdapter {
    /**
     * @param data 原网页来源
     * @return 支持受限内联图片，动画只保留静态首帧
     */
    @Override
    public boolean images(ITeaNekoMessageData data) {
        return true;
    }

    /**
     * @param data 原来源
     * @param settings 原本地媒体目录
     * @return 仅公开可明确映射的网页客户端表情
     */
    @Override
    public Map<String, String> faces(ITeaNekoMessageData data, MediaReplySettings settings) {
        return Map.of("smile", "微笑", "happy", "开心", "sad", "难过", "angry", "生气", "thinking", "思考");
    }

    /**
     * @param data 原来源
     * @param base64 原素材编码
     * @param mode plain 或 reply
     * @return 真实持久消息确认
     */
    @Override
    public CompletionStage<Void> image(ITeaNekoMessageData data, String base64, AgentReplyPlan.Mode mode) {
        // 2026/11/05：图片与其他客户端使用相同的独立消息、引用及确认规则。
        return AgentMediaSender.image(data, base64, mode);
    }

    /**
     * @param data 原来源
     * @param id 原目录客户端表情
     * @param mode plain 或 reply
     * @return 原消息发送链已经持久提交
     */
    @Override
    public CompletionStage<Void> face(ITeaNekoMessageData data, String id, AgentReplyPlan.Mode mode) {
        String text = switch (id) {
            case "smile" -> "🙂";
            case "happy" -> "😄";
            case "sad" -> "😢";
            case "angry" -> "😠";
            case "thinking" -> "🤔";
            default -> throw new IllegalArgumentException("网页客户端没有该表情。");
        };
        return AgentReplySender.send(data, new AgentReplyPlan.Text(text, mode));
    }
}
