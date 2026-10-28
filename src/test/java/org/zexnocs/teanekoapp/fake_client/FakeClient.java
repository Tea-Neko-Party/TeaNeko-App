package org.zexnocs.teanekoapp.fake_client;

import lombok.Getter;
import org.springframework.stereotype.Component;
import org.zexnocs.teanekoapp.client.api.IClient;
import org.zexnocs.teanekoapp.client.api.ITeaNekoClient;
import org.zexnocs.teanekoapp.fake_client.message.FakeMessageSenderTools;
import org.zexnocs.teanekoapp.fake_client.message.NormalMessageSender;
import org.zexnocs.teanekoapp.message.DefaultTeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.message.TeaNekoMessageData;
import org.zexnocs.teanekoapp.message.TeaNekoMessageReceiveEvent;
import org.zexnocs.teanekoapp.message.TeaNekoUserData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoContent;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekoapp.response.ResponseData;
import org.zexnocs.teanekoapp.response.ResponseEvent;
import org.zexnocs.teanekoapp.sender.api.ISendData;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.event.interfaces.IEvent;
import org.zexnocs.teanekocore.logger.DefaultLogger;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 假客户端，用于测试
 *
 * @author zExNocs
 * @date 2026/10/28
 * @since 4.2.3
 * @version 4.5.0-l
 */
@Component
public class FakeClient implements IClient {
    public static final String DEFAULT_USER_ID = "fake-user";
    public static final String DEFAULT_GROUP_ID = "fake-group";
    public static final String DEFAULT_NICKNAME = "Fake User";

    @Getter
    private final ILogger logger;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong inboundMessageId = new AtomicLong();
    private final AtomicLong outboundMessageId = new AtomicLong();
    private final List<String> sentRawMessages = new CopyOnWriteArrayList<>();
    private final List<ISendData<?>> sentData = new CopyOnWriteArrayList<>();
    private final List<FakeSendData> sentMessages = new CopyOnWriteArrayList<>();
    private final List<FakeKickRecord> kickRecords = new CopyOnWriteArrayList<>();
    private final Map<String, TeaNekoMessageData> receivedMessages = new ConcurrentHashMap<>();
    private final Map<String, FakePlatformUserResponseData> platformUsers = new ConcurrentHashMap<>();
    private final Map<String, Map<String, FakeGroupMemberResponseData>> groupMembers = new ConcurrentHashMap<>();

    private volatile FakeTeaNekoClient teaNekoClient;

    /**
     * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
     */
    public FakeClient() {
        this(new DefaultLogger());
    }

    /**
     * 通过构造参数固定当前实例的依赖与初始状态，避免执行时再从全局环境查找协作者。
     *
     * @param logger 项目日志接口
     */
    public FakeClient(ILogger logger) {
        this.logger = logger;
    }

    /**
     * 绑定本次测试使用的 TeaNeko 客户端实例，供后续输入和响应事件沿原链路流转。
     *
     * @param teaNekoClient 用于本次测试的 {@code teaNekoClient} 数据
     */
    void bind(FakeTeaNekoClient teaNekoClient) {
        this.teaNekoClient = teaNekoClient;
    }

    /**
     * 发送消息到服务器。
     *
     * @param message 要发送的消息
     */
    @Override
    public void send(String message) {
        sentRawMessages.add(message);
    }

    /**
     * 记录发送数据并生成受控回执，不向真实平台发出请求。
     *
     * @param data 本次处理的输入数据
     */
    @Override
    public void send(ISendData<?> data) {
        sentData.add(data);
        if (data instanceof FakeSendData fakeSendData) {
            sentMessages.add(fakeSendData);
        }
        send(data.toSendString());
    }

