package org.zexnocs.teanekoclient.onebot.agent;

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.springframework.stereotype.Component;
import org.zexnocs.teanekoagent.component.expression.AgentReplyPlan;
import org.zexnocs.teanekoagent.component.expression.IExpressionClientAdapter;
import org.zexnocs.teanekoagent.component.expression.MediaReplySettings;
import org.zexnocs.teanekoagent.component.expression.TeaNekoExpressionClient;
import org.zexnocs.teanekoagent.component.expression.adapter.AgentReplySender;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoclient.general.agent.AgentMediaSender;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotContent;
import org.zexnocs.teanekoclient.onebot.data.receive.message.content.FaceOnebotContentPart;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * 独立发送 OneBot 内联原图与 face，沿用原平台状态和实际消息回执。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：由客户端提供默认原生表情，共用独立素材发送规则。
 * <br>2026/11/05：明确原生表情配置中非法标识的拒绝条件。
 *
 * @author zExNocs
 * @date 2026/11/03
 * @since 4.6.2-l
 * @version 4.8.0-l
 */
@Component
@TeaNekoExpressionClient(OnebotTeaNekoClient.ID)
public final class OnebotExpressionClient implements IExpressionClientAdapter {
    // 2026/11/05：平台默认目录属于客户端；配置显式空表时仍关闭该平台表情。
    private static final Map<String, String> DEFAULT_FACES = Map.of(
            "14", "微笑", "5", "流泪", "32", "疑问", "76", "赞", "66", "爱心");

    /**
     * @param data 原目标
     * @return 已明确支持 base64 图片
     */
    @Override
    public boolean images(ITeaNekoMessageData data) {
        return true;
    }

    /**
     * 2026/11/05：解析本客户端目录，显式空目录关闭表情，非法标识不进入发送阶段。
     * @param data 原目标
     * @param settings 本地目录
     * @return 当前允许的原生表情
     * @throws IllegalArgumentException 当配置的表情标识不是一至五位非负整数时
     */
    @Override
    public Map<String, String> faces(ITeaNekoMessageData data, MediaReplySettings settings) {
        // 2026/11/05：仅整个字段缺省时使用默认值，全局空表和当前客户端空表均保持禁用。
        var faces = settings.nativeFacesFor(OnebotTeaNekoClient.ID, DEFAULT_FACES);
        if (faces.keySet().stream().anyMatch(id -> !id.matches("[0-9]{1,5}"))) {
            throw new IllegalArgumentException("OneBot 表情 ID 必须是有界非负整数。");
        }
        return faces;
    }

    /**
     * @param data 原目标
     * @param base64 原图编码
     * @param mode 方式
     * @return 实际确认
     */
    @Override
    public CompletionStage<Void> image(ITeaNekoMessageData data, String base64, AgentReplyPlan.Mode mode) {
        // 2026/11/05：复用客户端通用素材规则，不改变发送回执及引用目标。
        return AgentMediaSender.image(data, base64, mode);
    }

    /**
     * @param data 原目标
     * @param id 已验证 ID
     * @param mode 方式
     * @return 实际确认
     */
    @Override
    public CompletionStage<Void> face(ITeaNekoMessageData data, String id, AgentReplyPlan.Mode mode) {
        // 2026/11/05：共享发送约束，OneBot 专属表情协议继续留在本适配器。
        var sender = AgentMediaSender.sender(data, mode);
        sender.getMessageListBuilder().addContent(OnebotContent.builder().type(FaceOnebotContentPart.TYPE)
                .contentPart(FaceOnebotContentPart.builder().id(id).build()).build());
        return AgentReplySender.confirm(sender.sendWithFuture());
    }
}
