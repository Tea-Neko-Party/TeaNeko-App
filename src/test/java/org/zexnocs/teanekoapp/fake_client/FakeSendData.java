package org.zexnocs.teanekoapp.fake_client;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.sender.api.ISendData;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.*;

/**
 * 记录模拟发送目标与内容，供消息、转发和响应映射断言使用。
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.0.0
 * @version 4.5.0-l
 */
@Getter
public class FakeSendData implements ISendData<FakeMessageSendResponseData> {
    /**
     * 区分模拟发送的私聊、群聊等目标类型。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    public enum TargetType {
        PRIVATE,
        GROUP,
        PRIVATE_FORWARD,
        GROUP_FORWARD
    }

    /**
     * 固定模拟转发节点的发言者及内容。
     *
     * @author zExNocs
     * @date 2026/10/28
     * @since 4.0.0
     * @version 4.5.0-l
     */
    @Getter
    @AllArgsConstructor
    public static class ForwardNode {
        private String userId;
        private String nickname;
        private List<? extends ITeaNekoContent> messages;
    }

    private final String echo = UUID.randomUUID().toString();
    private final String messageId;
    private final String token;
    private final TargetType targetType;
    private final String targetId;
    private final List<? extends ITeaNekoContent> messages;
    private final List<ForwardNode> forwardNodes;
    private final @Nullable String prompt;
    private final @Nullable String summary;
    private final @Nullable String source;
    private final Duration delay;
    private final int retryCount;
    private final Duration retryInterval;
    private final boolean recordFailed;

    @JsonIgnore
    private final IClient client;

    @JsonIgnore
    private final ObjectMapper objectMapper;

    /**
     * 校验并固定当前实例的初始数据；集合在构造时复制，避免外部修改影响本次状态。
     *
     * @param client 用于本次测试的 {@code client} 数据
     * @param objectMapper 项目 JSON 解析与转换器
     * @param messageId 需要配对或查询的消息标识
     * @param token 原发送链路使用的状态令牌
     * @param targetType 用于本次测试的 {@code targetType} 数据
     * @param targetId 原业务规则允许操作的目标标识
     * @param messages 保持原始顺序的统一消息列表
     * @param forwardNodes 用于本次测试的 {@code forwardNodes} 数据
     * @param prompt 经过宿主装配的消息与本次模型选项
     * @param summary 转发消息摘要
     * @param source 待处理的来源数据
     * @param delay 提交任务前的延迟
     * @param retryCount 允许的重试次数
     * @param retryInterval 两次重试之间的等待时长
     * @param recordFailed 是否记录任务失败
     */
    public FakeSendData(@NonNull IClient client,
                        @NonNull ObjectMapper objectMapper,
                        @NonNull String messageId,
                        @NonNull String token,
                        @NonNull TargetType targetType,
                        @NonNull String targetId,
                        @NonNull List<? extends ITeaNekoContent> messages,
                        @NonNull List<ForwardNode> forwardNodes,
                        @Nullable String prompt,
                        @Nullable String summary,
                        @Nullable String source,
                        @NonNull Duration delay,
                        int retryCount,
                        @NonNull Duration retryInterval,
                        boolean recordFailed) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.messageId = messageId;
        this.token = token;
        this.targetType = targetType;
        this.targetId = targetId;
        this.messages = List.copyOf(messages);
        this.forwardNodes = List.copyOf(forwardNodes);
        this.prompt = prompt;
        this.summary = summary;
        this.source = source;
        this.delay = delay;
        this.retryCount = retryCount;
        this.retryInterval = retryInterval;
        this.recordFailed = recordFailed;
    }

    /**
     * 生成便于断言的发送文本表示，保留当前目标和内容关系。
     *
     * @return 按本方法契约生成的文本或标识
     */
    @Override
    public @NonNull String toSendString() {
        return objectMapper.writeValueAsString(toMap());
    }

    /**
     * 按模拟发送动作选择原响应事件需要的回执类型。
     *
     * @return 满足本方法约束的 {@code Class<FakeMessageSendResponseData>} 结果
     */
    @Override
    public @NonNull Class<FakeMessageSendResponseData> getResponseType() {
        return FakeMessageSendResponseData.class;
    }

    /**
     * 构造与测试原生协议一致的发送数据映射，不发起实际网络请求。
     *
     * @return 按当前定位规则生成的映射；是否可修改遵循本方法说明
     */
    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        map.put("echo", echo);
        map.put("message_id", messageId);
        map.put("token", token);
        map.put("target_type", targetType.name().toLowerCase());
        map.put("target_id", targetId);
        map.put("messages", simplifyMessages(messages));
        map.put("forward_nodes", forwardNodes.stream()
                .map(node -> {
                    var nodeMap = new LinkedHashMap<String, Object>();
                    nodeMap.put("user_id", node.getUserId());
                    nodeMap.put("nickname", node.getNickname());
                    nodeMap.put("messages", simplifyMessages(node.getMessages()));
                    return nodeMap;
                })
                .toList());
        map.put("prompt", prompt);
        map.put("summary", summary);
        map.put("source", source);
        map.put("delay", delay.toString());
        map.put("retry_count", retryCount);
        map.put("retry_interval", retryInterval.toString());
        map.put("record_failed", recordFailed);
        return map;
    }

    /**
     * 将内容列表转为测试可比较的表示，保留各内容项的类型与顺序。
     *
     * @param messageList 保持原始顺序的消息内容列表
     * @return 按当前定位规则生成的映射；是否可修改遵循本方法说明
     */
    private List<Map<String, Object>> simplifyMessages(List<? extends ITeaNekoContent> messageList) {
        return messageList.stream()
                .map(content -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    var contentPart = content.getContentPart();
                    map.put("type", content.getType());
                    map.put("raw", contentPart.toRawString());
                    map.put("args", Arrays.asList(contentPart.toCommandArgs()));
                    return map;
                })
                .toList();
    }
}