    /**
     * 从客户端接收消息并处理。
     * 一般就是将 message 解析后包装成事件对象并推送到事件总线中。
     * 一般分为两个主要事件：
     * 1. 消息事件 MessageEvent：用于处理用户发送的消息
     * 2. 响应事件 ResponseEvent：用于处理客户端的响应信息
     *
     * @param message 接收到的消息
     * @return 满足本方法约束的 {@code IEvent<?>} 结果
     */
    @Override
    public IEvent<?> handle(String message) {
        try {
            @SuppressWarnings("unchecked")
            var data = (Map<String, Object>) objectMapper.readValue(message, Map.class);
            if (isResponse(data)) {
                return buildResponseEvent(data);
            }
            return buildMessageEvent(data);
        } catch (Exception ignored) {
            return handlePrivateMessage(DEFAULT_USER_ID, DEFAULT_NICKNAME, message);
        }
    }

    /**
     * 构造带用户来源的私聊输入，交给与生产相同的消息事件入口。
     *
     * @param text 待处理的业务文本
     * @return 满足本方法约束的 {@code TeaNekoMessageReceiveEvent<TeaNekoMessageData>} 结果
     */
    public TeaNekoMessageReceiveEvent<TeaNekoMessageData> handlePrivateMessage(String text) {
        return handlePrivateMessage(DEFAULT_USER_ID, DEFAULT_NICKNAME, text);
    }

    /**
     * 构造带用户来源的私聊输入，交给与生产相同的消息事件入口。
     *
     * @param userId 原业务边界中的目标用户标识
     * @param nickname 用户在当前来源中的显示名称
     * @param text 待处理的业务文本
     * @return 满足本方法约束的 {@code TeaNekoMessageReceiveEvent<TeaNekoMessageData>} 结果
     */
    public TeaNekoMessageReceiveEvent<TeaNekoMessageData> handlePrivateMessage(String userId,
                                                                               String nickname,
                                                                               String text) {
        return buildMessageEvent(
                TeaNekoMessageType.PRIVATE,
                null,
                userId,
                nickname,
                CommandPermission.OWNER,
                DefaultTeaNekoContentListBuilder.builder().addText(text).build(),
                null,
                Instant.now()
        );
    }

    /**
     * 构造包含群、用户和角色的群聊输入，便于验证作用域及权限链。
     *
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     * @param text 待处理的业务文本
     * @return 满足本方法约束的 {@code TeaNekoMessageReceiveEvent<TeaNekoMessageData>} 结果
     */
    public TeaNekoMessageReceiveEvent<TeaNekoMessageData> handleGroupMessage(String groupId,
                                                                             String userId,
                                                                             String text) {
        return handleGroupMessage(groupId, userId, DEFAULT_NICKNAME, CommandPermission.MEMBER, text);
    }

    /**
     * 构造包含群、用户和角色的群聊输入，便于验证作用域及权限链。
     *
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     * @param nickname 用户在当前来源中的显示名称
     * @param role 来源中经过校验的成员角色
     * @param text 待处理的业务文本
     * @return 满足本方法约束的 {@code TeaNekoMessageReceiveEvent<TeaNekoMessageData>} 结果
     */
    public TeaNekoMessageReceiveEvent<TeaNekoMessageData> handleGroupMessage(String groupId,
                                                                             String userId,
                                                                             String nickname,
                                                                             CommandPermission role,
                                                                             String text) {
        return buildMessageEvent(
                TeaNekoMessageType.GROUP,
                groupId,
                userId,
                nickname,
                role,
                DefaultTeaNekoContentListBuilder.builder().addText(text).build(),
                null,
                Instant.now()
        );
    }

    /**
     * 为测试发送生成单调递增的消息标识，用于消息与回执配对。
     *
     * @return 按本方法契约生成的文本或标识
     */
    public String nextSentMessageId() {
        return "fake-sent-" + outboundMessageId.incrementAndGet();
    }

    /**
     * 提供已记录的原始发送内容快照，便于断言原生消息映射。
     *
     * @return 保持本方法顺序和筛选规则的结果集合
     */
    public List<String> getSentRawMessages() {
        return List.copyOf(sentRawMessages);
    }

    /**
     * 提供结构化发送记录，便于测试校验目标与分段内容。
     *
     * @return 保持本方法顺序和筛选规则的结果集合
     */
    public List<ISendData<?>> getSentData() {
        return List.copyOf(sentData);
    }

    /**
     * 提供已发送内容的测试视图，不启动任何实际发送。
     *
     * @return 保持本方法顺序和筛选规则的结果集合
     */
    public List<FakeSendData> getSentMessages() {
        return List.copyOf(sentMessages);
    }

