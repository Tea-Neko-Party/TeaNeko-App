package org.zexnocs.teanekoplugin.onebot.common.agent;

// 2026/11/07：模型能力迁入独立 LLM 子模块，保留业务协议和配置标识。

// 2026/11/07：随平台业务工具迁移回归夹具，保留原图文协议验证。

// 2026/11/04：按依赖方向整理类型引用，业务标识和序列化数据不变。

import org.junit.jupiter.api.Test;
import org.zexnocs.teanekoplugin.onebot.common.agent.OnebotExpressionClient;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.zexnocs.teanekoagent.component.AgentComponents;
import org.zexnocs.teanekoplugin.agent.expression.expression.file_config.AgentMediaReplyFileConfig;
import org.zexnocs.teanekoagent.llm.framework.input.LLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.input.interfaces.ILLMPrompt;
import org.zexnocs.teanekoagent.llm.framework.interaction.InteractionRuntime;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionResult;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionService;
import org.zexnocs.teanekoagent.llm.framework.interaction.LLMInteractionSettings;
import org.zexnocs.teanekoagent.llm.framework.message.LLMAssistantMessage;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageCodec;
import org.zexnocs.teanekoagent.llm.framework.message.LLMMessageHistory;
import org.zexnocs.teanekoagent.llm.framework.message.LLMUserMessage;
import org.zexnocs.teanekoagent.llm.framework.message.content.LLMContentListBuilder;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.ILLMMessage;
import org.zexnocs.teanekoagent.llm.framework.message.interfaces.LLMMessageRole;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptions;
import org.zexnocs.teanekoagent.llm.framework.model.LLMModelOptionsSnapshot;
import org.zexnocs.teanekoagent.llm.framework.model.interfaces.ILLMModelService;
import org.zexnocs.teanekoagent.llm.framework.response.LLMChoice;
import org.zexnocs.teanekoagent.llm.framework.response.LLMResult;
import org.zexnocs.teanekoagent.llm.framework.response.LLMUsage;
import org.zexnocs.teanekoagent.llm.framework.response.interfaces.ILLMResult;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolCall;
import org.zexnocs.teanekoagent.llm.framework.tool.LLMToolSet;
import org.zexnocs.teanekoagent.llm.framework.tool.ToolInvocationContext;
import org.zexnocs.teanekoagent.llm.instance.deepseek.DeepSeekChatCompletionMapper;
import org.zexnocs.teanekoagent.integration.message.NativeMessageSources;
import org.zexnocs.teanekoagent.integration.message.NativeMessageInput;
import org.zexnocs.teanekoagent.runtime.AgentActivity;
import org.zexnocs.teanekoagent.runtime.AgentDefinition;
import org.zexnocs.teanekoagent.runtime.AgentRunRequest;
import org.zexnocs.teanekoagent.runtime.prompt.AgentPromptInput;
import org.zexnocs.teanekoapp.message.DefaultTeaNekoContentListBuilder;
import org.zexnocs.teanekoapp.message.TeaNekoUserData;
import org.zexnocs.teanekoapp.message.api.ITeaNekoMessageData;
import org.zexnocs.teanekoapp.message.api.TeaNekoMessageType;
import org.zexnocs.teanekoclient.onebot.core.OnebotTeaNekoClient;
import org.zexnocs.teanekoclient.onebot.core.OnebotToolbox;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotMessageData;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotRawMessageData;
import org.zexnocs.teanekoclient.onebot.data.receive.message.OnebotSenderData;
import org.zexnocs.teanekoclient.onebot.data.response.params.GroupListResponseData;
import org.zexnocs.teanekoclient.onebot.data.response.params.GroupMemberResponseData;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupInfoSender;
import org.zexnocs.teanekoclient.onebot.sender.group.GetGroupMemberInfoSender;
import org.zexnocs.teanekoclient.onebot.sender.message.OnebotGetMsgSender;
import org.zexnocs.teanekoclient.onebot.utils.AvatarUtils;
import org.zexnocs.teanekocore.actuator.execution.ExecutionControl;
import org.zexnocs.teanekocore.actuator.execution.ExecutionPolicy;
import org.zexnocs.teanekocore.actuator.task.TaskFuture;
import org.zexnocs.teanekocore.actuator.task.TaskResult;
import org.zexnocs.teanekocore.command.api.CommandPermission;
import org.zexnocs.teanekocore.file_config.parser.YAMLFileTypeParser;
import org.zexnocs.teanekocore.framework.composition.ComponentPatch;
import org.zexnocs.teanekocore.framework.composition.EntityId;
import org.zexnocs.teanekocore.framework.composition.EntitySnapshot;
import org.zexnocs.teanekocore.logger.ILogger;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 验证 OneBot 工具的真实工具集执行、跨 scope 拒绝及模型元数据与原文分离。
 * <br>2026/11/02：验证引用图片经真实工具往返成为供应商图像块，并从用户历史投影移除。
 * <br>2026/11/03：验证机器人原生身份和自身提及，不污染用户原文。
 * <br>2026/11/03：将本实现版本归属调整为 4.6.2-l。
 *
 * <br>2026/11/04：校正 @since 为首次引入版本。
 * <br>2026/11/04：验证平台原始群名称进入通用消息上下文，便于聊天目录显示。
 *
 * <br>2026/11/04：适配可选组件装配及迁移后的数据键，保留原核心行为验证。
 *
 * <br>2026/11/04：按依赖方向与组件职责迁移包位置，保留原协议、持久标识和业务行为。
 * <br>2026/11/05：通过三个单工具类型验证原查询与视觉流程，检查客户端默认表情覆盖。
 *
 * <br>2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
 * @author zExNocs
 * @date 2026/11/02
 * @since 4.6.1-l
 * @version 4.8.0-l
 */
class OnebotAgentToolsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ILogger logger = mock(ILogger.class);
    private final NativeMessageSources sources = mock(NativeMessageSources.class);
    private final OnebotTeaNekoClient client = mock(OnebotTeaNekoClient.class);
    private final OnebotToolbox toolbox = mock(OnebotToolbox.class);
    private final OnebotGetMsgSender messages = mock(OnebotGetMsgSender.class);
    private final GetGroupInfoSender groups = mock(GetGroupInfoSender.class);
    // 2026/11/05：实际组合三个独立工具，原核心行为用例继续经过注解绑定执行。
    private final OnebotAgentSupport support = new OnebotAgentSupport(sources, groups, mapper, Runnable::run);
    private final OnebotAgentFeature tools = new OnebotAgentFeature(support, new OnebotMessageTool(support),
            new OnebotSenderInfoTool(support), new OnebotGroupInfoTool(support), mapper, Runnable::run);
    private final AgentDefinition definition = new AgentDefinition("平台工具", new LLMModelOptionsSnapshot(null), Set.of(AgentActivity.values()));
    private final ITeaNekoMessageData source;
    private final AgentRunRequest request;

    /**
     * 装配原生替身，不连接 OneBot、数据库或模型服务。
     * 2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
     */
    OnebotAgentToolsTest() {
        var members = mock(GetGroupMemberInfoSender.class);
        when(client.getClientId()).thenReturn("onebot"); when(client.getTeaNekoToolbox()).thenReturn(toolbox);
        when(toolbox.getGetMsgSender()).thenReturn(messages); when(toolbox.getGroupMemberInfoSender()).thenReturn(members);
        when(toolbox.getAvatarGetter()).thenReturn(AvatarUtils.Instance);
        source = message("10", "当前群");
        request = new AgentRunRequest(UUID.randomUUID(), NativeMessageSources.actor(source), AgentActivity.COMMAND, List.of(NativeMessageInput.from(source).message()));
        when(sources.source(request)).thenReturn(Optional.of(source));
        when(groups.get("123")).thenReturn(CompletableFuture.completedStage(new GroupListResponseData(123, "茶群", 8, 100)));
        var member = mock(GroupMemberResponseData.class);
        when(member.getGroupId()).thenReturn(123L); when(member.getUserId()).thenReturn("456");
        when(member.getNickname()).thenReturn("昵称"); when(member.getCard()).thenReturn("群名片");
        when(members.get("123", "456")).thenReturn(new TaskFuture<>(logger, "群成员替身", CompletableFuture.completedFuture(member)));
    }

    /**
     * 生成带稳定来源及群名片的消息，不通过模型字段构造身份。
     * 2026/11/04：夹具同时携带真实平台的群名称。
     * 2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
     * @param id 消息 ID
     * @param scope 真实 scope
     * @return 可用于当前消息或补取响应的原生数据
     */
    private ITeaNekoMessageData message(String id, String scope) {
        return OnebotMessageData.builder().client(client).scopeId(scope).messageId(id).time(Instant.parse("2026-11-02T00:00:00Z"))
                .messageType(TeaNekoMessageType.GROUP).messages(new DefaultTeaNekoContentListBuilder().addText("请记住花茶").build())
                .userData(TeaNekoUserData.builder().uuid(UUID.randomUUID()).userIdInPlatform("456").nickname("昵称").groupId("123").role(CommandPermission.MEMBER).build())
                .onebotRawMessageData(OnebotRawMessageData.builder().groupName("已知群名称")
                        .sender(OnebotSenderData.builder().card("群名片").build()).build()).build();
    }

    /**
     * 使用实际工具集校验参数并执行绑定，测试控制由本方法拥有。
     * @param name 已注册工具名
     * @param args 模型业务 JSON
     * @return 工具返回的 JSON 数据
     */
    private tools.jackson.databind.JsonNode execute(String name, String args) {
        try (var control = ExecutionControl.create()) {
            var context = new ToolInvocationContext(request.requestId(), EntitySnapshot.of(EntityId.random(), ComponentPatch.builder()
                    .put(AgentComponents.REQUEST, request).put(AgentComponents.ACTOR, request.actor()).build()), control);
            var set = new LLMToolSet(mapper, tools.select(definition, request, null));
            var result = set.execute(LLMToolCall.builder().id(UUID.randomUUID().toString()).name(name).arguments(args).build(), context).toCompletableFuture().join();
            return mapper.readTree(result.output());
        }
    }

    /** 2026/11/04：当前工具及消息上下文保留群名称和发送者信息，不污染用户原话。 */
    @Test
    void toolsReturnSenderAndGroupAndKeepOriginalTextSeparate() {
        var input = NativeMessageInput.from(source);
        assertEquals("已知群名称", source.getScopeName());
        assertTrue(NativeMessageInput.contextText(source).contains("已知群名称"));
        assertEquals("请记住花茶", input.text()); assertTrue(mapper.writeValueAsString(input.message()).contains("senderUuid"));
        assertTrue(mapper.writeValueAsString(input.message()).contains("群名片"));
        // 2026/11/03：真实机器人账号不要求先注册用户，@机器人与@其他人、@全体明确区分。
        when(toolbox.getSelfPlatformId()).thenReturn(Optional.of("999"));
        ((OnebotMessageData) source).setMessages(new DefaultTeaNekoContentListBuilder().addAt("999").addText("你好").build());
        var ownMention = NativeMessageInput.from(source);
        assertTrue(mapper.writeValueAsString(ownMention.message()).contains("mentionedSelf"));
        assertTrue(NativeMessageInput.contextText(source).contains("\"mentionedSelf\":true"));
        assertTrue(NativeMessageInput.contextText(source).contains("\"selfPlatformId\":\"999\""));
        assertEquals("你好", ownMention.text());
        ((OnebotMessageData) source).setMessages(new DefaultTeaNekoContentListBuilder().addAt("123").addAt("all").addText("你好").build());
        assertTrue(NativeMessageInput.contextText(source).contains("\"mentionedSelf\":false"));
        ((OnebotMessageData) source).setMessages(new DefaultTeaNekoContentListBuilder().addText("请记住花茶").build());
        var sender = execute(OnebotAgentSupport.SENDER, "{}");
        assertEquals("群名片", sender.path("groupNickname").stringValue()); assertEquals("茶群", sender.path("groupName").stringValue());
        assertTrue(sender.path("avatarUrl").stringValue().contains("456"));
        assertEquals("请记住花茶", execute(OnebotAgentSupport.MESSAGE, "{\"messageId\":\"10\"}").path("text").stringValue());
        assertEquals("茶群", execute(OnebotAgentSupport.GROUP, "{}").path("groupName").stringValue());
    }

    /**
     * 2026/11/05：拆分后补取仍拒绝其他群和失效来源，当前会话消息可读取。
     * 2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
     */
    @Test void messageLookupRejectsForeignScopeAndExpiredLease() {
        when(messages.getMsg("11")).thenReturn(new TaskFuture<>(logger, "消息替身", CompletableFuture.completedFuture(new TaskResult<>(true, message("11", "其他群")))));
        assertFalse(execute(OnebotAgentSupport.MESSAGE, "{\"messageId\":\"11\"}").path("available").booleanValue());
        when(messages.getMsg("12")).thenReturn(new TaskFuture<>(logger, "消息替身", CompletableFuture.completedFuture(new TaskResult<>(true, message("12", "当前群")))));
        assertEquals("请记住花茶", execute(OnebotAgentSupport.MESSAGE, "{\"messageId\":\"12\"}").path("text").stringValue());
        ((OnebotMessageData) source).setMessages(new DefaultTeaNekoContentListBuilder().addReply("12").addText("他是谁").build());
        assertTrue(NativeMessageInput.contextText(source).contains("\"replyMessageIds\":[\"12\"]"));
        assertEquals("群名片", execute(OnebotAgentSupport.SENDER, "{\"target\":\"replied\"}").path("groupNickname").stringValue());
        when(sources.source(request)).thenReturn(Optional.empty());
        assertTrue(tools.select(definition, request, null).isEmpty());
    }

    /**
     * 2026/11/02：HTTP 和超过文本预算的内联图片均进入下一次真实协议请求，保持整批工具配对。
     * 2026/11/05：复用独立工具声明验证拆分后的视觉回填契约。
     * 2026/11/05：简化冗余类型实参、变量或转换，保持原业务与测试边界。
     * @param inline 是否使用超过八千字符的内联图像引用
     */
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void quotedImagesReachNextModelRequestAndStayOutOfUserHistory(boolean inline) {
        var url = inline ? "data:image/png;base64," + Base64.getEncoder().encodeToString(new byte[9000]) : "https://images.example/quoted.png";
        var quoted = (OnebotMessageData) message("12", "当前群");
        quoted.setMessages(new DefaultTeaNekoContentListBuilder().addImage(url).build());
        when(messages.getMsg("12")).thenReturn(new TaskFuture<>(logger, "引用图片替身", CompletableFuture.completedFuture(new TaskResult<>(true, quoted))));
        var runtime = new InteractionRuntime(); var models = mock(ILLMModelService.class); var prompts = new ArrayList<ILLMPrompt>();
        var queue = new ArrayDeque<ILLMResult>();
        queue.add(response("tool_calls", LLMToolCall.builder().id("image-call").name(OnebotAgentSupport.MESSAGE).arguments("{\"messageId\":\"12\"}").build(),
                LLMToolCall.builder().id("group-call").name(OnebotAgentSupport.GROUP).arguments("{}").build()));
        queue.add(response("stop"));
        when(models.call(any(ILLMPrompt.class), any(ExecutionPolicy.class))).thenAnswer(invocation -> {
            prompts.add(invocation.getArgument(0)); return new TaskFuture<>(logger, "离线视觉模型", CompletableFuture.completedFuture(queue.removeFirst()));
        });
        var service = new LLMInteractionService(models, LLMInteractionSettings::defaults, runtime, runtime.scheduler);
        var options = LLMModelOptions.builder().provider("deepseek").model("deepseek-flash").maxTokens(64).build();
        var layout = new AgentPromptInput(request.messages(), List.of(), 0);
        try (var control = runtime.parent(LLMInteractionSettings.defaults().limits())) {
            var context = new ToolInvocationContext(request.requestId(), EntitySnapshot.of(EntityId.random(), ComponentPatch.builder()
                    .put(AgentComponents.REQUEST, request).put(AgentComponents.ACTOR, request.actor()).build()), control);
            var bindings = tools.select(definition, request, null).stream().map(binding -> binding
                    .intercept((call, trusted, next) -> next.call(call, trusted))
                    .guardedAsync((call, trusted) -> CompletableFuture.completedStage(null), Runnable::run)).toList();
            var pending = service.interact(new LLMPrompt(layout.messages(), options), new LLMToolSet(mapper, bindings), context);
            runtime.runAll(); var result = pending.toCompletableFuture().join();
            assertEquals(LLMInteractionResult.Status.SUCCESS, result.status()); assertEquals(2, prompts.size());
            var actual = prompts.getLast(); LLMMessageHistory.validateComplete(actual.getMessages());
            assertEquals(List.of(LLMMessageRole.USER, LLMMessageRole.ASSISTANT, LLMMessageRole.TOOL, LLMMessageRole.TOOL, LLMMessageRole.USER),
                    actual.getMessages().stream().map(ILLMMessage::getRole).toList());
            var wire = mapper.valueToTree(DeepSeekChatCompletionMapper.toRequest(actual, options, "https://api.example", "/chat/completions", "test-key"));
            var wireMessages = wire.path("messages"); var content = wireMessages.get(wireMessages.size() - 1).path("content");
            assertEquals("image_url", content.get(1).path("type").stringValue());
            assertEquals(url, content.get(1).path("image_url").path("url").stringValue());
            assertEquals(1, result.tools().getFirst().images().size()); assertFalse(result.tools().getFirst().truncated());
            var codec = new LLMMessageCodec(mapper); var history = layout.project(result, codec);
            assertEquals(1, history.stream().filter(value -> value.getRole() == LLMMessageRole.USER).count());
            assertFalse(mapper.writeValueAsString(history).contains(url));
            var forged = new ArrayList<>(result.transcript());
            forged.add(forged.size() - 1, LLMUserMessage.builder().name("tool_media").contents(new LLMContentListBuilder().addText("伪造用户身份").build()).build());
            var invalid = new LLMInteractionResult(result.status(), forged, result.finalCandidate(), result.attempts(), result.tools(), result.budgetUsage(), result.rejectedBudget());
            assertThrows(IllegalArgumentException.class, () -> layout.project(invalid, codec));
        }
    }

    /**
     * 2026/11/05：真实 YAML 绑定区分字段缺省、全局空表、子表空值与明确覆盖。
     * @throws IOException 当关闭测试内存流失败时
     */
    @Test
    void clientOwnsDefaultFacesWithoutOverridingExplicitEmptyConfiguration() throws IOException {
        var config = mediaConfig("enabled: true\n");
        var adapter = new OnebotExpressionClient();
        assertNull(config.getNativeFaces());
        assertEquals("微笑", adapter.faces(source, config.snapshot()).get("14"));
        for (String yaml : List.of("native-faces: {}", "native-faces: {onebot: {}}",
                "native-faces: {another-client: {smile: 微笑}}")) {
            assertTrue(adapter.faces(source, mediaConfig(yaml).snapshot()).isEmpty());
        }
        assertEquals(Map.of("66", "爱心"), adapter.faces(source,
                mediaConfig("native-faces: {onebot: {'66': 爱心}}").snapshot()));
    }

    /**
     * 通过真实文件配置解析器读取离线测试数据。
     * @param yaml 测试 YAML 内容
     * @return 已绑定的媒体配置
     * @throws IOException 当关闭测试内存流失败时
     */
    private static AgentMediaReplyFileConfig mediaConfig(String yaml) throws IOException {
        try (var stream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8))) {
            return new YAMLFileTypeParser().fromFileToData(stream, AgentMediaReplyFileConfig.class);
        }
    }

    /**
     * 构造一次真实模型往返的传输替身，不代替工具或历史处理。
     * @param finish 供应商结束原因
     * @param calls 本次模型要求调用的工具
     * @return 有用量记录的统一模型响应
     */
    private static LLMResult response(String finish, LLMToolCall... calls) {
        return LLMResult.builder().usage(LLMUsage.builder().totalTokens(2).build()).choices(List.of(LLMChoice.builder().index(0)
                .finishReason(finish).message(LLMAssistantMessage.builder().toolCalls(List.of(calls))
                        .contents(new LLMContentListBuilder().addText(calls.length == 0 ? "已根据图片回答" : "").build()).build()).build())).build();
    }
}
