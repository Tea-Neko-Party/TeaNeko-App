package org.zexnocs.teanekoclient.onebot.data.send.params.message;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessage;
import org.zexnocs.teanekoclient.onebot.data.response.params.group.GroupForwardMessageResponseData;
import org.zexnocs.teanekoclient.onebot.data.send.ISendParamsData;

import java.util.List;

/**
 * 发送群转发消息的参数数据。
 * <p>对应的响应类型为 GroupForwardMessageResponseData。
 *
 * @author zExNocs
 * @date 2026/02/28
 * @since 4.0.11
 */
@Getter
@Builder
public class GroupForwardMessageSendParamsData implements ISendParamsData<GroupForwardMessageResponseData> {
    public static final String ACTION = "send_group_forward_msg";

    /// 群号
    @JsonProperty("group_id")
    private final long groupId;

    /// 外显
    /// 不知道是什么
    @Builder.Default
    @JsonProperty("prompt")
    private final String prompt = null;

    /// 底下文本
    /// 目前仅桌面端可见
    @Builder.Default
    @JsonProperty("summary")
    private final String summary = null;

    /// 内容
    /// 放在外面展示的标题
    @JsonProperty("source")
    @Builder.Default
    private final String source = null;

    /**
     * 应该添加 node 节点。
     * 详细见 {@link org.zexnocs.teanekoapp.message.api.content.INodeTeaNekoContent}
     */
    @JsonProperty("messages")
    private final List<OnebotMessage> messages;

    @Override
    public String getAction() {
        return ACTION;
    }

    /**
     * 获取反应数据的类型。
     *
     * @return 反应数据的类型。
     */
    @Override
    public Class<GroupForwardMessageResponseData> getResponseDataType() {
        return GroupForwardMessageResponseData.class;
    }
}