    /**
     * 取得最近一次发送记录，未发送时按既有约定返回空值。
     *
     * @return 满足本方法约束的 {@code FakeSendData} 结果
     */
    public FakeSendData getLastSentMessage() {
        return sentMessages.isEmpty() ? null : sentMessages.getLast();
    }

    /**
     * 按已知消息标识查找测试采集的入站消息，不模拟全平台搜索。
     *
     * @param messageId 需要配对或查询的消息标识
     * @return 满足本方法约束的 {@code TeaNekoMessageData} 结果
     */
    public TeaNekoMessageData getReceivedMessage(String messageId) {
        return receivedMessages.get(messageId);
    }

    /**
     * 在当前 FakeClient 内按平台标识复用测试用户，保持同一用例身份稳定。
     *
     * @param userId 原业务边界中的目标用户标识
     * @return 满足本方法约束的 {@code FakePlatformUserResponseData} 结果
     */
    public FakePlatformUserResponseData getOrCreatePlatformUser(String userId) {
        return platformUsers.computeIfAbsent(userId,
                id -> new FakePlatformUserResponseData(id, 0, id));
    }

    /**
     * 按群与用户定位测试成员，避免不同群的成员状态混用。
     *
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     * @return 满足本方法约束的 {@code FakeGroupMemberResponseData} 结果
     */
    public FakeGroupMemberResponseData getOrCreateGroupMember(String groupId, String userId) {
        return groupMembers
                .computeIfAbsent(groupId, ignored -> new ConcurrentHashMap<>())
                .computeIfAbsent(userId, id -> new FakeGroupMemberResponseData(
                        id,
                        id,
                        id,
                        Instant.now(),
                        Instant.now(),
                        0,
                        null,
                        CommandPermission.MEMBER.name().toLowerCase()
                ));
    }

    /**
     * 提供指定测试群的成员视图，供离线群业务查询使用。
     *
     * @param groupId 目标群组标识
     * @return 保持本方法顺序和筛选规则的结果集合
     */
    public List<FakeGroupMemberResponseData> getGroupMembers(String groupId) {
        return List.copyOf(groupMembers
                .computeIfAbsent(groupId, ignored -> new ConcurrentHashMap<>())
                .values());
    }

    /**
     * 生成无需真实网络请求的用户头像引用，供消息展示测试使用。
     *
     * @param userId 原业务边界中的目标用户标识
     * @return 按本方法契约生成的文本或标识
     */
    public String getAvatarUrl(String userId) {
        return "fake://avatar/" + userId;
    }

    /**
     * 记录受控踢出动作，供测试断言目标群和用户。
     *
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     */
    public void recordKick(String groupId, String userId) {
        kickRecords.add(new FakeKickRecord(groupId, userId));
    }

    /**
     * 提供已记录的踢出操作快照，供平台动作完成链核对。
     *
     * @return 保持本方法顺序和筛选规则的结果集合
     */
    public List<FakeKickRecord> getKickRecords() {
        return List.copyOf(kickRecords);
    }

    /**
     * 清理当前测试客户端收发记录和模拟状态，避免不同用例相互污染。
     */
    public void clear() {
        sentRawMessages.clear();
        sentData.clear();
        sentMessages.clear();
        kickRecords.clear();
        receivedMessages.clear();
        platformUsers.clear();
        groupMembers.clear();
        inboundMessageId.set(0);
        outboundMessageId.set(0);
    }

    /**
     * 按测试协议字段区分回执与普通入站消息。
     *
     * @param data 本次处理的输入数据
     * @return 当前校验或原子操作是否满足本方法约定的条件
     */
    private boolean isResponse(Map<String, Object> data) {
        return data.containsKey("echo")
                && (data.containsKey("success") || data.containsKey("status"));
    }

