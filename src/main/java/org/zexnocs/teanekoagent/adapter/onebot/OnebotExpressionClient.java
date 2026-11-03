package org.zexnocs.teanekoagent.adapter.onebot;

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplyPlan;
import org.zexnocs.teanekoagent.adapter.platform.AgentReplySender;
import org.zexnocs.teanekoagent.expression.IExpressionClientAdapter;
import org.zexnocs.teanekoagent.expression.MediaReplySettings;
import org.zexnocs.teanekoagent.expression.TeaNekoExpressionClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotContent;
import org.zexnocs.teanekoclient.onebot.data.receive.message.content.FaceOnebotContentPart;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 独立发送 OneBot 内联原图与 face，沿用原平台状态和实际消息回执。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.0.0
 * @version 4.6.2-l
 */
@Component @TeaNekoExpressionClient(OnebotTeaNekoClient.ID)
public final class OnebotExpressionClient implements IExpressionClientAdapter {
    /**
     * @param data 原目标
     * @return 已明确支持 base64 图片
     */ @Override public boolean images(ITeaNekoMessageData data){return true;}
    /**
     * @param data 原目标
     * @param settings 本地目录
     * @return 当前允许的原生表情
     */
    @Override public Map<String,String> faces(ITeaNekoMessageData data, MediaReplySettings settings){
        var faces=settings.nativeFaces().getOrDefault(OnebotTeaNekoClient.ID,Map.of());
        if(faces.keySet().stream().anyMatch(id->!id.matches("[0-9]{1,5}"))){throw new IllegalArgumentException("OneBot 表情 ID 必须是有界非负整数。");}return faces;
    }
    /**
     * @param data 原目标
     * @param base64 原图编码
     * @param mode 方式
     * @return 实际确认
     */
    @Override public CompletionStage<Void> image(ITeaNekoMessageData data,String base64,AgentReplyPlan.Mode mode){
        var sender=sender(data,mode);sender.addImageMessage("base64://"+base64);return AgentReplySender.confirm(sender.sendWithFuture());
    }
    /**
     * @param data 原目标
     * @param id 已验证 ID
     * @param mode 方式
     * @return 实际确认
     */
    @Override public CompletionStage<Void> face(ITeaNekoMessageData data,String id,AgentReplyPlan.Mode mode){
        var sender=sender(data,mode);sender.getMessageListBuilder().addContent(OnebotContent.builder().type(FaceOnebotContentPart.TYPE).contentPart(FaceOnebotContentPart.builder().id(id).build()).build());
        return AgentReplySender.confirm(sender.sendWithFuture());
    }
    /**
     * @param data 原目标
     * @param mode 独立消息方式
     * @return 新建且不混入文本和 at 的构建器
     */
    private org.zexnocs.teanekoapp.sender.api.sender_box.IEasyMessageSenderBuilder sender(ITeaNekoMessageData data,AgentReplyPlan.Mode mode){
        if(mode==AgentReplyPlan.Mode.AT){throw new IllegalArgumentException("表情资源不可附带 at。");}
        var sender=data.getMessageSender().setRetryCount(0).setRecordFailed(false);if(mode==AgentReplyPlan.Mode.REPLY){sender.addReplyMessage(data.getMessageId());}return sender;
    }
}
