package org.zexnocs.teanekoagent.adapter.web;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplySender;
import org.zexnocs.teanekoagent.expression.IExpressionClientAdapter;
import org.zexnocs.teanekoagent.expression.MediaReplySettings;
import org.zexnocs.teanekoagent.expression.TeaNekoExpressionClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoclient.web.WebTeaNekoClient;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 网页表达适配器，图片沿用原持久发送器，客户端表情以明确 Unicode 目录单独发送。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.7.0-l
 * @version 4.7.0-l
 */
@Component
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
        if (mode == AgentReplyPlan.Mode.AT) {
            throw new IllegalArgumentException("网页表情图片不能附带提及。");
        }
        var sender = data.getMessageSender().setRetryCount(0).setRecordFailed(false);
        if (mode == AgentReplyPlan.Mode.REPLY) {
            sender.addReplyMessage();
        }
        return AgentReplySender.confirm(sender.addImageMessage("base64://" + base64).sendWithFuture());
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