    /**
     * 将测试回执转换为原响应事件，保留任务关联信息。
     *
     * @param data 本次处理的输入数据
     * @return 满足本方法约束的 {@code ResponseEvent} 结果
     */
    private ResponseEvent buildResponseEvent(Map<String, Object> data) {
        var echo = String.valueOf(data.get("echo"));
        var success = resolveSuccess(data.get("success"), data.get("status"));
        return new ResponseEvent(
                new ResponseData(success, echo, toRawData(data.get("data"))),
                ResponseData.class
        );
    }

    /**
     * 归一化测试协议中的成功标记和状态字段。
     *
     * @param success 协议中的显式成功标记
     * @param status 本次处理的终止状态
     * @return 当前校验或原子操作是否满足本方法约定的条件
     */
    private boolean resolveSuccess(Object success, Object status) {
        if (success instanceof Boolean bool) {
            return bool;
        }
        if (success != null) {
            return Boolean.parseBoolean(String.valueOf(success));
        }
        return status != null && ("ok".equalsIgnoreCase(String.valueOf(status))
                || "success".equalsIgnoreCase(String.valueOf(status)));
    }

    /**
     * 将测试输入转换为原生数据映射，供事件构造共用。
     *
     * @param data 本次处理的输入数据
     * @return 按当前定位规则生成的映射；是否可修改遵循本方法说明
     */
    private List<Map<String, Object>> toRawData(Object data) {
        switch (data) {
            case null -> {
                return null;
            }
            case List<?> list -> {
                var result = new ArrayList<Map<String, Object>>();
                for (var item : list) {
                    if (item instanceof Map<?, ?> map) {
                        result.add(toStringKeyMap(map));
                    } else {
                        result.add(Map.of("data", item));
                    }
                }
                return result;
            }
            case Map<?, ?> map -> {
                return List.of(toStringKeyMap(map));
            }
            default -> {
            }
        }
        return List.of(Map.of("data", data));
    }

    /**
     * 将测试输入的映射键转换为协议要求的字符串形式。
     *
     * @param map 按当前操作语义定位的输入映射
     * @return 按当前定位规则生成的映射；是否可修改遵循本方法说明
     */
    private Map<String, Object> toStringKeyMap(Map<?, ?> map) {
        var result = new LinkedHashMap<String, Object>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    /**
     * 从测试输入创建完整消息事件，统一补齐来源、内容和时间。
     *
     * @param data 本次处理的输入数据
     * @return 满足本方法约束的 {@code TeaNekoMessageReceiveEvent<TeaNekoMessageData>} 结果
     */
    private TeaNekoMessageReceiveEvent<TeaNekoMessageData> buildMessageEvent(Map<String, Object> data) {
        var type = resolveMessageType(data);
        var groupId = stringValue(firstPresent(data, "group_id", "groupId"));
        if (groupId == null && type == TeaNekoMessageType.GROUP) {
            groupId = DEFAULT_GROUP_ID;
        }
        var userId = stringValue(firstPresent(data, "user_id", "userId", "platform_id", "platformId"));
        if (userId == null) {
            userId = DEFAULT_USER_ID;
        }
        var nickname = stringValue(firstPresent(data, "nickname", "nick", "name"));
        if (nickname == null) {
            nickname = DEFAULT_NICKNAME;
        }
        var role = resolvePermission(stringValue(firstPresent(data, "role", "permission")), type);
        var messageId = stringValue(firstPresent(data, "message_id", "messageId", "id"));
        var time = resolveTime(firstPresent(data, "time", "timestamp"));
        var messages = resolveMessages(data);
        return buildMessageEvent(type, groupId, userId, nickname, role, messages, messageId, time);
    }

    /**
     * 从测试输入创建完整消息事件，统一补齐来源、内容和时间。
     *
     * @param type 运行时类型
     * @param groupId 目标群组标识
     * @param userId 原业务边界中的目标用户标识
     * @param nickname 用户在当前来源中的显示名称
     * @param role 来源中经过校验的成员角色
     * @param messages 保持原始顺序的统一消息列表
     * @param messageId 需要配对或查询的消息标识
     * @param time 事件发生时间
     * @return 满足本方法约束的 {@code TeaNekoMessageReceiveEvent<TeaNekoMessageData>} 结果
     */
    private TeaNekoMessageReceiveEvent<TeaNekoMessageData> buildMessageEvent(TeaNekoMessageType type,
                                                                             String groupId,
                                                                             String userId,
                                                                             String nickname,
                                                                             CommandPermission role,
                                                                             List<? extends ITeaNekoContent> messages,
                                                                             String messageId,
                                                                             Instant time) {
        var uuid = UUID.nameUUIDFromBytes((FakeTeaNekoClient.ID + ":" + userId).getBytes(StandardCharsets.UTF_8));
        var userData = TeaNekoUserData.builder()
                .uuid(uuid)
                .userIdInPlatform(userId)
                .nickname(nickname)
                .role(role)
                .groupId(groupId)
                .build();
        var resolvedMessageId = messageId == null ? "fake-msg-" + inboundMessageId.incrementAndGet() : messageId;
        var messageData = TeaNekoMessageData.builder()
                .time(time == null ? Instant.now() : time)
                .messageId(resolvedMessageId)
                .messages(messages)
                .messageType(type)
                .scopeId(resolveScopeId(type, userData))
                .userData(userData)
                .client(resolveTeaNekoClient())
                .build();
        receivedMessages.put(resolvedMessageId, messageData);
        rememberUser(userData);
        return new TeaNekoMessageReceiveEvent<>(messageData);
    }

    /**
     * 保存本次观察到的测试用户信息，供后续成员或用户查询复用。
     *
     * @param userData 已解析的原用户数据
     */
    private void rememberUser(TeaNekoUserData userData) {
        platformUsers.put(userData.getUserIdInPlatform(), new FakePlatformUserResponseData(
                userData.getUserIdInPlatform(),
                0,
                userData.getNickname()
        ));
        if (userData.getGroupId() != null) {
            groupMembers.computeIfAbsent(userData.getGroupId(), ignored -> new ConcurrentHashMap<>())
                    .put(userData.getUserIdInPlatform(), new FakeGroupMemberResponseData(
                            userData.getNickname(),
                            userData.getUserIdInPlatform(),
                            userData.getNickname(),
                            Instant.now(),
                            Instant.now(),
                            0,
                            null,
                            userData.getRole().name().toLowerCase()
                    ));
        }
    }

    /**
     * 解析当前绑定的客户端，避免以未绑定状态生成来源事件。
     *
     * @return 满足本方法约束的 {@code ITeaNekoClient} 结果
     */
    private ITeaNekoClient resolveTeaNekoClient() {
        if (teaNekoClient != null) {
            return teaNekoClient;
        }
        var messageSender = new NormalMessageSender(this, logger);
        var senderTools = new FakeMessageSenderTools(messageSender, this, logger);
        var toolbox = new FakeTeaNekoToolbox(logger, senderTools, this);
        return new FakeTeaNekoClient(this, toolbox);
    }

    /**
     * 按私聊或群聊及原用户数据计算原作用域标识。
     *
     * @param type 运行时类型
     * @param userData 已解析的原用户数据
     * @return 按本方法契约生成的文本或标识
     */
    private String resolveScopeId(TeaNekoMessageType type, TeaNekoUserData userData) {
        return switch (type) {
            case GROUP -> FakeTeaNekoClient.ID + "-group@" + userData.getGroupId();
            case PRIVATE, PRIVATE_TEMP -> "private@" + userData.getUuid();
            default -> "other@" + userData.getUuid();
        };
    }

    /**
     * 识别测试消息类型，使普通文本和结构化输入走一致事件链。
     *
     * @param data 本次处理的输入数据
     * @return 满足本方法约束的 {@code TeaNekoMessageType} 结果
     */
    private TeaNekoMessageType resolveMessageType(Map<String, Object> data) {
        var rawType = stringValue(firstPresent(data, "message_type", "messageType", "type"));
        if (rawType == null) {
            return data.containsKey("group_id") || data.containsKey("groupId")
                    ? TeaNekoMessageType.GROUP
                    : TeaNekoMessageType.PRIVATE;
        }
        return switch (rawType.toLowerCase()) {
            case "group" -> TeaNekoMessageType.GROUP;
            case "private_temp", "private-temp", "temp" -> TeaNekoMessageType.PRIVATE_TEMP;
            case "private" -> TeaNekoMessageType.PRIVATE;
            default -> TeaNekoMessageType.OTHER;
        };
    }

    /**
     * 按原始成员角色转换命令权限，不从普通消息正文推断权限。
     *
     * @param rawRole 测试输入提供的原始成员角色
     * @param type 运行时类型
     * @return 满足本方法约束的 {@code CommandPermission} 结果
     */
    private CommandPermission resolvePermission(String rawRole, TeaNekoMessageType type) {
        if (rawRole == null) {
            return switch (type) {
                case PRIVATE, PRIVATE_TEMP -> CommandPermission.OWNER;
                case GROUP -> CommandPermission.MEMBER;
                default -> CommandPermission.MEMBER;
            };
        }
        return switch (rawRole.toLowerCase()) {
            case "debug" -> CommandPermission.DEBUG;
            case "owner" -> CommandPermission.OWNER;
            case "admin" -> CommandPermission.ADMIN;
            case "all" -> CommandPermission.ALL;
            default -> CommandPermission.MEMBER;
        };
    }

    /**
     * 将测试输入的时间字段归一化为事件时间，缺失时采用既有测试默认值。
     *
     * @param value 待处理的值
     * @return 满足本方法约束的 {@code Instant} 结果
     */
    private Instant resolveTime(Object value) {
        if (value == null) {
            return Instant.now();
        }
        if (value instanceof Number number) {
            long raw = number.longValue();
            return raw > 10_000_000_000L ? Instant.ofEpochMilli(raw) : Instant.ofEpochSecond(raw);
        }
        try {
            return Instant.parse(String.valueOf(value));
        } catch (Exception ignored) {
            return Instant.now();
        }
    }

    /**
     * 将测试内容转换为原平台无关内容列表，保持输入顺序。
     *
     * @param data 本次处理的输入数据
     * @return 保持本方法顺序和筛选规则的结果集合
     */
    private List<? extends ITeaNekoContent> resolveMessages(Map<String, Object> data) {
        var builder = DefaultTeaNekoContentListBuilder.builder();
        var message = firstPresent(data, "message", "messages", "text", "content");
        if (message instanceof List<?> list) {
            for (var item : list) {
                addMessagePart(builder, item);
            }
        } else {
            addMessagePart(builder, message == null ? "" : message);
        }
        return builder.build();
    }

    /**
     * 按内容类型向测试消息构建器追加片段，不把结构化内容混成普通文本。
     *
     * @param builder 用于本次测试的 {@code builder} 数据
     * @param value 待处理的值
     */
    private void addMessagePart(DefaultTeaNekoContentListBuilder builder, Object value) {
        if (value instanceof Map<?, ?> map) {
            var type = stringValue(firstPresent(map, "type"));
            var data = firstPresent(map, "data", "content");
            var payload = data instanceof Map<?, ?> dataMap ? dataMap : map;
            switch (type == null ? "text" : type.toLowerCase()) {
                case "image" -> builder.addImage(stringValue(firstPresent(payload, "url", "file", "image")));
                case "at" -> builder.addAt(stringValue(firstPresent(payload, "id", "user_id", "userId", "qq")));
                case "reply" -> builder.addReply(stringValue(firstPresent(payload, "id", "message_id", "messageId")));
                default -> builder.addText(stringValue(firstPresent(payload, "text", "value", "raw")));
            }
            return;
        }
        builder.addText(String.valueOf(value));
    }

    /**
     * 按约定字段优先级查找首个存在的值，供兼容测试输入共用。
     *
     * @param map 按当前操作语义定位的输入映射
     * @param keys 按优先级查找的候选字段名
     * @return 纯文本内容或保持原图文顺序的协议数组，取决于输入是否包含图片
     */
    private Object firstPresent(Map<?, ?> map, String... keys) {
        for (var key : keys) {
            if (map.containsKey(key)) {
                return map.get(key);
            }
        }
        return null;
    }

    /**
     * 按测试协议将标量转换为字符串，空值保持既有缺省语义。
     *
     * @param value 待处理的值
     * @return 按本方法契约生成的文本或标识
     */
    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
